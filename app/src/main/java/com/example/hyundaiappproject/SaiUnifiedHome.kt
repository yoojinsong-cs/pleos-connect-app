@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import android.app.AlertDialog
import android.app.Fragment
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.*
import android.widget.*

/** Home, cards and nap preparation. Schedules use the shared Gleo JSON store. */
class SaiDemoHomeFragment : Fragment() {
    private lateinit var body: LinearLayout
    private lateinit var stars: View
    private lateinit var mute: ImageButton
    private lateinit var header: LinearLayout
    private var page="HOME"
    private var minutes=20
    private var music=0
    private var brightness=20
    private var dialog: AlertDialog?=null
    private val white=Color.rgb(242,243,246)
    private val gray=Color.rgb(170,174,184)
    private val gold=Color.rgb(220,198,171)
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    private fun shape(color:Int,radius:Int=18)=GradientDrawable().apply {
        setColor(color); cornerRadius=dp(radius).toFloat()
    }
    private fun text(value:String,size:Int=20,color:Int=white,bold:Boolean=false)=TextView(activity).apply {
        text=value; gravity=Gravity.CENTER; setTextColor(color); includeFontPadding=false
        setSingleLine(true); setHorizontallyScrolling(false); textSize=size.toFloat()
        setAutoSizeTextTypeUniformWithConfiguration(10,size,1,TypedValue.COMPLEX_UNIT_SP)
        if(bold) typeface=Typeface.create("sans-serif",Typeface.BOLD)
    }
    private fun addText(value:String,size:Int,color:Int=white,bold:Boolean=false,top:Int=0,height:Int=40):TextView {
        val view=text(value,size,color,bold)
        body.addView(view,LinearLayout.LayoutParams(-1,dp(height)).apply { topMargin=dp(top) })
        return view
    }
    private fun action(value:String,primary:Boolean=true,run:()->Unit)=Button(activity).apply {
        text=value; textSize=20f; isAllCaps=false; gravity=Gravity.CENTER
        setSingleLine(true); setHorizontallyScrolling(false); setAutoSizeTextTypeUniformWithConfiguration(12,20,1,TypedValue.COMPLEX_UNIT_SP)
        setTextColor(if(primary) Color.rgb(27,28,32) else white)
        background=shape(if(primary) Color.rgb(229,230,235) else Color.rgb(39,42,47),14)
        setPadding(dp(12),0,dp(12),0); stateListAnimator=null
        setOnClickListener { run() }
    }
    private fun headerIcon(icon:Int,description:String,run:()->Unit)=ImageButton(activity).apply {
        setImageResource(icon); setBackgroundResource(R.drawable.sai_icon_button)
        setPadding(dp(22),dp(22),dp(22),dp(22)); contentDescription=description
        setOnClickListener { run() }
    }
    override fun onCreateView(inflater:LayoutInflater,container:ViewGroup?,state:Bundle?):View {
        page=state?.getString("page")?:arguments?.getString("page")?:"HOME"; minutes=state?.getInt("minutes")?:arguments?.getInt("minutes",20)?:20
        music=state?.getInt("music")?:arguments?.getInt("asmr",0)?:0; brightness=state?.getInt("brightness")?:arguments?.getInt("light_brightness",20)?:20
        val outer=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(28),dp(12),dp(28),dp(64))
            setBackgroundColor(Color.rgb(27,28,32))
        }
        header=LinearLayout(activity).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(headerIcon(R.drawable.sai_ic_back,"뒤로 가기") { back() },LinearLayout.LayoutParams(dp(64),dp(64)))
        header.addView(ImageView(activity).apply {
            setImageResource(R.drawable.sai_wordmark); contentDescription="SAI"; scaleType=ImageView.ScaleType.FIT_CENTER
        },LinearLayout.LayoutParams(dp(144),dp(32)))
        header.addView(Space(activity),LinearLayout.LayoutParams(0,1,1f))
        mute=headerIcon(R.drawable.sai_ic_sound_on,"음소거") {
            val prefs=activity.getSharedPreferences("sai_simple_test",Context.MODE_PRIVATE)
            prefs.edit().putBoolean("app_muted",!prefs.getBoolean("app_muted",false)).apply(); updateMute()
        }
        header.addView(mute,LinearLayout.LayoutParams(dp(72),dp(72)))
        header.addView(headerIcon(R.drawable.sai_ic_close,"앱 닫기") { activity.finish() },LinearLayout.LayoutParams(dp(72),dp(72)))
        outer.addView(header,LinearLayout.LayoutParams(-1,dp(72)))
        val frame=FrameLayout(activity)
        stars=SaiCosmicBackdrop(activity); frame.addView(stars,FrameLayout.LayoutParams(-1,-1))
        body=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL
            setPadding(0,dp(26),0,0)
        }
        frame.addView(body,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER)); outer.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        showPage(page); updateMute(); return outer
    }
    override fun onSaveInstanceState(out:Bundle) {
        super.onSaveInstanceState(out); out.putString("page",page); out.putInt("minutes",minutes)
        out.putInt("music",music); out.putInt("brightness",brightness)
    }
    private fun updateMute() {
        val muted=activity.getSharedPreferences("sai_simple_test",Context.MODE_PRIVATE).getBoolean("app_muted",false)
        mute.setImageResource(if(muted) R.drawable.sai_ic_sound_off else R.drawable.sai_ic_sound_on)
        mute.contentDescription=if(muted) "음소거 해제" else "음소거"
    }
    private fun newPage(name:String) {
        page=name; body.removeAllViews(); stars.visibility=if(name=="HOME") View.VISIBLE else View.GONE
        header.visibility=if(name=="HOME") View.GONE else View.VISIBLE
        body.setPadding(0,if(name=="HOME") 0 else dp(26),0,0)
    }
    private fun space(weight:Float=1f) { body.addView(Space(activity),LinearLayout.LayoutParams(1,0,weight)) }
    private fun showPage(name:String) { when(name) {
        "SELECT" -> select(); "RELAX" -> relaxThemes(); "NAP_TIME" -> napTimes(); "NAP" -> napSettings(); "SETTINGS" -> settings(); "RECORDS" -> records(); else -> home()
    } }
    fun back() { when(page) { "HOME" -> activity.finish(); "RELAX","NAP_TIME" -> select(); "NAP" -> napTimes(); else -> home() } }
    fun refresh() { if(::body.isInitialized) showPage(page) }
    private fun home() {
        newPage("HOME")
        val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(0,dp(16),0,dp(16)) }
        val scroll=ScrollView(activity).apply { isFillViewport=true; clipToPadding=true }
        scroll.addView(content,FrameLayout.LayoutParams(-1,-2)); body.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        content.addView(ImageView(activity).apply { setImageResource(R.drawable.sai_wordmark_bold); contentDescription="SAI"; scaleType=ImageView.ScaleType.FIT_CENTER },LinearLayout.LayoutParams(dp(300),dp(86)))
        content.addView(text("Space Across Identities",18,gold).apply { letterSpacing=.08f },LinearLayout.LayoutParams(-1,dp(28)))
        content.addView(text("Atria님, 환영해요",32),LinearLayout.LayoutParams(-1,dp(48)).apply { topMargin=dp(14) })
        content.addView(text("오늘의 문구",16,gold),LinearLayout.LayoutParams(-1,dp(26)).apply { topMargin=dp(10) })
        content.addView(text("오늘은 잠시 쉬어가도 괜찮아요.",22,white),LinearLayout.LayoutParams(-1,dp(38)))
        val buttons=object:LinearLayout(activity) {
            override fun onMeasure(widthSpec:Int,heightSpec:Int) {
                val available=View.MeasureSpec.getSize(widthSpec)
                val width=minOf(dp(420),(available*.72f).toInt())
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),heightSpec)
            }
        }.apply { orientation=LinearLayout.VERTICAL }
        buttons.addView(action("SAI 시작하기") { select() },LinearLayout.LayoutParams(-1,dp(72)))
        buttons.addView(action("나의 리츄얼",false) { records() },LinearLayout.LayoutParams(-1,dp(72)).apply { topMargin=dp(14) })
        content.addView(buttons,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(22) })
        // Reserve a fixed footer so the settings icon never overlaps the scrolling welcome content.
        // The parent's bottom/right padding already supplies 64dp / 28dp respectively.
        val footer=FrameLayout(activity)
        footer.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.sai_ic_settings); setBackgroundResource(R.drawable.sai_icon_button)
            setPadding(dp(18),dp(18),dp(18),dp(18)); contentDescription="환경설정"
            setOnClickListener { settings() }
        },FrameLayout.LayoutParams(dp(64),dp(64),Gravity.END or Gravity.BOTTOM))
        body.addView(footer,LinearLayout.LayoutParams(-1,dp(64)))
    }

    private fun headings(tag:String,title:String,subtitle:String) {
        addText(tag,20,gold,height=30); addText(title,40,bold=true,top=10,height=58)
        addText(subtitle,20,gray,top=8,height=32)
    }
    private fun row()=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
    private fun cardSection(content:View,maxHeightDp:Int,top:Int=18,bottom:Int=0) {
        // Only the outer slot takes leftover height. The visible cards have a height limit.
        val slot=FrameLayout(activity)
        val bounded=object:FrameLayout(activity) {
            override fun onMeasure(widthSpec:Int,heightSpec:Int) {
                val height=minOf(View.MeasureSpec.getSize(heightSpec),dp(maxHeightDp))
                super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
            }
        }
        bounded.addView(content,FrameLayout.LayoutParams(-1,-1))
        slot.addView(bounded,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        body.addView(slot,LinearLayout.LayoutParams(-1,0,1f).apply { topMargin=dp(top); bottomMargin=dp(bottom) })
    }
    private fun card(icon:String,title:String,value:String,description:String="",accent:Int=gray,compact:Boolean=false,record:Boolean=false,run:()->Unit):LinearLayout {
        return SaiResponsiveCard(activity).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER
            setPadding(dp(12),dp(if(compact) 8 else 14),dp(12),dp(if(compact) 8 else 14))
            background=shape(Color.rgb(39,42,47),16)
            isClickable=true; isFocusable=true; contentDescription=listOf(title,value,description).filter { it.isNotBlank() }.joinToString(" ")
            setOnClickListener { run() }
            addView(SaiModeIconView(activity,icon),LinearLayout.LayoutParams(dp(if(record) 128 else if(compact) 72 else 88),dp(if(record) 128 else if(compact) 72 else 88)))
            addView(text(title,if(record) 30 else if(compact) 28 else 26,white,true),LinearLayout.LayoutParams(-1,dp(40)).apply { topMargin=dp(if(compact) 5 else 10) })
            if(value.isNotBlank()) addView(text(value,if(record) 48 else 23,if(record) gold else accent,record),LinearLayout.LayoutParams(-1,dp(if(record) 66 else 32)))
            if(description.isNotBlank()) addView(text(description,if(compact) 20 else 19,gray),LinearLayout.LayoutParams(-1,dp(30)).apply { topMargin=dp(if(compact) 4 else 8) })
        }
    }
    private fun addCard(row:LinearLayout,card:View,index:Int) {
        row.addView(card,LinearLayout.LayoutParams(0,-1,1f).apply { if(index>0) leftMargin=dp(18) })
    }
    private fun threeCardSection(cards:View,reservePreparationFooter:Boolean=false) {
        cardSection(cards,288,top=24,bottom=12)
        // Match the preparation page's 22 + 10 + 48 + 12 + 72dp footer.
        if(reservePreparationFooter) body.addView(Space(activity),LinearLayout.LayoutParams(-1,listOf(22,10,48,12,72).sumOf { dp(it) }))
    }
    private fun select() {
        newPage("SELECT"); headings("SELECT MODE","지금 필요한 시간은 무엇인가요?","원하는 모드와 시간을 선택해 주세요.")
        val cards=row()
        addCard(cards,card("relax","릴렉스","${SaiPreferences.load(activity).relaxMinutes}분","호흡과 스트레칭으로 긴장을 풀어요.",Color.rgb(190,181,232)) { relaxThemes() },0)
        addCard(cards,card("nap","냅핑","시간 선택","시간을 선택하고 천천히 깨어나요.",Color.rgb(177,174,223)) { napTimes() },1)
        addCard(cards,card("awaken","각성","${SaiPreferences.load(activity).awakenMinutes}분","다음 일정 전에 집중을 준비해요.",Color.rgb(157,210,221)) { SaiWakeTestFragment.show(activity) },2)
        threeCardSection(cards,reservePreparationFooter=true)
        // The root already supplies 28dp: 28 + 92 = 120dp at each screen edge.
        cards.setPadding(dp(92),0,dp(92),0)
    }
    private fun openRest(mode:String,theme:Int=0) {
        if(fragmentManager.isStateSaved) return
        fragmentManager.beginTransaction().replace(android.R.id.content,SaiRestFragment().apply {
            arguments=Bundle().apply {
                putString("mode",mode); putInt("minutes",minutes); putInt("theme",theme)
                putInt("asmr",music); putInt("light_brightness",brightness)
            }
        },"SAI_REST").commit()
    }
    private fun relaxThemes() {
        newPage("RELAX"); headings("RELAX · SELECT","어떤 풍경에서 쉬어갈까요?","잠시 후 싱잉볼과 함께 시작해요.")
        val titles=listOf("기본","비","캠핑","우주","바다","숲")
        val icons=listOf("basic","rain","camp","space","sea","forest")
        val descriptions=listOf("차분한 호흡으로 쉬어가요","빗소리에 긴장을 내려놓아요","따뜻한 모닥불 곁에서 쉬어요",
            "고요한 우주에 잠시 머물러요","잔잔한 파도에 호흡을 맞춰요","숲의 소리로 마음을 정돈해요")
        val grid=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        for(r in 0..1) {
            val line=row()
            for(c in 0..2) { val i=r*3+c; addCard(line,card(icons[i],titles[i],"",descriptions[i],compact=true) { openRest("RELAX",i) },c) }
            grid.addView(line,LinearLayout.LayoutParams(-1,0,1f).apply { if(r>0) topMargin=dp(14) })
        }
        cardSection(grid,500,top=18,bottom=0)
    }
    private fun napTimes() {
        newPage("NAP_TIME"); headings("NAPPING · TIME","얼마나 쉬어갈까요?","원하는 시간을 선택해 주세요.")
        val durations=intArrayOf(10,15,20,25,30)
        val grid=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        for(r in 0..1) {
            val line=row()
            for(c in 0..2) {
                val i=r*3+c
                addCard(line,card("clock",if(i<5) "${durations[i]}분" else "시간 선택","",if(i<5) "선택하고 환경 확인" else "원하는 시간 직접 입력",compact=true) {
                    if(i<5) { minutes=durations[i]; napSettings() } else customTime()
                },c)
            }
            grid.addView(line,LinearLayout.LayoutParams(-1,0,1f).apply { if(r>0) topMargin=dp(16) })
        }
        cardSection(grid,500,top=18)
    }
    private fun napSettings() {
        newPage("NAP"); headings("NAPPING · PREPARE","냅핑 전 환경을 확인해 주세요","휴식 ${minutes}분 · 이후 각성 ${SaiPreferences.load(activity).awakenMinutes}분")
        val cards=row(); val light=card("light","조명","${brightness}%") {}
        light.isClickable=false; light.isFocusable=false
        val value=light.getChildAt(light.childCount-1) as TextView
        light.addView(SeekBar(activity).apply {
            max=100; progress=brightness; contentDescription="조명 밝기"
            progressTintList=android.content.res.ColorStateList.valueOf(gold); thumbTintList=android.content.res.ColorStateList.valueOf(gold)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,v:Int,user:Boolean) { brightness=v; value.text=if(v==0) "소등" else "$v%" }
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        },LinearLayout.LayoutParams(-1,dp(36)))
        val schedule=SaiScheduleStore.load(activity)
        addCard(cards,light,0)
        addCard(cards,card("reminder","리마인더",schedule?.time?:"일정 없음",schedule?.title?.take(16)?:"눌러서 확인·변경") { scheduleDialog() },1)
        addCard(cards,card("music","음악",listOf("ASMR 1","ASMR 2","음악 없음")[music]) { chooseMusic() },2)
        threeCardSection(cards)
        addText("차량이 지원하는 설정만 적용됩니다.",14,gray,height=22)
        addText("글레오에게 말하면 일정을 확인하고 변경할 수 있어요.",23,gold,bold=true,top=10,height=48).apply {
            background=shape(Color.rgb(35,38,46),12); setPadding(dp(14),0,dp(14),0)
        }
        body.addView(action("시작하기") { openRest("NAP") },LinearLayout.LayoutParams(-1,dp(72)).apply { topMargin=dp(12) })
    }

    private fun chooseTime() { napTimes() }

    private fun customTime() {
        val field=EditText(activity).apply { inputType=InputType.TYPE_CLASS_NUMBER; hint="1~1440분"; setText(minutes.toString()) }
        val d=AlertDialog.Builder(activity).setTitle("냅핑 시간 (분)").setView(field).setPositiveButton("적용",null).setNegativeButton("취소",null).create()
        dialog=d; d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n=field.text.toString().toIntOrNull()
            if(n==null || n !in 1..1440) field.error="1~1440분을 입력해 주세요"
            else { minutes=n; d.dismiss(); napSettings() }
        } }; d.show()
    }
    private fun sizeNapDialog(value:AlertDialog) {
        val dm=resources.displayMetrics
        val bounds=if(android.os.Build.VERSION.SDK_INT>=30) activity.windowManager.currentWindowMetrics.bounds
            else android.graphics.Rect(0,0,dm.widthPixels,dm.heightPixels)
        val width=minOf(dp(720),(bounds.width()-dp(56)).coerceAtLeast(1))
        val height=minOf(dp(440),(bounds.height()-dp(128)).coerceAtLeast(1))
        value.window?.apply { setGravity(Gravity.CENTER); setLayout(width,height); setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN) }
    }
    private fun chooseMusic() {
        val d=AlertDialog.Builder(activity).setTitle("음악 선택")
            .setSingleChoiceItems(arrayOf("ASMR 1","ASMR 2","음악 없음"),music) { popup,index -> music=index; popup.dismiss(); napSettings() }
            .setNegativeButton("취소",null).create()
        dialog=d; d.setOnShowListener { sizeNapDialog(d) }; d.show()
    }
    private fun scheduleDialog() {
        val current=SaiScheduleStore.load(activity)
        val box=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(8),dp(24),0) }
        box.addView(TextView(activity).apply { text="글레오에게 말하거나 아래에서 직접 변경할 수 있어요."; textSize=16f })
        val time=EditText(activity).apply { hint="시간 (예: 14:30)"; inputType=InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME; setText(current?.time.orEmpty()) }
        val title=EditText(activity).apply { hint="일정 이름"; setSingleLine(true); setText(current?.title.orEmpty()) }
        box.addView(time); box.addView(title)
        val form=ScrollView(activity).apply { addView(box,FrameLayout.LayoutParams(-1,-2)) }
        val d=AlertDialog.Builder(activity).setTitle("다음 일정 확인·변경").setView(form)
            .setNeutralButton("새로고침",null).setPositiveButton("저장",null).setNegativeButton("닫기",null).create()
        dialog=d; d.setOnShowListener {
            sizeNapDialog(d)
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val latest=SaiScheduleStore.load(activity); time.setText(latest?.time.orEmpty()); title.setText(latest?.title.orEmpty())
            }
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t=SaiScheduleTime.normalize(time.text.toString())
                when { t==null -> time.error="24시간 형식으로 입력해 주세요 (14:30)"
                    title.text.isBlank() -> title.error="일정 이름을 입력해 주세요"
                    else -> runCatching { SaiScheduleStore.save(activity,t,title.text.toString()) }
                        .onSuccess { d.dismiss(); if(page=="NAP") napSettings() }.onFailure { title.error="저장하지 못했어요. 다시 시도해 주세요." }
                }
            }
        }; d.show()
    }
    private fun records() {
        newPage("RECORDS"); headings("MY RITUAL","이번 주 나의 리츄얼","완료한 리츄얼만 기록돼요.")
        val cards=row(); val modes=listOf(SaiRitualRecord.Mode.RELAX,SaiRitualRecord.Mode.NAP,SaiRitualRecord.Mode.AWAKEN)
        listOf("릴렉스","냅핑","각성").forEachIndexed { i,name ->
            addCard(cards,card(listOf("relax","nap","awaken")[i],name,"${SaiRitualRecord.week(activity,modes[i]).count}회",record=true) {}.apply { isClickable=false; isFocusable=false },i)
        }
        cardSection(cards,420,top=24)
    }
    private fun settings() {
        newPage("SETTINGS"); headings("PREFERENCES","나에게 맞는 리츄얼","저장한 설정은 다음에 시작하는 모드부터 적용돼요.")
        val config=SaiPreferences.load(activity)
        var relax=config.relaxMinutes; var awake=config.awakenMinutes
        var relaxSeat=config.relaxAngle; var napSeat=config.napAngle; var awakeSeat=config.awakenAngle
        val content=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(12),0,dp(12)) }
        val scroll=ScrollView(activity).apply { isFillViewport=false }
        scroll.addView(content,FrameLayout.LayoutParams(-1,-2)); body.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun slider(label:String,low:Int,high:Int,current:Int,unit:String,change:(Int)->Unit) {
            val box=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(14),dp(20),dp(14)); background=shape(Color.rgb(39,42,47)) }
            val caption=text("$label  $current$unit",24,white,true)
            box.addView(caption,LinearLayout.LayoutParams(-1,dp(38)))
            box.addView(SeekBar(activity).apply {
                max=high-low; progress=current-low; contentDescription=label
                progressTintList=android.content.res.ColorStateList.valueOf(gold); thumbTintList=android.content.res.ColorStateList.valueOf(gold)
                setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar:SeekBar?,v:Int,user:Boolean) { val n=low+v; change(n); caption.text="$label  $n$unit" }
                    override fun onStartTrackingTouch(bar:SeekBar?) {}
                    override fun onStopTrackingTouch(bar:SeekBar?) {}
                })
            },LinearLayout.LayoutParams(-1,dp(44)))
            box.addView(text("$low$unit ~ $high$unit",16,gray),LinearLayout.LayoutParams(-1,dp(26)))
            content.addView(box,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) })
        }
        slider("릴렉스 시간",1,60,relax,"분") { relax=it }
        slider("각성 시간",1,60,awake,"분") { awake=it }
        slider("릴렉스 등받이",115,125,relaxSeat,"°") { relaxSeat=it }
        slider("냅핑 등받이",115,130,napSeat,"°") { napSeat=it }
        slider("각성 등받이",115,120,awakeSeat,"°") { awakeSeat=it }
        body.addView(action("설정 저장") {
            SaiPreferences.save(activity,SaiModeSettings(relax,awake,relaxSeat,napSeat,awakeSeat))
            Toast.makeText(activity,"설정을 저장했어요",Toast.LENGTH_SHORT).show(); home()
        },LinearLayout.LayoutParams(-1,dp(64)).apply { topMargin=dp(14) })
    }

    override fun onDestroyView() { dialog?.dismiss(); dialog=null; super.onDestroyView() }
}
