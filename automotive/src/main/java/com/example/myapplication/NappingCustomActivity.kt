package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

class NappingCustomActivity : FragmentActivity() {

    private var lightLevel = 0
    private var fanLevel = 1
    private var seatAngle = 130

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_napping_custom)

        val tvLightValue = findViewById<TextView>(R.id.tvLightValue)
        val tvFanValue   = findViewById<TextView>(R.id.tvFanValue)
        val tvSeatValue  = findViewById<TextView>(R.id.tvSeatValue)

        findViewById<TextView>(R.id.tvBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            finishAffinity()
            System.exit(0)
        }

        // 조명 슬라이더
        findViewById<SeekBar>(R.id.seekLight).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                lightLevel = progress
                tvLightValue.text = when (progress) {
                    0 -> "소등"
                    1 -> "낮음"
                    2 -> "보통"
                    else -> "최대"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 풍량 슬라이더
        findViewById<SeekBar>(R.id.seekFan).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                fanLevel = progress
                tvFanValue.text = when (progress) {
                    0 -> "끔"
                    1 -> "낮음"
                    2 -> "보통"
                    else -> "최대"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 시트 슬라이더 (100~160도)
        findViewById<SeekBar>(R.id.seekSeat).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                seatAngle = 100 + progress
                tvSeatValue.text = "${seatAngle}°"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 시작하기
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val intent = Intent(this, NappingActivity::class.java)
            intent.putExtra("lightLevel", lightLevel)
            intent.putExtra("fanLevel", fanLevel)
            intent.putExtra("seatAngle", seatAngle)
            startActivity(intent)
        }
    }
}