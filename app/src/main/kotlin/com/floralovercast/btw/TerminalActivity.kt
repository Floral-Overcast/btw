package com.floralovercast.btw

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.floralovercast.btw.bootstrap.Bootstrap
import com.floralovercast.btw.bootstrap.Proot
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * A minimal interactive terminal: a vendored Termux [TerminalView] driving a
 * [TerminalSession] whose child process is our bundled proot, which in turn
 * runs bash inside the extracted rootfs. The session's own PTY JNI
 * (libtermux.so) fork+execs proot on a real pseudoterminal - that's why we
 * hand it the proot path and argv rather than a java.lang.Process (pipes
 * can't carry an interactive shell).
 *
 * This activity is both clients the view/session need. Most callbacks are
 * deliberate no-ops: this is the dumb-simple first cut (CLAUDE.md), enough
 * to prove a shell comes up. Richer input handling comes later.
 *
 * Terminal rendering/input code is vendored from Termux (GPL-3.0); see
 * third_party/termux/README.md.
 */
class TerminalActivity : AppCompatActivity(), TerminalViewClient, TerminalSessionClient {

    private lateinit var terminal: TerminalView
    private var session: TerminalSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        terminal = findViewById(R.id.terminal)
        terminal.setTerminalViewClient(this)
        terminal.setTextSize((14 * resources.displayMetrics.density).toInt())

        val rootfs = Bootstrap.rootfsDir(this)
        val argv = Proot.loginShellArgv(this, rootfs).toTypedArray()
        val s = TerminalSession(
            Proot.binaryPath(this),   // shellPath: the executable we exec (proot)
            rootfs.absolutePath,      // cwd (host side)
            argv,                     // argv, incl. argv[0]=proot and the proot flags
            Proot.envArray(this),
            TRANSCRIPT_ROWS,
            this,
        )
        session = s
        // The view starts the session (spawns proot) once it has been
        // measured; see TerminalView.updateSize.
        terminal.attachSession(s)
        terminal.requestFocus()
        terminal.post { showKeyboard() }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.finishIfRunning()
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
    }

    // --- TerminalViewClient ---------------------------------------------

    override fun onScale(scale: Float): Float = scale
    override fun onSingleTapUp(e: MotionEvent?) = showKeyboard()
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    override fun onLongPress(event: MotionEvent?): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
    override fun onEmulatorSet() {}

    // --- TerminalSessionClient ------------------------------------------

    override fun onTextChanged(changedSession: TerminalSession) {
        if (session == changedSession) terminal.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        finish()
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}

    override fun onPasteTextFromClipboard(session: TerminalSession?) {}

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = null

    // --- logging (shared by both client interfaces) ---------------------

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: TAG, message ?: "", e)
    }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "", e) }

    private companion object {
        const val TAG = "btw-term"
        const val TRANSCRIPT_ROWS = 2000
    }
}
