package keyhint

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import keyhint.utils.loopWhileHasNext
import keyhint.utils.unsafeCast
import java.awt.event.KeyEvent



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
	actionIdFilter: (actionId: String) -> Boolean = { true }
) {

	// -------- Public API --------

	fun findEntriesForPartialModifiers(modifiers: IntArray): Sequence<ShortcutEntry> = modifiers.asSequence()
		.map { singleModifier2shortcut[it] }
		.fold(mutableSetOf<ShortcutIndexKey>()) { acc, shortcutKeys -> if (acc.isEmpty()) shortcutKeys.toMutableSet() else acc.also { acc.retainAll(shortcutKeys) } }
		.asSequence()
		.flatMap { key2entries[it].orEmpty() }

	fun findEntriesForExactShortcut(modifiers: IntArray, keyCode: Int): List<ShortcutEntry>? = key2entries[ShortcutIndexKey(modifierBits(modifiers), keyCode)]

	// -------- Private Implementation --------

	private data class ShortcutIndexKey(val modifiers: Int, val keyCode: Int)

	private val singleModifier2shortcut: Int2ObjectMap<Set<ShortcutIndexKey>>
	private val key2entries: Map<ShortcutIndexKey, List<ShortcutEntry>>

	init {
		val singleModifier2shortcut = Int2ObjectOpenHashMap<MutableSet<ShortcutIndexKey>>()
		val key2entries = mutableMapOf<ShortcutIndexKey, MutableList<ShortcutEntry>>()

		val actionManager = ActionManager.getInstance()
		val actionIdList = actionManager.getActionIdList("")

		actionIdList.asSequence()
			.filter(actionIdFilter)
			.flatMap { actionId ->
				keymap.getShortcuts(actionId).asSequence()
					.map { shortcut -> shortcut to actionId }
			}
			.filter { (shortcut, _) -> shortcut.isKeyboard }
			.forEach { (shortcut, actionId) ->
				shortcut as KeyboardShortcut
				val entry = ShortcutEntry(shortcut, actionId)

				val modifiers = shortcut.firstKeyStroke.modifiers
				val modifierKeyCodes = IntArrayList()
				getModifierKeyCodes(modifiers, modifierKeyCodes)

				val indexKey = ShortcutIndexKey(modifiers, shortcut.firstKeyStroke.keyCode)

				modifierKeyCodes.iterator().loopWhileHasNext { iter ->
					singleModifier2shortcut.getOrPut(iter.nextInt()) { hashSetOf() }.add(indexKey)
				}
				key2entries.getOrPut(indexKey) { mutableListOf() }.add(entry)
			}

		this.singleModifier2shortcut = singleModifier2shortcut.unsafeCast()
		this.key2entries = key2entries
	}

	private fun getModifierKeyCodes(modifiers: Int, out: IntArrayList) {
		if (modifiers and KeyEvent.CTRL_DOWN_MASK != 0) out += KeyEvent.VK_CONTROL
		if (modifiers and KeyEvent.ALT_DOWN_MASK != 0) out += KeyEvent.VK_ALT
		if (modifiers and KeyEvent.SHIFT_DOWN_MASK != 0) out += KeyEvent.VK_SHIFT
		if (modifiers and KeyEvent.META_DOWN_MASK != 0) out += KeyEvent.VK_META
		if (modifiers and KeyEvent.ALT_GRAPH_DOWN_MASK != 0) out += KeyEvent.VK_ALT_GRAPH
	}

	private fun modifierBits(modifiers: IntArray): Int {
		var bits = 0
		for (modifier in modifiers) when (modifier) {
			KeyEvent.VK_CONTROL -> bits = bits or KeyEvent.CTRL_DOWN_MASK
			KeyEvent.VK_ALT -> bits = bits or KeyEvent.ALT_DOWN_MASK
			KeyEvent.VK_SHIFT -> bits = bits or KeyEvent.SHIFT_DOWN_MASK
			KeyEvent.VK_META -> bits = bits or KeyEvent.META_DOWN_MASK
			KeyEvent.VK_ALT_GRAPH -> bits = bits or KeyEvent.ALT_GRAPH_DOWN_MASK
		}
		return bits
	}
}
