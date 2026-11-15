package com.example.hyundaiappproject

// ─────────────────────────────────────────────
// [공용] CarControl.kt  ─ 차량 설정 바꾸기 / 원래대로 되돌리기
// 바꾸기 전 값을 기억해 두었다가 restore()에서 되돌려요.
// ─────────────────────────────────────────────
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CarControl(
    private val context: Context,
    private val vehicle: Vehicle,
    private val report: (String) -> Unit      // 실패 메시지를 화면 아래 작은 글씨로 보여줘요
) {
    private val savedTemp = ConcurrentHashMap<SeatArea, Float>()
    private val savedRecirc = ConcurrentHashMap<SeatArea, Boolean>()
    private val savedAc = ConcurrentHashMap<SeatArea, Boolean>()
    private val savedColor = ConcurrentHashMap<SeatArea, Int>()
    private val savedBrightness = ConcurrentHashMap<SeatArea, Int>()
    @Volatile private var brightnessGeneration = 0
    private val savedFan = ConcurrentHashMap<SeatArea, Int>()
    private val savedSeat = ConcurrentHashMap<String, Pair<SeatArea, Number>>()
    private val seatLock=Any()
    @Volatile private var seatGeneration=0

    private val ok: (Any?, Any?) -> Unit = { _, _ -> }
    private val okLight: (LightSwitch?) -> Unit = { }        // 조명 스위치는 결과가 1개로 와요
    private fun fail(what: String): (Exception) -> Unit = { e -> report("$what 실패: ${e.message}") }

    // ── 공조 ──────────────────────────────
    fun setTemperature(celsius: Float) {
        val hvac = vehicle.getHVAC()
        safe("온도") {
            hvac.checkHvacTemperatureCapabilities({ _, areas ->
                areas.forEach { (area, bounds) ->
                    val low = bounds.minValue ?: return@forEach
                    val high = bounds.maxValue ?: return@forEach
                    hvac.getHvacTemperature(area, TemperatureUnit.CELSIUS, { now ->
                        if (now != null) savedTemp.putIfAbsent(area, now)
                        hvac.setHvacTemperature(area, celsius.coerceIn(low, high), TemperatureUnit.CELSIUS, ok, fail("온도"))
                    }, fail("온도"))
                }
            }, fail("온도"))
        }
    }

    // on = true 내부 순환 / false 외기 유입
    fun setRecirculation(on: Boolean) {
        val hvac = vehicle.getHVAC()
        safe("순환") {
            hvac.checkHvacRecirculationCapabilities({ _, areas ->
                areas.forEach { area ->
                    hvac.getHvacRecirculationState(area, { now ->
                        if (now != null) savedRecirc.putIfAbsent(area, now)
                        hvac.setHvacRecirculation(area, on, ok, fail("순환"))
                    }, fail("순환"))
                }
            }, fail("순환"))
        }
    }

    fun setAc(on: Boolean) {
        val hvac = vehicle.getHVAC()
        safe("에어컨") {
            hvac.checkHvacAcCapabilities({ _, areas ->
                areas.forEach { area ->
                    hvac.getHvacAcState(area, { now ->
                        if (now != null) savedAc.putIfAbsent(area, now)
                        hvac.setHvacAc(area, on, ok, fail("에어컨"))
                    }, fail("에어컨"))
                }
            }, fail("에어컨"))
        }
    }

    // 바람 세기를 가장 약하게 (꺼짐 바로 위 단계)
    fun setFanLow() = setFanLevel(0f)

    // 바람 세기: 0f = 가장 약하게(꺼짐 바로 위) ~ 1f = 가장 세게
    fun setFanLevel(strength: Float) {
        val hvac = vehicle.getHVAC()
        safe("바람") {
            hvac.checkHvacFanSpeedCapabilities({ _, areas ->
                areas.forEach { (area, bounds) ->
                    val low = bounds.minValue ?: return@forEach
                    val high = bounds.maxValue ?: return@forEach
                    val target = (low + 1 + ((high - low - 1) * strength.coerceIn(0f, 1f)).toInt()).coerceIn(low, high)
                    hvac.getHvacFanSpeed(area, { now ->
                        if (now != null) savedFan.putIfAbsent(area, now)
                        hvac.setHvacFanSpeed(area, target, ok, fail("바람"))
                    }, fail("바람"))
                }
            }, fail("바람"))
        }
    }

    // ── 조명 ──────────────────────────────
    // 무드등 색 (2500K ≈ Color.rgb(255, 159, 70))
    fun setAmbientColor(color: Int) {
        val light = vehicle.getLight()
        safe("무드등") {
            light.checkAmbientLightBrightnessColorCapabilities({ _, areas ->
                areas.forEach { area ->
                    light.getAmbientLightBrightnessColor(area, { now ->
                        if (now != null) savedColor.putIfAbsent(area, now)
                        light.setAmbientLightBrightnessColor(area, color, ok, fail("무드등"))
                    }, fail("무드등"))
                }
            }, fail("무드등"))
        }
    }

    // 실내 조명(독서등·실내등) + 외부 조명 끄기
    /** Set and restore ambient brightness using the SDK capability's actual bounds. */
    fun setAmbientBrightness(percent: Int) {
        val token=brightnessGeneration
        val light=vehicle.getLight()
        safe("조명 밝기") {
            light.checkAmbientLightBrightnessCapabilities({ _,areas ->
                if(token==brightnessGeneration) areas.forEach { (area,bounds) ->
                    val low=bounds.minValue ?: return@forEach
                    val high=bounds.maxValue ?: return@forEach
                    val target=(low+(high-low)*(percent.coerceIn(0,100)/100f)).toInt().coerceIn(low,high)
                    light.getAmbientLightBrightness(area,{ now ->
                        if(token==brightnessGeneration) {
                            if(now!=null) savedBrightness.putIfAbsent(area,now)
                            light.setAmbientLightBrightness(area,target,ok,fail("조명 밝기"))
                        }
                    },fail("조명 밝기"))
                }
            },fail("조명 밝기"))
        }
    }

    fun lightsOff() {
        val light = vehicle.getLight()
        safe("실내 조명") {
            val found = Sdk.call(light, "checkReadingLightSwitchCapabilities", { _: Any?, areas: Any? ->
                Sdk.areas(areas).forEach { area ->
                    safe("실내 조명") { light.setReadingLightSwitch(area, LightSwitch.OFF, okLight, fail("실내 조명")) }
                }
            }, fail("실내 조명"))
            if (!found) light.setReadingLightSwitch(SeatArea(SeatValue.ROW_1_LEFT), LightSwitch.OFF, okLight, fail("실내 조명"))
        }
        // 이름을 확인하지 못한 함수들: 있으면 부르고, 없으면 건너뛰어요
        listOf(
            "setCabinLightSwitch", "setDomeLightSwitch", "setRoomLightSwitch",
            "setLowBeamLightSwitch", "setHighBeamLightSwitch",
            "setFrontFogLightSwitch", "setRearFogLightSwitch", "setTailLampLightSwitch"
        ).forEach { name ->
            safe("조명 끄기") { Sdk.call(light, name, LightSwitch.OFF, okLight, fail("조명 끄기")) }
        }
    }

    // ── 시트 ──────────────────────────────
    // 운전석 등받이 각도 (도). 차가 각도를 0~1 비율로 받으면 90°~130°를 비율로 바꿔요.
    fun moveSeatBackrest(degrees: Double) = moveSeat("SeatBackrestAngle") { low, high ->
        if (high >= 115) degrees.coerceIn(low, high)
        else low + (high - low) * ((degrees - 90) / 40).coerceIn(0.0, 1.0)
    }

    // 지금 등받이 각도(도) 읽기. 못 읽으면 null. 결과는 화면 쓰레드에서 와요.
    fun readSeatBackrestDegrees(done: (Double?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        var answered = false
        val answer: (Double?) -> Unit = { value ->
            main.post {
                if (!answered) {
                    answered = true
                    done(value)
                }
            }
        }
        val seat = vehicle.getSeat()
        try {
            val found = Sdk.call(seat, "checkSeatBackrestAngleCapabilities", { _: Any?, areas: Any? ->
                val target = Sdk.areaBounds(areas).firstOrNull { (area, _, _) -> SeatValue.ROW_1_LEFT in area.values }
                if (target == null) {
                    answer(null)
                } else {
                    val (area, low, high) = target
                    val canRead = Sdk.call(seat, "getCurrentSeatBackrestAngle", area, { now: Any? ->
                        val n = (now as? Number)?.toDouble()
                        answer(
                            when {
                                n == null -> null
                                high >= 115 -> n                                   // 이미 '도' 단위
                                high > low -> 90 + (n - low) / (high - low) * 40  // 비율 → 90~130도
                                else -> null
                            }
                        )
                    }, { _: Any? -> answer(null) })
                    if (!canRead) answer(null)
                }
            }, { _: Any? -> answer(null) })
            if (!found) answer(null)
        } catch (e: Exception) {
            answer(null)
        }
        main.postDelayed({ answer(null) }, 1500)   // 1.5초 안에 답이 없으면 포기
    }

    // 요추 지지대 앞으로 (가장 앞)
    fun moveLumbarForward() = moveSeat("SeatLumbarForeAft") { _, high -> high }

    private fun moveSeat(part: String, pick: (Double, Double) -> Double) {
        val token=seatGeneration
        val seat = vehicle.getSeat()
        safe("시트") {
            val found = Sdk.call(seat, "check${part}Capabilities", { _: Any?, areas: Any? ->
                Sdk.areaBounds(areas)
                    .filter { (area, _, _) -> SeatValue.ROW_1_LEFT in area.values }
                    .forEach { (area, low, high) ->
                        val target = pick(low, high)
                        val move = { synchronized(seatLock) {
                            if(token==seatGeneration) safe("시트") { Sdk.call(seat, "move$part", area, target, ok, fail("시트")) }
                        } }
                        val canRead = Sdk.call(seat, "getCurrent$part", area, { now: Any? ->
                            synchronized(seatLock) {
                                if(token==seatGeneration) {
                                    if (now is Number) savedSeat.putIfAbsent(part, area to now)
                                    move()
                                }
                            }
                        }, fail("시트"))
                        if (!canRead) move()
                    }
            }, fail("시트"))
            if (!found) report("이 차는 시트 조절($part)을 지원하지 않아요")
        }
    }

    // ── 외부 온도 ─────────────────────────
    // 1) Pleos SDK에 외부 온도 함수가 있으면 그걸, 2) 없으면 안드로이드 차량 기본 기능으로 읽어요.
    // 못 읽으면 null. 결과는 화면 쓰레드에서 done으로 와요.
    fun readOutsideTemp(done: (Float?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            val value = pleosOutsideTemp() ?: androidCarOutsideTemp()
            main.post { done(value) }
        }.start()
    }

    private fun pleosOutsideTemp(): Float? {
        val sources = listOfNotNull<Any>(
            runCatching { vehicle.getHVAC() as Any }.getOrNull(),
            runCatching {
                vehicle.javaClass.methods.firstOrNull { it.name == "getCarInfo" && it.parameterTypes.isEmpty() }?.invoke(vehicle)
            }.getOrNull()
        )
        val pattern = Regex("(?i)^get.*(outside|exterior|outdoor).*temp.*")
        for (source in sources) {
            val method = source.javaClass.methods.firstOrNull {
                pattern.matches(it.name) && it.parameterTypes.size in 2..3
            } ?: continue
            val latch = CountDownLatch(1)
            var result: Float? = null
            val onValue = { v: Any? -> result = (v as? Number)?.toFloat(); latch.countDown() }
            val onFail = { _: Any? -> latch.countDown() }
            try {
                if (method.parameterTypes.size == 2) method.invoke(source, onValue, onFail)
                else method.invoke(source, TemperatureUnit.CELSIUS, onValue, onFail)
            } catch (e: Exception) {
                continue
            }
            latch.await(2, TimeUnit.SECONDS)
            val found = result
            if (found != null) return found
        }
        return null
    }

    private fun androidCarOutsideTemp(): Float? {
        return try {
            val carClass = Class.forName("android.car.Car")
            val car = carClass.getMethod("createCar", Context::class.java).invoke(null, context)
            val manager = carClass.getMethod("getCarManager", String::class.java).invoke(car, "property")
                ?: return null
            val value = manager.javaClass
                .getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(manager, 0x11600703, 0) as? Float      // ENV_OUTSIDE_TEMPERATURE
            runCatching { carClass.getMethod("disconnect").invoke(car) }
            value
        } catch (e: Throwable) {
            null
        }
    }

    // ── 되돌리기 ──────────────────────────
    // keepSeat = true 이면 시트는 지금 자세 그대로 두고 나머지만 되돌려요.
    fun restore(keepSeat: Boolean = false) {
        synchronized(seatLock) { seatGeneration++ }
        brightnessGeneration++
        val hvac = vehicle.getHVAC()
        val light = vehicle.getLight()
        savedTemp.forEach { (area, v) -> safe("온도 복원") { hvac.setHvacTemperature(area, v, TemperatureUnit.CELSIUS, ok, fail("온도 복원")) } }
        savedRecirc.forEach { (area, v) -> safe("순환 복원") { hvac.setHvacRecirculation(area, v, ok, fail("순환 복원")) } }
        savedAc.forEach { (area, v) -> safe("에어컨 복원") { hvac.setHvacAc(area, v, ok, fail("에어컨 복원")) } }
        savedFan.forEach { (area, v) -> safe("바람 복원") { hvac.setHvacFanSpeed(area, v, ok, fail("바람 복원")) } }
        savedColor.forEach { (area, v) -> safe("무드등 복원") { light.setAmbientLightBrightnessColor(area, v, ok, fail("무드등 복원")) } }
        savedBrightness.forEach { (area,v) -> safe("밝기 복원") { light.setAmbientLightBrightness(area,v,ok,fail("밝기 복원")) } }
        savedBrightness.clear()
        if (!keepSeat) {
            savedSeat.forEach { (part, saved) ->
                safe("시트 복원") { Sdk.call(vehicle.getSeat(), "move$part", saved.first, saved.second, ok, fail("시트 복원")) }
            }
        }
        savedTemp.clear(); savedRecirc.clear(); savedAc.clear(); savedColor.clear(); savedFan.clear(); savedSeat.clear()
    }

    private fun safe(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            report("$what 실패: ${e.cause?.message ?: e.message}")
        }
    }
}

// SDK 함수를 이름으로 찾아 부르는 도우미 (함수가 없으면 false)
object Sdk {
    fun call(target: Any, name: String, vararg args: Any?): Boolean {
        val method = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == args.size
        } ?: return false
        val fixed = Array(args.size) { i -> fit(args[i], method.parameterTypes[i]) }
        method.invoke(target, *fixed)
        return true
    }

    fun areas(value: Any?): List<SeatArea> = when (value) {
        is Map<*, *> -> value.keys.filterIsInstance<SeatArea>()
        is Collection<*> -> value.filterIsInstance<SeatArea>()
        else -> emptyList()
    }

    fun areaBounds(areas: Any?): List<Triple<SeatArea, Double, Double>> {
        if (areas !is Map<*, *>) return emptyList()
        return areas.mapNotNull { (key, value) ->
            val area = key as? SeatArea ?: return@mapNotNull null
            val bounds = value as? AreaBounds<*> ?: return@mapNotNull null
            val low = (bounds.minValue as? Number)?.toDouble() ?: return@mapNotNull null
            val high = (bounds.maxValue as? Number)?.toDouble() ?: return@mapNotNull null
            Triple(area, low, high)
        }
    }

    private fun fit(value: Any?, type: Class<*>): Any? = when {
        value !is Number -> value
        type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> value.toInt()
        type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType -> value.toFloat()
        type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType -> value.toDouble()
        else -> value
    }
}
