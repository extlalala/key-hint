package keyhint

import com.intellij.codeInsight.editorActions.CodeBlockProviders
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCoreUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

// copied from com.intellij.openapi.editor.actions.EditorActionUtil

fun lineRange(editor: Editor): IntRange = lineStart(editor)..lineEnd(editor)

fun lineEnd(editor: Editor): Int {
	val document = editor.document
	val caretModel = editor.caretModel
	val softWrapModel = editor.softWrapModel

	var lineNumber = editor.caretModel.logicalPosition.line
	if (lineNumber >= document.lineCount) {
		val pos = LogicalPosition(lineNumber, 0)
		return editor.logicalPositionToOffset(pos)
	}
	val currentVisualCaret = editor.caretModel.visualPosition
	var visualEndOfLineWithCaret = VisualPosition(currentVisualCaret.line, EditorUtil.getLastVisualLineColumnNumber(editor, currentVisualCaret.line), true)

	// There is a possible case that the caret is already located at the visual end of line and the line is soft wrapped.
	// We want to move the caret to the end of the logical line then.
	if (currentVisualCaret == visualEndOfLineWithCaret) {
		val offset = editor.visualPositionToOffset(visualEndOfLineWithCaret)
		if (offset < editor.document.textLength) {
			val logicalLineEndOffset = EditorUtil.getNotFoldedLineEndOffset(editor, offset)
			visualEndOfLineWithCaret = editor.offsetToVisualPosition(logicalLineEndOffset, true, false)
		}
	}

	val logLineEnd = editor.visualToLogicalPosition(visualEndOfLineWithCaret)
	val offset = editor.logicalPositionToOffset(logLineEnd)
	lineNumber = logLineEnd.line
	var newOffset = offset

	val text = document.charsSequence
	for (i in newOffset - 1 downTo document.getLineStartOffset(lineNumber)) {
		if (softWrapModel.getSoftWrap(i) != null) {
			newOffset = offset
			break
		}
		if (text[i] != ' ' && text[i] != '\t') {
			break
		}
		newOffset = i
	}

	// Move to the calculated end of visual line if caret is located on a last non-white space symbols on a line and there are
	// remaining white space symbols.
	if (newOffset == offset || newOffset == caretModel.offset) {
		return editor.visualPositionToOffset(visualEndOfLineWithCaret)
	} else {
		return offset
	}
}

fun lineStart(editor: Editor): Int {
	val document = editor.document
	val caretModel = editor.caretModel
	val editorSettings = editor.settings

	val logCaretLine = caretModel.logicalPosition.line
	val currentVisCaret = caretModel.visualPosition
	val caretLogLineStartVis = editor.offsetToVisualPosition(document.getLineStartOffset(logCaretLine))

	if (currentVisCaret.line > caretLogLineStartVis.line) {
		// Caret is located not at the first visual line of soft-wrapped logical line.
		if (editorSettings.isSmartHome) {
			return startOfSoftWrappedLine(editor, currentVisCaret)
		} else {
			return editor.visualPositionToOffset(VisualPosition(currentVisCaret.line, 0))
		}
	}

	// Skip folded lines.
	var logLineToUse = logCaretLine - 1
	while (logLineToUse >= 0 && editor.offsetToVisualPosition(document.getLineEndOffset(logLineToUse)).line == currentVisCaret.line) {
		logLineToUse--
	}
	logLineToUse++

	if (logLineToUse >= document.lineCount || !editorSettings.isSmartHome) {
		return editor.logicalPositionToOffset(LogicalPosition(logLineToUse, 0))
	} else if (logLineToUse == logCaretLine) {
		val line = currentVisCaret.line
		var column: Int
		if (currentVisCaret.column == 0) {
			column = findSmartIndentColumn(editor, currentVisCaret.line)
		} else {
			column = EditorActionUtil.findFirstNonSpaceColumnOnTheLine(editor, currentVisCaret.line)
			if (column >= currentVisCaret.column) {
				column = 0
			}
		}
		return editor.visualPositionToOffset(VisualPosition(line, max(column.toDouble(), 0.0).toInt()))
	} else {
		val logLineEndLog = editor.offsetToLogicalPosition(document.getLineEndOffset(logLineToUse))
		val logLineEndVis = editor.logicalToVisualPosition(logLineEndLog)
		val softWrapCount = EditorUtil.getSoftWrapCountAfterLineStart(editor, logLineEndLog)
		if (softWrapCount > 0) {
			return startOfSoftWrappedLine(editor, logLineEndVis)
		} else {
			val line = logLineEndVis.line
			var column = 0
			if (currentVisCaret.column > 0) {
				val firstNonSpaceColumnOnTheLine = max(0.0, EditorActionUtil.findFirstNonSpaceColumnOnTheLine(editor, currentVisCaret.line).toDouble()).toInt()
				if (firstNonSpaceColumnOnTheLine < currentVisCaret.column) {
					column = firstNonSpaceColumnOnTheLine
				}
			}
			return editor.visualPositionToOffset(VisualPosition(line, column))
		}
	}
}

private fun findSmartIndentColumn(editor: Editor, visualLine: Int): Int {
	for (i in visualLine downTo 0) {
		val column = EditorActionUtil.findFirstNonSpaceColumnOnTheLine(editor, i)
		if (column >= 0) {
			return column
		}
	}
	return 0
}

private fun startOfSoftWrappedLine(editor: Editor, currentVisual: VisualPosition): Int {
	val startLineOffset = editor.visualPositionToOffset(VisualPosition(currentVisual.line, 0))
	val softWrapModel = editor.softWrapModel
	val softWrap = softWrapModel.getSoftWrap(startLineOffset)
	if (softWrap == null) {
		// Don't expect to be here.
		val column = EditorActionUtil.findFirstNonSpaceColumnOnTheLine(editor, currentVisual.line)
		var columnToMove = column
		if (column < 0 || currentVisual.column in 1..column) {
			columnToMove = 0
		}
		return editor.visualPositionToOffset(VisualPosition(currentVisual.line, columnToMove))
	}

	if (currentVisual.column > softWrap.indentInColumns) {
		return softWrap.start
	} else if (currentVisual.column > 0) {
		return editor.visualPositionToOffset(VisualPosition(currentVisual.line, 0))
	} else {
		// We assume that caret is already located at zero visual column of soft-wrapped line if control flow reaches this place.
		val lineStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, startLineOffset)
		val visualLine = editor.offsetToVisualPosition(lineStartOffset).line
		return editor.visualPositionToOffset(VisualPosition(visualLine, max(0, EditorActionUtil.findFirstNonSpaceColumnOnTheLine(editor, visualLine))))
	}
}

fun pageRange(editor: Editor): IntRange {
	val caretModel = editor.caretModel
	val visibleArea = getVisibleArea(editor)

	val topLineNumber = run {
		var lineNumber = editor.yToVisualLine(visibleArea.y)
		if (visibleArea.y > editor.visualLineToY(lineNumber) && visibleArea.y + visibleArea.height > editor.visualLineToY(lineNumber + 1)) {
			lineNumber++
		}
		lineNumber
	}

	val bottomLineNumber = run {
		val maxY = visibleArea.y + visibleArea.height - editor.lineHeight
		var lineNumber = editor.yToVisualLine(maxY)
		if (lineNumber > 0 && maxY < editor.visualLineToY(lineNumber) && visibleArea.y <= editor.visualLineToY(lineNumber - 1)) {
			lineNumber--
		}
		lineNumber
	}

	val topOff = editor.visualPositionToOffset(VisualPosition(topLineNumber, caretModel.visualPosition.column))
	val bottomOff = editor.visualPositionToOffset(VisualPosition(bottomLineNumber, caretModel.visualPosition.column))
	return bottomOff..topOff
}

private fun getVisibleArea(editor: Editor): Rectangle {
	val model = editor.scrollingModel
	return if (EditorCoreUtil.isTrueSmoothScrollingEnabled()) model.visibleAreaOnScrollingFinished else model.visibleArea
}

// copied from com.intellij.codeInsight.editorActions.java

fun codeBlockRange(editor: Editor): IntRange? {
	val project = editor.project ?: return null
	val document = editor.document
	val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null

	val provider = CodeBlockProviders.INSTANCE.forLanguage(file.language)
	if (provider != null) {
		val range = provider.getCodeBlockRange(editor, file) ?: return null
		return range.startOffset..range.endOffset
	} else {
		val guide = editor.indentsModel.caretIndentGuide
		if (guide != null) {
			val start = LogicalPosition(guide.startLine, guide.indentLevel)
			val end = LogicalPosition(guide.endLine, guide.indentLevel)
			return editor.logicalPositionToOffset(start)..editor.logicalPositionToOffset(end)
		} else {
			return calcBlockRange(editor, file)
		}
	}
}

private fun calcBlockRange(editor: Editor, file: PsiFile): IntRange? {
	val offsetFromBraceMatcher = calcBlockRangeFromBraceMatcher(editor, file)
	val rangeFromStructuralSupport = CodeBlockSupportHandler.findCodeBlockRange(editor, file)
	return if (rangeFromStructuralSupport.isEmpty) {
		offsetFromBraceMatcher
	} else if (offsetFromBraceMatcher == null) {
		rangeFromStructuralSupport.startOffset..rangeFromStructuralSupport.endOffset
	} else {
		max(rangeFromStructuralSupport.startOffset, offsetFromBraceMatcher.first)..
				min(rangeFromStructuralSupport.endOffset, offsetFromBraceMatcher.last)
	}
}

private fun calcBlockRangeFromBraceMatcher(editor: Editor, file: PsiFile): IntRange? {
	val startOffset = calcBlockStartOffsetFromBraceMatcher(editor, file)
	val endOffset = calcBlockEndOffsetFromBraceMatcher(editor, file)
	return if (startOffset == -1 || endOffset == -1) null else startOffset..endOffset
}

private fun calcBlockEndOffsetFromBraceMatcher(editor: Editor, file: PsiFile): Int {
	val document = editor.document
	val offset = editor.caretModel.offset
	val fileType = getFileType(file, offset)
	val iterator = editor.highlighter.createIterator(offset)
	if (iterator.atEnd()) return -1

	var depth = 0
	var braceType: Language?
	var isBeforeLBrace = false
	if (isLStructuralBrace(fileType, iterator, document.charsSequence)) {
		isBeforeLBrace = true
		depth = -1
		braceType = getBraceType(iterator)
	} else {
		braceType = null
	}

	var moved = false
	while (true) {
		if (iterator.atEnd()) return -1

		if (isRStructuralBrace(fileType, iterator, document.charsSequence) &&
			(braceType === getBraceType(iterator) ||
					braceType == null
					)
		) {
			if (moved) {
				if (depth == 0) break
				depth--
			}

			if (braceType == null) {
				braceType = getBraceType(iterator)
			}
		} else if (isLStructuralBrace(fileType, iterator, document.charsSequence) &&
			(braceType === getBraceType(iterator) ||
					braceType == null
					)
		) {
			if (braceType == null) {
				braceType = getBraceType(iterator)
			}
			depth++
		}

		moved = true
		iterator.advance()
	}

	return if (isBeforeLBrace) iterator.end else iterator.start
}

private fun calcBlockStartOffsetFromBraceMatcher(editor: Editor, file: PsiFile): Int {
	val offset = editor.caretModel.offset - 1
	if (offset < 0) return -1

	val document = editor.document
	val fileType = getFileType(file, offset)
	val iterator = editor.highlighter.createIterator(offset)

	var depth = 0
	var braceType: Language?
	var isAfterRBrace = false
	if (isRStructuralBrace(fileType, iterator, document.charsSequence)) {
		isAfterRBrace = true
		depth = -1
		braceType = getBraceType(iterator)
	} else {
		braceType = null
	}

	var moved = false
	while (true) {
		if (iterator.atEnd()) return -1

		if (isLStructuralBrace(fileType, iterator, document.charsSequence) &&
			(braceType === getBraceType(iterator) || braceType == null)
		) {
			if (braceType == null) {
				braceType = getBraceType(iterator)
			}

			if (moved) {
				if (depth == 0) break
				depth--
			}
		} else if (isRStructuralBrace(fileType, iterator, document.charsSequence) &&
			(braceType === getBraceType(iterator) || braceType == null)
		) {
			if (braceType == null) {
				braceType = getBraceType(iterator)
			}
			depth++
		}

		moved = true
		iterator.retreat()
	}

	return if (isAfterRBrace) iterator.start else iterator.end
}


private fun getBraceType(iterator: HighlighterIterator): Language {
	val type = iterator.tokenType
	return type.language
}

private fun getFileType(file: PsiFile, offset: Int): FileType {
	val psiElement = file.findElementAt(offset)
	return psiElement?.containingFile?.fileType ?: file.fileType
}

private fun isLStructuralBrace(fileType: FileType, iterator: HighlighterIterator, fileText: CharSequence): Boolean {
	return BraceMatchingUtil.isLBraceToken(iterator, fileText, fileType) && BraceMatchingUtil.isStructuralBraceToken(fileType, iterator, fileText)
}

private fun isRStructuralBrace(fileType: FileType, iterator: HighlighterIterator, fileText: CharSequence): Boolean {
	return BraceMatchingUtil.isRBraceToken(iterator, fileText, fileType) && BraceMatchingUtil.isStructuralBraceToken(fileType, iterator, fileText)
}
