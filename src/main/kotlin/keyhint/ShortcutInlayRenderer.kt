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
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

/**
 * 用于在跳转快捷键的目标位置显示提示信息, 提示快捷键键位
 */

class ShortcutInlayRenderer {

	// -------- Public API ----------

	fun show(editor: Editor, keymap: Keymap) {
		close()
		if (!config.enabled) return

		this.keymap = keymap
		val inlayModel = editor.inlayModel

		if (config.renderEditorCodeBlockStartAndEnd)
			codeBlockRange(editor)?.let {
				renderRange(it, display(EDITOR_CODE_BLOCK_START), display(EDITOR_CODE_BLOCK_END), inlayModel)
			}

		if (config.renderEditorMoveToPageBottomAndTop)
			renderRange(pageRange(editor), display(EDITOR_MOVE_TO_PAGE_BOTTOM), display(EDITOR_MOVE_TO_PAGE_TOP), inlayModel)

		if (config.renderEditorTextStartAndEnd)
			renderRange(0..editor.document.textLength, display(EDITOR_TEXT_START), display(EDITOR_TEXT_END), inlayModel)

		if (config.renderEditorLineStartAndEnd)
			renderRange(lineRange(editor), display(EDITOR_LINE_START), display(EDITOR_LINE_END), inlayModel)
	}

	fun close() {
		inlays.forEach { Disposer.dispose(it) }
		inlays.clear()
	}

	class Config {
		var enabled = true
		var renderEditorCodeBlockStartAndEnd = true
		var renderEditorMoveToPageBottomAndTop = true
		var renderEditorTextStartAndEnd = true
		var renderEditorLineStartAndEnd = true
	}

	val config = Config()

	// -------- Private Implementation ----------

	private val inlays = mutableListOf<Inlay<*>>()
	private var keymap = KeymapManager.getInstance().activeKeymap

	private fun renderRange(range: IntRange, lowDisplay: String, highDisplay: String, inlayModel: InlayModel) {
		drawInlay(range.first, lowDisplay, inlayModel)
		drawInlay(range.last, highDisplay, inlayModel)
	}

	private fun display(actionId: String): String {
		return keymap.getShortcuts(actionId).joinToString(separator = ", ") { KeymapUtil.getShortcutText(it) }
	}

	private fun drawInlay(offset: Int, s: String, inlayModel: InlayModel) {
		val renderer = MyEditorCustomElementRenderer(s)
		inlayModel.addInlineElement(offset, renderer)
			?.let { inlays.add(it) }
	}

	companion object {
		const val EDITOR_CODE_BLOCK_START = "EditorCodeBlockStart"
		const val EDITOR_CODE_BLOCK_END = "EditorCodeBlockEnd"
		const val EDITOR_MOVE_TO_PAGE_TOP = "EditorMoveToPageTop"
		const val EDITOR_MOVE_TO_PAGE_BOTTOM = "EditorMoveToPageBottom"
		const val EDITOR_TEXT_START = "EditorTextStart"
		const val EDITOR_TEXT_END = "EditorTextEnd"
		const val EDITOR_LINE_START = "EditorLineStart"
		const val EDITOR_LINE_END = "EditorLineEnd"

		val IMPLEMENTED_ACTIONS = setOf(
			EDITOR_CODE_BLOCK_START,
			EDITOR_CODE_BLOCK_END,
			EDITOR_MOVE_TO_PAGE_TOP,
			EDITOR_MOVE_TO_PAGE_BOTTOM,
			EDITOR_TEXT_START,
			EDITOR_TEXT_END,
			EDITOR_LINE_START,
			EDITOR_LINE_END
		)
	}
}

private class MyEditorCustomElementRenderer(
	val s: String
) : EditorCustomElementRenderer {
	private val config = object {
		val textColor: Color = JBColor.BLUE
		val backgroundColor: Color? = null // 可设置为浅蓝色背景增强可读性
		val font: Font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
		val verticalPadding = 2
		val horizontalPadding = 4
		val borderColor: Color? = JBColor.GRAY
		val borderWidth = 1f
		val cornerRadius = 3f
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

			// 计算带内边距的元素尺寸
			val elementWidth = textWidth + config.horizontalPadding * 2f
			val elementHeight = textHeight + config.verticalPadding * 2f

			// 计算绘制位置（居中）
			val x = targetRegion.x.toFloat()
			val y = targetRegion.y.toFloat() + (targetRegion.height.toFloat() - elementHeight) / 2

			// 绘制背景（如果有）
			config.backgroundColor?.let {
				g.color = it
				fillRoundRect(g, x, y, elementWidth, elementHeight, config.cornerRadius)
			}

			// 绘制边框（如果有）
			config.borderColor?.let {
				g.color = it
				g.stroke = BasicStroke(config.borderWidth)
				drawRoundRect(g, x, y, elementWidth, elementHeight, config.cornerRadius)
			}

			// 绘制文本
			g.color = config.textColor
			g.drawString(
				s,
				x + config.horizontalPadding,
				y + config.verticalPadding + textHeight
			)
		} finally {
			// 恢复原始图形状态
			g.transform = originalTransform
			g.color = originalColor
			g.font = originalFont
		}
	}

	override fun calcWidthInPixels(inlay: Inlay<*>): Int {
		val fontMetrics = inlay.editor.contentComponent.getFontMetrics(config.font)
		return fontMetrics.stringWidth(s) + config.horizontalPadding * 2
	}

	private fun drawRoundRect(g: Graphics2D, x: Float, y: Float, width: Float, height: Float, radius: Float) {
		g.draw(RoundRectangle2D.Float(x, y, width, height, radius, radius))
	}

	private fun fillRoundRect(g: Graphics2D, x: Float, y: Float, width: Float, height: Float, radius: Float) {
		g.fill(RoundRectangle2D.Float(x, y, width, height, radius, radius))
	}
}

/*



Ctrl+Shift+���Ҽ�ͷ - Move Caret to Next Word with Selection    EditorNextWordWithSelection
Ctrl+Backspace - Delete to Word Start                       EditorDeleteToWordStart
Ctrl+Enter - Split Line                                     EditorSplitLine
Ctrl+Shift+End - Move Caret to Text End with Selection      EditorTextEndWithSelection
Shift+���ϼ�ͷ - Up with Selection                              EditorUpWithSelection
Ctrl+���ϼ�ͷ - Scroll Up                                       EditorScrollUp
Shift+End - Move Caret to Line End with Selection           EditorLineEndWithSelection
Ctrl+Shift+Page Up - Move Caret to Page Top with Selection  EditorMoveToPageTopWithSelection
Shift+Home - Move Caret to Line Start with Selection        EditorLineStartWithSelection
Ctrl+D - Duplicate Line or Selection                        EditorDuplicate
Ctrl+Shift+[ - Move Caret to Code Block Start with SelectionEditorCodeBlockStartWithSelection
Ctrl+Shift+M - Move Caret to Matching Brace                 EditorMatchBrace
Ctrl+���¼�ͷ - Scroll Down                                     EditorScrollDown
Ctrl+���¼�ͷ - Lookup Down                                     EditorLookupDown
Ctrl+Y - Delete Line                                        EditorDeleteLine
Ctrl+M - Scroll to Center                                   EditorScrollToCenter
Ctrl+Shift+] - Move Caret to Code Block End with Selection  EditorCodeBlockEndWithSelection
Ctrl+Shift+Enter - Complete Current Statement               EditorCompleteStatement
Ctrl+Shift+J - Join Lines                                   EditorJoinLines
Ctrl+���Ҽ�ͷ - Move Caret to Next Word                         EditorNextWord
Shift+Tab - Unindent Line or Selection                      EditorUnindentSelection
Shift+Page Down - Page Down with Selection                  EditorPageDownWithSelection
Ctrl+Alt+Enter - Start New Line Before Current              EditorStartNewLineBefore
Shift+Page Up - Page Up with Selection                      EditorPageUpWithSelection
Ctrl+Shift+Home - Move Caret to Text Start with Selection   EditorTextStartWithSelection
Alt+Shift+Insert - Column Selection Mode                    EditorToggleColumnMode
Ctrl+Delete - Delete to Word End                            EditorDeleteToWordEnd
Shift+���¼�ͷ - Down with Selection                            EditorDownWithSelection
Ctrl+���ϼ�ͷ - Lookup Up                                       EditorLookupUp
Alt+Shift+G - Add Carets to Ends of Selected Lines          EditorAddCaretPerSelectedLine
Ctrl+Shift+�����ͷ - Move Caret to Previous Word with SelectionEditorPreviousWordWithSelection
Ctrl+Shift+Page Down - Move Caret to Page Bottom with SelectionEditorMoveToPageBottomWithSelection
Shift+Enter - Start New Line                                EditorStartNewLine
Ctrl+W - Extend Selection                                   EditorSelectWord
Ctrl+X - Cut                                                EditorCut
Ctrl+Shift+W - Shrink Selection                             EditorUnSelectWord
Shift+Delete - Cut                                          EditorCut
Ctrl+�����ͷ - Move Caret to Previous Word                EditorPreviousWord




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