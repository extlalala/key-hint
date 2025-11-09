package keyhint

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.awt.event.KeyEvent
import javax.swing.KeyStroke



/**
 * 快捷键条目数据类，封装快捷键及其关联的动作信息
 * @property shortcut 键盘快捷键对象
 * @property actionId 关联的动作ID
 * @property useCount 该快捷键被使用的次数统计
 */
class ShortcutEntry(val shortcut: KeyboardShortcut, val actionId: String) {
	var useCount = 0; private set
	var state = State.DEFAULT
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

	/** 快捷键状态枚举 */
	enum class State(val sortKey: Int) {
		DEFAULT(0),     // 普通状态
		FAVORITE(-1),   // 已收藏
		DISLIKED(1)     // 不喜欢
	}

	private fun prettyDesc(shortcut: KeyboardShortcut, actionId: String): String {
		val shortcutText = KeymapUtil.getShortcutText(shortcut)
		val actionText = ActionManager.getInstance().getAction(actionId)?.templatePresentation?.text ?: actionId

		return "$shortcutText - $actionText"
	}
}



class ShortcutIndex(
	keymap: Keymap = KeymapManagerEx.getInstanceEx().activeKeymap,
	actionIdPredicate: (actionId: String) -> Boolean = { true }
) {

	// -------- Public API --------

	fun findEntriesByExactKeyCodes(keyCodes: IntArray): List<ShortcutEntry>? = trie.findValueByExactMatch(keyCodes)

	fun findEntriesByKeyCodePrefix(prefix: IntArray): Sequence<ShortcutEntry> = trie.findValuesByPrefix(prefix).flatten()

	// -------- Private Implementation --------

	private val trie = IntValueTrie<MutableList<ShortcutEntry>>()

	init {
		val actionManager = ActionManager.getInstance()
		val actionIdList = actionManager.getActionIdList("")

		actionIdList.asSequence()
			.filter(actionIdPredicate)
			.flatMap { actionId ->
				keymap.getShortcuts(actionId).asSequence()
					.map { shortcut -> shortcut to actionId }
			}
			.filter { (shortcut, _) -> shortcut.isKeyboard }
			.forEach { (shortcut, actionId) ->
				val keyCodes = keyCodes(shortcut as KeyboardShortcut)
				if (keyCodes.size <= 1) return@forEach

				val entry = ShortcutEntry(shortcut, actionId)
				trie.getOrPut(keyCodes) { mutableListOf() }.add(entry)
			}
	}

	private fun keyCodes(shortcut: KeyboardShortcut): IntArray {
		val keyCodes = IntArrayList()

		getKeyStrokeCodes(shortcut.firstKeyStroke, keyCodes)
		shortcut.secondKeyStroke?.let { second -> getKeyStrokeCodes(second, keyCodes) }

		return keyCodes.toIntArray()
	}

	private fun getKeyStrokeCodes(keyStroke: KeyStroke, out: IntArrayList) {
		val modifiers = keyStroke.modifiers
		if (modifiers and KeyEvent.CTRL_DOWN_MASK != 0) out += KeyEvent.VK_CONTROL
		if (modifiers and KeyEvent.ALT_DOWN_MASK != 0) out += KeyEvent.VK_ALT
		if (modifiers and KeyEvent.SHIFT_DOWN_MASK != 0) out += KeyEvent.VK_SHIFT
		if (modifiers and KeyEvent.META_DOWN_MASK != 0) out += KeyEvent.VK_META
		out += keyStroke.keyCode
	}
}