package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

class NappingActivity : FragmentActivity() {

    private val totalSeconds = 1 * 60
    private var elapsedSeconds = 0
    private val handler = Handler(Looper.getMainLooper())

    // 단계 정의
    data class Phase(
        val label: String,
        val title: String,
        val subtitle: String,
        val phaseInfo: String,
        val startPercent: Int,
        val endPercent: Int
    )

    private val phases = listOf(
        Phase(
            label = "NAPPING · WIND DOWN",
            title = "눈을 감고 편안하게 쉬세요",
            subtitle = "안내 음성은 잠시 후 종료돼요.",
            phaseInfo = "수면 진입",
            startPercent = 0,
            endPercent = 25
        ),
        Phase(
            label = "NAPPING · SLEEP",
            title = "휴식 중이에요",
            subtitle = "편안하게 쉬고 계세요.",
            phaseInfo = "숙면 중",
            startPercent = 25,
            endPercent = 85
        ),
        Phase(
            label = "NAPPING · WAKE UP",
            title = "천천히 깨어날 시간이에요",
            subtitle = "부드럽게 일어날 준비를 해요.",
            phaseInfo = "각성 1",
            startPercent = 85,
            endPercent = 95
        ),
        Phase(
            label = "NAPPING · GET READY",
            title = "마지막 1분이에요",
            subtitle = "곧 완전히 깨어날 준비를 해주세요.",
            phaseInfo = "각성 2",
            startPercent = 95,
            endPercent = 100
        )
    )

    private val ticker = object : Runnable {
        override fun run() {
            elapsedSeconds++
            updateUI()
            if (elapsedSeconds < totalSeconds) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_napping_progress)

        findViewById<TextView>(R.id.tvBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnClose).setOnClickListener {
            finishAffinity()
            System.exit(0)
        }

        handler.postDelayed(ticker, 1000)
        updateUI()
    }

    private fun updateUI() {
        val percent = (elapsedSeconds * 100 / totalSeconds)
        val phase = phases.lastOrNull { percent >= it.startPercent } ?: phases[0]

        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60

        findViewById<TextView>(R.id.tvPhaseLabel).text = phase.label
        findViewById<TextView>(R.id.tvTitle).text = phase.title
        findViewById<TextView>(R.id.tvSubtitle).text = phase.subtitle
        findViewById<TextView>(R.id.tvPhaseInfo).text = phase.phaseInfo
        findViewById<TextView>(R.id.tvElapsed).text = "%02d:%02d".format(minutes, seconds)
        findViewById<TextView>(R.id.tvPhasePercent).text = "${percent}%"
        findViewById<ProgressBar>(R.id.progressBar).progress = percent
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
    }
}