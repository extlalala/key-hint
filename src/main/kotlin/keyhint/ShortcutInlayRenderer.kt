package keyhint

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import keyhint.utils.ConfigurationBuilder
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

/**
 * 用于在跳转快捷键的目标位置显示提示信息, 提示快捷键键位
 */

class ShortcutInlayRenderer(private val extraHintListener: ExtraHintListener) {

	// -------- Public API ----------

	fun reload(keymap: Keymap = KeymapManager.getInstance().activeKeymap) {
		loadEntries(keymap)
	}

	fun show(editor: Editor) {
		close()
		val config = KeyHint.state.shortcutInlayRenderer
		if (!config.inlayHintEnabled) return

		val inlayModel = editor.inlayModel

		val renderEntries = mutableListOf<ExtraHintListener.RenderEntry>()
		inlayRenderEntries.forEach { entry -> renderEntry(entry, editor, inlayModel, renderEntries) }
		extraHintListener.render(renderEntries)
	}

	fun close() {
		inlays.forEach { Disposer.dispose(it) }
		inlays.clear()
	}

	class Config {
		var inlayHintEnabled = true

		var showSelectionVariants = true
		var selectionModifierKey = "shift"

		var codeBlockStartAndEnd: RenderMode? = RenderMode.Full
		var moveToPageBottomAndTop: RenderMode? = RenderMode.Full
		var textStartAndEnd: RenderMode? = RenderMode.Full
		var lineStartAndEnd: RenderMode? = RenderMode.Simplified
		var pageDownAndUp: RenderMode? = RenderMode.Full
		var nextAndPreviousWord: RenderMode? = RenderMode.Simplified

		var codeBlockStartAndEndColor: String = "#4D97FF"
		var moveToPageBottomAndTopColor: String = "#666666"
		var textStartAndEndColor: String = "#FFA726"
		var lineStartAndEndColor: String = "#BA68C8"
		var pageDownAndUpColor: String = "#66BB6A"
		var nextAndPreviousWordColor: String = "#FFCA28"

		enum class RenderMode {
			Disabled,
			Simplified,
			Full
		}

		companion object {
			val configuration = ConfigurationBuilder<Config>()
				.boolean(Config::inlayHintEnabled)
				.boolean(Config::showSelectionVariants)
				.string(Config::selectionModifierKey)
				.subConfig("renderMode")
				.enum(Config::codeBlockStartAndEnd, RenderMode.Full)
				.enum(Config::moveToPageBottomAndTop, RenderMode.Full)
				.enum(Config::textStartAndEnd, RenderMode.Full)
				.enum(Config::lineStartAndEnd, RenderMode.Full)
				.enum(Config::pageDownAndUp, RenderMode.Full)
				.enum(Config::nextAndPreviousWord, RenderMode.Simplified)
				.endSubConfig()
				.subConfig("colors")
				.string(Config::codeBlockStartAndEndColor)
				.string(Config::moveToPageBottomAndTopColor)
				.string(Config::textStartAndEndColor)
				.string(Config::lineStartAndEndColor)
				.string(Config::pageDownAndUpColor)
				.string(Config::nextAndPreviousWordColor)
				.endSubConfig()
				.build()
		}
	}

	enum class MoveAction(val actionIdLo: String, val actionIdHi: String) {
		EDITOR_MOVE_CODE_BLOCK("EditorCodeBlockStart", "EditorCodeBlockEnd"),
		EDITOR_MOVE_MOVE_TO_PAGE("EditorMoveToPageTop", "EditorMoveToPageBottom"),
		EDITOR_MOVE_TEXT("EditorTextStart", "EditorTextEnd"),
		EDITOR_MOVE_LINE("EditorLineStart", "EditorLineEnd"),
		EDITOR_MOVE_PAGE("EditorPageUp", "EditorPageDown"),
		EDITOR_MOVE_WORD("EditorNextWord", "EditorPreviousWord");

		companion object {
			val allActionIds = MoveAction.entries.asSequence()
				.flatMap { listOf(it.actionIdLo, it.actionIdHi) }
				.flatMap { listOf(it, "${it}WithSelection") }
				.toSet()
		}
	}

	fun interface ExtraHintListener {
		data class RenderEntry(val action: MoveAction, val display: Pair<String, String>, val color: Color)

		fun render(entries: List<RenderEntry>)
	}

	// -------- Private Implementation ----------

	private class InlayRenderEntry(
		val action: MoveAction,
		val color: Color,
		val renderInExtraWindow: Boolean,
		val simplified: Boolean,
		val displayTextLo: String,
		val displayTextHi: String,
		val calcRange: (Editor) -> IntRange?
	)

	private val inlayRenderEntries = mutableListOf<InlayRenderEntry>()
	private val inlays = mutableListOf<Inlay<*>>()

	init {
		loadEntries(KeymapManager.getInstance().activeKeymap)
	}

	private fun loadEntries(keymap: Keymap) {
		inlayRenderEntries.clear()

		val config = KeyHint.state.shortcutInlayRenderer

		fun addEntry(action: MoveAction, color: Color, mode: Config.RenderMode?, calcRange: (Editor) -> IntRange?) {
			when (mode) {
				ShortcutInlayRenderer.Config.RenderMode.Disabled -> null
				ShortcutInlayRenderer.Config.RenderMode.Simplified -> InlayRenderEntry(action, color, true, true, display(action.actionIdLo, keymap), display(action.actionIdHi, keymap), calcRange)
				null, ShortcutInlayRenderer.Config.RenderMode.Full -> InlayRenderEntry(action, color, false, false, display(action.actionIdLo, keymap), display(action.actionIdHi, keymap), calcRange)
			}
				?.let { inlayRenderEntries += it }
		}

		addEntry(MoveAction.EDITOR_MOVE_CODE_BLOCK, toColor(config.codeBlockStartAndEndColor), config.codeBlockStartAndEnd, ::codeBlockRange)
		addEntry(MoveAction.EDITOR_MOVE_MOVE_TO_PAGE, toColor(config.moveToPageBottomAndTopColor), config.moveToPageBottomAndTop, ::pageRange)
		addEntry(MoveAction.EDITOR_MOVE_TEXT, toColor(config.textStartAndEndColor), config.textStartAndEnd) { 0..it.document.textLength }
		addEntry(MoveAction.EDITOR_MOVE_LINE, toColor(config.lineStartAndEndColor), config.lineStartAndEnd, ::lineRange)
		addEntry(MoveAction.EDITOR_MOVE_PAGE, toColor(config.pageDownAndUpColor), config.pageDownAndUp, ::pageUpDownRange)
		addEntry(MoveAction.EDITOR_MOVE_WORD, toColor(config.nextAndPreviousWordColor), config.nextAndPreviousWord, ::nextPrevWordRange)
	}

	private fun toColor(colorString: String): Color =
		try {
			Color.decode(colorString)
		} catch (e: NumberFormatException) {
			JBColor.BLUE
		}

	private fun display(actionId: String, keymap: Keymap): String {
		val config = KeyHint.state.shortcutInlayRenderer
		val showWithSelectionForms = config.showSelectionVariants
		val selectionModifier = config.selectionModifierKey
		return keymap.getShortcuts(actionId).joinToString(separator = ", ") { shortcut ->
			val suffix = if (showWithSelectionForms) {
				" (+$selectionModifier)"
			} else {
				""
			}
			KeymapUtil.getShortcutText(shortcut) + suffix
		}
	}

	private fun renderEntry(entry: InlayRenderEntry, editor: Editor, inlayModel: InlayModel, renderEntries: MutableList<ExtraHintListener.RenderEntry>) {
		val range = entry.calcRange(editor)
			?.takeIf { it.first != it.last }
			?: return

		val lo = if (entry.simplified) "|" else entry.displayTextLo
		val hi = if (entry.simplified) "|" else entry.displayTextHi
		drawInlay(range.first, lo, entry.color, inlayModel)
		drawInlay(range.last, hi, entry.color, inlayModel)

		if (entry.renderInExtraWindow)
			renderEntries += ExtraHintListener.RenderEntry(entry.action, entry.displayTextLo to entry.displayTextHi, entry.color)
	}

	private fun drawInlay(offset: Int, s: String, color: Color, inlayModel: InlayModel) {
		val renderer = MyEditorCustomElementRenderer(s, color)
		inlayModel.addInlineElement(offset, renderer)
			?.let { inlays.add(it) }
	}
}

private class MyEditorCustomElementRenderer(
	val s: String,
	val color: Color
) : EditorCustomElementRenderer {
	private val config = object {
		val backgroundColor: Color? = null // 可设置为浅蓝色背景增强可读性
		val font: Font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
		val borderColor: Color? = JBColor.GRAY
		val borderWidth = 1f
		val cornerRadius = 3f
		val verticalPadding = 2f
	}

	override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
		val originalTransform = g.transform
		val originalColor = g.color
		val originalFont = g.font

		try {
			// 设置字体
			g.font = config.font

			// 计算文本尺寸
			val fontMetrics = g.getFontMetrics(config.font)
			val textWidth = fontMetrics.stringWidth(s)
			val textHeight = fontMetrics.ascent

			// 计算绘制位置（居中）
			val x = targetRegion.x.toFloat()
			val y = targetRegion.y.toFloat() + (targetRegion.height.toFloat() - textHeight) / 2 - config.verticalPadding

			// 绘制背景（如果有）
			config.backgroundColor?.let {
				g.color = it
				fillRoundRect(g, x, y, textWidth.toFloat(), textHeight.toFloat() + 2 * config.verticalPadding, config.cornerRadius)
			}

			// 绘制边框（如果有）
			config.borderColor?.let {
				g.color = it
				g.stroke = BasicStroke(config.borderWidth)
				drawRoundRect(g, x, y, textWidth.toFloat(), textHeight.toFloat() + 2 * config.verticalPadding, config.cornerRadius)
			}

			// 绘制文本
			g.color = color
			g.drawString(s, x, targetRegion.y.toFloat() + targetRegion.height.toFloat() - textHeight / 2)
		} finally {
			// 恢复原始图形状态
			g.transform = originalTransform
			g.color = originalColor
			g.font = originalFont
		}
	}

	override fun calcWidthInPixels(inlay: Inlay<*>): Int {
		val fontMetrics = inlay.editor.contentComponent.getFontMetrics(config.font)
		return fontMetrics.stringWidth(s)
	}

	private fun drawRoundRect(g: Graphics2D, x: Float, y: Float, width: Float, height: Float, radius: Float) {
		g.draw(RoundRectangle2D.Float(x, y, width, height, radius, radius))
	}

	private fun fillRoundRect(g: Graphics2D, x: Float, y: Float, width: Float, height: Float, radius: Float) {
		g.fill(RoundRectangle2D.Float(x, y, width, height, radius, radius))
	}
}

/*
Ctrl+M - Scroll to Center                                   EditorScrollToCenter


Ctrl+Backspace - Delete to Word Start                       EditorDeleteToWordStart
Ctrl+Enter - Split Line                                     EditorSplitLine
Shift+���ϼ�ͷ - Up with Selection                              EditorUpWithSelection
Ctrl+���ϼ�ͷ - Scroll Up                                       EditorScrollUp
Ctrl+D - Duplicate Line or Selection                        EditorDuplicate
Ctrl+Shift+M - Move Caret to Matching Brace                 EditorMatchBrace
Ctrl+���¼�ͷ - Scroll Down                                     EditorScrollDown
Ctrl+���¼�ͷ - Lookup Down                                     EditorLookupDown
Ctrl+Y - Delete Line                                        EditorDeleteLine
Ctrl+Shift+Enter - Complete Current Statement               EditorCompleteStatement
Ctrl+Shift+J - Join Lines                                   EditorJoinLines
Shift+Tab - Unindent Line or Selection                      EditorUnindentSelection
Ctrl+Alt+Enter - Start New Line Before Current              EditorStartNewLineBefore
Alt+Shift+Insert - Column Selection Mode                    EditorToggleColumnMode
Ctrl+Delete - Delete to Word End                            EditorDeleteToWordEnd
Shift+���¼�ͷ - Down with Selection                            EditorDownWithSelection
Ctrl+���ϼ�ͷ - Lookup Up                                       EditorLookupUp
Alt+Shift+G - Add Carets to Ends of Selected Lines          EditorAddCaretPerSelectedLine
Shift+Enter - Start New Line                                EditorStartNewLine
Ctrl+W - Extend Selection                                   EditorSelectWord
Ctrl+X - Cut                                                EditorCut
Ctrl+Shift+W - Shrink Selection                             EditorUnSelectWord
Shift+Delete - Cut                                          EditorCut




Alt+Shift+. - Increase Font Size in All Editors             EditorIncreaseFontSizeGlobal
Alt+Shift+���Ҽ�ͷ - Select Next Tab in multi-editor file       NextEditorTab
Ctrl+NumPad / - Actual Size                                 Images.Editor.ActualSize
Ctrl+/ - Actual Size                                        Images.Editor.ActualSize
Shift+���Ҽ�ͷ - Right with Selection                           EditorRightWithSelection
Ctrl+Alt+Shift+V - Paste as Plain Text                      EditorPasteSimple
Shift+Backspace - Backspace                                 EditorBackSpace
Alt+Shift+I - Inspect Code With Editor Settings             CodeInspection.OnEditor
Ctrl+NumPad + - Zoom In                                     Images.Editor.ZoomIn
Ctrl+= - Zoom In                                            Images.Editor.ZoomIn
Shift+�����ͷ - Left with Selection                            EditorLeftWithSelection
Alt+Shift+�����ͷ - Select Previous Tab in multi-editor file   PreviousEditorTab
Alt+Q - Context Info                                        EditorContextInfo
Alt+Shift+6, F - Focus Gutter (accessibility)               EditorFocusGutter
Ctrl+. - Choose Lookup Item and Insert Dot                  EditorChooseLookupItemDot
Alt+Shift+6, T - Show Gutter Icon Tooltip (accessibility)   EditorShowGutterIconTooltip
Ctrl+���� - Grid                                              Images.Editor.ToggleGrid
Ctrl+NumPad - - Zoom Out                                    Images.Editor.ZoomOut
Ctrl+���� - Zoom Out                                          Images.Editor.ZoomOut
Ctrl+V - Paste                                              EditorPaste
Shift+Insert - Paste                                        EditorPaste
Ctrl+C - Copy                                               EditorCopy
Ctrl+Insert - Copy                                          EditorCopy
Alt+Shift+���� - Decrease Font Size in All Editors            EditorDecreaseFontSizeGlobal
Ctrl+Shift+Enter - Choose Lookup Item and Invoke Complete StatementEditorChooseLookupItemCompleteStatement
Ctrl+Shift+U - Toggle Case                                  EditorToggleCase
 */