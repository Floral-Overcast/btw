package com.floralovercast.btw

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.floralovercast.btw.bootstrap.Bootstrap
import com.floralovercast.btw.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.subtitle.text = Bootstrap.preflight(this)
        binding.installButton.setOnClickListener { startInstall() }
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
            binding.subtitle.text = getString(R.string.already_installed)
            return
        }
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
            }
        }
    }

    private fun fail(e: Exception) {
        binding.progress.visibility = View.GONE
        binding.installButton.isEnabled = true
        binding.subtitle.text = getString(R.string.phase_failed, e.message ?: "unknown error")
    }

    private fun mb(bytes: Long): Int = (bytes / (1024 * 1024)).toInt()
}
