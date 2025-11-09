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
import com.intellij.openapi.keymap.ex.KeymapManagerEx
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
		if (listener != null) return
		val new = KeyHintEditorFactoryListener()
		listener = new
		EditorFactory.getInstance().addEditorFactoryListener(new, this)
	}

	override fun dispose() {
		listener?.close()
	}
}

class KeyHintEditorFactoryListener : EditorFactoryListener {
	private val listenerMap = Collections.synchronizedMap(IdentityHashMap<Editor, KeyHintKeyListener>())

	override fun editorCreated(event: EditorFactoryEvent) {
		val editor = event.editor
		if (editor in listenerMap) return

		val listener = KeyHintKeyListener(editor)
		listenerMap[editor] = listener
	}

	override fun editorReleased(event: EditorFactoryEvent) {
		val editor = event.editor
		val listener = listenerMap.remove(editor)
		if (listener != null) Disposer.dispose(listener)
	}

	fun close() {
		listenerMap.forEach { (_, listener) ->
			Disposer.dispose(listener)
		}
		listenerMap.clear()
	}
}

class KeyHintKeyListener(private val editor: Editor) : Disposable {

	override fun dispose() {}

	// -------- private --------

	private val analyzer = run {
		val regex = KeyHint.state.actionIdPattern.toRegex()
		ShortcutAnalyzer { actionId ->
			actionId !in ShortcutInlayRenderer.IMPLEMENTED_ACTIONS && regex.matches(actionId)
		}
	}
	private val inlayRenderer = ShortcutInlayRenderer()
	private val panelManager = HintListPanelManager(editor)

	init {
		IdeEventQueue.getInstance().addPreprocessor(
			{ e -> if (e is KeyEvent) onKeyEvent(e); false },
			this,
		)
	}

//	private val test = Test(editor)

	private fun updateTestState(e: KeyEvent) {
		if (e.id != KeyEvent.KEY_PRESSED || e.keyCode != KeyEvent.VK_SHIFT) return
//		test.testInlayModel()
	}

	private fun onKeyEvent(e: KeyEvent) {
		updateTestState(e)
		analyzer.onKeyEvent(e)
		if (!analyzer.stateChanged()) return
		when (analyzer.keyIntent()) {
			ShortcutAnalyzer.KeyIntent.CharacterTyping -> {
				panelManager.close()
				inlayRenderer.close()
				return
			}

			ShortcutAnalyzer.KeyIntent.ShortcutPreparation,
			ShortcutAnalyzer.KeyIntent.ShortcutComposition,
			ShortcutAnalyzer.KeyIntent.ShortcutTriggered -> {
			}

			ShortcutAnalyzer.KeyIntent.ShortcutAborted -> return
		}
		val list = analyzer.currentShortcuts()
			.sortedWith(compareBy({ it.state.sortKey }, { -it.useCount }, { it.prettyDesc() }))
			.map { it.prettyDesc() + ", " + it.actionId }
			.toList()
		panelManager.showHint(list)
		inlayRenderer.show(editor, KeymapManagerEx.getInstanceEx().activeKeymap)
	}
}
