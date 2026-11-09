package com.example.hyundai_first_project

// ─────────────────────────────────────────────
// [공용] MainActivity.kt  ─ 앱의 "껍데기"
// 기어 확인 → 휴식 제안 팝업 → 고른 모드 실행 → 끝나면 원래대로
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
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {

    // ===== 바꿔도 되는 설정값 =====
    private val oneMinute = 1_000L          // 테스트: 1초 = 1분 (실제: 60_000L)
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
    private lateinit var proposalCard: View
    private lateinit var modeContainer: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var adviceText: TextView
    private lateinit var proposalTitle: TextView
    private lateinit var debugText: TextView

    private var parked: Boolean? = null
    private var driveStartTime = System.currentTimeMillis()
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
        proposalCard = findViewById(R.id.proposalCard)
        modeContainer = findViewById(R.id.modeContainer)
        statusText = findViewById(R.id.statusText)
        adviceText = findViewById(R.id.adviceText)
        proposalTitle = findViewById(R.id.proposalTitle)
        debugText = findViewById(R.id.debugText)

        // ★ 모드 버튼 → 각자 만든 모드 실행 (모드를 새로 만들면 여기 한 줄 추가)
        findViewById<TextView>(R.id.relaxButton).setOnClickListener { startMode(RelaxMode()) }
        findViewById<TextView>(R.id.napButton).setOnClickListener { startMode(NapMode()) }
        findViewById<TextView>(R.id.awakeButton).setOnClickListener { startMode(AwakeMode()) }
        findViewById<TextView>(R.id.noButton).setOnClickListener {
            show(homeView)
            adviceText.text = "알겠어요. 피곤하면 언제든 P에 두세요."
        }

        // 버튼 색 / 둥근 모서리 (색 3개: 배경 1B1C20 · 흰색 F6F6F7 · 강조 DCC6AB)
        proposalCard.round("#1FF6F6F7", 24f, "#33F6F6F7")
        findViewById<View>(R.id.relaxButton).round("#DCC6AB", 14f)
        findViewById<View>(R.id.noButton).round("#1B1C20", 14f, "#4DF6F6F7")
        findViewById<View>(R.id.napButton).round("#1B1C20", 20f, "#66DCC6AB")
        findViewById<View>(R.id.awakeButton).round("#1B1C20", 20f, "#66DCC6AB")
    }

    // 화면 하나만 보이기
    private fun show(view: View) {
        homeView.visibility = if (view == homeView || view == proposalCard) View.VISIBLE else View.GONE
        proposalCard.visibility = if (view == proposalCard) View.VISIBLE else View.GONE
        modeContainer.visibility = if (view == modeContainer) View.VISIBLE else View.GONE
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

    private fun finishMode(message: String) {
        currentMode?.stop()
        currentMode = null
        sounds.stopLoop()
        car.restore()                     // 바꿨던 차량 설정을 원래대로
        modeContainer.removeAllViews()
        show(homeView)
        adviceText.text = message
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
            if (currentMode != null) finishMode("출발해서 휴식을 멈췄어요. 안전 운전하세요.")
            driveStartTime = System.currentTimeMillis()
            show(homeView)
            statusText.text = "주행 중 (${gear?.name})"
            adviceText.text = "안전 운전하세요. P에 두면 휴식을 제안할게요."
        }
    }

    // ④ P가 되면: 휴식 제안 팝업
    private fun onParked() {
        val minutes = (System.currentTimeMillis() - driveStartTime) / oneMinute
        val message = if (minutes < 1) "주차를 마쳤어요. 이브닝 릴렉스 5분, 잠시 쉬어 갈까요?"
        else "${minutes}분 달렸어요. 이브닝 릴렉스 5분, 잠시 쉬어 갈까요?"
        statusText.text = "주차됨 (P) · $place"
        proposalTitle.text = message
        show(proposalCard)
        speak(message, null)
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
