package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [릴렉스 담당] RelaxMode.kt  ─ 릴렉스 (SAI v8)
// ① 싱잉볼 1분 (10초마다 한 번씩 6번 울림)
// ② 환경 확인: 조명·바람·등받이 → "이대로 시작" 또는 "설정 변경"(조명·바람·등받이·온도·소리)
// ③ 3초 카운트다운 하면서 차량 환경 맞추기
// ④ 릴렉스 5분: 1단계 호흡 → 2단계 어깨 → 3단계 목 → 4단계 가슴
// ⑤ 릴렉스 완료 → 세션 완료 (차량 환경 원래대로) → 이번 주 리추얼 기록 → 홈
// 진행 시간은 음악 플레이어처럼 "경과 / 전체 + 진행바 + %", 오른쪽 위엔 "다음 스텝".
// 위쪽 ✕ 는 MainActivity가 처리해요 (리추얼 중엔 홈으로, 끝난 뒤엔 앱 닫기).
// ─────────────────────────────────────────────
import android.annotation.SuppressLint
import android.content.Context
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

// 릴렉스 단계 하나: 칸 이름 / 제목 / 설명 / 음성 안내 / 시간(초)
private data class Stage(val name: String, val title: String, val sub: String, val guide: String, val seconds: Int)

class RelaxMode : RestMode {

    // ===== 바꿔도 되는 값 (대부분 여기만 고치면 돼요) =====
    private val speed = 5                           // ★ 테스트용: 릴렉스 5분만 5배 빠르게. 실제로 쓸 땐 1 로!
    private val bowlSeconds = 60                    // 싱잉볼 시간 (1분)
    private val strikeEvery = 10                    // ★ 싱잉볼을 몇 초마다 칠지 (10초에 한 번 = 1분에 6번)
    private val countdownSeconds = 3                // 환경 맞추는 카운트다운 (3초)
    private val prepareAutoSeconds = 20             // 환경 확인 화면에서 안 누르면 자동 시작 (0이면 안 함)
    private val doneSeconds = 8                     // 세션 완료 화면 뒤 자동으로 기록 화면
    private val recordSeconds = 15                  // 기록 화면 뒤 자동으로 홈
    private val baseSeat = 118                      // 등받이 기본 각도
    private val baseTemp = 24f                      // 공조 기본 온도

    // 조명 고르기: 이름 / 색
    private val lights = listOf(
        "따뜻한 색" to Color.rgb(255, 159, 70),
        "은은한 흰색" to Color.rgb(255, 214, 170),
        "차분한 보라" to Color.rgb(169, 162, 232)
    )
    // 바람 고르기: 이름 / 세기 (0 = 가장 약하게, 1 = 가장 세게)
    private val winds = listOf(
        "약하게" to 0f,
        "보통" to 0.4f,
        "강하게" to 0.8f
    )

    // 릴렉스 5분 = 150 + 50 + 50 + 50 초
    private val stages = listOf(
        Stage("호흡", "호흡을 천천히 이어가세요", "입자의 움직임에 맞춰 편안하게 호흡해 보세요.",
            "호흡을 천천히 이어가세요. 입자가 커질 때 코로 들이마시고, 작아질 때 입으로 천천히 내쉬어 보세요.", 150),
        Stage("어깨", "어깨의 힘을 빼 주세요", "어깨를 귀 쪽으로 올렸다가, 툭 떨어뜨려 보세요.",
            "어깨를 귀 쪽으로 천천히 올렸다가, 숨을 내쉬며 툭 떨어뜨려 보세요.", 50),
        Stage("목", "목을 천천히 풀어 주세요", "고개를 오른쪽, 왼쪽으로 천천히 기울여 보세요.",
            "고개를 오른쪽으로 천천히 기울이고, 다시 왼쪽으로 기울여 보세요. 통증이 없는 범위에서 해 주세요.", 50),
        Stage("가슴", "가슴을 활짝 열어 주세요", "어깨를 뒤로 크게 돌려 가슴을 열어 보세요.",
            "어깨를 뒤로 크게 돌리면서, 가슴을 활짝 열어 보세요.", 50)
    )

    // 색
    private val beige = "#DCC6AB"
    private val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")
    private val dark = "#1B1C20"

    private lateinit var host: ModeHost
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var phase = ""           // bowl → prepare → loading → relax → complete → done → record
    private var elapsed = 0          // 지금 단계에서 지난 초
    private var stageIndex = -1
    private var prepareLeft = 0
    private var countdownLeft = 0
    private var lightChoice = 0
    private var windChoice = 0
    private var targetSeat = baseSeat
    private var targetTemp = baseTemp
    private var seatNow: Double? = null

    private lateinit var label: TextView
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var stepRow: LinearLayout
    private lateinit var bowl: SingingBowlView
    private lateinit var particles: ParticleView
    private lateinit var loadingPanel: View
    private lateinit var countdownText: TextView
    private lateinit var loadingText: TextView
    private lateinit var preparePanel: View
    private lateinit var cardsRow: View
    private lateinit var settingsPanel: View
    private lateinit var buttonsRow: View
    private lateinit var note: TextView
    private lateinit var lightSub: TextView
    private lateinit var airSub: TextView
    private lateinit var seatSub: TextView
    private lateinit var lightChoices: LinearLayout
    private lateinit var airChoices: LinearLayout
    private lateinit var seatLabel: TextView
    private lateinit var tempLabel: TextView
    private lateinit var volumeLabel: TextView
    private lateinit var donePanel: View
    private lateinit var recordPanel: View
    private lateinit var weekRow: LinearLayout
    private lateinit var progressCard: View
    private lateinit var nowLabel: TextView
    private lateinit var nextLabel: TextView
    private lateinit var elapsedText: TextView
    private lateinit var totalText: TextView
    private lateinit var percentText: TextView
    private lateinit var seek: SeekBar
    private val stepChips = mutableListOf<TextView>()

    private val relaxSeconds get() = stages.sumOf { it.seconds }   // 300초 = 5분

    override fun start(host: ModeHost) {
        this.host = host
        active = true
        val root = LayoutInflater.from(host.activity).inflate(R.layout.mode_relax, host.container, true)
        bind(root)
        startBowl()
    }

    override fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        if (::host.isInitialized) host.sounds.stopLoop()
    }

    // 릴렉스 완료 화면부터는 "끝난 상태" → ✕ 를 누르면 앱이 닫혀요
    override fun isFinished() = phase == "complete" || phase == "done" || phase == "record"

    private val tickMillis get() = 1000L / speed.coerceAtLeast(1)
    private fun dp(value: Int) = (value * host.activity.resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun bind(root: View) {
        label = root.findViewById(R.id.relaxLabel)
        title = root.findViewById(R.id.relaxTitle)
        sub = root.findViewById(R.id.relaxSub)
        stepRow = root.findViewById(R.id.relaxSteps)
        bowl = root.findViewById(R.id.relaxBowl)
        particles = root.findViewById(R.id.relaxParticles)
        loadingPanel = root.findViewById(R.id.relaxLoading)
        countdownText = root.findViewById(R.id.relaxCountdown)
        loadingText = root.findViewById(R.id.relaxLoadingText)
        preparePanel = root.findViewById(R.id.relaxPrepare)
        cardsRow = root.findViewById(R.id.relaxCards)
        settingsPanel = root.findViewById(R.id.relaxSettings)
        buttonsRow = root.findViewById(R.id.relaxButtons)
        note = root.findViewById(R.id.relaxNote)
        lightSub = root.findViewById(R.id.relaxLightSub)
        airSub = root.findViewById(R.id.relaxAirSub)
        seatSub = root.findViewById(R.id.relaxSeatSub)
        lightChoices = root.findViewById(R.id.relaxLightChoices)
        airChoices = root.findViewById(R.id.relaxAirChoices)
        seatLabel = root.findViewById(R.id.relaxSeatLabel)
        tempLabel = root.findViewById(R.id.relaxTempLabel)
        volumeLabel = root.findViewById(R.id.relaxVolumeLabel)
        donePanel = root.findViewById(R.id.relaxDone)
        recordPanel = root.findViewById(R.id.relaxRecord)
        weekRow = root.findViewById(R.id.relaxWeekRow)
        progressCard = root.findViewById(R.id.relaxProgress)
        nowLabel = root.findViewById(R.id.relaxNowLabel)
        nextLabel = root.findViewById(R.id.relaxPhaseLabel)
        elapsedText = root.findViewById(R.id.relaxElapsed)
        totalText = root.findViewById(R.id.relaxTotal)
        percentText = root.findViewById(R.id.relaxPercent)
        seek = root.findViewById(R.id.relaxSeek)
        root.findViewById<ParticleView>(R.id.relaxSphere).style = ParticleView.Style.SPHERE

        // 꾸미기
        listOf(R.id.relaxLightCard, R.id.relaxAirCard, R.id.relaxSeatCard, R.id.relaxDoneCard, R.id.relaxProgress)
            .forEach { root.findViewById<View>(it).round("#14F6F6F7", 20f) }
        listOf(R.id.relaxLightIcon, R.id.relaxAirIcon, R.id.relaxSeatIcon, R.id.relaxDoneIcon)
            .forEach { root.findViewById<View>(it).round("#1AF6F6F7", 22f) }
        settingsPanel.round("#14F6F6F7", 20f)
        root.findViewById<View>(R.id.relaxStart).round("#E6F6F6F7", 14f)
        root.findViewById<View>(R.id.relaxChange).round("#1AF6F6F7", 14f)
        root.findViewById<View>(R.id.relaxHome).round("#E6F6F6F7", 14f)
        root.findViewById<View>(R.id.relaxRecordHome).round("#E6F6F6F7", 14f)

        // 버튼
        root.findViewById<View>(R.id.relaxStart).setOnClickListener { startLoading() }
        root.findViewById<View>(R.id.relaxChange).setOnClickListener { toggleSettings() }
        root.findViewById<View>(R.id.relaxHome).setOnClickListener { showRecord() }       // 세션 완료 → 기록
        root.findViewById<View>(R.id.relaxRecordHome).setOnClickListener { goHome() }   // 기록 → 홈

        // 진행바는 보기만 (손으로 못 움직이게)
        seek.setOnTouchListener { _, _ -> true }

        buildStepChips()
        buildChoices()
        bindSeekBars(root)
        showChoices()
    }

    // ── 1~4단계 칸 만들기 ─────────────────
    private fun buildStepChips() {
        stages.forEachIndexed { i, stage ->
            val chip = TextView(host.activity).apply {
                text = "${i + 1}  ${stage.name}"
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(dp(26), dp(10), dp(26), dp(10))
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i > 0) params.marginStart = dp(12)
            stepRow.addView(chip, params)
            stepChips.add(chip)
        }
    }

    // 지난 단계 ✓ / 지금 단계 베이지 / 남은 단계 흐리게
    private fun showStepChips(current: Int) {
        stepChips.forEachIndexed { i, chip ->
            when {
                i < current -> {
                    chip.text = "✓  ${stages[i].name}"
                    chip.setTextColor(Color.parseColor(beige))
                    chip.round("#1AF6F6F7", 22f)
                }
                i == current -> {
                    chip.text = "${i + 1}단계  ${stages[i].name}"
                    chip.setTextColor(Color.parseColor(dark))
                    chip.round(beige, 22f)
                }
                else -> {
                    chip.text = "${i + 1}  ${stages[i].name}"
                    chip.setTextColor(Color.parseColor("#80F6F6F7"))
                    chip.round("#0DF6F6F7", 22f)
                }
            }
        }
    }

    // ── 설정 변경: 조명·바람 고르는 칸 ───────
    private fun makeChip(name: String, onClick: () -> Unit) = TextView(host.activity).apply {
        text = name
        textSize = 20f
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(12), dp(24), dp(12))
        setOnClickListener { onClick() }
    }

    private fun buildChoices() {
        lights.forEachIndexed { i, choice ->
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i > 0) params.marginStart = dp(12)
            lightChoices.addView(makeChip(choice.first) { lightChoice = i; showChoices() }, params)
        }
        winds.forEachIndexed { i, choice ->
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i > 0) params.marginStart = dp(12)
            airChoices.addView(makeChip(choice.first) { windChoice = i; showChoices() }, params)
        }
    }

    private fun bindSeekBars(root: View) {
        // 등받이: 110 ~ 125도
        val seatSeek = root.findViewById<SeekBar>(R.id.relaxSeatSeek)
        seatSeek.max = 15
        seatSeek.progress = baseSeat - 110
        seatSeek.setOnSeekBarChangeListener(simpleListener { targetSeat = 110 + it; showChoices() })

        // 온도: 18 ~ 28도, 0.5도 단위
        val tempSeek = root.findViewById<SeekBar>(R.id.relaxTempSeek)
        tempSeek.max = 20
        tempSeek.progress = ((baseTemp - 18f) * 2).toInt()
        tempSeek.setOnSeekBarChangeListener(simpleListener { targetTemp = 18f + it * 0.5f; showChoices() })

        // 소리 크기
        val volumeSeek = root.findViewById<SeekBar>(R.id.relaxVolumeSeek)
        volumeSeek.max = 100
        volumeSeek.progress = (host.sounds.volume * 100).toInt()
        volumeSeek.setOnSeekBarChangeListener(simpleListener { host.sounds.volume = it / 100f; showChoices() })
    }

    private fun simpleListener(changed: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            changed(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    // 고른 값을 카드·칸·글자에 반영
    private fun showChoices() {
        for (i in 0 until lightChoices.childCount) highlight(lightChoices.getChildAt(i) as TextView, i == lightChoice)
        for (i in 0 until airChoices.childCount) highlight(airChoices.getChildAt(i) as TextView, i == windChoice)
        lightSub.text = lights[lightChoice].first
        airSub.text = winds[windChoice].first
        seatSub.text = seatText()
        seatLabel.text = "${targetSeat}°"
        tempLabel.text = String.format(Locale.KOREA, "%.1f°C", targetTemp)
        volumeLabel.text = "${(host.sounds.volume * 100).roundToInt()}%"
    }

    private fun seatText(): String {
        val now = seatNow
        return if (now != null && abs(now - targetSeat) >= 1) "${now.roundToInt()}° → ${targetSeat}°" else "${targetSeat}°"
    }

    private fun highlight(chip: TextView, selected: Boolean) {
        chip.setTextColor(Color.parseColor(if (selected) dark else "#F6F6F7"))
        chip.round(if (selected) beige else "#1AF6F6F7", 14f)
    }

    // ── 화면 공통 ─────────────────────────
    // 가운데 그림 하나만 보이기
    private fun showCenter(view: View) {
        listOf(bowl, particles, loadingPanel, preparePanel, donePanel, recordPanel).forEach {
            it.visibility = if (it == view) View.VISIBLE else View.GONE
        }
        progressCard.visibility = if (view == bowl || view == particles) View.VISIBLE else View.GONE
        stepRow.visibility = if (view == particles) View.VISIBLE else View.GONE
    }

    private fun setTexts(labelText: String, titleText: String, subText: String) {
        label.text = labelText
        title.text = titleText
        sub.text = subText
        title.alpha = 0f
        title.animate().alpha(1f).setDuration(500).start()
    }

    // 음악 플레이어처럼: 00:00 / 05:00 + 진행바 + %
    private fun mmss(seconds: Int) = String.format(Locale.KOREA, "%02d:%02d", seconds / 60, seconds % 60)

    private fun showProgress(now: Int, total: Int, nowText: String, nextText: String) {
        val done = now.coerceIn(0, total)
        elapsedText.text = mmss(done)
        totalText.text = "/ ${mmss(total)}"
        percentText.text = "${done * 100 / total}%"
        seek.progress = done * 1000 / total
        nowLabel.text = "지금 · $nowText"
        nextLabel.text = "다음 스텝 : $nextText"
    }

    private fun strike() {
        bowl.strike()
        host.sounds.strikeBowl()
    }

    // ── ① 싱잉볼 1분 ──────────────────────
    private val bowlStrikes get() = (bowlSeconds + strikeEvery - 1) / strikeEvery

    private fun startBowl() {
        phase = "bowl"
        elapsed = 0
        showCenter(bowl)
        setTexts("SINGING BOWL", "싱잉볼 소리에 귀 기울여 보세요", "첫 번째 울림이 시작됩니다.")
        showProgress(0, bowlSeconds, "싱잉볼 1번째 울림 / $bowlStrikes", "환경 확인")
        host.car.readSeatBackrestDegrees { seatNow = it; if (active) showChoices() }   // 바꾸기 전 등받이 각도
        host.speak("릴렉스를 시작할게요. 싱잉볼 소리에 귀 기울여 보세요.")
        strike()
        handler.postDelayed(bowlTick, 1000)
    }

    // 싱잉볼은 소리 간격을 지키려고 speed와 상관없이 실제 1초마다 흘러요
    private val bowlTick = object : Runnable {
        override fun run() {
            if (!active || phase != "bowl") return
            elapsed++
            if (elapsed < bowlSeconds && elapsed % strikeEvery == 0) {
                strike()
                sub.text = "${elapsed / strikeEvery + 1}번째 울림이 퍼지고 있어요."
            }
            val rang = (elapsed / strikeEvery + 1).coerceAtMost(bowlStrikes)
            showProgress(elapsed, bowlSeconds, "싱잉볼 ${rang}번째 울림 / $bowlStrikes", "환경 확인")
            if (elapsed >= bowlSeconds) showPrepare() else handler.postDelayed(this, 1000)
        }
    }

    // ── ② 환경 확인 ───────────────────────
    private fun showPrepare() {
        phase = "prepare"
        showCenter(preparePanel)
        cardsRow.visibility = View.VISIBLE
        settingsPanel.visibility = View.GONE
        buttonsRow.visibility = View.VISIBLE
        setTexts("RELAX · PREPARE", "릴렉스 시작 전 환경을 확인해 주세요", "설정을 확인하거나 원하는 대로 조절할 수 있어요.")
        showChoices()
        host.speak("릴렉스 시작 전 환경을 확인해 주세요. 이대로 시작하거나, 설정을 바꿀 수 있어요.")
        prepareLeft = prepareAutoSeconds
        showPrepareNote()
        if (prepareAutoSeconds > 0) handler.postDelayed(prepareTick, 1000)
    }

    private fun showPrepareNote() {
        note.text = if (prepareLeft > 0) "차량이 지원하는 설정만 적용됩니다. · ${prepareLeft}초 뒤 자동 시작"
        else "차량이 지원하는 설정만 적용됩니다."
    }

    private val prepareTick = object : Runnable {
        override fun run() {
            if (!active || phase != "prepare" || prepareLeft <= 0) return
            prepareLeft--
            showPrepareNote()
            if (prepareLeft == 0) startLoading() else handler.postDelayed(this, 1000)
        }
    }

    // "설정 변경": 카드 대신 조명·바람·등받이·온도·소리 칸이 나와요. 한 번 더 누르면 닫혀요.
    private fun toggleSettings() {
        if (phase != "prepare") return
        prepareLeft = 0                                       // 설정을 만지면 자동 시작은 멈춰요
        showPrepareNote()
        val open = settingsPanel.visibility != View.VISIBLE
        settingsPanel.visibility = if (open) View.VISIBLE else View.GONE
        cardsRow.visibility = if (open) View.GONE else View.VISIBLE
        if (open) host.speak("조명, 바람, 등받이, 온도, 소리를 고른 뒤, 이대로 시작을 눌러 주세요.")
    }

    // ── ③ 3초 카운트다운 (그동안 차량 환경 바꾸기) ──
    private fun startLoading() {
        if (!active || phase != "prepare") return
        phase = "loading"
        showCenter(loadingPanel)
        setTexts("RELAX · READY", "환경을 맞추고 있어요", "잠시 후 릴렉스를 시작해요.")
        applyCar()
        loadingText.text = "조명 ${lights[lightChoice].first} · 바람 ${winds[windChoice].first} · 등받이 ${seatText()}"
        host.speak("환경을 맞추고 있어요. ${countdownSeconds}초 뒤에 시작할게요.")
        countdownLeft = countdownSeconds
        countdownText.text = "$countdownLeft"
        handler.postDelayed(countdownTick, 1000)
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            if (!active || phase != "loading") return
            countdownLeft--
            if (countdownLeft <= 0) {
                startRelax()
            } else {
                countdownText.text = "$countdownLeft"
                countdownText.alpha = 0.3f
                countdownText.animate().alpha(1f).setDuration(300).start()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun applyCar() {
        val car = host.car
        car.setAmbientColor(lights[lightChoice].second)   // 조명 색
        car.lightsOff()                                   // 실내·외부 조명 끄기
        car.setFanLevel(winds[windChoice].second)         // 바람 세기
        car.setTemperature(targetTemp)                    // 온도
        car.setRecirculation(true)                        // 내부 순환
        car.moveSeatBackrest(targetSeat.toDouble())       // 등받이 (바꾸기 전 값은 자동 저장)
        car.moveLumbarForward()                           // 허리 받침 앞으로
    }

    // ── ④ 릴렉스 5분 (1~4단계) ─────────────
    private fun startRelax() {
        phase = "relax"
        elapsed = 0
        stageIndex = -1
        showCenter(particles)
        particles.style = ParticleView.Style.SWIRL
        host.sounds.startLoop(Ambience.MEDITATION)
        showStage(0)
        showProgress(0, relaxSeconds, "1단계 ${stages[0].name}", nextName(0))
        handler.postDelayed(relaxTick, tickMillis)
    }

    // 지난 시간으로 지금 몇 번째 단계인지 계산
    private fun stageAt(second: Int): Int {
        var sum = 0
        for (i in stages.indices) {
            sum += stages[i].seconds
            if (second < sum) return i
        }
        return stages.size - 1
    }

    private fun nextName(index: Int) =
        if (index + 1 < stages.size) "${index + 2}단계 ${stages[index + 1].name}" else "릴렉스 완료"

    private fun showStage(index: Int) {
        stageIndex = index
        val stage = stages[index]
        setTexts("${index + 1}단계 / ${stages.size}단계 · ${stage.name}", stage.title, stage.sub)
        showStepChips(index)
        if (index > 0) strike()                          // 단계가 바뀔 때 싱잉볼 한 번
        host.speak("${index + 1}단계. ${stage.guide}")
    }

    private val relaxTick = object : Runnable {
        override fun run() {
            if (!active || phase != "relax") return
            elapsed++
            if (elapsed >= relaxSeconds) {
                completeRelax()
                return
            }
            val index = stageAt(elapsed)
            if (index != stageIndex) showStage(index)
            showProgress(elapsed, relaxSeconds, "${index + 1}단계 ${stages[index].name}", nextName(index))
            handler.postDelayed(this, tickMillis)
        }
    }

    // ── ⑤ 완료 → 세션 완료 ─────────────────
    private fun completeRelax() {
        phase = "complete"
        showStepChips(stages.size)                       // 모든 칸 ✓
        showProgress(relaxSeconds, relaxSeconds, "릴렉스 완료", "원래 환경으로")
        setTexts("RELAX · COMPLETE", "릴렉스를 마쳤어요", "${relaxSeconds / 60}분간의 이완을 완료했어요.")
        strike()
        saveRecord()
        host.speak("릴렉스를 마쳤어요. 수고하셨어요.") {
            if (active && phase == "complete") handler.postDelayed({ showDone() }, 1500)
        }
    }

    private fun showDone() {
        if (!active) return
        phase = "done"
        host.sounds.stopLoop()
        host.car.restore()                        // 차량 환경을 원래대로
        showCenter(donePanel)
        setTexts("RITUAL COMPLETE", "다음의 나를 만날 시간이에요", "리추얼을 마쳤어요. 다음 일정으로 넘어갈 준비를 해 주세요.")
        host.speak("차량 환경을 이전 설정으로 복원했어요. 다음의 나를 만날 시간이에요.")
        handler.postDelayed({ showRecord() }, doneSeconds * 1000L)
    }

    // ── ⑥ 이번 주 리추얼 기록 (월~일) ─────────
    private fun showRecord() {
        if (!active || phase != "done") return
        phase = "record"
        showCenter(recordPanel)
        val week = thisWeekCounts()
        val total = week.sum()
        val titleText = if (total <= 1) "이번 주 첫 번째 리추얼이에요" else "이번 주도 벌써 ${total}번째군요"
        setTexts("RITUAL RECORD", titleText, "이번 주에 리추얼을 한 날이 밝게 표시돼요.")
        val todayIndex = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7   // 월=0 … 일=6

        weekRow.removeAllViews()
        dayNames.forEachIndexed { i, name ->
            val box = TextView(host.activity).apply {
                text = if (week[i] > 0) "$name\n✓" else "$name\n·"
                gravity = Gravity.CENTER
                textSize = 24f
                setLineSpacing(dp(6).toFloat(), 1f)
                if (week[i] > 0) {
                    round("#4DDCC6AB", 16f, beige)                 // 리추얼 한 날
                    setTextColor(Color.parseColor("#F6F6F7"))
                } else {
                    round("#0DF6F6F7", 16f, "#33F6F6F7")
                    setTextColor(Color.parseColor("#80F6F6F7"))
                }
                if (i == todayIndex) setTypeface(null, Typeface.BOLD)
            }
            val params = LinearLayout.LayoutParams(0, dp(130), 1f)
            if (i > 0) params.marginStart = dp(14)
            weekRow.addView(box, params)
        }
        host.speak("$titleText. 마이현대 앱에서 자세히 확인하실 수 있어요.")
        handler.postDelayed({ goHome() }, recordSeconds * 1000L)
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
        val prefs = host.activity.getSharedPreferences("sai_ritual", Context.MODE_PRIVATE)
        val dates = (prefs.getString("dates", "") ?: "").split(",")
        return IntArray(7) { i -> dates.count { it == weekDays[i] } }
    }

    private fun goHome() {
        if (!active) return
        host.finish("리추얼을 마쳤어요. 개운하게 출발해요.")   // MainActivity가 정리하고 홈으로
    }

    // ── 기록 저장 (앱 안에 저장돼요) ──
    private fun saveRecord() {
        val prefs = host.activity.getSharedPreferences("sai_ritual", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Calendar.getInstance().time)
        val old = prefs.getString("dates", "") ?: ""
        val dates = if (old.isBlank()) today else "$old,$today"
        prefs.edit().putString("dates", dates).apply()
    }
}
