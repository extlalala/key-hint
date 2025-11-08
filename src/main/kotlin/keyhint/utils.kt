package keyhint


inline fun <T> T?.nullOr(predicate: (T) -> Boolean): Boolean = this == null || predicate(this)
inline fun <T> T?.exists(predicate: (T) -> Boolean): Boolean = this != null && predicate(this)


/**
 * 返回从seed到每个叶子节点的路径, 不过滤已访问节点
 */
inline fun <T> deepFirstSearchPath(seed: T, crossinline update: (T) -> Collection<T>): Sequence<List<T>> {
	val stack = mutableListOf(listOf(seed))
	return sequence {
		while (stack.isNotEmpty()) {
			val currentPath = stack.removeLast()
			val current = currentPath.last()

			val children = update(current)
			if (children.isEmpty()) {
				yield(currentPath)
				continue
			}

			for (child in children) {
				val childPath = currentPath + child
				stack.add(childPath)
			}
		}
	}
}

class HashableIntArray(val arr: IntArray) {
	override fun hashCode(): Int = arr.contentHashCode()
	override fun equals(other: Any?): Boolean = other is HashableIntArray && arr.contentEquals(other.arr)
}
