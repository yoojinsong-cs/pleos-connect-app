package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity

class NappingSettingActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_napping_setting)

        val totalSeconds = intent.getIntExtra("TOTAL_SECONDS", 20 * 60)

        findViewById<ImageView>(R.id.tvBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            finishAffinity()
            System.exit(0)
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val intent = Intent(this, NappingActivity::class.java)
            intent.putExtra("TOTAL_SECONDS", totalSeconds)
            startActivity(intent)
        }
    }
}