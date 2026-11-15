@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import ai.pleos.playground.vehicle.Vehicle
import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/** 정상/X 종료는 완료 화면, P단 해제는 중단 화면. 안전 화면의 닫기만 task를 종료한다. */
class SaiWakeTestFragment : Fragment() {
    companion object {
        const val TAG = "SAI_SIMPLE_WAKE_TEST"
        @JvmStatic fun show(activity: Activity) { open(activity, null) }
        fun showAfterNap(activity: Activity, sessionId: String) { open(activity, null, sessionId) }
        fun showSafety(activity: Activity, unavailable: Boolean) {
            val current = activity.fragmentManager.findFragmentByTag(TAG) as? SaiWakeTestFragment
            if (current != null) current.onVehicleSafety(unavailable) else open(activity, unavailable)
        }
        private fun open(activity: Activity, unavailable: Boolean?, sessionId: String? = null) {
            if (activity.fragmentManager.isStateSaved || activity.isFinishing || activity.isDestroyed) return
            if (activity.fragmentManager.findFragmentByTag(TAG) != null) return
            val screen = SaiWakeTestFragment()
            screen.arguments = Bundle().apply {
                if (unavailable != null) putBoolean("sai_safety", unavailable)
                if (sessionId != null) putString("nap_record_session", sessionId)
            }
            activity.fragmentManager.beginTransaction().replace(android.R.id.content, screen, TAG).commit()
        }
    }
    private fun host(): Activity = activity ?: error("Activity 연결 없음")
    private fun prefs() = host().getSharedPreferences("sai_simple_test", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var root: View? = null
    private val contentLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { fitVisualToViewport() }
    private val windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (!hasFocus) pauseForSystemConversation("window_focus")
        else {
            if (returnHomeWhenVisible) returnToHome()
            val pending = pendingEndSpeech
            if (phase=="STOPPING" && pending!=null && !muted) speakEndWhenVisible(pending)
            else { tryStartDemo(); resumeWhenReturned() }
        }
    }
    private var voice: SaiTestTts? = null
    private var music: SaiMusicPlayer? = null
    private var parkGuard: SaiParkGuard? = null
    private var conversation: SaiConversationMonitor? = null
    private var parked = false
    private var gearStatus = "P단 확인 중…"
    private var safetyTitle = "차량 상태가 바뀌어 중단했어요"
    private var safetyMessage = "안전을 위해 모드를 종료했어요.\n주차 상태를 확인한 뒤 다시 시작해 주세요."
    private var phase = "IDLE"
    private var externalSafety: Boolean? = null
    private var startDemoPending = true
    private var homeNavigationPending = false
    private var returnHomeWhenVisible = false
    private var ready = false
    private var voiceFallback = false
    private var voiceConnection = 0
    private var voiceRecoveryPending = false
    private var voiceRecoveryAttempts = 0
    private var voiceRecoveryFault = false
    private var recoveryQuietSince = 0L
    private val guidanceReady get() = ready || muted || voiceFallback
    private val voiceRecovery = object : Runnable {
        override fun run() {
            if (root == null || !voiceRecoveryPending || phase in setOf("SAFETY", "DONE", "STOPPING")) return
            if (!visibleToUser || root?.hasWindowFocus()!=true || conversation?.busy==true || !parked) {
                recoveryQuietSince = 0L; main.postDelayed(this, 250L); return
            }
            if (recoveryQuietSince == 0L) recoveryQuietSince = SystemClock.elapsedRealtime()
            if (SystemClock.elapsedRealtime()-recoveryQuietSince < conversationQuietMs) {
                main.postDelayed(this, 250L); return
            }
            voiceRecoveryPending = false
            if (voiceRecoveryFault && voiceRecoveryAttempts >= 2) {
                voiceFallback = true; speechBarrier = false
                status = "음성 연결을 복구하지 못해 텍스트 안내로 계속합니다."
                android.util.Log.e("SAI_TTS", "Recovery exhausted; using text guidance")
                tryStartDemo()
                if (active && paused && !explicitPause) { resumeOnReturn = true; resumeWhenReturned() }
                else pump()
                render(); return
            }
            if (voiceRecoveryFault) voiceRecoveryAttempts++
            status = if (voiceRecoveryFault) "음성 서비스를 다시 연결하고 있어요. (${voiceRecoveryAttempts}/2)" else "대화 후 음성 연결을 준비하고 있어요."
            android.util.Log.i("SAI_TTS", "Reconnecting attempt=$voiceRecoveryAttempts")
            openVoice(); render()
        }
    }
    private var paused = false
    private var muted = false
    private var visibleToUser = false
    private var resumeOnReturn = false
    private var explicitPause = false
    private var quietSince = 0L
    private val conversationQuietMs = 2500L
    private val autoResume = object : Runnable {
        override fun run() {
            if (!canResumeAfterConversation()) { quietSince = 0L; return }
            val remaining = conversationQuietMs - (SystemClock.elapsedRealtime()-quietSince)
            if (remaining > 0L) main.postDelayed(this, remaining)
            else {
                android.util.Log.i("SAI_WAKE", "Conversation quiet; resuming routine")
                status = "대화를 마쳐 각성 모드를 이어갑니다."
                resumeGuidance()
            }
        }
    }
    private var pendingEndSpeech: String? = null
    private var returnToModesAfterEnd=false
    private var endingSpeechActive=false
    private var endingTextVisibleAt=0L
    private val backExitText=object:Runnable {
        override fun run() {
            if(root==null || phase!="STOPPING" || !returnToModesAfterEnd) return
            if(!visibleToUser || root?.hasWindowFocus()!=true || conversation?.busy==true) {
                endingTextVisibleAt=0L; main.postDelayed(this,250L); return
            }
            if(endingTextVisibleAt==0L) endingTextVisibleAt=SystemClock.elapsedRealtime()
            val readingMs=(line.length*75L).coerceIn(5_000L,10_000L)
            if(SystemClock.elapsedRealtime()-endingTextVisibleAt>=readingMs) finishRoutine(line)
            else main.postDelayed(this,250L)
        }
    }
    private fun showBackExitText() {
        if(root==null || phase!="STOPPING" || !returnToModesAfterEnd) return
        generation++; endingSpeechActive=false; pendingEndSpeech=null
        endingTextVisibleAt=0L; main.removeCallbacks(backExitText); main.post(backExitText); render()
    }
    private var recordVisibleSince = 0L
    private var recordNavigationRequested = false
    private val advanceToRecord = object : Runnable {
        override fun run() {
            if (root==null || phase!="DONE" || stoppedByUser || recordNavigationRequested) return
            if (!visibleToUser || root?.hasWindowFocus()!=true || !parked || host().fragmentManager.isStateSaved) {
                recordVisibleSince=0L; main.postDelayed(this,250L); return
            }
            if (recordVisibleSince==0L) recordVisibleSince=SystemClock.elapsedRealtime()
            if (SystemClock.elapsedRealtime()-recordVisibleSince>=8000L) openWeeklyRecord()
            else main.postDelayed(this,250L)
        }
    }
    private fun openWeeklyRecord() {
        if (phase!="DONE" || stoppedByUser || recordNavigationRequested || !parked || !visibleToUser || root?.hasWindowFocus()!=true || host().fragmentManager.isStateSaved) return
        recordNavigationRequested=true; main.removeCallbacks(advanceToRecord)
        SaiNavigation.page(host(),"RECORDS")
    }
    private var awakeSessionId = SaiRitualRecord.newSessionId()
    private var stoppedByUser = false
    private var deadline = 0L
    private var pausedAt = 0L
    private var modeSettings=SaiModeSettings()
    private val totalSeconds get()=modeSettings.awakenSeconds
    private var seatConfigured=false
    private var inheritedNapEnvironment=false
    private var vehicle:Vehicle?=null
    private var car:CarControl?=null
    private var seconds = 180
    private var index = 0
    private var generation = 0
    private var speechBarrier = false
    private var speaking: String? = null
    private var speakingItem: String? = null
    private val speechQueue = ArrayDeque<String>()
    private var line = "호흡을 가다듬어 각성을 준비해요."
    private var status = "Pleos TTS 연결 중…"
    private val active get() = phase == "WAKE"
    private val elapsed get() = (totalSeconds-seconds).coerceIn(0,totalSeconds)
    private val stageElapsed get()=elapsed*180/totalSeconds
    private data class Cue(val second: Int, val text: String)
    private val cues get() = listOf(
        Cue(0, "각성 모드를 시작합니다. 앞으로 ${modeSettings.awakenMinutes}분 동안 천천히 몸을 깨워 볼게요."),
        Cue(8, "창문을 엽니다."), Cue(16, "차 실내 온도를 낮춥니다."),
        Cue(24, "등받이를 올립니다."), Cue(32, "엠비언트 등을 켭니다."),
        Cue(45, "엄지와 검지로 귓불을 가볍게 잡고 부드럽게 마사지해 주세요."),
        Cue(75, "코로 천천히 숨을 들이마시고 입으로 길게 내쉬어 주세요."),
        Cue(110, "한 번 더 천천히 숨을 들이마시고 길게 내쉬어 주세요."),
        Cue(145, "어깨의 힘을 빼고 손과 발을 가볍게 움직여 주세요."),
        Cue(160, "곧 마무리됩니다. 호흡을 정리하고 다음 일정을 준비해 주세요."),
        Cue(180, "@finish")
    ).map { it.copy(second=it.second*totalSeconds/180) }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        modeSettings=SaiPreferences.load(host()); seconds=totalSeconds
        root = inflater.inflate(R.layout.sai_wake_test, container, false)
        root!!.viewTreeObserver.addOnGlobalLayoutListener(contentLayoutListener)
        root!!.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusListener)
        root!!.keepScreenOn = true; muted = prefs().getBoolean("app_muted", false)
        root!!.findViewById<View>(R.id.sai_back).setOnClickListener { if(phase=="SAFETY") closeApp() else navigateBack() }
        root!!.findViewById<View>(R.id.sai_force).setOnClickListener { if(phase=="SAFETY") closeApp() else forceStop() }
        root!!.findViewById<View>(R.id.sai_mute).setOnClickListener { toggleMute() }
        root!!.findViewById<View>(R.id.sai_home).setOnClickListener {
            if (phase=="DONE" && !stoppedByUser)
                openWeeklyRecord()
            else returnToHome()
        }
        root!!.findViewById<View>(R.id.sai_safety_close).setOnClickListener { closeApp() }
        SaiWakeTestBridge.attach(this)
        val requestedSafety = externalSafety ?: arguments?.takeIf { it.containsKey("sai_safety") }?.getBoolean("sai_safety")
        if (requestedSafety != null) { safetyStop(requestedSafety); return root!! }
        car=SaiNapAwakenTransition.takeCar()
        inheritedNapEnvironment=car!=null || arguments?.containsKey("nap_record_session")==true
        SaiNapAwakenTransition.attach(root as ViewGroup)
        if(car==null) vehicle=Vehicle(host()).also { sdk -> runCatching {
            sdk.initialize(); car=CarControl(host(),sdk) { message -> main.post { if(root!=null) { status=message; render() } } }
        } }
        music = SaiMusicPlayer(host(), changed = { if (root != null) render() }, failed = { message -> if (root != null) { status = message; render() } }, interrupted = { pauseForSystemConversation("audio_focus") })
        music?.setOutputMuted(muted)
        conversation = SaiConversationMonitor(host(),
            interrupt = { source -> pauseForSystemConversation(source) },
            idle = { tryStartDemo(); conversationStateChanged() }
        ).also { it.open() }
        openVoice()
        parkGuard = SaiParkGuard(host()) { isParked, message ->
            if (root != null && phase != "SAFETY") {
                parked = isParked == true; gearStatus = message
                if (isParked == false) safetyStop(false)
                else if (isParked == null && (active || phase == "STOPPING" || phase == "DONE" || inheritedNapEnvironment)) safetyStop(true)
                tryStartDemo(); render()
            }
        }.also { it.open() }
        render(); main.post(tick); return root!!
    }
    private fun retireVoice() {
        voiceConnection++; ready = false
        val old = voice; voice = null; speechBarrier = false
        old?.close()
    }
    private fun cancelVoiceRecovery() {
        voiceRecoveryPending = false; recoveryQuietSince = 0L
        main.removeCallbacks(voiceRecovery)
    }
    private fun scheduleVoiceRecovery() {
        if (root == null || phase in setOf("SAFETY", "DONE", "STOPPING")) return
        voiceRecoveryPending = true; recoveryQuietSince = 0L
        main.removeCallbacks(voiceRecovery); main.postDelayed(voiceRecovery, 250L)
    }
    private fun openVoice() {
        val token = ++voiceConnection
        try {
            val next = SaiTestTts(host(),
                onReady = {
                    if (root != null && token==voiceConnection && phase!="SAFETY") {
                        ready = true; voiceFallback = false; cancelVoiceRecovery()
                        status = if (explicitPause) "요청에 따라 멈췄어요. 재개를 요청해 주세요." else "음성 안내 준비 완료"
                        tryStartDemo(); resumeWhenReturned(); render()
                    }
                },
                onFailure = { message -> if (root != null && token==voiceConnection && phase!="SAFETY") handleVoiceFailure(message) },
                onInterrupted = {
                    if (root != null && token==voiceConnection && phase!="SAFETY") {
                        if (phase=="STOPPING") {
                            status = "종료 음성이 중지되었습니다. 일정은 화면에서 확인해 주세요."
                            endingSpeechActive=false
                            if(returnToModesAfterEnd) showBackExitText() else finishRoutine(line)
                        } else if (muted || voiceFallback) {
                            speaking = null; speakingItem = null; speechBarrier = false; pump()
                        } else pauseForSystemConversation("tts_interrupted")
                        render()
                    }
                }
            )
            voice = next; next.open()
        } catch (e: Exception) { if (token==voiceConnection) handleVoiceFailure("TTS 연결 실패: ${e.message}") }
    }
    private fun handleVoiceFailure(message: String) {
        android.util.Log.e("SAI_TTS", "phase=$phase error=$message")
        status = message; voiceRecoveryFault = true
        if (phase=="STOPPING") { cancelVoiceRecovery(); retireVoice(); endingSpeechActive=false; if(returnToModesAfterEnd) showBackExitText() else finishRoutine(line); render(); return }
        if (phase=="DONE") { retireVoice(); return }
        val mayAutoResume = !explicitPause && (!paused || resumeOnReturn)
        if (active && !muted && !voiceFallback) pauseGuidance()
        resumeOnReturn = mayAutoResume && paused
        retireVoice(); scheduleVoiceRecovery()
        if (muted || voiceFallback) pump()
        render()
    }
    /** 홈 단독 체험 또는 냅핑 만료 연결: 화면/P단/음성 준비 후 한 번 시작한다. */
    private fun tryStartDemo() {
        val v = root ?: return
        if (!startDemoPending || phase != "IDLE" || !visibleToUser || !v.hasWindowFocus() || conversation?.busy==true || !parked || (!guidanceReady)) return
        startDemoPending = false
        startWake()
    }
    private fun cancelConversationResume() {
        resumeOnReturn = false; quietSince = 0L; main.removeCallbacks(autoResume)
    }
    private fun canResumeAfterConversation(): Boolean =
        root!=null && active && paused && resumeOnReturn && !explicitPause && parked &&
            visibleToUser && root?.hasWindowFocus()==true && conversation?.busy!=true &&
            !speechBarrier && guidanceReady
    private fun conversationStateChanged() {
        // 새 입력/글레오 응답이 들어오면 조용한 구간을 처음부터 다시 센다.
        quietSince = 0L; main.removeCallbacks(autoResume); resumeWhenReturned()
    }
    private fun pauseForSystemConversation(source: String) {
        if (!active) return
        android.util.Log.i("SAI_WAKE", "System interaction: $source")
        // 오류 등으로 이미 멈춘 세션을 외부 포커스 이벤트가 임의로 재개하지 않는다.
        val mayAutoResume = !explicitPause && (!paused || resumeOnReturn)
        pauseGuidance()
        resumeOnReturn = mayAutoResume
        if (mayAutoResume) status = "대화를 위해 잠시 멈췄어요. 대화가 끝나면 자동으로 이어갑니다."
        // 중단된 서비스 연결을 재사용하지 않고 대화가 끝난 뒤 새 리스너로 연결한다.
        retireVoice(); scheduleVoiceRecovery()
        conversationStateChanged(); render()
    }
    private fun resumeWhenReturned() {
        if (!canResumeAfterConversation()) {
            quietSince = 0L; main.removeCallbacks(autoResume); return
        }
        if (quietSince == 0L) quietSince = SystemClock.elapsedRealtime()
        main.removeCallbacks(autoResume)
        main.postDelayed(autoResume, (conversationQuietMs-(SystemClock.elapsedRealtime()-quietSince)).coerceAtLeast(0L))
    }
    private fun startWake() {
        if (!parked || (!guidanceReady) || phase != "IDLE") return
        generation++; speechQueue.clear(); speaking = null; speakingItem = null; speechBarrier = false
        cancelConversationResume(); explicitPause = false
        phase = "WAKE"; paused = false; stoppedByUser = false; index = 0; seconds = totalSeconds; seatConfigured=false; resumeOnReturn = false
        SaiNapLink.awakeningStarted()
        deadline = SystemClock.elapsedRealtime()+totalSeconds*1_000L
        line = "호흡을 가다듬어 각성을 준비해요."
        music?.setOutputMuted(muted); music?.setDucked(false)
        if(inheritedNapEnvironment) music?.fadeInNextStart(700L)
        music?.play()
        main.removeCallbacks(tick); main.post(tick); render()
        root?.findViewById<SaiAwakenVisualView>(R.id.sai_visual)?.whenSceneReady {
            if(root!=null && active) SaiNapAwakenTransition.revealAwaken()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (root == null) return
            if (active && !paused) {
                seconds = ceil((deadline-SystemClock.elapsedRealtime())/1000.0).toInt().coerceAtLeast(0)
                if(!seatConfigured && stageElapsed>=24 && car!=null) {
                    seatConfigured=true; car?.moveSeatBackrest(modeSettings.awakenAngle.toDouble())
                }
                while (index<cues.size && cues[index].second<=elapsed) enqueue(cues[index++].text)
            }
            render(); if (phase != "SAFETY" && phase != "DONE") main.postDelayed(this, 200L)
        }
    }
    private fun enqueue(item: String) { speechQueue.addLast(item); pump() }
    private fun pump() {
        if (!active || paused || speechBarrier || speaking != null || speechQueue.isEmpty()) return
        if (!guidanceReady) return
        val item = speechQueue.removeFirst(); val finalMessage = item=="@finish"
        val text = if (finalMessage) "${modeSettings.awakenMinutes}분 동안 애쓰셨습니다. "+scheduleSpeech() else item
        line = text
        if (muted || voiceFallback) {
            if (finalMessage) finishRoutine(text) else pump()
            render(); return
        }
        speaking = text; speakingItem = item; val token = generation; music?.setDucked(true)
        voice?.speak(text) {
            if (token==generation && root!=null) {
                speaking = null; speakingItem = null; voiceRecoveryAttempts = 0; voiceRecoveryFault = false
                if (finalMessage) finishRoutine(text) else { music?.setDucked(false); pump() }
                render()
            }
        }
        render()
    }
    private fun finishRoutine(finalLine: String) {
        if (phase == "SAFETY" || phase == "DONE" || root == null) return
        endingSpeechActive=false; main.removeCallbacks(backExitText)
        cancelConversationResume(); cancelVoiceRecovery(); explicitPause = false
        music?.pause(); music?.setDucked(false)
        car?.restore(); restoreNapScreenBrightness()
        phase = "DONE"; pendingEndSpeech = null; paused = false; speaking = null; speakingItem = null; speechBarrier = false; resumeOnReturn = false
        if (!stoppedByUser) {
            seconds = 0
            awakeSessionId.let { session ->
                try { SaiRitualRecord.complete(host(), SaiRitualRecord.Mode.AWAKEN, session) }
                catch (e: Exception) { Log.e("SAI_RECORD", "Completion could not be saved", e); status = "기록을 저장하지 못했어요." }
            }
        }
        line = finalLine; speechQueue.clear(); main.removeCallbacks(tick)
        if (!stoppedByUser) {
            recordVisibleSince=0L; recordNavigationRequested=false
            main.removeCallbacks(advanceToRecord); main.post(advanceToRecord)
        }
        if (stoppedByUser) {
            prefs().edit().putString("last_exit_notice", finalLine).apply()
            if(returnToModesAfterEnd) returnToHome() else render()
        }
    }
    private fun pauseGuidance() {
        if (!active || paused) return
        music?.pause(); paused = true; pausedAt = SystemClock.elapsedRealtime(); generation++
        speaking?.let { speechQueue.addFirst(speakingItem ?: it) }; speaking = null; speakingItem = null
        speechBarrier = true
        val v = voice
        if (v == null) speechBarrier = false else v.stopThen { speechBarrier = false; if (!paused) pump() else resumeWhenReturned(); render() }
        render()
    }
    private fun resumeGuidance() {
        if (!active || (!guidanceReady)) return
        if (paused) deadline += SystemClock.elapsedRealtime()-pausedAt
        paused = false; cancelConversationResume(); explicitPause = false
        music?.setOutputMuted(muted); music?.setDucked(false); music?.play(); pump(); render()
    }
    fun isRoutineRunning(): Boolean = active
    fun isSafetyStopped(): Boolean = phase == "SAFETY"
    fun canStartVehicleRoutine(): Boolean = parked && phase != "SAFETY"
    fun pauseForGleo() { pauseForSystemConversation("gleo_action") }
    fun scheduleSavedFromGleo() {
        if (phase == "SAFETY") return
        val wasRunning = active
        if (wasRunning) pauseForSystemConversation("schedule_saved")
        status = if (wasRunning && resumeOnReturn) "일정을 저장했어요. 대화가 끝나면 자동으로 이어갑니다." else "다음 일정을 저장했어요."
        line = "${scheduleTime()} ${scheduleTitle()} · " + status
        render()
    }
    fun controlFromGleo(command: String): Boolean {
        if (!active) return false
        when(command) {
            "pause" -> { explicitPause = true; cancelConversationResume(); pauseGuidance(); status = "요청에 따라 멈췄어요. 글레오에게 재개를 요청해 주세요." }
            "resume" -> { explicitPause = false; resumeOnReturn = true; status = "대화가 끝나면 각성 모드를 이어갑니다."; conversationStateChanged() }
            else -> return false
        }
        render(); return true
    }
    /** 상단 뒤로 가기: 실행 중이면 X와 같은 종료 안내 후 첫 화면으로 돌아간다. */
    fun navigateBack() {
        if(root==null || homeNavigationPending) return
        if(phase=="SAFETY") { closeApp(); return }
        if(phase=="STOPPING") return
        returnToModesAfterEnd=true
        if(phase=="DONE") { returnToHome(); return }
        forceStop()
    }
    /** X/음성 종료 모두 앱을 종료하지 않고 기능만 멈춘다. */
    fun forceStop(): Boolean {
        android.util.Log.i("SAI_WAKE", "forceStop phase=$phase")
        if (root==null || (!active && !(returnToModesAfterEnd && phase=="IDLE"))) return false
        startDemoPending=false; main.removeCallbacks(tick)
        cancelConversationResume(); explicitPause = false
        cancelVoiceRecovery()
        generation++; phase = "STOPPING"; paused = false; stoppedByUser = true; resumeOnReturn = false
        speechQueue.clear(); speaking = null; speakingItem = null; music?.pause()
        val text = if(returnToModesAfterEnd) "각성 리츄얼을 마칠게요. "+scheduleSpeech()+" 모드 선택 화면으로 돌아갈게요." else scheduleSpeech()+" 종료합니다."
        if(returnToModesAfterEnd) car?.restore()
        line = text; render()
        if (muted || voiceFallback || !ready || voice==null) {
            if (!guidanceReady) status = "음성 사용 불가 · 다음 일정은 화면에서 확인해 주세요."
            if(returnToModesAfterEnd) showBackExitText() else finishRoutine(text)
            render(); return true
        }
        speakEndWhenVisible(text)
        return true
    }
    private fun speakEndWhenVisible(text: String) {
        if(endingSpeechActive || phase!="STOPPING") return
        if(returnToModesAfterEnd && (muted || voiceFallback || !ready || voice==null)) { showBackExitText(); return }
        if (!visibleToUser || root?.hasWindowFocus()!=true) { pendingEndSpeech = text; status = "기능은 중단했어요. 글레오 대화 후 돌아오면 일정을 안내해요."; render(); return }
        pendingEndSpeech = null
        endingSpeechActive=true; main.removeCallbacks(backExitText)
        val token = generation
        voice?.stopThen { if (root!=null && phase=="STOPPING" && token==generation) voice?.speak(text) {
            if (root!=null && phase=="STOPPING" && token==generation) { endingSpeechActive=false; finishRoutine(text); render() }
        } }
    }
    private fun toggleMute() {
        muted = !muted; prefs().edit().putBoolean("app_muted", muted).apply()
        SaiNapAwakenTransition.setMuted(muted)
        music?.setOutputMuted(muted)
        if (muted && (speaking!=null || phase=="STOPPING")) {
            val finishNow = phase=="STOPPING" || speakingItem=="@finish"
            val currentLine = line
            generation++; speaking = null; speakingItem = null; speechBarrier = true
            voice?.stopThen {
                speechBarrier = false
                endingSpeechActive=false
                if (root!=null) { if (finishNow) { if(returnToModesAfterEnd && phase=="STOPPING") showBackExitText() else finishRoutine(currentLine) } else pump(); render() }
            }
            if (voice==null) { speechBarrier = false; endingSpeechActive=false; if (finishNow) { if(returnToModesAfterEnd && phase=="STOPPING") showBackExitText() else finishRoutine(currentLine) } else pump() }
        } else if (!muted && active && !paused) { music?.play(); pump() }
        tryStartDemo(); render()
    }
    fun onVehicleSafety(unavailable: Boolean) {
        externalSafety = unavailable
        if (root != null) safetyStop(unavailable)
    }
    /** P단 해제는 X와 다르다. 종료 TTS/일정까지 취소하고 안전 화면을 유지한다. */
    private fun safetyStop(unavailable: Boolean) {
        if (phase == "SAFETY") return
        SaiNapAwakenTransition.cancel(); restoreNapScreenBrightness()
        cancelConversationResume(); cancelVoiceRecovery(); explicitPause = false
        if (active && !paused) seconds = ceil((deadline-SystemClock.elapsedRealtime())/1000.0).toInt().coerceIn(0,totalSeconds)
        main.removeCallbacks(tick); startDemoPending = false
        generation++; phase = "SAFETY"; parked = false; ready = false; paused = false
        resumeOnReturn = false; pendingEndSpeech = null; speechBarrier = false
        speaking = null; speakingItem = null; speechQueue.clear()
        music?.close(); music = null
        voice?.close(); voice = null
        car?.restore(); car=null
        SaiNapLink.cancel("gear_not_parked_or_unavailable")
        if (unavailable) {
            safetyTitle = "차량 상태를 확인할 수 없어 중단했어요"
            safetyMessage = "안전을 위해 모드를 종료했어요.\n차량 연결과 주차 상태를 확인한 뒤 다시 시작해 주세요."
        }
        render()
    }
    private fun closeApp() {
        if (phase != "SAFETY") return
        cancelVoiceRecovery(); voiceConnection++
        generation++; main.removeCallbacksAndMessages(null)
        conversation?.close(); conversation = null
        parkGuard?.close(); parkGuard = null
        music?.close(); music = null; voice?.close(); voice = null
        SaiWakeTestBridge.detach(this)
        SaiNapLink.cancel("safety_close")
        // SAI가 속한 task만 종료한다. 다른 앱이나 시스템 프로세스는 건드리지 않는다.
        host().finishAndRemoveTask()
    }
    private fun returnToHome() {
        android.util.Log.i("SAI_WAKE", "home phase=$phase stoppedByUser=$stoppedByUser")
        if (phase != "DONE" || homeNavigationPending) return
        val manager = host().fragmentManager
        if (!visibleToUser || root?.hasWindowFocus()!=true || manager.isStateSaved) {
            returnHomeWhenVisible = true
            return
        }
        returnHomeWhenVisible = false; homeNavigationPending = true
        if (!stoppedByUser) prefs().edit().remove("last_exit_notice").apply()
        if(returnToModesAfterEnd) SaiNavigation.page(host(),"SELECT") else SaiNavigation.home(host())
    }
    private fun scheduleTime() = SaiScheduleStore.time(host())
    private fun scheduleTitle() = SaiScheduleStore.title(host())
    private fun scheduleSpeech() = SaiScheduleStore.speech(host())
    fun saveSchedule(time: String, title: String) { SaiScheduleStore.save(host(),time,title); render() }
    /** 앱 표시 영역의 중앙에 그래픽을 고정하고 위/아래 여백을 따로 계산한다. */
    private fun restoreNapScreenBrightness() {
        if(inheritedNapEnvironment && isAdded) host().window.attributes=host().window.attributes.apply {
            screenBrightness=android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
    private fun fitVisualToViewport() {
        val v = root ?: return
        val scroll = v.findViewById<View>(R.id.sai_scroll)
        val content = v.findViewById<LinearLayout>(R.id.sai_content)
        val visual = v.findViewById<View>(R.id.sai_visual)
        val topSpace = v.findViewById<View>(R.id.sai_visual_space_top)
        val bottomSpace = v.findViewById<View>(R.id.sai_visual_space_bottom)
        if (scroll.height <= 0 || content.visibility != View.VISIBLE || visual.visibility != View.VISIBLE) return
        var before = content.paddingTop
        var after = content.paddingBottom
        var passedVisual = false
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child === visual) { passedVisual = true; continue }
            if (child === topSpace || child === bottomSpace || child.visibility == View.GONE) continue
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
            val occupied = child.measuredHeight + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
            if (passedVisual) after += occupied else before += occupied
        }
        val rootPosition = IntArray(2)
        val scrollPosition = IntArray(2)
        v.getLocationInWindow(rootPosition)
        scroll.getLocationInWindow(scrollPosition)
        val target = rootPosition[1] + v.height / 2 - scrollPosition[1]
        val available = minOf(target - before, scroll.height - target - after) * 2
        val minimum = (120 * resources.displayMetrics.density).toInt()
        // 화면 크기에 비례하되 위 제목/아래 일정이 차지하는 공간을 침범하지 않는다.
        val preferred = maxOf(minimum, scroll.height - before - after)
        // 큰 접근성 글꼴/짧은 창에서는 내용을 자르지 않고 기존 스크롤을 유지한다.
        val fits = available >= minimum
        val height = maxOf(0, scroll.height - before - after)
        val upper = 0
        val lower = 0
        fun setHeight(view: View, value: Int) {
            if (view.layoutParams.height != value) {
                view.layoutParams = view.layoutParams.apply { this.height = value }
            }
        }
        setHeight(visual, height)
        setHeight(topSpace, upper)
        setHeight(bottomSpace, lower)
    }
    private fun render() {
        val v = root ?: return
        (v as? ViewGroup)?.getChildAt(0)?.visibility=if(phase=="DONE") View.INVISIBLE else View.VISIBLE
        v.findViewById<View>(R.id.sai_content).setPadding(0,(26*resources.displayMetrics.density).toInt(),0,0)
        v.findViewById<TextView>(R.id.sai_title).typeface=if(phase=="DONE")
            android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)
            else android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
        if (phase == "SAFETY") {
            val panel = v.findViewById<View>(R.id.sai_progress_panel)
            val parent = panel.parent as? ViewGroup
            if (parent !== v) {
                parent?.removeView(panel)
                (v as LinearLayout).addView(panel, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() })
            }
        }
        fun text(id: Int, value: String) { v.findViewById<TextView>(id).text = value }
        fun show(id: Int, visible: Boolean) { v.findViewById<View>(id).visibility = if (visible) View.VISIBLE else View.GONE }
        val tag: String
        val title: String
        when {
            phase=="DONE" -> { tag = "RITUAL COMPLETE"; title = "다음의 나를 만날 시간이에요" }
            phase=="STOPPING" -> { tag = "AWAKEN · ENDING"; title = "다음 일정을 안내할게요" }
            paused -> { tag = "AWAKEN · PAUSED"; title = "잠시 쉬어 가고 있어요" }
            stageElapsed>=160 && active -> { tag = "AWAKEN · FINISH"; title = "곧 마무리돼요" }
            stageElapsed>=45 && active -> { tag = "AWAKEN · ACTIVATE"; title = "가볍게 몸을 깨워 보세요" }
            else -> { tag = if (phase=="IDLE") "AWAKEN · WAITING" else "AWAKEN · BEGIN"; title = if (phase=="IDLE") "각성을 준비하고 있어요" else "각성을 시작해요" }
        }
        text(R.id.sai_stage,tag); text(R.id.sai_title,title)
        text(R.id.sai_guidance,if (phase=="IDLE") "차량과 음성 안내가 준비되면 ${modeSettings.awakenMinutes}분 각성을 시작해요." else if (phase=="DONE") "리추얼을 마쳤어요. 다음 일정으로 넘어갈 준비를 해 주세요." else line)
        text(R.id.sai_status,if (parked) status else gearStatus)
        if (phase=="DONE") {
            text(R.id.sai_completion_schedule,scheduleSpeech())
            text(R.id.sai_home,if(stoppedByUser) "홈화면으로 돌아가기" else "리츄얼 기록 보기")
        }
        text(R.id.sai_elapsed,String.format(Locale.US,"%02d:%02d",elapsed/60,elapsed%60))
        text(R.id.sai_percent,"${(elapsed*100+totalSeconds/2)/totalSeconds}%")
        text(R.id.sai_progress_stage,if (phase=="SAFETY") "안전 중단" else if (phase=="DONE") "종료" else if (phase=="STOPPING") "일정 안내 중" else if (paused) "일시정지" else if (stageElapsed>=160) "정렬 · 다음 일정 준비" else if (stageElapsed>=45) "활성 · 호흡과 이완" else "시작 · 감각 깨우기")
        v.findViewById<SaiDotProgressBar>(R.id.sai_progress).apply { max=totalSeconds; progress=elapsed }
        text(R.id.sai_total,String.format(Locale.US,"/ %02d:%02d",totalSeconds/60,totalSeconds%60))
        v.findViewById<SaiAwakenVisualView>(R.id.sai_visual).apply { playbackDurationSeconds=totalSeconds.toFloat(); completed = phase=="DONE"; running = active && !paused; progress = elapsed/totalSeconds.toFloat() }
        v.findViewById<ImageButton>(R.id.sai_mute).apply {
            setImageResource(if (muted) R.drawable.sai_ic_sound_off else R.drawable.sai_ic_sound_on)
            contentDescription = if (muted) "앱 음악과 음성 안내 켜기" else "앱 음악과 음성 안내 음소거"
            isSelected = muted
        }
        text(R.id.sai_sound_state,if (muted) "앱 음소거 · 일정은 화면으로 안내해요" else if (voiceFallback) "음성 연결 실패 · 텍스트로 안내해요" else "음성 안내 켜짐")
        text(R.id.sai_safety_title,safetyTitle); text(R.id.sai_safety_message,safetyMessage)
        show(R.id.sai_content,phase!="SAFETY")
        show(R.id.sai_safety_panel,phase=="SAFETY")
        show(R.id.sai_back,true)
        v.findViewById<View>(R.id.sai_back).apply {
            isEnabled = phase!="STOPPING"
            alpha = if (isEnabled) 1f else 0.35f
        }
        show(R.id.sai_mute,true)
        show(R.id.sai_force,true)
        v.findViewById<View>(R.id.sai_force).apply {
            isEnabled = active || phase=="SAFETY"; alpha = if (isEnabled) 1f else 0.35f
        }
        show(R.id.sai_status,phase=="IDLE" || (active && (!ready || paused || voiceFallback)))
        show(R.id.sai_sound_state,phase=="IDLE" || (active && (paused || voiceFallback)))
        show(R.id.sai_completion_panel,phase=="DONE")
        show(R.id.sai_progress_panel,phase!="DONE" && phase!="SAFETY")
        show(R.id.sai_visual,phase!="SAFETY")

    }
    override fun onResume() {
        super.onResume(); visibleToUser = true
        if (returnHomeWhenVisible) returnToHome()
        val pending = pendingEndSpeech
        if (phase=="STOPPING" && pending!=null && !muted) speakEndWhenVisible(pending)
        else resumeWhenReturned()
        tryStartDemo()
    }
    override fun onPause() {
        visibleToUser = false; music?.pause()
        pauseForSystemConversation("screen_hidden")
        super.onPause()
    }
    override fun onDestroyView() {
        SaiNapAwakenTransition.detach(root); restoreNapScreenBrightness()
        car?.restore(); car=null; vehicle=null
        android.util.Log.i("SAI_WAKE", "destroyView phase=$phase")
        root?.viewTreeObserver?.takeIf { it.isAlive }?.let {
            it.removeOnGlobalLayoutListener(contentLayoutListener)
            it.removeOnWindowFocusChangeListener(windowFocusListener)
        }
        startDemoPending = false; cancelConversationResume(); cancelVoiceRecovery(); voiceConnection++; explicitPause = false
        conversation?.close(); conversation = null
        parkGuard?.close(); parkGuard = null; parked = false
        SaiWakeTestBridge.detach(this); generation++; main.removeCallbacksAndMessages(null)
        voice?.close(); voice = null; music?.close(); music = null; root = null
        phase = "IDLE"; ready = false; paused = false; speaking = null; speechQueue.clear()
        super.onDestroyView()
    }
}
