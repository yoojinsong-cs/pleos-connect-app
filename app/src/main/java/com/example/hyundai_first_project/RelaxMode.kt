package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [릴렉스 담당] RelaxMode.kt  ─ 이브닝 릴렉스 (SAI)
// ① 오늘의 환경: Gleo가 옆에서 말하면서 조명·바람·시트를 바꿔요
// ② 오늘의 싱잉볼 → Step 1~5 스트레칭 (각 12초)
// ③ 오늘의 리추얼 완료 → 이번 주 리추얼 기록 (월~일) → 원래대로
// 종료하기 버튼은 내내 오른쪽 아래에 있어요.
// ─────────────────────────────────────────────
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Step 칸 이름 / 화면 제목 / 안내 음성
private data class RitualStep(val chip: String, val title: String, val guide: String)

class RelaxMode : RestMode {

    // ===== 바꿔도 되는 값 (대부분 여기만 고치면 돼요) =====
    private val stepSeconds = 12                    // Step 하나당 시간
    private val bowlSeconds = 15                    // Step 1 전에 싱잉볼만 울리는 시간
    private val autoStartSeconds = 10               // 오늘의 환경 화면 최소 시간
    private val recordSeconds = 10                  // 기록 화면 보여주는 시간
    private val seatAngle = 118.0                   // 시트 등받이 (115~120)
    private val baseTemp = 24f                      // 공조 기준 온도
    private val warmLight = Color.rgb(255, 159, 70) // 무드등 2500K 느낌

    private val steps = listOf(
        RitualStep("호흡", "숨을 크게 쉬어주세요", "코로 깊게 들이마시고, 입으로 천천히 내쉬어 보세요."),
        RitualStep("어깨", "어깨의 힘을 빼주세요", "어깨를 귀 쪽으로 올렸다가, 툭 떨어뜨려 보세요."),
        RitualStep("턱", "턱의 긴장을 풀어주세요", "윗니와 아랫니 사이를 살짝 띄우고, 턱을 편안하게 두세요."),
        RitualStep("손", "손을 편하게 펼쳐주세요", "주먹을 꼭 쥐었다가, 천천히 펴 보세요."),
        RitualStep("집중", "지금의 나에게 집중해요", "역할을 잠시 내려놓고, 지금 이 순간의 나를 느껴 보세요.")
    )
    private val introText = "릴렉스 모드를 선택하셨습니다. 맞춤 릴렉스 모드 환경을 구축합니다."
    private val lightText = "오늘의 환경에 따라 따뜻한 조명을 구성하고, 바람이 약하게 나오게 설정합니다."
    private val bowlText = "오늘의 싱잉볼을 울립니다. 소리를 따라 천천히 몸을 풀어 볼게요."
    private val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")

    // 색 3개
    private val bg = "#1B1C20"
    private val white = "#F6F6F7"
    private val accent = "#DCC6AB"

    private lateinit var host: ModeHost
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var step = ""            // env → ritual → done → record
    private var countdown = 0
    private var remaining = 0
    private var currentStep = -1
    private var targetTemp = baseTemp
    private var gleoDone = false
    private var airApplied = false
    private var seatApplied = false
    private var seatNow: Double? = null

    private lateinit var envLayer: View
    private lateinit var ritualLayer: View
    private lateinit var recordLayer: View
    private lateinit var countdownText: TextView
    private lateinit var tempLabel: TextView
    private lateinit var tempSeek: SeekBar
    private lateinit var volumeSeek: SeekBar
    private lateinit var gleoLog: TextView
    private lateinit var gleoBubble: TextView
    private lateinit var timerText: TextView
    private lateinit var ritualTitle: TextView
    private lateinit var ritualSub: TextView
    private lateinit var bowl: SingingBowlView
    private lateinit var recordTitle: TextView
    private lateinit var recordCountdown: TextView
    private lateinit var weekRow: LinearLayout
    private val stepChips = mutableListOf<TextView>()

    private val totalSeconds get() = bowlSeconds + stepSeconds * steps.size

    override fun start(host: ModeHost) {
        this.host = host
        active = true
        val root = LayoutInflater.from(host.activity).inflate(R.layout.mode_relax, host.container, true)
        bind(root)
        showEnvironment()
    }

    override fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        if (::host.isInitialized) host.sounds.stopLoop()
    }

    private fun dp(value: Int) = (value * host.activity.resources.displayMetrics.density).toInt()

    private fun bind(root: View) {
        envLayer = root.findViewById(R.id.relaxEnvLayer)
        ritualLayer = root.findViewById(R.id.relaxRitualLayer)
        recordLayer = root.findViewById(R.id.relaxRecordLayer)
        countdownText = root.findViewById(R.id.relaxCountdown)
        tempLabel = root.findViewById(R.id.relaxTempLabel)
        tempSeek = root.findViewById(R.id.relaxTempSeek)
        volumeSeek = root.findViewById(R.id.relaxVolumeSeek)
        gleoLog = root.findViewById(R.id.relaxGleoLog)
        gleoBubble = root.findViewById(R.id.relaxGleoBubble)
        timerText = root.findViewById(R.id.relaxTimer)
        ritualTitle = root.findViewById(R.id.relaxRitualTitle)
        ritualSub = root.findViewById(R.id.relaxRitualSub)
        bowl = root.findViewById(R.id.relaxBowl)
        recordTitle = root.findViewById(R.id.relaxRecordTitle)
        recordCountdown = root.findViewById(R.id.relaxRecordCountdown)
        weekRow = root.findViewById(R.id.relaxWeekRow)

        root.findViewById<TextView>(R.id.relaxEnvTitle).text = "${host.driverName}님에게 맞는\n오늘의 환경이에요."

        // 버튼
        root.findViewById<View>(R.id.relaxExit).setOnClickListener { host.finish("릴렉스 모드를 종료했어요.") }
        countdownText.setOnClickListener { startRitual() }               // "10s ›" 누르면 바로 시작
        recordCountdown.setOnClickListener { goBack() }                  // 기록 화면에서 바로 복귀

        // 꾸미기 (#1AF6F6F7 = 흰색을 10%만 보이게)
        listOf(R.id.relaxTileLight, R.id.relaxTileFan, R.id.relaxTileSeat).forEach {
            root.findViewById<View>(it).round("#1AF6F6F7", 32f, "#33F6F6F7")
        }
        root.findViewById<View>(R.id.relaxGleoPanel).round("#14F6F6F7", 28f, "#4DDCC6AB")
        root.findViewById<View>(R.id.relaxExit).round(bg, 6f, "#80F6F6F7")
        listOf(tempSeek, volumeSeek).forEach { seek ->
            seek.progressTintList = ColorStateList.valueOf(Color.parseColor(accent))
            seek.thumbTintList = ColorStateList.valueOf(Color.parseColor(white))
            seek.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#4DF6F6F7"))
        }

        // Step 1~5 칸 만들기
        val stepRow = root.findViewById<LinearLayout>(R.id.relaxStepRow)
        steps.forEachIndexed { i, s ->
            val chip = TextView(host.activity).apply {
                text = "STEP ${i + 1}\n${s.chip}"
                gravity = Gravity.CENTER
                textSize = 20f
                setLineSpacing(dp(4).toFloat(), 1f)
            }
            val params = LinearLayout.LayoutParams(0, dp(96), 1f)
            if (i > 0) params.marginStart = dp(14)
            stepRow.addView(chip, params)
            stepChips.add(chip)
        }
        showChips(-1)

        // 온도 슬라이더: 18 ~ 28도, 0.5도 단위
        tempSeek.max = 20
        tempSeek.progress = ((baseTemp - 18f) * 2).toInt()
        showTemp()
        tempSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                targetTemp = 18f + progress * 0.5f
                showTemp()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                countdown = autoStartSeconds                         // 만지는 동안 카운트다운 다시
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                host.car.setTemperature(targetTemp)
                gleoSay(String.format(Locale.KOREA, "온도를 %.1f도로 맞출게요.", targetTemp))
            }
        })
        volumeSeek.max = 100
        volumeSeek.progress = (host.sounds.volume * 100).toInt()
        volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                host.sounds.volume = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showTemp() {
        tempLabel.text = String.format(Locale.KOREA, "%.1f°C", targetTemp)
    }

    private fun showLayer(layer: View) {
        listOf(envLayer, ritualLayer, recordLayer).forEach {
            it.visibility = if (it == layer) View.VISIBLE else View.GONE
        }
    }

    // Gleo가 말하기: 말풍선 글자 + 음성. 앞에 한 말은 위로 흐리게 올라가요.
    private fun gleoSay(text: String, then: (() -> Unit)? = null) {
        val previous = gleoBubble.text.toString()
        if (previous.isNotBlank() && previous != "환경을 준비하고 있어요.") {
            gleoLog.text = if (gleoLog.text.isBlank()) previous else "${gleoLog.text}\n$previous"
        }
        gleoBubble.text = text
        gleoBubble.alpha = 0f
        gleoBubble.animate().alpha(1f).setDuration(400).start()
        host.speak(text) { if (active && step == "env") then?.invoke() }
    }

    // ── ① 오늘의 환경 ─────────────────────
    private fun showEnvironment() {
        step = "env"
        showLayer(envLayer)
        host.car.readSeatBackrestDegrees { seatNow = it }      // 바꾸기 전 각도 먼저 읽기
        gleoSay(introText) {
            applyAir()
            gleoSay(lightText) {
                applySeat()
                gleoSay(seatSentence()) {
                    gleoSay(String.format(Locale.KOREA, "공조는 %.0f도, 내부 순환으로 맞췄어요. 원하는 온도가 있으면 말씀해 주세요.", targetTemp)) {
                        gleoDone = true
                    }
                }
            }
        }
        countdown = autoStartSeconds
        handler.post(countdownTick)
    }

    private fun seatSentence(): String {
        val now = seatNow
        val target = seatAngle.roundToInt()
        return if (now != null && abs(now - seatAngle) >= 1) {
            "시트의 각도를 현재 ${now.roundToInt()}도에서 ${target}도로 눕힙니다. 허리 받침도 앞으로 당길게요."
        } else {
            "시트의 각도를 ${target}도로 눕히고, 허리 받침을 앞으로 당깁니다."
        }
    }

    private fun applyAir() {
        if (airApplied) return
        airApplied = true
        val car = host.car
        car.setAmbientColor(warmLight)            // 따뜻한 조명 2500K
        car.lightsOff()                           // 실내·외부 조명 끄기
        car.setFanLow()                           // 약한 바람
        car.setTemperature(targetTemp)            // 기준 24도
        car.setRecirculation(true)                // 내부 순환
    }

    private fun applySeat() {
        if (seatApplied) return
        seatApplied = true
        host.car.moveSeatBackrest(seatAngle)      // 시트 118° (바꾸기 전 값은 자동 저장)
        host.car.moveLumbarForward()              // 허리 받침 앞으로
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            if (!active || step != "env") return
            if (countdown > 0) {
                countdownText.text = "${countdown}s  ›"
                countdown--
            } else if (gleoDone) {
                startRitual()
                return
            } else {
                countdownText.text = "설정 중…  ›"             // Gleo 말이 끝날 때까지 기다려요
            }
            handler.postDelayed(this, 1000)
        }
    }

    // ── ② 싱잉볼 + Step 1~5 ───────────────
    private fun startRitual() {
        if (!active || step != "env") return
        step = "ritual"
        handler.removeCallbacks(countdownTick)
        applyAir()                                // 건너뛰었어도 설정은 꼭 적용
        applySeat()
        showLayer(ritualLayer)
        ritualTitle.text = "오늘의 싱잉볼"
        ritualSub.text = "역할과 역할 사이를 조율한다"
        showChips(-1)
        strike()
        host.speak(bowlText)
        remaining = totalSeconds
        currentStep = -1
        handler.post(ritualTick)
    }

    private val ritualTick = object : Runnable {
        override fun run() {
            if (!active || step != "ritual") return
            timerText.text = String.format(Locale.KOREA, "%d:%02d", remaining / 60, remaining % 60)
            if (remaining <= 0) {
                completeRitual()
                return
            }
            val elapsed = totalSeconds - remaining
            if (elapsed < bowlSeconds) {
                if (elapsed > 0 && elapsed % 8 == 0) strike()           // 싱잉볼 8초마다
            } else {
                val index = minOf(steps.size - 1, (elapsed - bowlSeconds) / stepSeconds)
                if (index != currentStep) showStep(index)
            }
            remaining--
            handler.postDelayed(this, 1000)
        }
    }

    private fun showStep(index: Int) {
        currentStep = index
        val s = steps[index]
        if (index == 0) host.sounds.startLoop(Ambience.MEDITATION)    // 잔잔한 배경음
        ritualTitle.text = s.title
        ritualSub.text = s.guide
        showChips(index)
        strike()
        host.speak(s.guide)
        // 숨 한 번: 5초 동안 볼이 커지고(들이쉬기) 7초 동안 작아져요(내쉬기)
        bowl.animate().scaleX(1.08f).scaleY(1.08f).setDuration(5000).withEndAction {
            bowl.animate().scaleX(1f).scaleY(1f).setDuration(7000).start()
        }.start()
    }

    // Step 칸 색: 지난 칸 = 강조 글자, 지금 칸 = 강조 바탕, 남은 칸 = 흐린 흰색
    private fun showChips(current: Int) {
        stepChips.forEachIndexed { i, chip ->
            when {
                i == current -> {
                    chip.round("#4DDCC6AB", 8f, accent)
                    chip.setTextColor(Color.parseColor(white))
                    chip.setTypeface(null, Typeface.BOLD)
                }
                i < current -> {
                    chip.round(bg, 8f, "#80DCC6AB")
                    chip.setTextColor(Color.parseColor(accent))
                    chip.setTypeface(null, Typeface.NORMAL)
                }
                else -> {
                    chip.round(bg, 8f, "#4DF6F6F7")
                    chip.setTextColor(Color.parseColor("#80F6F6F7"))
                    chip.setTypeface(null, Typeface.NORMAL)
                }
            }
        }
    }

    private fun strike() {
        host.sounds.strikeBowl()
        bowl.strike()
    }

    // ── ③ 완료 → 기록 → 복귀 ───────────────
    private fun completeRitual() {
        if (!active || step != "ritual") return
        step = "done"
        host.sounds.stopLoop()
        bowl.animate().cancel()
        bowl.scaleX = 1f
        bowl.scaleY = 1f
        timerText.text = "0:00"
        showChips(steps.size)                     // 전부 지난 칸으로
        ritualTitle.text = "오늘의 리추얼 완료"
        ritualSub.text = "수고하셨어요"
        strike()
        saveRecord()
        host.speak("오늘의 리추얼을 완료했어요.") {
            handler.postDelayed({ showRecord() }, 1000)
        }
    }

    private fun showRecord() {
        if (!active || step != "done") return
        step = "record"
        showLayer(recordLayer)
        val week = thisWeekCounts()
        val total = week.sum()
        recordTitle.text = if (total <= 1) "이번주 첫 번째 리추얼이에요" else "이번주도 벌써 ${total}번째군요"
        val todayIndex = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7   // 월=0 … 일=6

        weekRow.removeAllViews()
        dayNames.forEachIndexed { i, name ->
            val box = TextView(host.activity).apply {
                text = String.format(Locale.KOREA, "%02d  %s", i + 1, name)
                gravity = Gravity.CENTER
                textSize = 22f
                if (week[i] > 0) {
                    round("#4DDCC6AB", 4f, accent)                  // 리추얼 한 날
                    setTextColor(Color.parseColor(white))
                } else {
                    round(bg, 4f, "#4DF6F6F7")
                    setTextColor(Color.parseColor("#B3F6F6F7"))
                }
                if (i == todayIndex) setTypeface(null, Typeface.BOLD)
            }
            val params = LinearLayout.LayoutParams(0, dp(76), 1f)
            if (i > 0) params.marginStart = dp(14)
            weekRow.addView(box, params)
        }

        host.speak("${recordTitle.text}. 마이현대 앱에서 자세히 확인하실 수 있습니다.")
        countdown = recordSeconds
        handler.post(recordTick)
    }

    private val recordTick = object : Runnable {
        override fun run() {
            if (!active || step != "record") return
            recordCountdown.text = "${countdown}s  ›"
            if (countdown <= 0) {
                goBack()
                return
            }
            countdown--
            handler.postDelayed(this, 1000)
        }
    }

    private fun goBack() {
        if (!active) return
        host.finish("원래 환경으로 돌아왔어요. 개운하게 출발해요.")   // MainActivity가 시트·환경 전부 원래대로
    }

    // ── 기록 저장 / 읽기 (앱 안에 저장돼요) ──
    private fun prefs() = host.activity.getSharedPreferences("sai_ritual", Context.MODE_PRIVATE)

    private fun saveRecord() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Calendar.getInstance().time)
        val old = prefs().getString("dates", "") ?: ""
        val dates = if (old.isBlank()) today else "$old,$today"
        prefs().edit().putString("dates", dates).apply()
    }

    // 이번 주 월~일 각각 몇 번 했는지
    private fun thisWeekCounts(): IntArray {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val monday = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val weekDays = (0 until 7).map { i ->
            val day = monday.clone() as Calendar
            day.add(Calendar.DAY_OF_MONTH, i)
            format.format(day.time)
        }
        val dates = (prefs().getString("dates", "") ?: "").split(",")
        return IntArray(7) { i -> dates.count { it == weekDays[i] } }
    }
}
