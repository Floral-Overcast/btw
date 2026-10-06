package com.floralovercast.btw

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.floralovercast.btw.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Placeholder. Layer 1 wires this to the bootstrap flow:
        // download Arch Linux ARM rootfs, verify, extract, open a shell.
        binding.installButton.setOnClickListener {
            binding.installButton.isEnabled = false
            binding.installButton.text = getString(R.string.install_pending)
        }
    }
}
