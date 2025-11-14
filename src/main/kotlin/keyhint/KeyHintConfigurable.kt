package keyhint

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import keyhint.utils.ComboBoxBinding
import keyhint.utils.ConfigComponent
import keyhint.utils.JBCheckBoxBinding
import keyhint.utils.JBTextFieldBinding
import keyhint.utils.UiBinding
import keyhint.utils.apply
import keyhint.utils.isModified
import keyhint.utils.reset
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class KeyHintConfigurable : Configurable {
	private val keyHint = KeyHint

	private val inlayRendererUiBindings: List<UiBinding<ShortcutInlayRenderer.Config>>

	private val actionIdRegexField = JBTextField()
	private val actionIdBlockedListField = JBTextArea()

	private val mainPanel = JPanel(BorderLayout())

	init {
		actionIdBlockedListField.apply {
			rows = 8
			columns = 20
		}

		val scrollPane = JBScrollPane(actionIdBlockedListField)

		val formBuilder = FormBuilder.createFormBuilder()
			.addLabeledComponent("Action ID regex:", actionIdRegexField)
			.addVerticalGap(10)
			.addLabeledComponent("Action ID blocked list(split by line):", scrollPane)
			.addVerticalGap(10)
			.addComponent(JBLabel("Inlay hint settings:"))

		val bindings = mutableListOf<UiBinding<ShortcutInlayRenderer.Config>>()

		fun addConfigComponents(list: List<ConfigComponent<ShortcutInlayRenderer.Config>>) {
			list.forEach { c ->
				when (c) {
					is ConfigComponent.BooleanField -> bindings += JBCheckBoxBinding(JBCheckBox(c.name).also { formBuilder.addComponent(it) }, c)
					is ConfigComponent.EnumField -> bindings += ComboBoxBinding(ComboBox(c.entries.toTypedArray()).also { formBuilder.addLabeledComponent(c.name, it) }, c)
					is ConfigComponent.StringField -> bindings += JBTextFieldBinding(JBTextField().also { formBuilder.addLabeledComponent(c.name, it) }, c)
					is ConfigComponent.SubConfig -> {
						formBuilder.addComponent(JBLabel(c.name))
						addConfigComponents(c.components)
					}
				}
			}
		}
		addConfigComponents(ShortcutInlayRenderer.Config.configuration.configComponents)

		inlayRendererUiBindings = bindings

		mainPanel.add(BorderLayout.NORTH, formBuilder.panel)
	}

	override fun getDisplayName(): String = "Key Hint"

	override fun apply() {
		keyHint.state.actionIdPattern = actionIdRegexField.text.trim()
		inlayRendererUiBindings.apply(keyHint.state.shortcutInlayRenderer)

		keyHint.state.actionIdBlockedList = actionIdBlockedListField.text
			.lineSequence()
			.map { it.trim() }
			.filter { it.isNotBlank() }
			.toSet()

		keyHint.stateChanged()
	}

	override fun reset() {
		actionIdRegexField.text = keyHint.state.actionIdPattern
		inlayRendererUiBindings.reset(keyHint.state.shortcutInlayRenderer)
		actionIdBlockedListField.text = keyHint.state.actionIdBlockedList.joinToString("\n")
	}

	override fun createComponent(): JComponent = mainPanel

	override fun isModified(): Boolean =
		actionIdRegexField.text.trim() != keyHint.state.actionIdPattern ||
				inlayRendererUiBindings.isModified(keyHint.state.shortcutInlayRenderer) ||
				linesToSet(actionIdBlockedListField.text) != keyHint.state.actionIdBlockedList

	private fun linesToSet(text: String): Set<String> =
		text.lineSequence()
			.map { it.trim() }
			.filter { it.isNotBlank() }
			.toSet()
}