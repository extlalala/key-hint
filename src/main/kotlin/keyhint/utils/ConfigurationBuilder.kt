package keyhint.utils

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import java.awt.Component
import kotlin.reflect.KMutableProperty1

sealed interface FieldOf<T> {
	data class BooleanField<T>(val name: String, val get: (T) -> Boolean, val set: (T, Boolean) -> Unit) : FieldOf<T>
	data class StringField<T>(val name: String, val get: (T) -> String, val set: (T, String) -> Unit) : FieldOf<T>
}

class Configuration<T>(val fields: List<FieldOf<T>>)

class ConfigurationBuilder<T> {
	fun boolean(name: String, property: KMutableProperty1<T, Boolean>): ConfigurationBuilder<T> = boolean(name, property.getter, property.setter)

	fun boolean(name: String, get: (T) -> Boolean, set: (T, Boolean) -> Unit): ConfigurationBuilder<T> = apply {
		fields += FieldOf.BooleanField(name, get, set)
	}

	fun string(name: String, property: KMutableProperty1<T, String>): ConfigurationBuilder<T> = string(name, property.getter, property.setter)

	fun string(name: String, get: (T) -> String, set: (T, String) -> Unit): ConfigurationBuilder<T> = apply {
		fields += FieldOf.StringField(name, get, set)
	}

	fun build(): Configuration<T> = Configuration(fields.toList())

	private val fields = mutableListOf<FieldOf<T>>()
}

fun <T> createBoundUiList(
	values: List<FieldOf<T>>,
	mkTextUi: (name: String) -> JBTextField,
	mkCheckBox: (name: String) -> JBCheckBox
): List<BoundUi<T>> =
	values.map { value ->
		when (value) {
			is FieldOf.BooleanField -> BoundJBCheckBox(mkCheckBox(value.name), value)
			is FieldOf.StringField -> BoundJBTextField(mkTextUi(value.name), value)
		}
	}

fun <T> List<BoundUi<T>>.apply(config: T) {
	forEach { it.apply(config) }
}

fun <T> List<BoundUi<T>>.reset(config: T) {
	forEach { it.reset(config) }
}

fun <T> List<BoundUi<T>>.isModified(config: T): Boolean = any { it.isModified(config) }

interface BoundUi<T> {
	val component: Component
	fun apply(config: T)
	fun reset(config: T)
	fun isModified(config: T): Boolean
}

private class BoundJBTextField<T>(override val component: JBTextField, private val value: FieldOf.StringField<T>) : BoundUi<T> {
	override fun apply(config: T) {
		value.set(config, component.text)
	}

	override fun reset(config: T) {
		component.text = value.get(config)
	}

	override fun isModified(config: T): Boolean = component.text != value.get(config)
}

private class BoundJBCheckBox<T>(override val component: JBCheckBox, private val value: FieldOf.BooleanField<T>) : BoundUi<T> {
	override fun apply(config: T) {
		value.set(config, component.isSelected)
	}

	override fun reset(config: T) {
		component.isSelected = value.get(config)
	}

	override fun isModified(config: T): Boolean = component.isSelected != value.get(config)
}