@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener


/** 구조화된 time 값만 해석한다. 오전/오후가 없는 1~12시 표현은 추측하지 않는다. */
object SaiScheduleTime {
    private fun number(value: String): Int? {
        value.toIntOrNull()?.let { return it }
        val native = mapOf("한" to 1,"하나" to 1,"두" to 2,"둘" to 2,"세" to 3,"셋" to 3,
            "네" to 4,"넷" to 4,"다섯" to 5,"여섯" to 6,"일곱" to 7,"여덟" to 8,
            "아홉" to 9,"열" to 10,"열한" to 11,"열하나" to 11,"열두" to 12,"열둘" to 12)
        native[value]?.let { return it }
        val digit = mapOf("영" to 0,"공" to 0,"일" to 1,"이" to 2,"삼" to 3,"사" to 4,
            "오" to 5,"육" to 6,"칠" to 7,"팔" to 8,"구" to 9)
        digit[value]?.let { return it }
        if (value.count { it=='십' }==1) {
            val parts=value.split("십")
            val tens=if (parts[0].isEmpty()) 1 else digit[parts[0]] ?: return null
            val units=if (parts[1].isEmpty()) 0 else digit[parts[1]] ?: return null
            if (tens !in 1..9 || (parts[1].isNotEmpty() && units==0)) return null
            return tens*10+units
        }
        return null
    }
    fun normalize(raw: String): String? {
        var value=raw.trim().replace(Regex("\\s+"),"").replace('：',':')
        val period=when { value.startsWith("오전") -> "오전"; value.startsWith("오후") -> "오후"; else -> "" }
        if (period.isNotEmpty()) value=value.removePrefix(period)
        var hour: Int; var minute: Int
        val colon=Regex("^([0-9]{1,2}):([0-9]{2})$").matchEntire(value)
        if (colon!=null) {
            hour=colon.groupValues[1].toInt(); minute=colon.groupValues[2].toInt()
        } else {
            val korean=Regex("^([0-9가-힣]+)시(?:(반)|([0-9가-힣]+)분)?$").matchEntire(value) ?: return null
            hour=number(korean.groupValues[1]) ?: return null
            minute=when {
                korean.groupValues[2]=="반" -> 30
                korean.groupValues[3].isNotEmpty() -> number(korean.groupValues[3]) ?: return null
                else -> 0
            }
            if (period.isEmpty() && hour in 1..12) return null
        }
        if (minute !in 0..59) return null
        if (period.isNotEmpty()) {
            if (hour !in 1..12) return null
            hour=hour%12+if (period=="오후") 12 else 0
        }
        if (hour !in 0..23) return null
        return hour.toString().padStart(2,'0')+":"+minute.toString().padStart(2,'0')
    }
}

/** Gleo -> 검증 -> 내부 JSON 파일 -> 각성 종료 안내. 외부 저장소/추가 권한이 필요 없다. */
object SaiScheduleStore {
    const val FILE_NAME = "sai_next_schedule.json"
    data class Schedule(val time: String, val title: String, val savedAtEpochMillis: Long)
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("sai_simple_test", Context.MODE_PRIVATE)
    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))
    fun validTime(time: String) = Regex("^(?:[01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(time)

    /** 성공 반환 전에 finishWrite를 완료한다. 실패 시 기존 정상 파일을 유지하고 예외를 알린다. */
    @Synchronized fun save(context: Context, time: String, title: String): Schedule {
        val cleanTime=time.trim(); val cleanTitle=title.trim().take(80)
        require(validTime(cleanTime)) { "A valid HH:mm time is required." }
        require(cleanTitle.isNotBlank()) { "A schedule title is required." }
        val normalizedTime=cleanTime.split(":").let { it[0].padStart(2,'0')+":"+it[1] }
        val schedule=Schedule(normalizedTime,cleanTitle,System.currentTimeMillis())
        val json=JSONObject().put("version",1).put("time",schedule.time)
            .put("title",schedule.title).put("savedAtEpochMillis",schedule.savedAtEpochMillis)
        val target=file(context)
        var output: FileOutputStream?=null
        try {
            val stream=target.startWrite(); output=stream
            stream.write(json.toString(2).toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (e: Exception) {
            target.failWrite(output)
            throw e
        }
        // 기존 버전에서 직접 저장한 일정은 파일로 옮긴 후 옛 키를 제거한다.
        runCatching { prefs(context).edit().remove("time").remove("title").apply() }
        Log.i("SAI_SCHEDULE","Schedule file saved")
        return schedule
    }

    @Synchronized fun load(context: Context): Schedule? {
        val text=try { file(context).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() } }
        catch (_: FileNotFoundException) { return migrateLegacy(context) }
        catch (e: Exception) { Log.e("SAI_SCHEDULE","Cannot read schedule file",e); return null }
        return try {
            val json=JSONObject(text)
            val time=json.getString("time").trim(); val title=json.getString("title").trim()
            require(json.getInt("version")==1 && validTime(time) && title.isNotBlank())
            Schedule(time,title.take(80),json.optLong("savedAtEpochMillis",0L))
        } catch (e: Exception) {
            // 손상된 파일을 가짜 기본 일정으로 덮어쓰거나 성공한 것처럼 안내하지 않는다.
            Log.e("SAI_SCHEDULE","Invalid schedule file; keeping it for inspection",e); null
        }
    }
    private fun migrateLegacy(context: Context): Schedule? {
        // 기존 NextScheduleStore와 데모의 이중 저장소를 같은 JSON으로 이관한다.
        val current = prefs(context)
        val nap = context.applicationContext.getSharedPreferences("nap_wake", Context.MODE_PRIVATE)
        val useCurrent = current.contains("time") && current.contains("title")
        val source = if (useCurrent) current else nap
        val time = source.getString(if (useCurrent) "time" else "next_time", null).orEmpty()
        val title = source.getString(if (useCurrent) "title" else "next_title", null).orEmpty().ifBlank { "일정" }
        if (!validTime(time)) return null
        return try {
            save(context, time, title).also {
                nap.edit().remove("next_time").remove("next_title").remove("next_saved_at").apply()
            }
        } catch (e: Exception) { Log.e("SAI_SCHEDULE", "Cannot migrate old schedule", e); null }
    }
    fun time(context: Context): String = load(context)?.time ?: "미설정"
    fun title(context: Context): String = load(context)?.title ?: "다음 일정 없음"
    fun speech(context: Context): String {
        val schedule=load(context) ?: return "저장된 다음 일정이 없습니다."
        val parts=schedule.time.split(":"); val h=parts[0].toInt(); val m=parts[1].toInt()
        val period=if (h<12) "오전" else "오후"
        val hour=if (h%12==0) 12 else h%12
        return "다음 일정은 $period ${hour}시${if (m==0) "" else " ${m}분"}, ${schedule.title}입니다."
    }
}


/** 홈/냅핑 전에도 일정 저장을 받고, 각성 화면이 있을 때만 화면 제어를 전달한다. */
object SaiWakeTestBridge {
    private var reference = WeakReference<SaiWakeTestFragment>(null)
    fun attach(fragment: SaiWakeTestFragment) { reference = WeakReference(fragment) }
    fun detach(fragment: SaiWakeTestFragment) { if (reference.get() === fragment) reference.clear() }
    fun validTime(time: String) = SaiScheduleStore.validTime(time)
    fun handle(context: Context, intent: Intent): Boolean {
        val action = intent.action ?: return false
        if (action !in setOf("STOP_NAP_ROUTINE", "SET_NEXT_SCHEDULE", "GET_NEXT_SCHEDULE", "CONTROL_AWAKEN_MODE")) return false
        Log.i("SAI_GLEO", "Received action=$action")
        val fragment = reference.get()?.takeIf { it.isAdded }
        if (fragment?.isSafetyStopped()==true || SaiNapLink.state == SaiNapLink.State.SAFETY_STOP) {
            val response = JSONObject().put("functionName",action).put("status","fail")
                .put("message","Vehicle safety stop is active. Close the app and park before restarting.")
                .put("mode","silent").put("data","[]")
            context.sendBroadcast(Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
                .putExtra("packageName",context.packageName).putExtra("response",response.toString()))
            return true
        }
        val params = parse(intent.getStringExtra("ACTION_PARAM"))
        fun parameter(key: String): String = (params?.takeIf { it.has(key) && !it.isNull(key) }?.optString(key) ?: intent.getStringExtra(key)).orEmpty().trim()
        var status = "success"
        var mode = "silent"
        var message: String
        val data = JSONArray()
        when(action) {
            "GET_NEXT_SCHEDULE" -> {
                fragment?.pauseForGleo()
                mode="voice"
                val saved=SaiScheduleStore.load(context)
                message=SaiScheduleStore.speech(context)
                data.put(JSONObject().put("hasSchedule",saved!=null).put("spokenPrompt",message))
                if(saved!=null) data.put(JSONObject().put("time",saved.time).put("title",saved.title))
            }
            "SET_NEXT_SCHEDULE" -> {
                fragment?.pauseForGleo()
                val rawTime=parameter("time"); val suppliedTitle=parameter("title").take(80)
                val previous=SaiScheduleStore.load(context)
                val pending=context.applicationContext.getSharedPreferences("sai_schedule_pending",Context.MODE_PRIVATE)
                val pendingAge=System.currentTimeMillis()-pending.getLong("saved_at",0L)
                val pendingValid=pendingAge in 0L..300_000L
                val pendingTime=if (pendingValid) pending.getString("time",null) else null
                val pendingTitle=if (pendingValid) pending.getString("title",null) else null
                if (!pendingValid) pending.edit().clear().apply()
                val normalized=if (rawTime.isNotBlank()) SaiScheduleTime.normalize(rawTime) else null
                val time=if (rawTime.isNotBlank()) normalized else pendingTime ?: previous?.time
                val title=suppliedTitle.ifBlank { pendingTitle.orEmpty().ifBlank { previous?.title.orEmpty() } }
                mode="voice"
                when {
                    rawTime.isBlank() && suppliedTitle.isBlank() -> {
                        status="asking"; message="Please ask the user for the next schedule time and title."
                        data.put(JSONObject().put("spokenPrompt","다음 일정의 시간과 이름을 알려주세요."))
                    }
                    time==null -> {
                        pending.edit().remove("time").putString("title",title)
                            .putLong("saved_at",System.currentTimeMillis()).apply()
                        status="asking"
                        message="Please ask the user for a valid schedule time, clarifying AM or PM when ambiguous."
                        data.put(JSONObject().put("spokenPrompt","일정 시간을 오전 또는 오후를 포함해서 다시 알려주세요."))
                    }
                    title.isBlank() -> {
                        pending.edit().putString("time",time).remove("title")
                            .putLong("saved_at",System.currentTimeMillis()).apply()
                        status="asking"; message="The time is available, but no schedule title is stored. Please ask which event the user means."
                        data.put(JSONObject().put("time",time).put("spokenPrompt","어떤 일정인지 알려주세요."))
                    }
                    else -> {
                        try {
                            val saved=SaiScheduleStore.save(context,time,title)
                            pending.edit().clear().apply()
                            runCatching { fragment?.scheduleSavedFromGleo() }
                                .onFailure { Log.e("SAI_SCHEDULE","Schedule saved; screen update failed",it) }
                            val parts=saved.time.split(":"); val hour=parts[0].toInt(); val minute=parts[1].toInt()
                            val period=if (hour<12) "오전" else "오후"
                            val hour12=if (hour%12==0) 12 else hour%12
                            val spokenConfirmation="다음 일정을 $period ${hour12}시${if (minute==0) "" else " ${minute}분"} ${saved.title}로 변경했어요."
                            message="The next schedule has been updated successfully. The Korean confirmation is: $spokenConfirmation"
                            data.put(JSONObject().put("time",saved.time).put("title",saved.title)
                                .put("spokenConfirmation",spokenConfirmation).put("titlePreserved",suppliedTitle.isBlank()))
                        } catch (e: Exception) {
                            status="fail"; message="The schedule could not be saved. The previous schedule was not replaced."
                            Log.e("SAI_SCHEDULE","Schedule action failed",e)
                        }
                    }
                }
            }
            "STOP_NAP_ROUTINE" -> {
                val awakeStopped = fragment?.forceStop() == true
                val napStopped = if (awakeStopped) false else SaiNapLink.stopFromGleo()
                val stopped = awakeStopped || napStopped
                mode = if (awakeStopped) "silent" else "voice"
                message = when {
                    awakeStopped -> "The awakening routine is ending."
                    napStopped -> "The nap timer was stopped and awakening will not start."
                    else -> "No nap timer or awakening routine is running."
                }
                data.put(JSONObject().put("stopped", stopped))
            }
            else -> {
                val command = parameter("command")
                if (command !in setOf("pause", "resume")) {
                    status = "asking"; mode = "voice"; message = "Should the awakening routine pause or resume?"
                } else {
                    val changed = fragment?.controlFromGleo(command) == true
                    if (!changed) status = "fail"
                    message = if (!changed) "No running awakening routine can accept this request." else if (command == "pause") "The awakening routine is paused." else "The awakening routine will resume when the app is visible."
                    data.put(JSONObject().put("command", command).put("accepted", changed))
                }
            }
        }
        val response = JSONObject().put("functionName", action).put("status", status).put("message", message)
            .put("mode", mode).put("data", data.toString())
        context.sendBroadcast(Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
            .putExtra("packageName", context.packageName).putExtra("response", response.toString()))
        return true
    }
    private fun parse(raw: String?): JSONObject? = try {
        when(val result = JSONTokener(raw ?: "{}").nextValue()) {
            is JSONObject -> result
            is String -> JSONObject(result)
            else -> null
        }
    } catch (_: Exception) { null }
}


/** Manifest에는 이 수신기 하나만 등록한다. */
class GleoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { SaiWakeTestBridge.handle(context, intent) }
}
