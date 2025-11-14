package keyhint.utils

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import kotlin.reflect.KMutableProperty1

sealed interface ConfigComponent<T> {
	data class BooleanField<T>(val name: String, val get: (T) -> Boolean, val set: (T, Boolean) -> Unit) : ConfigComponent<T>
	data class StringField<T>(val name: String, val get: (T) -> String, val set: (T, String) -> Unit) : ConfigComponent<T>
	data class EnumField<T>(val name: String, val entries: List<String>, val getIndex: (T) -> Int, val setIndex: (T, index: Int) -> Unit) : ConfigComponent<T>

	data class SubConfig<T>(val name: String, val components: List<ConfigComponent<T>>) : ConfigComponent<T>
}

class Configuration<T>(val configComponents: List<ConfigComponent<T>>)

class ConfigurationBuilder<T> {
	inline fun <reified E : Enum<E>> enum(property: KMutableProperty1<T, E?>, default: E, name: String = property.name): ConfigurationBuilder<T> = enum(name, E::class.java, { property.get(it) ?: default }, property.setter)

	inline fun <reified E : Enum<E>> enum(property: KMutableProperty1<T, E>, name: String = property.name): ConfigurationBuilder<T> = enum(name, E::class.java, property.getter, property.setter)

	fun <E : Enum<E>> enum(name: String, clazz: Class<E>, get: (T) -> E, set: (T, E) -> Unit): ConfigurationBuilder<T> = addField(
		ConfigComponent.EnumField(
			name,
			clazz.enumConstants.map { it.name },
			{ obj -> get(obj).ordinal },
			{ obj, index -> set(obj, clazz.enumConstants[index]) }
		)
	)

	fun boolean(property: KMutableProperty1<T, Boolean>, name: String = property.name): ConfigurationBuilder<T> = boolean(name, property.getter, property.setter)

	fun boolean(name: String, get: (T) -> Boolean, set: (T, Boolean) -> Unit): ConfigurationBuilder<T> = addField(ConfigComponent.BooleanField(name, get, set))

	fun string(property: KMutableProperty1<T, String>, name: String = property.name): ConfigurationBuilder<T> = string(name, property.getter, property.setter)

	fun string(name: String, get: (T) -> String, set: (T, String) -> Unit): ConfigurationBuilder<T> = addField(ConfigComponent.StringField(name, get, set))

	fun build(): Configuration<T> = Configuration(fields.toList())

	fun subConfig(name: String): ConfigurationBuilder<T> = apply {
		stack += name to mutableListOf()
	}

	fun endSubConfig(): ConfigurationBuilder<T> = apply {
		val (name, components) = stack.removeLast()
		addField(ConfigComponent.SubConfig(name, components))
	}

	private val fields = mutableListOf<ConfigComponent<T>>()
	private val stack = mutableListOf<Pair<String, MutableList<ConfigComponent<T>>>>()

	private fun addField(field: ConfigComponent<T>): ConfigurationBuilder<T> = apply {
		val current = stack.lastOrNull()?.second ?: fields
		current += field
	}
}

fun <T> Iterable<UiBinding<T>>.apply(config: T) {
	forEach { it.apply(config) }
}

fun <T> Iterable<UiBinding<T>>.reset(config: T) {
	forEach { it.reset(config) }
}

fun <T> Iterable<UiBinding<T>>.isModified(config: T): Boolean = any { it.isModified(config) }

interface UiBinding<T> {
	fun apply(config: T)
	fun reset(config: T)
	fun isModified(config: T): Boolean
}

class ComboBoxBinding<T>(val component: ComboBox<*>, private val value: ConfigComponent.EnumField<T>) : UiBinding<T> {
	override fun apply(config: T) {
		value.setIndex(config, component.selectedIndex)
	}

	override fun reset(config: T) {
		component.selectedIndex = value.getIndex(config)
	}

	override fun isModified(config: T): Boolean = component.selectedIndex != value.getIndex(config)
}

class JBTextFieldBinding<T>(val component: JBTextField, private val value: ConfigComponent.StringField<T>) : UiBinding<T> {
	override fun apply(config: T) {
		value.set(config, component.text)
	}

	override fun reset(config: T) {
		component.text = value.get(config)
	}

	override fun isModified(config: T): Boolean = component.text != value.get(config)
}

class JBCheckBoxBinding<T>(val component: JBCheckBox, private val value: ConfigComponent.BooleanField<T>) : UiBinding<T> {
	override fun apply(config: T) {
		value.set(config, component.isSelected)
	}

	override fun reset(config: T) {
		component.isSelected = value.get(config)
	}

	override fun isModified(config: T): Boolean = component.isSelected != value.get(config)
}