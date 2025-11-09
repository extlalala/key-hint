package keyhint

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

class IntValueTrie<T : Any> {
	private val root = TrieNode<T>()

	fun put(arr: IntArray, value: T) {
		findOrCreateNode(arr).value = value
	}

	fun getOrPut(arr: IntArray, defaultValue: () -> T): T {
		val node = findOrCreateNode(arr)
		return node.value ?: defaultValue().also { node.value = it }
	}

	fun findValueByExactMatch(arr: IntArray): T? = findNodeOrNull(arr)?.value

	fun findValuesByPrefix(arr: IntArray): Sequence<T> {
		val seed = findNodeOrNull(arr) ?: return emptySequence()
		return sequence {
			deepFirstSearch(
				seed,
				{ node -> node.childrenOrNull()?.values.orEmpty() },
				{ node -> node.value?.let { yield(it) } }
			)
		}
	}

	fun clear() {
		root.childrenOrNull()?.clear()
	}

	private fun findNodeOrNull(arr: IntArray): TrieNode<T>? {
		var node = root
		for (int in arr)
			node = node.childrenOrNull()?.get(int) ?: return null
		return node
	}

	private fun findOrCreateNode(arr: IntArray): TrieNode<T> {
		var node = root
		for (int in arr)
			node = node.children()[int] ?: TrieNode<T>().also { node.children()[int] = it }
		return node
	}

	private class TrieNode<T> {
		var value: T? = null
		private var children: Int2ObjectOpenHashMap<TrieNode<T>>? = null

		fun childrenOrNull() = children
		fun children() = children ?: Int2ObjectOpenHashMap<TrieNode<T>>().also { children = it }
	}
}
