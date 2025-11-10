package keyhint

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import keyhint.utils.BoundUi
import keyhint.utils.apply
import keyhint.utils.createBoundUiList
import keyhint.utils.isModified
import keyhint.utils.reset
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class KeyHintConfigurable : Configurable {
	private val keyHint = KeyHint

	private val shortcutInlayRendererConfigs: List<BoundUi<ShortcutInlayRenderer.Config>>

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

		shortcutInlayRendererConfigs = createBoundUiList(
			ShortcutInlayRenderer.Config.configuration.fields,
			{ name -> JBTextField(name).also { formBuilder.addLabeledComponent(name, it) } },
			{ name -> JBCheckBox(name).also { formBuilder.addLabeledComponent(name, it) } }
		)

		mainPanel.add(BorderLayout.NORTH, formBuilder.panel)
	}

	override fun getDisplayName(): String = "Key Hint"

	override fun apply() {
		keyHint.state.actionIdPattern = actionIdRegexField.text.trim()
		shortcutInlayRendererConfigs.apply(keyHint.state.shortcutInlayRenderer)

		keyHint.state.actionIdBlockedList = actionIdBlockedListField.text
			.lineSequence()
			.map { it.trim() }
			.filter { it.isNotBlank() }
			.toSet()

		keyHint.stateChanged()
	}

	override fun reset() {
		actionIdRegexField.text = keyHint.state.actionIdPattern
		shortcutInlayRendererConfigs.reset(keyHint.state.shortcutInlayRenderer)
		actionIdBlockedListField.text = keyHint.state.actionIdBlockedList.joinToString("\n")
	}

	override fun createComponent(): JComponent = mainPanel

	override fun isModified(): Boolean =
		actionIdRegexField.text.trim() != keyHint.state.actionIdPattern ||
				shortcutInlayRendererConfigs.isModified(keyHint.state.shortcutInlayRenderer) ||
				linesToSet(actionIdBlockedListField.text) != keyHint.state.actionIdBlockedList

	private fun linesToSet(text: String): Set<String> =
		text.lineSequence()
			.map { it.trim() }
			.filter { it.isNotBlank() }
			.toSet()
}