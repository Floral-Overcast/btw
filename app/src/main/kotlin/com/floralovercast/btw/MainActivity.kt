package com.floralovercast.btw

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.floralovercast.btw.bootstrap.Bootstrap
import com.floralovercast.btw.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Suppresses repeat log lines from the many Download progress ticks. */
    private var lastPhaseLogged: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.subtitle.text = Bootstrap.preflight(this)
        refreshButton()

        // Debug hook for the hands-free adb smoke test: start the install
        // without a tap. `am start ... --ez auto_install true`.
        if (intent.getBooleanExtra(EXTRA_AUTO_INSTALL, false)) {
            Log.i(TAG, "auto_install extra set; starting install")
            startInstall()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshButton()
    }

    /** Install when there's no rootfs yet, otherwise open the terminal. */
    private fun refreshButton() {
        if (Bootstrap.isInstalled(this)) {
            binding.installButton.setText(R.string.open_terminal)
            binding.installButton.setOnClickListener { openTerminal() }
        } else {
            binding.installButton.setText(R.string.install)
            binding.installButton.setOnClickListener { startInstall() }
        }
    }

    private fun openTerminal() {
        startActivity(Intent(this, TerminalActivity::class.java))
    }

    /**
     * Run the bootstrap off the main thread; marshal phase updates back to
     * the UI. Dumb-simple: one worker thread, runOnUiThread for the view.
     * No service yet - the install is a one-shot foreground task (phantom
     * process killer mitigation lands with the long-running terminal, not
     * here; see docs/architecture.md gotchas).
     */
    private fun startInstall() {
        if (Bootstrap.isInstalled(this)) {
            Log.i(TAG, "already installed; nothing to do")
            binding.subtitle.text = getString(R.string.already_installed)
            return
        }
        Log.i(TAG, "install started")
        binding.installButton.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.progress.isIndeterminate = true

        thread(name = "btw-bootstrap") {
            try {
                Bootstrap.install(this) { phase -> runOnUiThread { render(phase) } }
            } catch (e: Exception) {
                runOnUiThread { fail(e) }
            }
        }
    }

    private fun render(phase: Bootstrap.Phase) {
        logPhase(phase)
        when (phase) {
            is Bootstrap.Phase.CheckProot ->
                binding.subtitle.text = getString(R.string.phase_check)

            is Bootstrap.Phase.Download -> {
                if (phase.total > 0) {
                    binding.progress.isIndeterminate = false
                    val pct = (phase.soFar * 100 / phase.total).toInt()
                    binding.progress.progress = pct
                    binding.subtitle.text =
                        getString(R.string.phase_download_pct, pct, mb(phase.total))
                } else {
                    binding.progress.isIndeterminate = true
                    binding.subtitle.text = getString(R.string.phase_download)
                }
            }

            is Bootstrap.Phase.Verify -> {
                binding.progress.isIndeterminate = true
                binding.subtitle.text = getString(R.string.phase_verify)
            }

            is Bootstrap.Phase.Extract ->
                binding.subtitle.text = getString(R.string.phase_extract)

            is Bootstrap.Phase.Setup ->
                binding.subtitle.text = getString(R.string.phase_setup)

            is Bootstrap.Phase.Done -> {
                binding.progress.visibility = View.GONE
                binding.subtitle.text = getString(R.string.phase_done)
                binding.installButton.isEnabled = true
                refreshButton() // flip Install -> Open terminal
            }
        }
    }

    private fun fail(e: Exception) {
        Log.e(TAG, "install failed: ${e.message}", e)
        binding.progress.visibility = View.GONE
        binding.installButton.isEnabled = true
        binding.subtitle.text = getString(R.string.phase_failed, e.message ?: "unknown error")
    }

    /**
     * One logcat line per phase transition under a single tag so an adb-driven
     * smoke test can follow progress hands-free. Download ticks many times;
     * collapse them to the first and last line.
     */
    private fun logPhase(phase: Bootstrap.Phase) {
        val line = when (phase) {
            is Bootstrap.Phase.CheckProot -> "phase: check proot"
            is Bootstrap.Phase.Download ->
                if (phase.total > 0 && phase.soFar >= phase.total)
                    "phase: download complete (${mb(phase.total)} MB)"
                else "phase: download"
            is Bootstrap.Phase.Verify -> "phase: verify checksum"
            is Bootstrap.Phase.Extract -> "phase: extract"
            is Bootstrap.Phase.Setup -> "phase: first-run setup"
            is Bootstrap.Phase.Done -> "phase: done"
        }
        if (line != lastPhaseLogged) {
            Log.i(TAG, line)
            lastPhaseLogged = line
        }
    }

    private fun mb(bytes: Long): Int = (bytes / (1024 * 1024)).toInt()

    companion object {
        /** Single logcat tag for the whole bootstrap flow; `adb logcat -s BTW`. */
        const val TAG = "BTW"

        /** `am start ... --ez auto_install true` to drive install without a tap. */
        const val EXTRA_AUTO_INSTALL = "auto_install"
    }
}
