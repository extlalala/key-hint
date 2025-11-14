package keyhint

import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.awt.event.KeyEvent

/**
 * 快捷键分析器，用于实时跟踪和分析当前可用的快捷键组合。
 */
class ShortcutAnalyzer(private var actionIdFilter: (actionId: String) -> Boolean = { true }) {

	// -------- Public API --------

	/**
	 * 检查内部状态是否发生变化
	 * @return true表示自上次检查后状态已改变
	 */
	fun stateChanged(): Boolean = stateChanged.also { stateChanged = false }

	fun keyIntent(): KeyIntent = keyIntent

	/**
	 * 获取当前可用的快捷键提示序列
	 * @return 匹配当前按键前缀的所有快捷键条目序列
	 */
	fun currentShortcuts(): Sequence<ShortcutEntry> {
		if (lastPressedKey == -1) return shortcutIndex.findEntriesForPartialModifiers(pressedModifiers.toIntArray())
		return shortcutIndex.findEntriesForExactShortcut(pressedModifiers.toIntArray(), lastPressedKey)?.asSequence().orEmpty()
	}

	/**
	 * 处理键盘事件并更新内部状态
	 * @param e 键盘事件对象
	 */
	fun onKeyEvent(e: KeyEvent) {
		keyIntent = KeyIntent.CharacterTyping
		stateChanged = false
		when (e.id) {
			KeyEvent.KEY_TYPED -> {}

			KeyEvent.KEY_PRESSED -> {
				val keyCode = e.keyCode
				if (keyCode in keyStates) return
				keyStates.add(keyCode)

				val isModifierKey = isModifierKey(e)
				if (isModifierKey) {
					keyIntent = if (pressedModifiers.isEmpty) KeyIntent.ShortcutPreparation else KeyIntent.ShortcutComposition
					pressedModifiers.add(keyCode)
				} else {
					keyIntent = if (pressedModifiers.isEmpty) KeyIntent.CharacterTyping else KeyIntent.ShortcutComposition
					lastPressedKey = keyCode
				}

				stateChanged = true

				if (!isModifierKey)
					shortcutIndex.findEntriesForExactShortcut(pressedModifiers.toIntArray(), keyCode)?.let { entries ->
						keyIntent = KeyIntent.ShortcutTriggered
						entries.forEach { it.incUseCount() }
					}
			}

			KeyEvent.KEY_RELEASED -> {
				val keyCode = e.keyCode
				if (keyCode !in keyStates) return
				keyStates.remove(keyCode)
				lastPressedKey = -1

				if (isModifierKey(e)) {
					pressedModifiers.rem(keyCode)
					keyIntent = if (pressedModifiers.isEmpty) KeyIntent.ShortcutAborted else KeyIntent.ShortcutComposition
				}

				if (keyCode != KeyEvent.VK_ESCAPE)
					stateChanged = true
			}
		}
	}

	/**
	 * 重新加载所有快捷键配置
	 * 从当前活动的keymap中读取所有匹配的编辑器动作及其快捷键
	 */
	fun reload(
		keymap: Keymap = KeymapManagerEx.getInstanceEx().activeKeymap,
		actionIdFilter: (actionId: String) -> Boolean = this.actionIdFilter
	) {
		shortcutIndex = ShortcutIndex(keymap, actionIdFilter)
	}

	/** KeyEvent的意图 */
	enum class KeyIntent {
		CharacterTyping,     // 输入字符,      修饰键未按下
		ShortcutPreparation, // 准备输入快捷键, 修饰键刚按下
		ShortcutComposition, // 输入快捷键
		ShortcutTriggered,   // 触发一次快捷键, 修饰键未放开
		ShortcutAborted      // 结束输入快捷键, 修饰键被释放
	}

	// -------- Private Implementation --------

	// 动态维护的按键状态
	private val keyStates = IntOpenHashSet()
	private val pressedModifiers = IntArrayList()
	private var stateChanged = false
	private var keyIntent = KeyIntent.CharacterTyping
	private var lastPressedKey = -1

	// 静态数据结构

	private var shortcutIndex = ShortcutIndex(actionIdFilter = actionIdFilter)

	init {
		reload()
	}

	private fun isModifierKey(e: KeyEvent): Boolean = when (e.keyCode) {
		KeyEvent.VK_CONTROL,
		KeyEvent.VK_ALT,
		KeyEvent.VK_SHIFT,
		KeyEvent.VK_META,
		KeyEvent.VK_ALT_GRAPH -> true

		else -> false
	}
}
