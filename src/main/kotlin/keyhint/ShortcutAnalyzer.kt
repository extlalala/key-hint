package keyhint

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

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
class ShortcutAnalyzer(private var actionIdPattern: Regex) {

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
	 * 更新动作ID匹配模式并重新加载快捷键配置
	 * @param pattern 新的正则表达式模式
	 */
	fun setActionIdPattern(pattern: Regex) {
		actionIdPattern = pattern
		reload()
	}

	/**
	 * 获取当前可用的快捷键提示序列
	 * @return 匹配当前按键前缀的所有快捷键条目序列
	 */
	fun currentShortcuts(): Sequence<ShortcutEntry> {
		val prefix = pressedKeys.toIntArray()
		val suffixes = trie.findSuffixByPrefix(prefix)
		return suffixes.flatMap { suffix ->
			val key = HashableIntArray(prefix + suffix)
			keyCodes2entry[key].orEmpty().asSequence()
		}
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

				val arr = HashableIntArray(pressedKeys.toIntArray())
				keyCodes2entry[arr]?.let { entries ->
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
	fun reload() {
		keyCodes2entry.clear()
		trie.clear()
		val keymap = KeymapManagerEx.getInstanceEx().activeKeymap
		val actionManager = ActionManager.getInstance()
		val actionIdList = actionManager.getActionIdList("")

		actionIdList.asSequence()
			.filter { actionIdPattern.matches(it) }
			.flatMap { actionId ->
				keymap.getShortcuts(actionId).asSequence()
					.map { shortcut -> shortcut to actionId }
			}
			.filter { (shortcut, _) -> shortcut.isKeyboard }
			.forEach { (shortcut, actionId) ->
				val keyCodes = keyCodes(shortcut as KeyboardShortcut)

				if (keyCodes.size <= 1) return@forEach

				val entry = ShortcutEntry(shortcut, actionId)

				keyCodes2entry.getOrPut(HashableIntArray(keyCodes)) { mutableListOf() }.add(entry)
				trie.add(keyCodes)
			}
	}

	/**
	 * 快捷键条目数据类，封装快捷键及其关联的动作信息
	 * @property shortcut 键盘快捷键对象
	 * @property actionId 关联的动作ID
	 * @property useCount 该快捷键被使用的次数统计
	 */
	class ShortcutEntry(val shortcut: KeyboardShortcut, val actionId: String) {
		var useCount = 0; private set
		private var prettyDesc: String? = null

		/** 增加该快捷键的使用计数 */
		fun incUseCount() {
			useCount++
		}

		/**
		 * 获取格式化后的快捷键描述
		 * 格式示例: "Ctrl+Alt+S - Save All"
		 */
		fun prettyDesc() = prettyDesc ?: prettyDesc(shortcut, actionId).also { prettyDesc = it }

		private fun prettyDesc(shortcut: KeyboardShortcut, actionId: String): String {
			val shortcutText = KeymapUtil.getShortcutText(shortcut)
			val actionText = ActionManager.getInstance().getAction(actionId)?.templatePresentation?.text ?: actionId

			return "$shortcutText - $actionText"
		}
	}

	/** KeyEvent的意图 */
	enum class KeyIntent {
		CharacterTyping,     // 输入字符, 修饰键未按下
		ShortcutPreparation, // 准备输入快捷键, 修饰键刚按下
		ShortcutComposition, // 输入快捷键
		ShortcutTriggered,   // 触发一次快捷键
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
	private val trie = IntTrie()
	private val keyCodes2entry = mutableMapOf<HashableIntArray, MutableList<ShortcutEntry>>()

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

	private fun keyCodes(shortcut: KeyboardShortcut): IntArray {
		val keyCodes = IntArrayList()

		getKeyCodes(shortcut.firstKeyStroke, keyCodes)
		shortcut.secondKeyStroke?.let { second -> getKeyCodes(second, keyCodes) }

		return keyCodes.toIntArray()
	}

	private fun getKeyCodes(keyStroke: KeyStroke, out: IntArrayList) {
		val modifiers = keyStroke.modifiers
		if (modifiers and KeyEvent.CTRL_DOWN_MASK != 0) out += KeyEvent.VK_CONTROL
		if (modifiers and KeyEvent.ALT_DOWN_MASK != 0) out += KeyEvent.VK_ALT
		if (modifiers and KeyEvent.SHIFT_DOWN_MASK != 0) out += KeyEvent.VK_SHIFT
		if (modifiers and KeyEvent.META_DOWN_MASK != 0) out += KeyEvent.VK_META
		out += keyStroke.keyCode
	}
}