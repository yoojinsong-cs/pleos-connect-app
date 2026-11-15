package com.example.hyundaiappproject

import android.content.Context

/** Immutable per-session settings; changing preferences never changes a running timer. */
data class SaiModeSettings(
    val relaxMinutes:Int=5,
    val awakenMinutes:Int=3,
    val relaxAngle:Int=120,
    val napAngle:Int=130,
    val awakenAngle:Int=115
) {
    init {
        require(relaxMinutes in 1..60 && awakenMinutes in 1..60)
        require(relaxAngle in 115..125 && napAngle in 115..130 && awakenAngle in 115..120)
    }
    val relaxSeconds get()=relaxMinutes*60
    val awakenSeconds get()=awakenMinutes*60
}

object SaiPreferences {
    private fun prefs(context:Context)=context.applicationContext.getSharedPreferences("sai_mode_settings",Context.MODE_PRIVATE)
    fun load(context:Context):SaiModeSettings {
        val p=prefs(context)
        return SaiModeSettings(p.getInt("relax_minutes",5).coerceIn(1,60),p.getInt("awaken_minutes",3).coerceIn(1,60),
            p.getInt("relax_angle",120).coerceIn(115,125),p.getInt("nap_angle",130).coerceIn(115,130),p.getInt("awaken_angle",115).coerceIn(115,120))
    }
    fun save(context:Context,value:SaiModeSettings) {
        prefs(context).edit().putInt("relax_minutes",value.relaxMinutes).putInt("awaken_minutes",value.awakenMinutes)
            .putInt("relax_angle",value.relaxAngle).putInt("nap_angle",value.napAngle).putInt("awaken_angle",value.awakenAngle).apply()
    }
}
