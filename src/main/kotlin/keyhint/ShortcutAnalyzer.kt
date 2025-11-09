package keyhint

import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.awt.event.KeyEvent

/**
 * 快捷键分析器，用于实时跟踪和分析当前可用的快捷键组合。
 *
 * 这个类是IDEA插件"Key Hint"的核心组件之一，负责：
 * 1. 监听键盘事件并维护当前按键状态
 * 2. 构建快捷键前缀树(Trie)用于快速查找
 * 3. 提供当前可用的快捷键提示
 *
 * @property actionIdPattern 用于匹配编辑器动作的正则表达式，决定哪些动作会被纳入快捷键提示
 */
class ShortcutAnalyzer(private var actionIdPredicate: (actionId: String) -> Boolean = { true }) {

	// -------- Public API --------

	/**
	 * 检查当前是否有修饰键(Ctrl/Alt/Shift等)被按下
	 * @return true表示当前有修饰键被按下
	 */
	fun isTypingShortcut(): Boolean = pressedModifiers.isNotEmpty()

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
		return shortcutIndex.findEntriesByKeyCodePrefix(pressedKeys.toIntArray())
	}

	/**
	 * 处理键盘事件并更新内部状态
	 * @param e 键盘事件对象
	 */
	fun onKeyEvent(e: KeyEvent) {
		keyIntent = KeyIntent.CharacterTyping
		val id = e.id
		when (id) {
			KeyEvent.KEY_TYPED -> {}

			KeyEvent.KEY_PRESSED -> {
				val keyIndex = e.keyCode
				if (keyIndex in keyStates) return
				keyStates.add(keyIndex)
				pressedKeys.add(keyIndex)

				if (isModifierKey(e)) {
					if (pressedModifiers.isEmpty) keyIntent = KeyIntent.ShortcutPreparation
					pressedModifiers.add(keyIndex)
				} else {
					keyIntent = if (pressedModifiers.isEmpty) KeyIntent.CharacterTyping
					else KeyIntent.ShortcutComposition
				}

				stateChanged = true

				val arr = pressedKeys.toIntArray()
				shortcutIndex.findEntriesByExactKeyCodes(arr)?.let { entries ->
					keyIntent = KeyIntent.ShortcutTriggered
					entries.forEach { it.incUseCount() }
				}
			}

			KeyEvent.KEY_RELEASED -> {
				val keyIndex = e.keyCode
				if (keyIndex !in keyStates) return
				keyStates.remove(keyIndex)
				pressedKeys.rem(keyIndex)
				if (isModifierKey(e)) {
					pressedModifiers.rem(keyIndex)
					if (pressedModifiers.isEmpty) keyIntent = KeyIntent.ShortcutAborted
				}

				if (keyIndex != KeyEvent.VK_ESCAPE)
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
		actionIdPattern: (actionId: String) -> Boolean = actionIdPredicate
	) {
		shortcutIndex = ShortcutIndex(keymap, actionIdPattern)
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
	private val pressedKeys = IntArrayList()
	private val pressedModifiers = IntArrayList()
	private var stateChanged = false
	private var keyIntent = KeyIntent.CharacterTyping

	// 静态数据结构

	private var shortcutIndex = ShortcutIndex(actionIdPredicate = actionIdPredicate)

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
