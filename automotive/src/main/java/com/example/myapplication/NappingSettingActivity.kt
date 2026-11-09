package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

class NappingSettingActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_napping_setting)

        findViewById<TextView>(R.id.tvBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnClose).setOnClickListener {
            finishAffinity()
            System.exit(0)
        }

        // 이 설정으로 시작하기
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, NappingTimeSelectActivity::class.java))
        }

        // 직접 조절하기
        findViewById<Button>(R.id.btnCustom).setOnClickListener {
            startActivity(Intent(this, NappingCustomActivity::class.java))
        }
    }
}