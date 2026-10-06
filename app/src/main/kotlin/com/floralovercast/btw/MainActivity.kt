package com.floralovercast.btw

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.floralovercast.btw.bootstrap.Bootstrap
import com.floralovercast.btw.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Layer 1 groundwork: Install runs the bootstrap preflight, which
        // checks the bundled proot without pulling the ~800 MB rootfs. The
        // full download/verify/extract/setup flow lands next (Bootstrap).
        binding.installButton.setOnClickListener {
            binding.subtitle.text = Bootstrap.preflight(this)
        }
    }
}
