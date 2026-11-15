@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** 기존 프로젝트의 Pleos TTS SDK만 사용한다. 새 Gradle 의존성/Android TTS 엔진 설치 없음. */
class SaiTestTts(
    context: Context,
    private val onReady: () -> Unit,
    private val onFailure: (String) -> Unit,
    private val onInterrupted: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val sdk = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var closed = false
    private var opened = false
    private var ready = false
    private var busy = false
    private var started = false
    private var stopping = false
    private var sequence = 0L
    private var done: (() -> Unit)? = null
    private var afterStop: (() -> Unit)? = null
    private fun fail(message: String) {
        if (closed) return
        sequence++; ready = false; busy = false; started = false; stopping = false
        done = null; afterStop = null
        Log.e("SAI_TTS", message)
        onFailure(message)
    }
    private fun stopped() {
        sequence++; busy = false; started = false; stopping = false; done = null
        val callback = afterStop; afterStop = null; callback?.invoke()
    }
    private val listener = object : EventListener {
        override fun onReady() { Log.d("SAI_TTS", "SDK ready event") }
        override fun onStart() { main.post { if (!closed && busy && !stopping) {
            started = true; Log.i("SAI_TTS", "Speech started")
        } } }
        override fun onUpdatedRms(rms: Double) { main.post { if (!closed && busy && !stopping) started = true } }
        override fun onDone() { main.post {
            if (closed) return@post
            if (stopping) stopped()
            else if (busy) {
                sequence++; busy = false; started = false
                Log.i("SAI_TTS", "Speech completed")
                val callback = done; done = null; callback?.invoke()
            }
        } }
        override fun onStop() { main.post {
            if (closed) return@post
            if (stopping) stopped()
            else if (busy) {
                sequence++; busy = false; started = false; done = null
                Log.i("SAI_TTS", "External speech interruption")
                onInterrupted()
            }
        } }
        override fun onError(errorType: ErrorType) { main.post { fail("TTS 오류: $errorType") } }
    }
    fun open() {
        if (closed || opened) return
        opened = true
        val token = ++sequence
        main.postDelayed({ if (!closed && !ready && token == sequence) fail("TTS 초기화 응답이 없습니다.") }, 8_000L)
        try {
            sdk.initialize(object : OnInitializeListener {
                override fun onSuccess() { main.post {
                    if (closed) return@post
                    try {
                        sdk.addEventListener(listener); sdk.setLocale(Locale.KOREA)
                        sequence++; ready = true; Log.i("SAI_TTS", "Connection initialized"); onReady()
                    } catch (e: Exception) { fail("TTS 초기화 오류: ${e.message}") }
                } }
                override fun onFailure(errorType: ErrorType) { main.post { fail("TTS 초기화 실패: $errorType") } }
            })
        } catch (e: Exception) { fail("TTS 연결 요청 실패: ${e.message}") }
    }
    fun speak(text: String, completed: () -> Unit) {
        if (closed) return
        if (!ready || busy || stopping) { fail("TTS 준비 상태를 확인해 주세요."); return }
        busy = true; started = false; done = completed
        val token = ++sequence
        Log.i("SAI_TTS", "Speech requested")
        try { sdk.speak(text) } catch (e: Exception) { fail("TTS 요청 실패: ${e.message}"); return }
        main.postDelayed({
            if (!closed && busy && !started && sequence == token) fail("TTS 시작 응답이 없습니다.")
        }, 8_000L)
        main.postDelayed({
            if (!closed && busy && sequence == token) fail("TTS 완료 응답이 없습니다. 음성 서비스를 다시 연결합니다.")
        }, 30_000L)
    }
    fun stopThen(action: () -> Unit) {
        if (closed) return
        done = null; afterStop = action
        if (stopping) return
        if (!busy) { afterStop = null; action(); return }
        stopping = true
        val token = ++sequence
        try { sdk.stop() } catch (e: Exception) { fail("TTS 중지 실패: ${e.message}"); return }
        main.postDelayed({
            if (!closed && stopping && sequence == token) fail("TTS 중지 응답이 없어 음성 서비스를 다시 연결합니다.")
        }, 3_000L)
    }
    fun close() {
        if (closed) return
        closed = true; sequence++; ready = false; busy = false; stopping = false
        done = null; afterStop = null; main.removeCallbacksAndMessages(null)
        // 이전 연결의 콜백을 먼저 제거하고 서비스 자원을 해제한다.
        runCatching { sdk.removeEventListener(listener) }
        runCatching { sdk.stop() }
        runCatching { sdk.release() }
    }
}


/** 파일 선택기 없이 raw 기본곡 + assets/music의 앱 내 음악 목록을 재생한다. */
class SaiMusicPlayer(context: Context, private val changed: () -> Unit, private val failed: (String) -> Unit, private val interrupted: () -> Unit = {}) {
    data class Track(val key: String, val title: String, val rawId: Int = 0, val assetPath: String? = null)
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("sai_music", Context.MODE_PRIVATE)
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var loading = false
    private var requested = false
    private var transitionGain=1f
    private var pendingFadeMs=0L
    private var volumeFade:android.animation.ValueAnimator?=null
    fun fadeInNextStart(durationMs:Long) {
        volumeFade?.cancel(); pendingFadeMs=durationMs.coerceAtLeast(1L); transitionGain=0f; applyVolume()
    }
    private var hasFocus = false
    private var speaking = false
    private var outputSilenced = false
    private var focusDucked = false
    private var waitingFocus = false
    private var lostDuringSpeech = false
    private var closed = false
    private var loadToken = 0
    private var previewToken = 0
    private var previewDone: (() -> Unit)? = null
    private var state = "음악 준비 중"
    private var lastError = ""
    var enabled: Boolean = true
        private set
    var volume: Float = 0.35f
        private set
    var title: String = "Morning Light · 기본 음악"
        private set
    private var selected = "morning"
    val tracks: List<Track> = buildTracks()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (!closed && requested) when(change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true; waitingFocus = false; focusDucked = false; lostDuringSpeech = false
                attemptPlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { focusDucked = true; applyVolume(); publish("재생 중 · 시스템 감쇄") }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false; waitingFocus = true; lostDuringSpeech = speaking
                runCatching { player?.pause() }; publish("일시정지 · 다른 음성 종료 대기")
                if (!speaking) interrupted()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false; waitingFocus = false; lostDuringSpeech = speaking
                runCatching { player?.pause() }
                // 자체 TTS 중에 잃은 요청은 발화 완료 뒤에만 복구한다. 외부 음원에는 양보한다.
                if (!speaking) requested = false
                publish(if (speaking) "일시정지 · 안내 완료 대기" else "정지 · 다른 앱이 오디오 사용 중")
                if (!speaking) interrupted()
            }
        }
    }
    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(focusListener, main).build()
    }
    init {
        // 이전 버전의 꺼짐/지나치게 낮은 설정은 이 수정본 최초 적용 때만 정리한다.
        if (!prefs.getBoolean("audio_fix_1", false)) {
            prefs.edit().putBoolean("enabled", true).putFloat("volume", 0.35f).putBoolean("audio_fix_1", true).apply()
        }
        enabled = prefs.getBoolean("enabled", true)
        volume = prefs.getFloat("volume", 0.35f).coerceIn(0f, 1f)
        selected = prefs.getString("source", "morning") ?: "morning"
        if (tracks.none { it.key == selected }) selected = "morning"
        load()
    }
    private fun buildTracks(): List<Track> {
        val items = mutableListOf(
            Track("morning", "Morning Light · 기본 음악", R.raw.sai_morning_light),
            Track("soft", "Soft Return · 기본 음악", R.raw.sai_soft_return)
        )
        val extensions = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
        runCatching { app.assets.list("music")?.sorted()?.forEach { name ->
            if (name.substringAfterLast('.', "").lowercase() in extensions)
                items.add(Track("asset:$name", name.substringBeforeLast('.').replace('_', ' '), assetPath = "music/$name"))
        } }
        return items
    }
    fun play() {
        if (closed) return
        stopPreviewTimer(); requested = true; waitingFocus = false
        if (!prepared && !loading && player == null) load()
        attemptPlayback()
    }
    fun pause() {
        requested = false; waitingFocus = false; lostDuringSpeech = false
        stopPreviewTimer(); runCatching { if (prepared) player?.pause() }; abandonFocus()
        publish(if (lastError.isNotEmpty()) "재생 오류" else if (enabled) "재생 대기" else "음악 꺼짐")
    }
    fun setDucked(value: Boolean) {
        speaking = value; applyVolume()
        if (!value && lostDuringSpeech) { lostDuringSpeech = false; waitingFocus = false }
        if (!value) attemptPlayback()
    }
    fun setOutputMuted(value: Boolean) {
        outputSilenced = value
        if (value) { runCatching { if (prepared) player?.pause() }; abandonFocus(); publish("앱 음소거") }
        else attemptPlayback()
    }
    fun changeVolume(value: Float) {
        volume = value.coerceIn(0f, 1f); prefs.edit().putFloat("volume", volume).apply(); applyVolume(); changed()
    }
    fun changeEnabled(value: Boolean) {
        enabled = value; prefs.edit().putBoolean("enabled", value).apply()
        if (!value) { runCatching { if (prepared) player?.pause() }; abandonFocus(); publish("음악 꺼짐") }
        else attemptPlayback()
        changed()
    }
    fun selectTrack(key: String) {
        selected = tracks.firstOrNull { it.key == key }?.key ?: "morning"
        enabled = true
        prefs.edit().putString("source", selected).putBoolean("enabled", true).apply()
        load()
    }
    /** 각성 모드/TTS 없이도 미디어 출력만 확인할 수 있는 10초 재생. */
    fun preview(completed: () -> Unit) {
        stopPreviewTimer()
        speaking = false; enabled = true
        prefs.edit().putBoolean("enabled", true).apply()
        if (volume == 0f) changeVolume(0.35f)
        play(); previewDone = completed
        val token = ++previewToken
        main.postDelayed({
            if (!closed && previewToken == token) {
                val callback = previewDone; previewDone = null
                requested = false; runCatching { if (prepared) player?.pause() }; abandonFocus()
                publish(if (lastError.isNotEmpty()) "재생 오류" else "10초 테스트 종료")
                callback?.invoke()
            }
        }, 10_000L)
    }
    private fun stopPreviewTimer() { previewToken++; previewDone = null }
    private fun load() {
        if (closed) return
        val track = tracks.firstOrNull { it.key == selected } ?: tracks.first()
        title = track.title; lastError = ""; prepared = false; loading = true
        player?.let { runCatching { it.release() } }; player = null; abandonFocus()
        val token = ++loadToken
        publish("파일 읽는 중")
        // openRawResourceFd 실패/압축 여부에 의존하지 않도록 앱 캐시에 먼저 복사한다.
        worker.execute {
            try {
                val extension = track.assetPath?.substringAfterLast('.', "wav") ?: "wav"
                val file = File(app.cacheDir, "sai_audio_${token}_$extension.$extension")
                val input = if (track.rawId != 0) app.resources.openRawResource(track.rawId) else app.assets.open(track.assetPath!!)
                input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
                main.post {
                    if (closed || token != loadToken) { file.delete(); return@post }
                    prepareFile(file, token)
                }
            } catch (e: Exception) { main.post { if (!closed && token == loadToken) error("음원 읽기 실패: ${e.javaClass.simpleName}: ${e.message}") } }
        }
    }
    private fun prepareFile(file: File, token: Int) {
        if (closed) return
        val p = try { MediaPlayer() } catch (e: Exception) { error("미디어 서비스 초기화 실패: ${e.message}"); return }
        player = p
        try {
            p.setAudioAttributes(attributes); p.isLooping = true
            p.setOnPreparedListener { mp ->
                if (!closed && token == loadToken && mp === player) { prepared = true; loading = false; publish("준비 완료"); applyVolume(); attemptPlayback() }
            }
            p.setOnErrorListener { mp, what, extra ->
                if (!closed && mp === player) error("디코더 오류: what=$what / extra=$extra")
                true
            }
            p.setDataSource(file.absolutePath); p.prepareAsync(); publish("디코더 준비 중")
            main.postDelayed({ if (!closed && token == loadToken && !prepared && player === p) error("디코더 준비 응답 없음") }, 10_000L)
        } catch (e: Exception) { error("재생 준비 실패: ${e.javaClass.simpleName}: ${e.message}") }
    }
    private fun attemptPlayback() {
        if (closed || !requested) return
        if (outputSilenced) { publish("앱 음소거"); return }
        if (!enabled) { publish("음악 꺼짐"); return }
        if (!prepared) return
        if (waitingFocus) { publish("오디오 포커스 복귀 대기"); return }
        if (!hasFocus) {
            if (speaking) { publish("안내 완료 뒤 음악 시작 대기"); return }
            val result = try {
                if (Build.VERSION.SDK_INT >= 26) audio.requestAudioFocus(focusRequest)
                else audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            } catch (e: Exception) { error("포커스 요청 오류: ${e.message}"); return }
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!hasFocus) {
                waitingFocus = Build.VERSION.SDK_INT >= 26 && result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
                publish(if (waitingFocus) "오디오 포커스 대기" else "오디오 포커스 거부 · 다른 재생 종료 후 재시도")
                return
            }
        }
        applyVolume()
        try {
            player?.start()
            if(pendingFadeMs>0L) {
                val fadeDuration=pendingFadeMs; pendingFadeMs=0L
                volumeFade=android.animation.ValueAnimator.ofFloat(0f,1f).apply {
                    duration=fadeDuration; addUpdateListener { transitionGain=it.animatedValue as Float; applyVolume() }; start()
                }
            }
            publish("재생 중") } catch (e: Exception) { error("재생 시작 실패: ${e.message}") }
    }
    private fun applyVolume() {
        // 중복 곱셈 감쇄 대신 한 번만 낮춘다. 기본 35%, 안내 중 최대 12%.
        val level = (if (speaking || focusDucked) minOf(volume, 0.12f) else volume)*transitionGain
        runCatching { player?.setVolume(level, level) }
    }
    private fun abandonFocus() {
        runCatching { if (Build.VERSION.SDK_INT >= 26) audio.abandonAudioFocusRequest(focusRequest) else audio.abandonAudioFocus(focusListener) }
        hasFocus = false; focusDucked = false; waitingFocus = false
    }
    private fun error(message: String) {
        lastError = message; requested = false; prepared = false; loading = false
        player?.let { runCatching { it.release() } }; player = null; abandonFocus()
        Log.e("SaiMusic", message); state = "재생 오류"; changed(); failed(message)
    }
    private fun publish(value: String) { state = value; Log.i("SaiMusic", value); changed() }
    fun diagnostic(): String {
        val systemVolume = runCatching { "${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}/${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}" }.getOrDefault("확인 불가")
        val position = runCatching { if (prepared && player?.isPlaying == true) " · ${(player?.currentPosition ?: 0)/1000}초" else "" }.getOrDefault("")
        return "$state$position · 앱 ${(volume*100).toInt()}% · 미디어 $systemVolume" + if (lastError.isEmpty()) "" else "\n$lastError"
    }
    fun close() {
        if (closed) return
        volumeFade?.cancel(); volumeFade=null; pause(); closed = true; loadToken++; main.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.release() } }; player = null; worker.shutdownNow()
    }
}


/** 공개된 상태만 관찰한다. 녹음하지 않으며 Gleo 전용 대화 종료 이벤트는 아니다. */
class SaiConversationMonitor(context: Context, private val interrupt: (String) -> Unit, private val idle: () -> Unit = {}) {
    private val audio=context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main=Handler(Looper.getMainLooper())
    private var closed=false
    private var recordingRegistered=false
    private var playbackRegistered=false
    private var count=0
    private var baselineMicrophones=0
    var voiceInputActive=false
        private set
    var assistantOutputActive=false
        private set
    val busy get() = voiceInputActive || assistantOutputActive
    private val callback=object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) { inspect(configs.orEmpty(),false) }
    }
    private val playbackCallback=object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) { inspectPlayback(configs.orEmpty()) }
    }
    private fun inspectPlayback(configs: List<AudioPlaybackConfiguration>) {
        if (closed) return
        // 콜백의 공개 활성 재생 목록만 사용한다. 숨겨진 isActive API는 사용하지 않는다.
        val output=configs.any { it.audioAttributes.usage in setOf(AudioAttributes.USAGE_ASSISTANT,AudioAttributes.USAGE_VOICE_COMMUNICATION) }
        if (assistantOutputActive==output) return
        assistantOutputActive=output
        Log.i("SAI_CONVERSATION","assistantOutput=$output")
        // 자체 TTS도 같은 usage일 수 있다. 출력만으로 새 대화 멈춤을 발생시키지 않는다.
        idle()
    }
    private fun inspect(configs: List<AudioRecordingConfiguration>, initial: Boolean) {
        if (closed) return
        val plainMicrophones=configs.count { it.clientAudioSource in setOf(MediaRecorder.AudioSource.DEFAULT,MediaRecorder.AudioSource.MIC) }
        if (initial) baselineMicrophones=plainMicrophones
        else baselineMicrophones=minOf(baselineMicrophones,plainMicrophones)
        val voice=configs.any { it.clientAudioSource in setOf(MediaRecorder.AudioSource.VOICE_RECOGNITION,MediaRecorder.AudioSource.VOICE_COMMUNICATION) }
        val microphoneCount=plainMicrophones+configs.count { it.clientAudioSource in setOf(MediaRecorder.AudioSource.VOICE_RECOGNITION,MediaRecorder.AudioSource.VOICE_COMMUNICATION) }
        val busyInput=voice || plainMicrophones>baselineMicrophones
        val activated=busyInput && !voiceInputActive
        val newlyOpened=!initial && microphoneCount>count
        val changed=busyInput!=voiceInputActive || microphoneCount!=count
        voiceInputActive=busyInput; count=microphoneCount
        Log.i("SAI_CONVERSATION","voiceInput=$busyInput visibleMicCount=$microphoneCount initial=$initial")
        if (activated || newlyOpened) interrupt("voice_input")
        else if (changed) idle()
    }
    fun open() {
        if (recordingRegistered || playbackRegistered || closed) return
        try {
            audio.registerAudioRecordingCallback(callback,main); recordingRegistered=true
            inspect(audio.activeRecordingConfigurations,true)
        } catch (e: Exception) { Log.w("SAI_CONVERSATION","Recording metadata unavailable; using focus/TTS signals",e) }
        try {
            audio.registerAudioPlaybackCallback(playbackCallback,main); playbackRegistered=true
            inspectPlayback(audio.activePlaybackConfigurations)
        } catch (e: Exception) { Log.w("SAI_CONVERSATION","Playback metadata unavailable; using focus/input signals",e) }
    }
    fun close() {
        if (closed) return
        closed=true
        if (recordingRegistered) runCatching { audio.unregisterAudioRecordingCallback(callback) }
        if (playbackRegistered) runCatching { audio.unregisterAudioPlaybackCallback(playbackCallback) }
        recordingRegistered=false; playbackRegistered=false
    }
}
