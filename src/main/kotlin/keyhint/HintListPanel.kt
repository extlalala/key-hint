package keyhint

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import keyhint.utils.nullOr
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.ListSelectionListener



class HintListPanelManager(
	private val editor: Editor,
	private val setPosition: (Editor, JBPopup) -> Unit
) {
	fun show(list: List<DecoratedText>) {
		updatePanel(list)
	}

	fun hide() {
		panel?.close()
		panel = null
	}

	private var panel: HintListPanel? = null

	private fun updatePanel(list: List<DecoratedText>) {
		if (panel.nullOr { !it.isReusable() })
			panel = HintListPanel(editor)

		panel?.let { panel ->
			panel.setTexts(list)
			setPosition(editor, panel.hint)
		}
	}
}

data class DecoratedText(val text: String, val color: Color? = null)

/**
 * A popup panel that displays a list of available keyboard shortcuts in an IntelliJ IDEA editor,
 * similar to code completion suggestions. The panel appears when a shortcut is pressed
 * and provides visual hints without interfering with normal shortcut operation.
 *
 * This class handles the entire lifecycle of the hint popup - creation to disposal.
 * The popup will automatically close when focus is lost or ESC is pressed.
 *
 * @property editor The target editor where the hint should be displayed
 * @constructor Creates a new hint panel attached to the specified editor
 */
class HintListPanel(editor: Editor) {

	// -------- Public API --------

	/**
	 * Updates the list of shortcuts displayed in the hint panel.
	 * This must be called from the EDT (Event Dispatch Thread).
	 *
	 * @param list The new list of shortcut descriptions to display
	 */
	fun setTexts(list: List<DecoratedText>) {
		SwingUtilities.invokeLater {
			this.list.setListData(list.toTypedArray())
			if (this.list.model.size > 0)
				this.list.selectedIndex = 0
			contentPanel.revalidate()
			contentPanel.repaint()
		}
	}

	/**
	 * Positions the hint popup at the best location relative to the editor cursor.
	 * Uses IntelliJ Platform's popup location heuristics.
	 *
	 * @param editor The editor context for position calculation
	 */
	fun setToCursorPosition(editor: Editor) {
		val pos = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
		hint.setLocation(pos.screenPoint)
	}

	/**
	 * Checks if this hint panel can be reused for another display operation.
	 *
	 * @return true if the panel is still in a reusable state, false otherwise
	 */
	fun isReusable(): Boolean = reusable

	/**
	 * Programmatically closes the hint popup.
	 * After calling this, the panel is no longer reusable.
	 */
	fun close() {
		hint.cancel()
	}

	/**
	 * Adds a listener for selection changes in the shortcut list.
	 *
	 * @param listener The listener to be notified of selection events
	 */
	fun addSelectionListener(listener: ListSelectionListener) {
		list.addListSelectionListener(listener)
	}

	// -------- Private Implementation --------

	private class DecoratedTextListCellRenderer : DefaultListCellRenderer() {
		override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
			if (value is DecoratedText) {
				val ret = super.getListCellRendererComponent(list, value.text, index, isSelected, cellHasFocus)
				if (value.color != null)
					ret.foreground = value.color
				return ret
			}
			return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
		}
	}

	// UI Components
	val hint: JBPopup
	private val scrollPane = JBScrollPane()
	private val contentPanel = JPanel()
	private val list = JBList<DecoratedText>().apply {
		cellRenderer = DecoratedTextListCellRenderer()
	}

	// State Tracking
	private var reusable = true

	init {
		setupUI()

		hint = createPopup()
		hint.showInBestPositionFor(editor)
	}

	/**
	 * Creates the actual popup window using IntelliJ Platform's popup factory.
	 * Configures cancellation behavior and appearance settings.
	 *
	 * @return A configured JBPopup instance ready for display
	 */
	private fun createPopup(): JBPopup {
		val hint = JBPopupFactory.getInstance().createComponentPopupBuilder(contentPanel, null)
			.setCancelCallback { reusable = false; true }
			.setRequestFocus(false)
			.setFocusable(false)
			.createPopup()

		hint.addListener(object : JBPopupListener {
			override fun beforeShown(event: LightweightWindowEvent) {}
			override fun onClosed(event: LightweightWindowEvent) {
				reusable = false
			}
		})
		return hint
	}

	/**
	 * Initializes all UI components with proper styling and layout.
	 * Configures colors, fonts, borders and dimensions according to IntelliJ Platform's UI guidelines.
	 */
	private fun setupUI() {
		list.apply {
			fixedCellHeight = 24
			selectionMode = ListSelectionModel.SINGLE_SELECTION
			font = JBUI.Fonts.label()
			background = JBUI.CurrentTheme.List.BACKGROUND
			selectionBackground = JBUI.CurrentTheme.List.Selection.background(false)
			selectionForeground = JBUI.CurrentTheme.List.Selection.foreground(false)
			border = BorderFactory.createEmptyBorder()
		}

		scrollPane.apply {
			setViewportView(list)
			border = BorderFactory.createEmptyBorder()
			preferredSize = Dimension(400, 500)
		}

		contentPanel.apply {
			layout = BorderLayout()
			add(scrollPane, BorderLayout.CENTER)
			border = JBUI.Borders.empty(4)
			background = JBUI.CurrentTheme.Popup.BACKGROUND
		}
	}
}