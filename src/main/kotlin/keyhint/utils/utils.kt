package keyhint.utils

@Suppress("UNCHECKED_CAST")
fun <T> Any?.unsafeCast(): T = this as T

inline fun <T> T?.nullOr(predicate: (T) -> Boolean): Boolean = this == null || predicate(this)
inline fun <T> T?.exists(predicate: (T) -> Boolean): Boolean = this != null && predicate(this)

inline fun <I : Iterator<*>> I.loopWhileHasNext(action: (I) -> Unit) {
	while (hasNext())
		action(this)
}

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

/**
 * 遍历所有节点, 不过滤已访问节点
 */
inline fun <T> deepFirstSearch(seed: T, update: (T) -> Collection<T>, out: (T) -> Unit) {
	val stack = mutableListOf(seed)
	while (stack.isNotEmpty()) {
		val current = stack.removeLast()
		out(current)
		for (child in update(current))
			stack.add(child)
	}
}

inline fun <T, P> deepFirstSearchPath(
	seed: T,
	crossinline update: (T) -> Collection<T>,

	crossinline mkPath: (T) -> P,
	crossinline concatPath: (P, T) -> P,
	crossinline getPathLast: (P) -> T
): Sequence<P> {
	val stack = mutableListOf(mkPath(seed))
	return sequence {
		while (stack.isNotEmpty()) {
			val currentPath = stack.removeLast()
			val current = getPathLast(currentPath)

			val children = update(current)
			if (children.isEmpty()) {
				yield(currentPath)
				continue
			}

			for (child in children) {
				val childPath = concatPath(currentPath, child)
				stack.add(childPath)
			}
		}
	}
}


class HashableIntArray(val arr: IntArray) {
	override fun hashCode(): Int = arr.contentHashCode()
	override fun equals(other: Any?): Boolean = other is HashableIntArray && arr.contentEquals(other.arr)
}
