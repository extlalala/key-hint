package keyhint

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Disposer
import java.awt.event.KeyEvent
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set



class KeyHintListenerRegistrar : AppLifecycleListener, DynamicPluginListener {
	override fun appFrameCreated(commandLineArgs: MutableList<String>) {
		KeyHint.initialize()
	}

	override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
		if (pluginDescriptor.pluginId.idString == "zl.key-lister") {
			KeyHint.initialize()
		}
	}
}

val KeyHint: KeyHintService get() = ApplicationManager.getApplication().getService(KeyHintService::class.java)

class KeyHintState {
	var actionIdPattern: String = ".*Editor.*"
}

@Service
@State(name = "KeyHint", storages = [Storage("key-hint.xml")])
class KeyHintService : PersistentStateComponent<KeyHintState>, Disposable {
	private var listener: KeyHintEditorFactoryListener? = null
	private val state = KeyHintState()

	override fun getState(): KeyHintState = state
	override fun loadState(state: KeyHintState) {
		this.state.actionIdPattern = state.actionIdPattern
	}

	fun initialize() {
		if (listener == null) {
			val new = KeyHintEditorFactoryListener()
			listener = new
			EditorFactory.getInstance().addEditorFactoryListener(new, this)
		}
	}

	override fun dispose() {
		listener?.close()
	}
}

class KeyHintEditorFactoryListener : EditorFactoryListener {
	private val listenerMap = Collections.synchronizedMap(IdentityHashMap<Editor, KeyHintEditorKeyListener>())

	override fun editorCreated(event: EditorFactoryEvent) {
		val editor = event.editor
		if (editor in listenerMap) return

		val listener = KeyHintEditorKeyListener(editor)
		listenerMap[editor] = listener
	}

	override fun editorReleased(event: EditorFactoryEvent) {
		val editor = event.editor
		val listener = listenerMap.remove(editor)
		if (listener != null) Disposer.dispose(listener)
	}

	fun close() {
		listenerMap.forEach { (_, listener) ->
			if (listener != null) Disposer.dispose(listener)
		}
		listenerMap.clear()
	}
}

class KeyHintEditorKeyListener(private val editor: Editor) : Disposable {

	override fun dispose() {}

	// ---- private ----

	private var panel: HintListPanel? = null
	private val analyzer = ShortcutAnalyzer(KeyHint.state.actionIdPattern.toRegex())

	init {
		IdeEventQueue.getInstance().addPreprocessor(
			{ e -> if (e is KeyEvent) onKeyEvent(e); false },
			this,
		)
	}

	private fun onKeyEvent(e: KeyEvent) {
		analyzer.onKeyEvent(e)
		if (!analyzer.stateChanged()) return
		when (analyzer.keyIntent()) {
			ShortcutAnalyzer.KeyIntent.CharacterTyping -> {
				panel?.close()
				return
			}

			ShortcutAnalyzer.KeyIntent.ShortcutPreparation -> {}

			ShortcutAnalyzer.KeyIntent.ShortcutComposition -> {}

			ShortcutAnalyzer.KeyIntent.ShortcutTriggered -> {}

			ShortcutAnalyzer.KeyIntent.ShortcutAborted -> return
		}
		val list = analyzer.currentShortcuts()
			.sortedWith(compareBy({ -it.useCount }, { it.prettyDesc() }))
			.map { it.prettyDesc() + ", " + it.actionId }
			.toList()
		updatePanel(list)
	}

	private fun updatePanel(list: List<String>) {
		if (panel.nullOr { !it.isReusable() })
			panel = HintListPanel(editor)

		panel?.let { panel ->
			panel.setTexts(list)
			panel.setToCursorPosition(editor)
		}
	}
}
