@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.lang.ref.WeakReference

/** 냅핑 UI/차량 제어는 구현하지 않는다. 설정 시간과 각성 전환만 소유한다. */
object SaiNapLink {
    enum class State { IDLE, CHECKING_PARK, NAPPING, AWAKEN_PENDING, SAFETY_STOP }
    var state = State.IDLE
        private set
    val isRunning get() = state in setOf(State.CHECKING_PARK, State.NAPPING, State.AWAKEN_PENDING)
    val totalSeconds get() = durationMs / 1000L
    val remainingSeconds get() = if (state == State.NAPPING)
        ((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L) / 1000L else 0L

    private val main = Handler(Looper.getMainLooper())
    private var host = WeakReference<Activity>(null)
    private var visible = false
    private var deadline = 0L
    private var durationMs = 0L
    private var guard: SaiParkGuard? = null
    private var unavailable = false
    private var safetyDelivered = false
    private var endCallback: ((String) -> Unit)? = null
    private var transitionRequested = false
    private var recordSessionId: String? = null
    private var awakenPreloaded=false
    private val pulse = object : Runnable {
        override fun run() {
            if(state==State.NAPPING && remainingSeconds<=4 && !awakenPreloaded) {
                awakenPreloaded=true
                host.get()?.let { com.airbnb.lottie.LottieCompositionFactory.fromRawRes(it.applicationContext,R.raw.sai_awaken_loop) }
            }
            if (state == State.NAPPING && SystemClock.elapsedRealtime() >= deadline) {
                state = State.AWAKEN_PENDING
                host.get()?.let { context -> recordSessionId?.let { SaiRitualRecord.complete(context, SaiRitualRecord.Mode.NAP, "nap:"+it) } }
                if (!endNap("elapsed")) {
                    val activity = host.get()
                    cancel("nap_cleanup_failed")
                    activity?.getSharedPreferences("sai_simple_test", Context.MODE_PRIVATE)?.edit()
                        ?.putString("last_exit_notice", "냅핑 정리에 실패해 각성 전환을 중단했어요.")?.apply()
                    activity?.let { SaiNavigation.home(it) }
                }
            }
            notifyHost()
            deliver()
            if (isRunning) main.postDelayed(this, if (state == State.NAPPING)
                (deadline - SystemClock.elapsedRealtime()).coerceIn(1L, 1000L) else 1000L)
        }
    }

    fun attach(activity: Activity) {
        if (host.get() !== activity) transitionRequested = false
        host = WeakReference(activity)
    }
    fun onResume(activity: Activity) {
        attach(activity); visible = true
        main.removeCallbacks(pulse); pulse.run()
    }
    fun onPause(activity: Activity) {
        if (host.get() === activity) visible = false
    }
    fun onWindowFocusChanged(activity: Activity, focused: Boolean) {
        if (host.get() === activity && focused) deliver()
    }
    fun detach(activity: Activity) {
        if (host.get() !== activity) return
        visible = false; host.clear()
        if (!activity.isChangingConfigurations) cancel("activity_destroyed")
    }

    /** 분 선택 완료 시 딱 한 번 호출한다. true는 요청 수락이며 P단 확인 후 카운트가 시작된다. */
    fun startNap(activity: Activity, minutes: Int, onNapEnd: (String) -> Unit = {}): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Call startNap on the UI thread" }
        if (minutes !in 1..1440 || state != State.IDLE || activity.isFinishing || activity.isDestroyed ||
            activity.fragmentManager.isStateSaved ||
            activity.fragmentManager.findFragmentByTag(SaiWakeTestFragment.TAG) != null) return false
        attach(activity)
        recordSessionId = SaiRitualRecord.newSessionId()
        durationMs = minutes * 60_000L
        endCallback = onNapEnd
        state = State.CHECKING_PARK; safetyDelivered = false; transitionRequested = false; awakenPreloaded=false
        val next = SaiParkGuard(activity) { parked, _ ->
            if (!isRunning) return@SaiParkGuard
            if (parked != true) stopForSafety(parked == null)
            else if (state == State.CHECKING_PARK) {
                deadline = SystemClock.elapsedRealtime() + durationMs
                state = State.NAPPING
                main.removeCallbacks(pulse); pulse.run()
            }
        }
        guard = next; notifyHost(); next.open()
        return true
    }

    /** X/음성 종료/홈 이동은 이 전환 예약도 반드시 취소한다. */
    fun cancel(reason: String): Boolean {
        SaiNapAwakenTransition.cancel()
        val wasRunning = isRunning
        main.removeCallbacks(pulse); guard?.close(); guard = null
        recordSessionId = null
        state = State.IDLE; deadline = 0L; safetyDelivered = false; transitionRequested = false
        endNap(reason)
        Log.i("SAI_NAP_LINK", "Canceled: $reason")
        notifyHost()
        return wasRunning
    }
    fun stopFromGleo(): Boolean {
        if (!isRunning) return false
        val activity = host.get()
        cancel("gleo_stop")
        activity?.let { SaiNavigation.home(it) }
        return true
    }
    private fun stopForSafety(missing: Boolean) {
        SaiNapAwakenTransition.cancel()
        main.removeCallbacks(pulse); guard?.close(); guard = null
        recordSessionId = null
        state = State.SAFETY_STOP; unavailable = missing; safetyDelivered = false
        endNap("vehicle_safety")
        notifyHost(); deliver()
    }
    private fun deliver() {
        val activity = host.get() ?: return
        if (!visible || activity.isFinishing || activity.isDestroyed ||
            !activity.hasWindowFocus() || activity.fragmentManager.isStateSaved) return
        if (state == State.SAFETY_STOP && !safetyDelivered) {
            safetyDelivered = true
            SaiWakeTestFragment.showSafety(activity, unavailable)
        } else if (state == State.AWAKEN_PENDING && !transitionRequested) {
            // 각성 화면에서도 차량 상태를 다시 확인한다. 같은 만료로 두 번 시작하지 않는다.
            transitionRequested = true
            SaiWakeTestFragment.showAfterNap(activity, recordSessionId ?: return)
        }
    }
    /** 새 각성 화면의 P단/TTS 확인이 끝날 때까지 냅핑 쪽 차량 감시를 유지한다. */
    fun awakeningStarted() {
        if (state != State.AWAKEN_PENDING) return
        main.removeCallbacks(pulse); guard?.close(); guard = null
        recordSessionId = null
        state = State.IDLE; deadline = 0L; transitionRequested = false; notifyHost()
    }
    private fun notifyHost() { (host.get() as? SaiHomeHost)?.onNapLinkChanged() }
    private fun endNap(reason: String): Boolean {
        val callback = endCallback ?: return true
        endCallback = null // 만료/취소/기어 변경이 겹쳐도 정리는 한 번만 호출한다.
        return try { callback(reason); true }
        catch (e: Exception) { Log.e("SAI_NAP_LINK", "Nap cleanup failed", e); false }
    }
}
