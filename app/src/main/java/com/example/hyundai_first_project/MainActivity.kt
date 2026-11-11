package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [공용] MainActivity.kt  ─ 앱의 "껍데기"
// P단 → 준비 완료 → 모드 선택 → 모드 실행 → 끝나면 원래대로
// 위쪽 ‹ 는 한 화면 뒤로, ✕ 는 언제든 강제 종료(차량 설정 원래대로)예요.
// 모드 중에 P에서 빠지면 "차량 상태가 바뀌어 중단했어요" 화면을 보여줘요.
// 3명 모두 쓰는 파일이라, 고칠 땐 팀에 먼저 말해 주세요.
// (맨 윗줄 package ... 은 원래 파일 것을 그대로 두고, 그 아래에 붙여 넣으세요)
// ─────────────────────────────────────────────
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {

    // ===== 바꿔도 되는 설정값 =====
    private val driverName = "붕붕이"
    private val locationPermission = "pleos.car.permission.FUSED_LOCATION"

    private lateinit var vehicle: Vehicle
    private lateinit var location: FusedLocation
    private lateinit var car: CarControl
    private val sounds = Sounds()
    private var tts: TextToSpeech? = null
    private val ttsDone = ConcurrentHashMap<String, () -> Unit>()
    private var ttsCount = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var homeView: View
    private lateinit var readyView: View
    private lateinit var modeSelectView: View
    private lateinit var modeContainer: FrameLayout
    private lateinit var safetyView: View
    private lateinit var headerBack: View
    private lateinit var headerClose: View
    private lateinit var statusText: TextView
    private lateinit var adviceText: TextView
    private lateinit var readyParkSub: TextView
    private lateinit var debugText: TextView
    private lateinit var homeStart: TextView
    private lateinit var homeGreeting: TextView
    private lateinit var homeWeekText: TextView

    private var parked: Boolean? = null
    private var locationReady = false
    private var place = "위치 확인 중"
    private var currentMode: RestMode? = null

    // ① 앱이 켜질 때
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()                // 위쪽 보라색 제목 줄 숨기기
        bindViews()
        setupTts()
        sounds.prepare()

        vehicle = Vehicle(this)
        location = FusedLocation(this)
        car = CarControl(this, vehicle) { message -> runOnUiThread { debugText.text = message } }
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            statusText.text = "차량 연결 실패: ${e.message}"
            return
        }
        handler.postDelayed(gearCheck, 1000)

        if (checkSelfPermission(locationPermission) == PackageManager.PERMISSION_GRANTED) {
            startLocation()
        } else {
            requestPermissions(arrayOf(locationPermission), 1)
        }
    }

    private fun bindViews() {
        homeView = findViewById(R.id.homeView)
        readyView = findViewById(R.id.readyView)
        modeSelectView = findViewById(R.id.modeSelectView)
        modeContainer = findViewById(R.id.modeContainer)
        safetyView = findViewById(R.id.safetyView)
        headerBack = findViewById(R.id.headerBack)
        headerClose = findViewById(R.id.headerClose)
        statusText = findViewById(R.id.statusText)
        adviceText = findViewById(R.id.adviceText)
        readyParkSub = findViewById(R.id.readyParkSub)
        debugText = findViewById(R.id.debugText)
        homeStart = findViewById(R.id.homeStart)
        homeGreeting = findViewById(R.id.homeGreeting)
        homeWeekText = findViewById(R.id.homeWeekText)

        // 버튼
        homeStart.setOnClickListener { startFromHome() }                 // 홈 → 준비 완료
        findViewById<View>(R.id.readyNext).setOnClickListener { show(modeSelectView) }
        // ★ 모드 카드 → 각자 만든 모드 실행 (모드를 새로 만들면 여기 한 줄 추가)
        findViewById<View>(R.id.relaxButton).setOnClickListener { startMode(RelaxMode()) }
        findViewById<View>(R.id.napButton).setOnClickListener { startMode(NapMode()) }
        findViewById<View>(R.id.awakeButton).setOnClickListener { startMode(AwakeMode()) }
        headerBack.setOnClickListener { goBack() }
        headerClose.setOnClickListener { closeNow() }
        findViewById<View>(R.id.safetyClose).setOnClickListener { show(homeView) }
        findViewById<View>(R.id.safetyDetail).setOnClickListener {
            Toast.makeText(this, "온도·바람·순환·조명·시트를 원래대로 돌려 놓았어요 (지원되는 항목만).", Toast.LENGTH_LONG).show()
        }

        // 카드·버튼 꾸미기 (#1AF6F6F7 = 흰색을 10%만 보이게)
        listOf(R.id.readyPark, R.id.readyDriver, R.id.readyVehicle,
            R.id.relaxButton, R.id.napButton, R.id.awakeButton, R.id.safetyCard).forEach {
            findViewById<View>(it).round("#27292E", 20f)
        }
        listOf(R.id.readyParkIcon, R.id.readyDriverIcon, R.id.readyVehicleIcon,
            R.id.relaxButtonIcon, R.id.napButtonIcon, R.id.awakeButtonIcon, R.id.safetyIcon).forEach {
            findViewById<View>(it).round("#1AF6F6F7", 20f)
        }
        findViewById<View>(R.id.readyNext).round("#F6F6F7", 20f)
        findViewById<View>(R.id.safetyClose).round("#F6F6F7", 20f)
        homeStart.round("#F6F6F7", 20f)
        findViewById<View>(R.id.homeStatusCard).round("#27292E", 20f)
        findViewById<View>(R.id.homeWeekCard).round("#27292E", 20f)
        findViewById<View>(R.id.safetyDetail).round("#27292E", 20f)
        show(homeView)
    }

    // 화면 하나만 보이기
    private fun show(view: View) {
        listOf(homeView, readyView, modeSelectView, modeContainer, safetyView).forEach {
            it.visibility = if (it == view) View.VISIBLE else View.GONE
        }
        val onHome = view == homeView
        headerBack.visibility = if (onHome) View.INVISIBLE else View.VISIBLE
        headerClose.visibility = View.VISIBLE        // ✕ 는 어느 화면에서나 보여요
        if (onHome) updateHome()
    }

    // SAI 홈 화면 채우기: 인사 · 시작 버튼 상태 · 이번 주 리추얼 횟수
    private fun updateHome() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        homeGreeting.text = when (hour) {
            in 5..10 -> "${driverName}님, 좋은 아침이에요"
            in 11..17 -> "${driverName}님, 잠시 쉬어 가요"
            else -> "${driverName}님, 오늘도 수고 많으셨어요"
        }
        homeStart.alpha = if (parked == true) 1f else 0.4f     // P가 아니면 흐리게
        homeWeekText.text = "${thisWeekCount()}회"
    }

    // 이번 주(월~일)에 리추얼을 끝낸 횟수 (RelaxMode가 저장한 기록)
    private fun thisWeekCount(): Int {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val monday = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val week = (0 until 7).map { i ->
            val day = monday.clone() as Calendar
            day.add(Calendar.DAY_OF_MONTH, i)
            format.format(day.time)
        }
        val dates = (getSharedPreferences("sai_ritual", MODE_PRIVATE).getString("dates", "") ?: "").split(",")
        return dates.count { it in week }
    }

    // 홈의 "SAI 시작하기"
    private fun startFromHome() {
        if (parked == true) {
            readyParkSub.text = "P단 확인 ✓"
            show(readyView)
            speak("SAI를 시작할 준비가 됐어요. 원하는 모드를 선택해 주세요.", null)
        } else {
            Toast.makeText(this, "기어를 P에 두면 시작할 수 있어요.", Toast.LENGTH_SHORT).show()
            speak("안전을 위해 기어를 P에 두면 시작할 수 있어요.", null)
        }
    }

    // ‹ 한 화면 뒤로 (홈 ← 준비 완료 ← 모드 선택 ← 모드 안의 화면들)
    private fun goBack() {
        when {
            currentMode != null -> {
                // 모드 안에서 먼저 이전 화면으로. 더 갈 곳이 없으면 모드를 끝내고 "모드 선택"으로
                if (currentMode?.goBack() != true) {
                    endMode()
                    show(modeSelectView)
                }
            }
            modeSelectView.visibility == View.VISIBLE -> show(readyView)
            else -> goHome()
        }
    }

    // ✕ 버튼
    // 리추얼 중간 → 끝내고 홈으로 / 리추얼이 끝난 뒤·홈 화면 → 앱 닫기
    private fun closeNow() {
        val mode = currentMode
        when {
            mode != null && !mode.isFinished() -> finishMode("리추얼을 종료했어요.")
            mode != null -> {
                endMode()                 // 차량 설정 원래대로
                finishAffinity()          // 앱 닫기
            }
            homeView.visibility == View.VISIBLE -> finishAffinity()   // 홈 화면 → 앱 닫기
            else -> goHome()              // 준비 완료·모드 선택·안전 중단 → 홈으로
        }
    }

    // 홈으로: 언제나 SAI 홈 화면 (P면 "SAI 시작하기"를 누를 수 있어요)
    private fun goHome() {
        show(homeView)
    }

    // ② 모드 시작 / 끝
    private fun startMode(mode: RestMode) {
        currentMode?.stop()
        modeContainer.removeAllViews()
        show(modeContainer)
        currentMode = mode
        val host = ModeHost(this, modeContainer, car, sounds, driverName, ::speak) { message -> finishMode(message) }
        mode.start(host)
    }

    private fun endMode() {
        currentMode?.stop()
        currentMode = null
        sounds.stopLoop()
        car.restore()                     // 바꿨던 차량 설정을 원래대로
        modeContainer.removeAllViews()
    }

    private fun finishMode(message: String) {
        endMode()
        adviceText.text = message
        goHome()
        speak(message, null)
    }

    // ③ 1초마다 기어 확인
    private val gearCheck = object : Runnable {
        override fun run() {
            try {
                vehicle.getGear().getCurrentGearSelectionState(
                    { gear -> runOnUiThread { onGear(gear) } },
                    { e -> runOnUiThread { statusText.text = "기어 확인 실패: ${e.message}" } }
                )
            } catch (e: Exception) {
                statusText.text = "기어 확인 실패: ${e.message}"
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun onGear(gear: VehicleGear?) {
        val nowParked = gear == VehicleGear.GEAR_PARK
        if (nowParked == parked) return
        parked = nowParked

        if (nowParked) {
            onParked()
        } else {
            statusText.text = "주행 중 (${gear?.name})"
            adviceText.text = "안전 운전하세요. 기어를 P에 두면 SAI를 시작할 수 있어요."
            if (currentMode != null) {
                endMode()                                           // 안전 중단
                show(safetyView)
                speak("차량 상태가 바뀌어 중단했어요. 안전을 위해 모드를 종료했어요.", null)
            } else {
                show(homeView)
            }
        }
    }

    // ④ P가 되면: 준비 완료 화면
    private fun onParked() {
        statusText.text = "주차됨 (P) · $place"
        if (currentMode != null) return
        adviceText.text = "주차를 확인했어요. SAI 시작하기를 눌러 주세요."
        if (safetyView.visibility == View.VISIBLE || homeView.visibility == View.VISIBLE) show(homeView)
        speak("주차를 확인했어요. SAI 시작하기를 눌러 주세요.", null)
        updateLocation()
    }

    // ⑤ 음성 안내 (말이 끝나면 then 실행)
    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                val callback = ttsDone.remove(utteranceId ?: return) ?: return
                handler.post { callback() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onDone(utteranceId)
            }
        })
    }

    private fun speak(text: String, then: (() -> Unit)?) {
        val id = "say${ttsCount++}"
        if (then != null) {
            var called = false
            val once = {
                if (!called) {
                    called = true
                    then()
                }
            }
            ttsDone[id] = once
            // 음성이 안 나와도 멈추지 않도록, 글자 수만큼 기다렸다가 넘어가기
            handler.postDelayed({ ttsDone.remove(id); once() }, 2500L + text.length * 180L)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    // ⑥ 위치
    private fun startLocation() {
        try {
            if (location.isSupported()) {
                location.initialize()
                locationReady = true
            }
        } catch (e: Exception) {
            statusText.text = "위치 연결 실패: ${e.message}"
        }
    }

    private fun updateLocation() {
        if (!locationReady) return
        try {
            location.getLastKnownLocation(locationListener)
        } catch (e: Exception) {
            statusText.text = "위치 확인 실패: ${e.message}"
        }
    }

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            runOnUiThread {
                place = if (location == null) "위치 없음"
                else String.format(Locale.KOREA, "위도 %.4f, 경도 %.4f", location.latitude, location.longitude)
                if (parked == true) statusText.text = "주차됨 (P) · $place"
            }
        }

        override fun onError(exception: Exception) {
            runOnUiThread { statusText.text = "위치 오류: ${exception.message}" }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startLocation()
    }

    // ⑦ 앱이 꺼질 때 정리
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        currentMode?.stop()
        sounds.stopLoop()
        try { car.restore() } catch (_: Exception) {}
        tts?.shutdown()
        try { location.release() } catch (_: Exception) {}
        try { vehicle.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
