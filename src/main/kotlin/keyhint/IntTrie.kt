package keyhint

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

class IntTrie {
	private val children = Int2ObjectOpenHashMap<IntTrieNode>()

	fun add(arr: IntArray) {
		var map = children
		for (int in arr) {
			val node = map[int] ?: IntTrieNode().also { map.put(int, it) }
			map = node.children()
		}
	}

	fun findSuffixByPrefix(arr: IntArray): Sequence<IntArray> {
		var map = children
		for (int in arr) {
			map = map[int]?.childrenOrNull() ?: return emptySequence()
		}
		return map.int2ObjectEntrySet().asSequence()
			.flatMap { entry ->
				deepFirstSearchPath(entry) { (_, node) -> node.children().int2ObjectEntrySet() }
			}
			.map { entries -> entries.map { it.intKey }.toIntArray() }
	}

	fun clear() {
		children.clear()
	}

	private class IntTrieNode {
		private var children: Int2ObjectOpenHashMap<IntTrieNode>? = null
		fun childrenOrNull() = children
		fun children() = children ?: Int2ObjectOpenHashMap<IntTrieNode>().also { children = it }
	}
}