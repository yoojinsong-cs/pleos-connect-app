@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** 같은 sessionId는 DB UNIQUE 제약으로 한 번만 집계. 완료 날짜는 기기 현지 시간 기준. */
object SaiRitualRecord {
    enum class Mode { RELAX, NAP, AWAKEN, NAP_AWAKEN }
    data class Week(val count: Int, val days: List<Boolean>)
    fun newSessionId(): String = UUID.randomUUID().toString()
    private class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "sai_ritual_records.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE completions (session_id TEXT PRIMARY KEY, mode TEXT NOT NULL, local_day TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
    private fun day(calendar: Calendar) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    /** 정상 완료 분기에서만 호출. 강제 종료/기어 변경/뒤로 가기에서는 호출하지 않는다. */
    @Synchronized fun complete(context: Context, mode: Mode, sessionId: String): Boolean {
        require(sessionId.isNotBlank())
        val helper = Db(context)
        return try {
            helper.writableDatabase.insertWithOnConflict("completions", null, ContentValues().apply {
                put("session_id", sessionId); put("mode", mode.name); put("local_day", day(Calendar.getInstance()))
            }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        } finally { helper.close() }
    }
    @Synchronized fun week(context: Context, mode: Mode): Week {
        val monday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }
        val dates = (0..6).map { offset -> day((monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }) }
        val helper = Db(context)
        try {
            val days = mutableSetOf<String>(); var count = 0
            helper.readableDatabase.rawQuery("SELECT local_day, COUNT(*) FROM completions WHERE mode=? AND local_day>=? AND local_day<=? GROUP BY local_day",
                arrayOf(mode.name, dates.first(), dates.last())).use { cursor ->
                while (cursor.moveToNext()) { days.add(cursor.getString(0)); count += cursor.getInt(1) }
            }
            return Week(count, dates.map { it in days })
        } finally { helper.close() }
    }
    fun show(activity: Activity, mode: Mode, standalone: Boolean = false) {
        if (activity.isFinishing || activity.isDestroyed || activity.fragmentManager.isStateSaved) return
        activity.fragmentManager.beginTransaction().replace(android.R.id.content,
            SaiRitualRecordFragment().apply { arguments = Bundle().apply { putString("mode", mode.name); putBoolean("standalone",standalone) } }, "SAI_RITUAL_RECORD").commit()
    }
}

/** 통합 홈과도 동일한 SaiNavigation.home 경로를 사용한다. */
class SaiRitualRecordFragment : Fragment() {
    private var guard: SaiParkGuard? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = activity ?: error("Activity 연결 없음")
        val mode = SaiRitualRecord.Mode.valueOf(arguments?.getString("mode") ?: "NAP_AWAKEN")
        val week = SaiRitualRecord.week(context, mode)
        fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
        val white = Color.rgb(246,246,247); val accent = Color.rgb(220,198,171)
        fun shape(fill: Int, stroke: Boolean = false) = GradientDrawable().apply {
            setColor(fill); cornerRadius = dp(20).toFloat(); if (stroke) setStroke(dp(1), accent)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(28), dp(28), dp(28)); setBackgroundColor(Color.rgb(27,28,32))
        }
        fun label(value: String, size: Float, color: Int, top: Int = 0) = TextView(context).also {
            it.text = value; it.textSize = size; it.setTextColor(color); it.gravity = android.view.Gravity.CENTER
            content.addView(it, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(top) })
        }
        label("RITUAL RECORD",22f,accent)
        label(if (week.count>0) "이번 주도 벌써 ${week.count}번째군요" else "이번 주 리츄얼 ${week.count}회",36f,white,16).setTypeface(null,Typeface.BOLD)
        label(if (mode == SaiRitualRecord.Mode.RELAX) "릴렉스 · 이번 주 리츄얼을 한 날이 밝게 표시돼요." else if (mode == SaiRitualRecord.Mode.NAP) "냅핑 · 이번 주 리츄얼" else "각성 · 이번 주 리츄얼",22f,Color.LTGRAY,12)
        content.addView(View(context),LinearLayout.LayoutParams(1,0,1f))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("월","화","수","목","금","토","일").forEachIndexed { i, name ->
            row.addView(TextView(context).apply {
                text = name + "\n" + if (week.days[i]) "✓" else "·"
                textSize = 24f; gravity = android.view.Gravity.CENTER
                setTextColor(if (week.days[i]) white else Color.GRAY)
                background = shape(if (week.days[i]) Color.rgb(85,79,72) else Color.rgb(39,41,46),week.days[i])
                setPadding(dp(4),dp(16),dp(4),dp(16))
            },LinearLayout.LayoutParams(0,-2,1f).apply { if (i>0) leftMargin = dp(10) })
        }
        content.addView(row,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(28) })
        label(if (arguments?.getBoolean("standalone")==true) "각성 단독 실행도 정상 완료하면 기록돼요." else "정상 완료한 리츄얼만 기록돼요.",20f,Color.GRAY,24)
        content.addView(Button(context).apply {
            text="홈으로 돌아가기"; textSize=28f; isAllCaps=false; setTextColor(Color.rgb(27,28,32))
            background=shape(white); setOnClickListener { SaiNavigation.home(context) }
        },LinearLayout.LayoutParams(dp(340),dp(72)).apply { topMargin=dp(24); bottomMargin=dp(28) })
        // 작은 화면은 스크롤 가능, 넓은 화면은 이미지처럼 아래쪽에 요일 표시.
        return ScrollView(context).apply { isFillViewport=true; addView(content,ViewGroup.LayoutParams(-1,-2)) }
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view,state)
        activity?.let { host -> guard = SaiParkGuard(host) { parked, _ ->
            if (parked != true && isAdded) SaiWakeTestFragment.showSafety(host,parked==null)
        }.also { it.open() } }
    }
    override fun onDestroyView() { guard?.close(); guard=null; super.onDestroyView() }
}
