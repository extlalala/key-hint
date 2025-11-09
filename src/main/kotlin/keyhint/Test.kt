package keyhint

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

class Test(private val editor: Editor) {
	private var testHighlighter: RangeHighlighter? = null

	fun testHighlighter() {
		println("testHighlighter: $testHighlighter")
		if (testHighlighter == null) {
			val caretOff = editor.caretModel.currentCaret.offset
			val doc = editor.document
			val line = doc.getLineNumber(caretOff)
			val start = doc.getLineStartOffset(line)
			val end = doc.getLineEndOffset(line)
			val attrs = TextAttributes(
				editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR),
				null,
				null,
				null,
				0
			)
			testHighlighter = editor.markupModel.addRangeHighlighter(
				start,
				end,
				HighlighterLayer.SELECTION,
				attrs,
				HighlighterTargetArea.EXACT_RANGE
			)
		} else {
			editor.markupModel.removeHighlighter(testHighlighter!!)
			testHighlighter = null
		}
	}

	private var inlay: Inlay<*>? = null

	fun testInlayModel() {
		if (inlay == null) {
			val inlayModel = editor.inlayModel

			val renderer = object : EditorCustomElementRenderer {
				override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
					g.color = JBColor.BLUE
					g.drawString("Hello, world!", targetRegion.x.toFloat() + 5, targetRegion.y.toFloat() + 15)
				}

				override fun calcWidthInPixels(inlay: Inlay<*>): Int {
					return 10
				}
			}

			inlay = inlayModel.addInlineElement(editor.caretModel.currentCaret.offset, renderer)
		} else {
			Disposer.dispose(inlay!!)
			inlay = null
		}
	}
}