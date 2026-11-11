package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [릴렉스 담당] RelaxMode.kt  ─ 릴렉스 (SAI v12)
// ① 오늘의 싱잉볼: 8초마다 한 번, 모두 3번 울려요
// ② 오늘의 릴렉스: 6개 모드 중 하나 고르기 (기본 · 비 · 캠핑 · 우주 · 바다 · 숲)
//    모드마다 조명 색 · 음악 · 가운데 움직이는 그림이 정해져 있어요.
//    등받이 각도(117°)는 우리만 아는 값이라 화면·음성에는 나오지 않아요.
// ③ 모드를 누르면 "릴렉스 모드로 진입합니다" → 3초 카운트다운 하면서 차량 바꾸기
// ④ 릴렉스 5분: 1단계 호흡 → 2단계 어깨 → 3단계 목 → 4단계 가슴
// ⑤ 릴렉스 완료 → 세션 완료 (차량 원래대로) → 이번 주 리추얼 기록 → 홈
// 위쪽 ✕ 는 MainActivity가 처리해요 (리추얼 중엔 홈으로, 끝난 뒤엔 앱 닫기).
// 위쪽 ‹ 는 이전 화면으로 (goBack 참고). 싱잉볼 화면엔 "건너뛰기" 버튼이 있어요.
// ─────────────────────────────────────────────
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// 릴렉스 모드 하나: 이름 / 설명 / 아이콘 / 조명 이름 / 조명 색 / 음악 이름 / 음악 / 그림
private data class Theme(
    val name: String, val desc: String, val icon: String,
    val lightName: String, val light: Int,
    val musicName: String, val music: Ambience,
    val graphic: ParticleView.Style
)

// 릴렉스 단계 하나: 칸 이름 / 제목 / 설명 / 음성 안내 / 시간(초)
private data class Stage(val name: String, val title: String, val sub: String, val guide: String, val seconds: Int)

class RelaxMode : RestMode {

    // ===== 바꿔도 되는 값 (대부분 여기만 고치면 돼요) =====
    private val speed = 5                           // ★ 테스트용: 릴렉스 5분만 5배 빠르게. 실제로 쓸 땐 1 로!
    private val strikeEvery = 8                     // ★ 싱잉볼 간격 (8초)
    private val strikeCount = 3                     // ★ 싱잉볼 치는 횟수 (3번)
    private val countdownSeconds = 4                // 모드 고른 뒤 카운트다운 (등받이 그림 보는 시간)
    private val chooseAutoSeconds = 20              // 오늘의 릴렉스에서 안 고르면 "기본"으로 자동 시작 (0이면 안 함)
    private val doneSeconds = 8                     // 세션 완료 → 기록 화면
    private val recordSeconds = 15                  // 기록 화면 → 홈
    private val seatAngle = 117                     // ★ 등받이는 항상 117°

    // ★ 오늘의 릴렉스 모드 6개. 5·6번(바다·숲)은 자유롭게 바꿔도 돼요.
    //   음악: Ambience.MEDITATION / RAIN / FIRE / SPACE / OCEAN / FOREST
    //   그림: ParticleView.Style.SWIRL / RAIN / CAMPFIRE / SPACE / OCEAN / FOREST
    private val themes = listOf(
        Theme("기본", "따뜻한 조명과 명상 음악", "◎", "따뜻한 색", Color.rgb(255, 159, 70),
            "명상 음악", Ambience.MEDITATION, ParticleView.Style.SWIRL),
        Theme("비", "파란 조명과 빗소리", "☂", "파란색", Color.rgb(70, 120, 255),
            "빗소리", Ambience.RAIN, ParticleView.Style.RAIN),
        Theme("캠핑", "모닥불 조명과 타닥타닥 소리", "▲", "모닥불색", Color.rgb(255, 110, 40),
            "모닥불 소리", Ambience.FIRE, ParticleView.Style.CAMPFIRE),
        Theme("우주", "보랏빛 조명과 우주의 울림", "✦", "보라색", Color.rgb(120, 90, 255),
            "우주의 울림", Ambience.SPACE, ParticleView.Style.SPACE),
        Theme("바다", "청록 조명과 파도 소리", "≈", "청록색", Color.rgb(40, 190, 220),
            "파도 소리", Ambience.OCEAN, ParticleView.Style.OCEAN),
        Theme("숲", "초록 조명과 새소리", "♣", "초록색", Color.rgb(110, 200, 110),
            "숲 소리", Ambience.FOREST, ParticleView.Style.FOREST)
    )

    // 릴렉스 5분 = 150 + 50 + 50 + 50 초
    private val stages = listOf(
        Stage("호흡", "호흡을 천천히 이어가세요", "그림의 움직임에 맞춰 편안하게 호흡해 보세요.",
            "호흡을 천천히 이어가세요. 코로 깊게 들이마시고, 입으로 천천히 내쉬어 보세요.", 150),
        Stage("어깨", "어깨의 힘을 빼 주세요", "어깨를 귀 쪽으로 올렸다가, 툭 떨어뜨려 보세요.",
            "어깨를 귀 쪽으로 천천히 올렸다가, 숨을 내쉬며 툭 떨어뜨려 보세요.", 50),
        Stage("목", "목을 천천히 풀어 주세요", "고개를 오른쪽, 왼쪽으로 천천히 기울여 보세요.",
            "고개를 오른쪽으로 천천히 기울이고, 다시 왼쪽으로 기울여 보세요. 통증이 없는 범위에서 해 주세요.", 50),
        Stage("가슴", "가슴을 활짝 열어 주세요", "어깨를 뒤로 크게 돌려 가슴을 열어 보세요.",
            "어깨를 뒤로 크게 돌리면서, 가슴을 활짝 열어 보세요.", 50)
    )

    // 디자인 포맷 색
    private val white = "#F6F6F7"
    private val beige = "#DCC6AB"
    private val dark = "#1B1C20"
    private val buttonBg = "#27292E"
    private val radius = 20f
    private val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")

    private lateinit var host: ModeHost
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var phase = ""           // bowl → choose → loading → relax → complete → done → record
    private var elapsed = 0
    private var stageIndex = -1
    private var chooseLeft = 0
    private var countdownLeft = 0
    private var theme = 0                     // 고른 모드 번호
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
    private lateinit var seatAngleView: SeatAngleView
    private lateinit var skipButton: TextView
    private lateinit var choosePanel: View
    private lateinit var modeGrid: LinearLayout
    private lateinit var note: TextView
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

    private val relaxSeconds get() = stages.sumOf { it.seconds }       // 300초 = 5분
    private val bowlSeconds get() = strikeEvery * strikeCount           // 24초

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

    // 릴렉스 완료부터는 "끝난 상태" → ✕ 를 누르면 앱이 닫혀요
    override fun isFinished() = phase == "complete" || phase == "done" || phase == "record"

    private val tickMillis get() = 1000L / speed.coerceAtLeast(1)
    private fun dp(value: Int) = (value * host.activity.resources.displayMetrics.density).toInt()
    private fun hex(color: Int, alpha: String = "") = "#" + alpha + String.format("%06X", 0xFFFFFF and color)

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
        seatAngleView = root.findViewById(R.id.relaxSeatAngle)
        skipButton = root.findViewById(R.id.relaxSkip)
        choosePanel = root.findViewById(R.id.relaxChoose)
        modeGrid = root.findViewById(R.id.relaxModeGrid)
        note = root.findViewById(R.id.relaxNote)
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

        // 꾸미기 (버튼 배경 #27292E · 라운드 20)
        listOf(R.id.relaxDoneCard, R.id.relaxProgress).forEach { root.findViewById<View>(it).round(buttonBg, radius) }
        root.findViewById<View>(R.id.relaxDoneIcon).round("#1AF6F6F7", radius)
        skipButton.round(buttonBg, radius)
        countdownText.round(buttonBg, 48f)
        root.findViewById<View>(R.id.relaxHome).round(white, radius)
        root.findViewById<View>(R.id.relaxRecordHome).round(white, radius)

        // 버튼
        skipButton.setOnClickListener { skipBowl() }               // 싱잉볼 건너뛰기
        root.findViewById<View>(R.id.relaxHome).setOnClickListener { showRecord() }
        root.findViewById<View>(R.id.relaxRecordHome).setOnClickListener { goHome() }
        seek.setOnTouchListener { _, _ -> true }               // 진행바는 보기만

        buildStepChips()
        buildModeCards()
    }

    // ── 오늘의 릴렉스: 모드 카드 6개 (한 줄에 3개씩 2줄, 모두 같은 크기) ──
    private fun buildModeCards() {
        themes.chunked(3).forEachIndexed { row, list ->
            val line = LinearLayout(host.activity).apply { orientation = LinearLayout.HORIZONTAL }
            list.forEachIndexed { col, item ->
                val index = row * 3 + col
                val card = LinearLayout(host.activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(28), dp(20), dp(28))
                    round(buttonBg, radius)
                    setOnClickListener { pickTheme(index) }
                }
                card.addView(TextView(host.activity).apply {
                    text = item.icon
                    textSize = 30f
                    gravity = Gravity.CENTER
                    setTextColor(item.light)
                    round(hex(item.light, "33"), 36f)
                }, LinearLayout.LayoutParams(dp(72), dp(72)))
                card.addView(TextView(host.activity).apply {
                    text = item.name
                    textSize = 28f                                  // 중간 버튼 제목 28
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(white))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(16), 0, 0)
                })
                card.addView(TextView(host.activity).apply {
                    text = item.desc
                    textSize = 22f                                  // 중간 버튼 설명 22
                    setTextColor(Color.parseColor("#99F6F6F7"))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (col > 0) params.marginStart = dp(20)
                line.addView(card, params)
            }
            val lineParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (row > 0) lineParams.topMargin = dp(20)
            modeGrid.addView(line, lineParams)
        }
    }

    // ── 1~4단계 칸 ──
    private fun buildStepChips() {
        stages.forEachIndexed { i, stage ->
            val chip = TextView(host.activity).apply {
                text = "${i + 1}  ${stage.name}"
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(dp(26), dp(10), dp(26), dp(10))
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i > 0) params.marginStart = dp(12)
            stepRow.addView(chip, params)
            stepChips.add(chip)
        }
    }

    private fun showStepChips(current: Int) {
        stepChips.forEachIndexed { i, chip ->
            when {
                i < current -> {
                    chip.text = "✓  ${stages[i].name}"
                    chip.setTextColor(Color.parseColor(beige))
                    chip.round(buttonBg, radius)
                }
                i == current -> {
                    chip.text = "${i + 1}단계  ${stages[i].name}"
                    chip.setTextColor(Color.parseColor(dark))
                    chip.round(beige, radius)
                }
                else -> {
                    chip.text = "${i + 1}  ${stages[i].name}"
                    chip.setTextColor(Color.parseColor("#80F6F6F7"))
                    chip.round(buttonBg, radius)
                }
            }
        }
    }

    // ── 화면 공통 ──
    private fun showCenter(view: View) {
        listOf(bowl, particles, loadingPanel, choosePanel, donePanel, recordPanel).forEach {
            it.visibility = if (it == view) View.VISIBLE else View.GONE
        }
        progressCard.visibility = if (view == bowl || view == particles) View.VISIBLE else View.GONE
        stepRow.visibility = if (view == particles) View.VISIBLE else View.GONE
        skipButton.visibility = if (view == bowl) View.VISIBLE else View.GONE
    }

    private fun setTexts(labelText: String, titleText: String, subText: String) {
        label.text = labelText
        title.text = titleText
        sub.text = subText
        title.alpha = 0f
        title.animate().alpha(1f).setDuration(500).start()
    }

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

    // ── ① 오늘의 싱잉볼 (8초마다, 3번) ──
    private fun startBowl() {
        phase = "bowl"
        elapsed = 0
        showCenter(bowl)
        setTexts("SINGING BOWL", "오늘의 싱잉볼", "역할과 역할 사이를 조율합니다.")
        showProgress(0, bowlSeconds, "싱잉볼 1번째 울림 / $strikeCount", "오늘의 릴렉스")
        host.car.readSeatBackrestDegrees { seatNow = it }      // 바꾸기 전 등받이 각도
        host.speak("릴렉스를 시작할게요. 싱잉볼 소리에 귀 기울여 보세요.")
        handler.postDelayed({ if (active && phase == "bowl") strike() }, 600)   // 첫 번째 울림
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
            val rang = (elapsed / strikeEvery + 1).coerceAtMost(strikeCount)
            showProgress(elapsed, bowlSeconds, "싱잉볼 ${rang}번째 울림 / $strikeCount", "오늘의 릴렉스")
            if (elapsed >= bowlSeconds) showChoose() else handler.postDelayed(this, 1000)
        }
    }

    // 싱잉볼 건너뛰기
    private fun skipBowl() {
        if (!active || phase != "bowl") return
        showChoose()
    }

    // ‹ 뒤로 가기: 이전 화면으로 (true = 여기서 처리했어요)
    //   싱잉볼 → (모드 끝내고) 모드 선택 / 오늘의 릴렉스 → 싱잉볼 / 카운트다운 → 오늘의 릴렉스
    //   릴렉스 2~4단계 → 앞 단계 / 1단계 → 오늘의 릴렉스 / 완료 뒤 → (모드 끝내고) 모드 선택
    override fun goBack(): Boolean {
        if (!active) return false
        when (phase) {
            "choose" -> startBowl()
            "loading" -> backToChoose()
            "relax" -> {
                if (stageIndex > 0) {
                    val previous = stageIndex - 1
                    elapsed = stages.take(previous).sumOf { it.seconds }       // 앞 단계 처음으로
                    showStage(previous)
                    showProgress(elapsed, relaxSeconds, "${previous + 1}단계 ${stages[previous].name}", nextName(previous))
                } else {
                    backToChoose()
                }
            }
            else -> return false
        }
        return true
    }

    // 바꿨던 조명·음악·등받이를 원래대로 하고 "오늘의 릴렉스"로
    private fun backToChoose() {
        handler.removeCallbacksAndMessages(null)
        host.sounds.stopLoop()
        host.car.restore()
        showChoose()
    }

    // ── ② 오늘의 릴렉스 (모드 6개) ──
    private fun showChoose() {
        phase = "choose"
        showCenter(choosePanel)
        setTexts("TODAY'S RELAX", "오늘의 릴렉스", "지금 마음에 맞는 공간을 골라 주세요.")
        host.speak("오늘의 릴렉스를 골라 주세요.")
        chooseLeft = chooseAutoSeconds
        showChooseNote()
        if (chooseAutoSeconds > 0) handler.postDelayed(chooseTick, 1000)
    }

    private fun showChooseNote() {
        val base = "모드를 누르면 조명과 음악이 모드에 맞게 바뀌어요."
        note.text = if (chooseLeft > 0) "$base · ${chooseLeft}초 뒤 기본으로 시작" else base
    }

    private val chooseTick = object : Runnable {
        override fun run() {
            if (!active || phase != "choose" || chooseLeft <= 0) return
            chooseLeft--
            showChooseNote()
            if (chooseLeft == 0) pickTheme(0) else handler.postDelayed(this, 1000)
        }
    }

    // ── ③ 모드를 고르면: 음성 안내 + 3초 카운트다운 + 차량 바꾸기 ──
    private fun pickTheme(index: Int) {
        if (!active || phase != "choose") return
        theme = index
        val item = themes[index]
        phase = "loading"
        showCenter(loadingPanel)
        setTexts("RELAX · ${item.name}", "릴렉스 모드로 진입합니다", "잠시 후 ${item.name} 릴렉스를 시작해요.")
        applyCar(item)
        val now = seatNow
        loadingText.text = "${item.lightName} 조명 · ${item.musicName}"
        host.speak("릴렉스 모드로 진입합니다.")

        // 등받이 그림: 지금 각도 → 117° (지금 각도를 못 읽었으면 105°에서 시작하는 그림)
        seatAngleView.animate(now ?: 105.0, seatAngle.toDouble(), 2500)
        // 2.8초 뒤 실제 차량에서 다시 읽어서 정말 움직였는지 확인 (화면엔 안 나오고, Logcat에 "SAI"로 남아요)
        handler.postDelayed({
            if (!active || phase != "loading") return@postDelayed
            host.car.readSeatBackrestDegrees { value ->
                Log.d("SAI", "등받이: 시작 ${now ?: "못 읽음"}° → 지금 ${value ?: "못 읽음"}° (목표 ${seatAngle}°)")
            }
        }, 2800)

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


    private fun applyCar(item: Theme) {
        val car = host.car
        car.moveSeatBackrest(seatAngle.toDouble())        // 등받이 117° (바꾸기 전 값은 자동 저장)
        car.setAmbientColor(item.light)                   // 모드 조명 색
        car.lightsOff()                                   // 실내·외부 조명 끄기
        host.sounds.startLoop(item.music)                 // 모드 음악
    }

    // ── ④ 릴렉스 5분 (1~4단계) ──
    private fun startRelax() {
        phase = "relax"
        elapsed = 0
        stageIndex = -1
        particles.style = themes[theme].graphic          // 모드에 맞는 그림
        showCenter(particles)
        showStage(0)
        showProgress(0, relaxSeconds, "1단계 ${stages[0].name}", nextName(0))
        handler.postDelayed(relaxTick, tickMillis)
    }

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
        setTexts("${themes[theme].name} · ${index + 1}단계 / ${stages.size}단계", stage.title, stage.sub)
        showStepChips(index)
        if (index > 0) host.sounds.strikeBowl()           // 단계가 바뀔 때 싱잉볼 한 번
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

    // ── ⑤ 완료 → 세션 완료 → 이번 주 기록 ──
    private fun completeRelax() {
        phase = "complete"
        showStepChips(stages.size)
        showProgress(relaxSeconds, relaxSeconds, "릴렉스 완료", "원래 환경으로")
        setTexts("RELAX · COMPLETE", "릴렉스를 마쳤어요", "${relaxSeconds / 60}분간의 이완을 완료했어요.")
        host.sounds.strikeBowl()
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
                    round("#4DDCC6AB", radius, beige)
                    setTextColor(Color.parseColor(white))
                } else {
                    round(buttonBg, radius)
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

    private fun goHome() {
        if (!active) return
        host.finish("리추얼을 마쳤어요. 개운하게 출발해요.")
    }

    // ── 기록 저장 / 읽기 (앱 안에 저장돼요) ──
    private fun prefs() = host.activity.getSharedPreferences("sai_ritual", Context.MODE_PRIVATE)

    private fun saveRecord() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Calendar.getInstance().time)
        val old = prefs().getString("dates", "") ?: ""
        val dates = if (old.isBlank()) today else "$old,$today"
        prefs().edit().putString("dates", dates).apply()
    }

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
