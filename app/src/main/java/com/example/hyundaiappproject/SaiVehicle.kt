@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * AAOS 공개 API의 GEAR_SELECTION을 읽고 변경을 구독한다. 차량 제어는 하지 않는다.
 * android.car는 기기의 공유 라이브러리에서 로드한다. Gradle/SDK 의존성 추가 불필요.
 * null=확인 불가, true=P단, false=P단 아님. 확인 전에는 시작을 허용하지 않는다.
 */
class SaiParkGuard(context: Context, private val changed: (Boolean?, String) -> Unit) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var car: Any? = null
    private var manager: Any? = null
    private var callback: Any? = null
    private var callbackType: Class<*>? = null
    private var propertyId = 0
    private var parkValue = 0
    private var availableStatus = 0
    private var closed = false
    private val revision = AtomicInteger(0)
    private val timeout = Runnable { if (!closed && revision.get()==0) emit(null,"차량 P단을 확인할 수 없어요. 차량 연결을 확인해 주세요.") }

    fun open() {
        if (closed || car != null) return
        main.postDelayed(timeout, 5000L)
        try {
            val carType = Class.forName("android.car.Car")
            val lifecycleType = Class.forName("android.car.Car\$CarServiceLifecycleListener")
            propertyId = Class.forName("android.car.VehiclePropertyIds").getField("GEAR_SELECTION").getInt(null)
            parkValue = Class.forName("android.car.VehicleGear").getField("GEAR_PARK").getInt(null)
            availableStatus = Class.forName("android.car.hardware.CarPropertyValue").getField("STATUS_AVAILABLE").getInt(null)
            val listener = proxy(lifecycleType) { name, args ->
                if (name == "onLifecycleChanged") {
                    val instance = args?.getOrNull(0)
                    val ready = args?.getOrNull(1) == true
                    main.post {
                        if (!closed) {
                            if (ready && instance != null) { car = instance; subscribe(carType,instance) }
                            else { revision.incrementAndGet(); unsubscribe(); emit(null,"차량 연결이 끊겼어요.") }
                        }
                    }
                }
            }
            val wait = carType.getField("CAR_WAIT_TIMEOUT_DO_NOT_WAIT").getLong(null)
            car = carType.getMethod("createCar", Context::class.java, Handler::class.java, java.lang.Long.TYPE, lifecycleType)
                .invoke(null, context, main, wait, listener)
        } catch (e: Exception) { fail(e) }
        catch (e: LinkageError) { fail(e) }
    }
    private fun subscribe(carType: Class<*>, instance: Any) {
        try {
            unsubscribe()
            val service = carType.getField("PROPERTY_SERVICE").get(null) as String
            val propertyManager = carType.getMethod("getCarManager",String::class.java).invoke(instance,service)
                ?: error("CarPropertyManager 없음")
            manager = propertyManager
            val type = Class.forName("android.car.hardware.property.CarPropertyManager")
            val cbType = Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback")
            callbackType = cbType
            lateinit var cb: Any
            cb = proxy(cbType) { name, args ->
                when(name) {
                    "onChangeEvent" -> {
                        val value = args?.getOrNull(0)
                        // 동기 초기 조회보다 새 변경 알림이 항상 우선한다.
                        revision.incrementAndGet()
                        // P→D→P가 짧게 연속해도 D 이벤트를 버리지 않는다.
                        main.post { if (!closed && callback === cb) consume(value) }
                    }
                    "onErrorEvent" -> {
                        revision.incrementAndGet()
                        main.post { if (!closed && callback === cb) emit(null,"차량 기어 정보를 읽을 수 없어요.") }
                    }
                }
            }
            callback = cb
            val rate = type.getField("SENSOR_RATE_ONCHANGE").getFloat(null)
            val registered = type.getMethod("registerCallback",cbType,Integer.TYPE,java.lang.Float.TYPE)
                .invoke(propertyManager,cb,propertyId,rate) as Boolean
            if (!registered) { unsubscribe(); emit(null,"차량이 기어 변경 알림을 지원하지 않아요."); return }
            val token = revision.get()
            worker.execute {
                try {
                    val initial = type.getMethod("getProperty",Integer.TYPE,Integer.TYPE).invoke(propertyManager,propertyId,0)
                    main.post { if (!closed && manager===propertyManager && token==revision.get()) consume(initial) }
                } catch (e: Exception) {
                    main.post { if (!closed && manager===propertyManager && token==revision.get()) fail(e) }
                }
            }
        } catch (e: Exception) { fail(e) }
    }
    private fun consume(value: Any?) {
        try {
            if (value == null) { emit(null,"차량 기어 정보가 없어요."); return }
            val type = value.javaClass
            val id = type.getMethod("getPropertyId").invoke(value) as Int
            if (id != propertyId) return
            val status = type.getMethod("getStatus").invoke(value) as Int
            if (status != availableStatus) { emit(null,"차량 기어 정보가 일시적으로 없어요."); return }
            val gear = type.getMethod("getValue").invoke(value) as? Int
            val unknown = Class.forName("android.car.VehicleGear").getField("GEAR_UNKNOWN").getInt(null)
            if (gear==null || gear==unknown) emit(null,"차량 기어 상태를 확인할 수 없어요.")
            else emit(gear==parkValue,if (gear==parkValue) "P단 확인됨" else "P단이 해제되었어요.")
        } catch (e: Exception) { fail(e) }
    }
    private fun proxy(type: Class<*>, call: (String,Array<out Any?>?) -> Unit): Any =
        Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { self, method, args ->
            when(method.name) {
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.getOrNull(0)
                "toString" -> "SaiParkGuardCallback"
                else -> { call(method.name,args); null }
            }
        }
    private fun emit(parked: Boolean?, message: String) {
        if (closed) return
        main.removeCallbacks(timeout)
        changed(parked,message)
    }
    private fun fail(error: Throwable) {
        Log.e("SAI_PARK_GUARD","Gear read/subscription failed",error)
        emit(null,"P단 확인 실패 · 차량 연결과 CAR_POWERTRAIN 권한을 확인해 주세요.")
    }
    private fun unsubscribe() {
        val m = manager; val cb = callback; val cbType = callbackType
        callback = null; manager = null
        if (m!=null && cb!=null && cbType!=null) try {
            m.javaClass.getMethod("unregisterCallback",cbType,Integer.TYPE).invoke(m,cb,propertyId)
        } catch (e: Exception) { Log.w("SAI_PARK_GUARD","unsubscribe failed",e) }
    }
    fun close() {
        if (closed) return
        closed = true; revision.incrementAndGet(); main.removeCallbacksAndMessages(null)
        unsubscribe()
        car?.let { try { it.javaClass.getMethod("disconnect").invoke(it) } catch (e: Exception) { Log.w("SAI_PARK_GUARD","disconnect failed",e) } }
        car = null; worker.shutdownNow()
    }
}
