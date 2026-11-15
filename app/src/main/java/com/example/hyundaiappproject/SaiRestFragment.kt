@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import android.app.Fragment
import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.os.*
import android.view.*
import android.widget.*
import ai.pleos.playground.vehicle.Vehicle
import java.util.Locale

/** Relax guidance and nap presentation reuse the awake layout without overlapping the animation. */
class SaiRestFragment : Fragment() {
    private val main=Handler(Looper.getMainLooper())
    private var root:View?=null
    private val nap get()=arguments?.getString("mode")=="NAP"
    private val duration get()=if(nap) (arguments?.getInt("minutes",20)?:20)*60 else sessionSettings.relaxSeconds
    private val theme get()=(arguments?.getInt("theme",0)?:0).coerceIn(0,5)
    private var sessionSettings=SaiModeSettings()
    private var leaving=false
    private var handingOff=false
    private var sessionId=SaiRitualRecord.newSessionId()
    private var guard:SaiParkGuard?=null
    private var voice:SaiTestTts?=null
    private var ready=false
    private var speaking=false
    private var pendingSpeech:String?=null
    private var started=false
    private var done=false
    private var safety=false
    private var muted=false
    private var parked=false
    private var deadline=0L
    private var elapsed=0
    private var stage=-1
    private var bowl=true
    private var bowlStart=0L
    private var napLightApplied=false
    private var strike=-1
    private var pausedAt=0L
    private var conversation:SaiConversationMonitor?=null
    private var quietSince=0L
    private var car:CarControl?=null
    private var vehicle:Vehicle?=null
    private var player:MediaPlayer?=null
    private val sounds=Sounds()
    private val accent get()=if(nap) Color.rgb(139,174,218) else Color.rgb(151,198,169)
    private val cues=listOf("호흡을 천천히 이어가세요. 코로 깊게 들이마시고, 입으로 천천히 내쉬어 보세요.","어깨를 귀 쪽으로 천천히 올렸다가, 숨을 내쉬며 툭 떨어뜨려 보세요.","고개를 오른쪽으로 천천히 기울이고, 다시 왼쪽으로 기울여 보세요. 통증이 없는 범위에서 해 주세요.","어깨를 뒤로 크게 돌리면서, 가슴을 활짝 열어 보세요.")
    private fun text(id:Int,value:String) { root?.findViewById<TextView>(id)?.text=value }
    private fun show(id:Int,yes:Boolean) { root?.findViewById<View>(id)?.visibility=if(yes) View.VISIBLE else View.GONE }
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View {
        sessionSettings=SaiPreferences.load(activity)
        val v=inflater.inflate(R.layout.sai_wake_test,container,false); root=v
        muted=activity.getSharedPreferences("sai_simple_test",Context.MODE_PRIVATE).getBoolean("app_muted",false)
        v.findViewById<View>(R.id.sai_back).setOnClickListener { navigateBack() }
        v.findViewById<View>(R.id.sai_force).setOnClickListener { cancel() }
        v.findViewById<View>(R.id.sai_home).setOnClickListener { SaiNavigation.home(activity) }
        v.findViewById<View>(R.id.sai_safety_close).setOnClickListener { activity.finish() }
        v.findViewById<View>(R.id.sai_skip_bowl).apply {
            contentDescription="싱잉볼 건너뛰고 ${sessionSettings.relaxMinutes}분 릴렉스 시작"
            setOnClickListener { skipBowl() }
        }
        v.findViewById<ImageButton>(R.id.sai_mute).setOnClickListener {
            muted=!muted
            activity.getSharedPreferences("sai_simple_test",Context.MODE_PRIVATE).edit().putBoolean("app_muted",muted).apply()
            player?.setVolume(if(muted) 0f else .35f,if(muted) 0f else .35f)
            sounds.volume=if(muted) 0f else .7f
            if(muted) voice?.stopThen { speaking=false }
            else {
                if(!nap && !bowl && !done && pausedAt==0L) playRelax()
                pendingSpeech?.let { say(it) }
            }
            render()
        }
        v.findViewById<SaiDotProgressBar>(R.id.sai_progress).apply { max=duration; this.accent=this@SaiRestFragment.accent }
        v.viewTreeObserver.addOnGlobalLayoutListener { fit() }
        v.findViewById<SaiAwakenVisualView>(R.id.sai_visual).setScene(if(nap) R.raw.sai_nap_loop else R.raw.sai_bowl_loop)
        sounds.prepare(); sounds.volume=if(muted) 0f else .7f
        vehicle=Vehicle(activity).also { sdk -> runCatching { sdk.initialize(); car=CarControl(activity,sdk) { message -> main.post { text(R.id.sai_status,message) } } } }
        voice=SaiTestTts(activity,onReady={ ready=true; pendingSpeech?.let { say(it) } },onFailure={ ready=false; speaking=false; text(R.id.sai_status,"음성 연결 실패 · 화면 안내로 계속합니다") },onInterrupted={ speaking=false }).also { it.open() }
        conversation=SaiConversationMonitor(activity,interrupt={
            if(nap) player?.pause()
            if(!nap && started && !done && pausedAt==0L) { pausedAt=SystemClock.elapsedRealtime(); voice?.stopThen { speaking=false }; sounds.stopLoop() }
            quietSince=0L
        },idle={ quietSince=SystemClock.elapsedRealtime(); if(nap && !done) runCatching { player?.start() } }).also { it.open() }
        if(nap) {
            started=SaiNapLink.startNap(activity,arguments?.getInt("minutes",20)?:20) { reason ->
                if(reason=="elapsed") {
                    elapsed=duration; render()
                    handingOff=true; main.removeCallbacks(tick)
                    pendingSpeech=null; voice?.close(); voice=null
                    SaiNapAwakenTransition.prepare(root,player,car,muted)
                    player=null; car=null
                } else cleanupMedia()
            }
        } else {
            guard=SaiParkGuard(activity) { value,message ->
                parked=value==true
                if(parked && !started) startRelax()
                else if(!parked) stopSafety(message)
            }.also { it.open() }
        }
        main.post(tick); render(); return v
    }
    private fun startRelax() {
        started=true; bowlStart=SystemClock.elapsedRealtime(); bowl=true
        say("오늘 하루 수고 많았어요. 싱잉볼 소리에 귀 기울이며 편안한 휴식을 시작할게요.")
        main.removeCallbacks(tick); main.post(tick); render()
    }
    private fun startRelaxRoutine() {
        if(nap || !started || !bowl || done || safety || !parked) return
        sounds.stopBowl()
        bowl=false; deadline=SystemClock.elapsedRealtime()+duration*1_000L
        elapsed=0; stage=-1
        root?.findViewById<SaiAwakenVisualView>(R.id.sai_visual)?.setScene(intArrayOf(R.raw.sai_relax_loop, R.raw.sai_relax_rain, R.raw.sai_relax_camping, R.raw.sai_relax_space, R.raw.sai_relax_sea, R.raw.sai_relax_forest)[theme])
        car?.moveSeatBackrest(sessionSettings.relaxAngle.toDouble()); car?.moveLumbarForward(); car?.setFanLow(); car?.setTemperature(24f); car?.setRecirculation(false)
        car?.setAmbientColor(listOf(Color.rgb(255,159,70),Color.rgb(70,120,255),Color.rgb(255,110,40),Color.rgb(120,90,255),Color.rgb(40,190,220),Color.rgb(110,200,110))[theme])
        playRelax()
    }
    private fun skipBowl() {
        if(nap || !started || !bowl || bowlStart==0L || done || safety || !parked || pausedAt!=0L) return
        pendingSpeech=null
        val speech=voice
        speaking=speech!=null
        speech?.stopThen { speaking=false; pendingSpeech?.let { say(it) } }
        startRelaxRoutine()
        stage=0; say(cues[0]); render()
    }
    private fun playRelax() { if(!muted) sounds.startLoop(listOf(Ambience.MEDITATION,Ambience.RAIN,Ambience.FIRE,Ambience.SPACE,Ambience.OCEAN,Ambience.FOREST)[theme]) }
    private fun playNap() {
        if(arguments?.getInt("asmr",0)==2) return
        if(player!=null) return
        player=MediaPlayer.create(activity,if(arguments?.getInt("asmr",0)==1) R.raw.asmr2 else R.raw.asmr1)?.apply {
            isLooping=true; setVolume(if(muted) 0f else .35f,if(muted) 0f else .35f); start()
        }
    }
    private fun say(value:String) {
        pendingSpeech=value; text(R.id.sai_guidance,value)
        if(!ready || muted || speaking) return
        speaking=true; pendingSpeech=null
        voice?.speak(value) { speaking=false; pendingSpeech?.let { say(it) } }
    }
    private val tick=object:Runnable {
        override fun run() {
            if(root==null || safety || leaving || handingOff) return
            if(nap) refreshNap()
            else if(started && !done) {
                if(pausedAt!=0L) {
                    if(activity.hasWindowFocus() && conversation?.busy!=true) {
                        if(quietSince==0L) quietSince=SystemClock.elapsedRealtime()
                        if(SystemClock.elapsedRealtime()-quietSince>=2500L) {
                            val delay=SystemClock.elapsedRealtime()-pausedAt
                            if(bowl) bowlStart+=delay else deadline+=delay
                            pausedAt=0L; quietSince=0L; if(!bowl) playRelax()
                        }
                    } else quietSince=0L
                } else if(bowl) {
                    val bowlElapsed=SystemClock.elapsedRealtime()-bowlStart
                    val sec=(bowlElapsed/1000).toInt()
                    // Striker contact frames 36 and 936 at 60fps in the supplied composition.
                    val nextStrike=when { bowlElapsed>=15_600L -> 1; bowlElapsed>=600L -> 0; else -> -1 }
                    if(nextStrike>strike && sec<30) { strike=nextStrike; if(!muted) sounds.strikeBowl() }
                    if(sec>=30) startRelaxRoutine()
                } else {
                    elapsed=(duration-kotlin.math.ceil((deadline-SystemClock.elapsedRealtime())/1000.0).toInt()).coerceIn(0,duration)
                    val nominal=elapsed*300/duration
                    val next=when { nominal<150 -> 0; nominal<200 -> 1; nominal<250 -> 2; else -> 3 }
                    if(next!=stage) { stage=next; say(cues[next]) }
                    if(elapsed>=duration) {
                        done=true; cleanupMedia(); car?.restore(); car=null
                        SaiRitualRecord.complete(activity,SaiRitualRecord.Mode.RELAX,sessionId)
                        say("릴렉스 루틴을 마쳤어요. 오늘도 나를 돌보는 시간을 함께해 주셔서 고마워요.")
                    }
                }
            }
            render(); if(!done) main.postDelayed(this,200)
        }
    }
    fun refreshNap() {
        if(!nap || root==null || leaving || handingOff) return
        if(SaiNapLink.state==SaiNapLink.State.NAPPING) {
            if(!napLightApplied && car!=null) {
                napLightApplied=true
                car?.moveSeatBackrest(sessionSettings.napAngle.toDouble())
                car?.setAmbientBrightness(arguments?.getInt("light_brightness",20)?:20)
            }
            parked=true; elapsed=(duration-SaiNapLink.remainingSeconds.toInt()).coerceIn(0,duration); playNap()
            val percent=elapsed*100/duration
            val next=when { percent<10 -> 0; percent<85 -> 1; percent<95 -> 2; else -> 3 }
            if(next!=stage) {
                stage=next
                activity.window.attributes=activity.window.attributes.apply { screenBrightness=if(next==1) .5f else .7f }
                when(next) { 0 -> say("눈을 감고 편하게 쉬세요."); 2 -> say("천천히 깨어날 시간이에요."); 3 -> say("곧 각성 모드가 시작됩니다.") }
            }
        }
        render()
    }
    private fun render() {
        val v=root?:return
        if(leaving) return
        val waitingForPark=!parked && !done && !safety
        val total=if(!nap && bowl && started) 30 else duration
        val displayElapsed=if(!nap && bowl && started) (((if(pausedAt>0L) pausedAt else SystemClock.elapsedRealtime())-bowlStart)/1000L).toInt().coerceIn(0,30) else elapsed
        text(R.id.sai_stage,if(done) "RITUAL COMPLETE" else if(nap) "NAPPING" else if(bowl) "SINGING BOWL" else "RELAX · ${listOf("호흡","어깨","목","가슴")[stage.coerceAtLeast(0)]}")
        text(R.id.sai_title,if(done) "다음의 나를 만날 시간이에요" else if(!parked) "주차 상태를 확인하고 있어요" else if(nap) listOf("눈을 감고 편하게 쉬세요","휴식 중이에요","천천히 깨어날 시간이에요","곧 각성 모드로 이어져요")[stage.coerceAtLeast(0)] else if(bowl) "오늘의 싱잉볼" else "나에게 집중하는 시간")
        text(R.id.sai_elapsed,String.format(Locale.US,"%02d:%02d",displayElapsed/60,displayElapsed%60))
        text(R.id.sai_total,String.format(Locale.US,"/ %02d:%02d",total/60,total%60))
        text(R.id.sai_percent,"${displayElapsed*100/total}%")
        text(R.id.sai_progress_stage,if(done) "완료" else if(nap) "냅핑 후 각성 자동 시작" else if(bowl) "싱잉볼 · 2번의 울림" else "${stage+1}단계")
        text(R.id.sai_sound_state,if(muted) "앱 음소거 · 텍스트 안내" else "음성 안내 켜짐")
        if(nap && stage>=1) text(R.id.sai_guidance,"눈을 감고 편하게 쉬세요.")
        v.findViewById<ImageButton>(R.id.sai_mute).setImageResource(if(muted) R.drawable.sai_ic_sound_off else R.drawable.sai_ic_sound_on)
        v.findViewById<SaiDotProgressBar>(R.id.sai_progress).apply { max=total; progress=displayElapsed }
        v.findViewById<SaiAwakenVisualView>(R.id.sai_visual).apply { playbackDurationSeconds=total.toFloat(); progress=displayElapsed/total.toFloat(); completed=done; running=started && !done && pausedAt==0L }
        val bowlActive=!nap && started && bowl && bowlStart>0L && !done && !safety && parked
        show(R.id.sai_skip_bowl,bowlActive)
        v.findViewById<View>(R.id.sai_skip_bowl).apply { isEnabled=bowlActive && pausedAt==0L; alpha=if(isEnabled) 1f else .45f }
        show(R.id.sai_visual,!safety && !waitingForPark)
        show(R.id.sai_completion_panel,done); show(R.id.sai_progress_panel,!done && !safety && !waitingForPark); show(R.id.sai_status,false)
        show(R.id.sai_safety_panel,safety); show(R.id.sai_content,!safety && !waitingForPark)
        if(done) { text(R.id.sai_completion_schedule,"이번 주 릴렉스 ${SaiRitualRecord.week(activity,SaiRitualRecord.Mode.RELAX).count}회"); text(R.id.sai_home,"홈으로 돌아가기") }
    }
    private fun fit() {
        if(!parked) return
        val v=root?:return; val scroll=v.findViewById<View>(R.id.sai_scroll)
        val content=v.findViewById<LinearLayout>(R.id.sai_content); val visual=v.findViewById<View>(R.id.sai_visual)
        if(scroll.height<=0) return
        var used=content.paddingTop+content.paddingBottom
        for(i in 0 until content.childCount) {
            val child=content.getChildAt(i)
            if(child===visual || child.visibility==View.GONE || child.id==R.id.sai_visual_space_top || child.id==R.id.sai_visual_space_bottom) continue
            val lp=child.layoutParams as? ViewGroup.MarginLayoutParams
            used+=child.measuredHeight+(lp?.topMargin?:0)+(lp?.bottomMargin?:0)
        }
        val h=(scroll.height-used).coerceAtLeast(0)
        if(visual.layoutParams.height!=h) visual.layoutParams=visual.layoutParams.apply { height=h }
    }
    private fun cleanupMedia() {
        player?.release(); player=null; sounds.stopLoop(); sounds.stopBowl()
        if(nap) { car?.restore(); car=null }
        if(isAdded && !handingOff) activity.window.attributes=activity.window.attributes.apply { screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
    }
    private fun stopSafety(message:String) {
        safety=true; cleanupMedia(); voice?.close(); voice=null; car?.restore(); car=null
        text(R.id.sai_safety_message,message); render()
    }
    fun navigateBack() {
        if(safety) { activity.finish(); return }
        leave(if(nap) "NAP" else "RELAX")
    }
    private fun cancel() { leave("HOME") }
    private fun leave(page:String) {
        if(leaving) return
        leaving=true; done=true; main.removeCallbacksAndMessages(null)
        guard?.close(); guard=null; voice?.close(); voice=null
        SaiNapLink.cancel("user_exit"); cleanupMedia(); car?.restore(); car=null
        SaiNavigation.page(activity,page,arguments?.let { Bundle(it) })
    }
    override fun onPause() {
        if(!nap && started && !done && pausedAt==0L) { pausedAt=SystemClock.elapsedRealtime(); sounds.stopLoop(); voice?.stopThen { speaking=false } }
        super.onPause()
    }
    override fun onDestroyView() {
        main.removeCallbacksAndMessages(null); guard?.close(); guard=null
        conversation?.close(); conversation=null; voice?.close(); voice=null
        cleanupMedia(); sounds.close(); car?.restore(); car=null; root=null
        super.onDestroyView()
    }
}
