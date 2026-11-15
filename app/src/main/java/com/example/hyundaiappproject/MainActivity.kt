@file:Suppress("DEPRECATION")
package com.example.hyundaiappproject

import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import java.util.Locale

/** 향후 통합 홈의 Activity가 이 인터페이스를 구현하면 버튼 세 개의 목적지가 함께 바뀐다. */
interface SaiHomeHost {
    fun openSaiHome()
    fun openSaiPage(page:String, extras:Bundle?=null) { openSaiHome() }
    fun onNapLinkChanged() {}
}

object SaiNavigation {
    fun page(activity:Activity,page:String,extras:Bundle?=null) {
        SaiNapLink.cancel("navigate_"+page.lowercase(Locale.US))
        if(activity is SaiHomeHost) activity.openSaiPage(page,extras)
        else if(!activity.fragmentManager.isStateSaved) {
            val args=Bundle(extras?:Bundle()).apply { putString("page",page) }
            activity.fragmentManager.beginTransaction().replace(android.R.id.content,SaiDemoHomeFragment().apply { arguments=args },"SAI_DEMO_HOME").commit()
        }
    }
    fun home(activity: Activity) {
        SaiNapLink.cancel("navigate_home")
        if (activity is SaiHomeHost) activity.openSaiHome()
        else {
            val manager = activity.fragmentManager
            if (!manager.isStateSaved) {
                manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                manager.beginTransaction().replace(android.R.id.content, SaiDemoHomeFragment(), "SAI_DEMO_HOME").commit()
            }
        }
    }
}

class MainActivity : Activity(), SaiHomeHost {
    private var visible = false
    private var pendingHome = false
    private var pendingPage="HOME"
    private var pendingPageExtras:Bundle?=null
    private var appParkGuard: SaiParkGuard? = null
    private var vehicleBlocked = false
    private var vehicleUnavailable = false
    private var pendingSafety = false
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.SaiDemoTheme)
        super.onCreate(savedInstanceState)
        android.util.Log.i("SAI_LIFECYCLE", "create restored=${savedInstanceState!=null}")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        SaiNapLink.attach(this)
        // 홈에서 Gleo로 일정을 저장한 다음 냅핑 UI의 시간 선택으로 연결할 수 있다.
        if (savedInstanceState == null && !SaiNapLink.isRunning) pendingHome = true
        vehicleBlocked=savedInstanceState?.getBoolean("vehicle_blocked",false)?:false
        vehicleUnavailable=savedInstanceState?.getBoolean("vehicle_unavailable",false)?:false
        pendingSafety=vehicleBlocked
        appParkGuard=SaiParkGuard(this) { parked, _ ->
            if(parked!=true && !vehicleBlocked) {
                vehicleBlocked=true; vehicleUnavailable=parked==null; pendingSafety=true
                deliverAppSafety()
            }
        }.also { it.open() }
    }
    private fun deliverAppSafety() {
        if(!pendingSafety || !visible || fragmentManager.isStateSaved || isFinishing || isDestroyed) return
        pendingSafety=false; pendingHome=false
        SaiNapLink.cancel("app_vehicle_safety")
        SaiWakeTestFragment.showSafety(this,vehicleUnavailable)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("vehicle_blocked",vehicleBlocked)
        outState.putBoolean("vehicle_unavailable",vehicleUnavailable)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume(); visible = true
        if(vehicleBlocked) { pendingSafety=true; deliverAppSafety() }
        else if (pendingHome) openSaiPage(pendingPage,pendingPageExtras)
        SaiNapLink.onResume(this)
    }
    override fun onPause() { visible = false; SaiNapLink.onPause(this); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        SaiNapLink.onWindowFocusChanged(this, hasFocus)
    }
    override fun onDestroy() { appParkGuard?.close(); appParkGuard=null; android.util.Log.i("SAI_LIFECYCLE", "destroy finishing=$isFinishing changing=$isChangingConfigurations"); SaiNapLink.detach(this); super.onDestroy() }
    override fun onBackPressed() {
        val mode = fragmentManager.findFragmentByTag(SaiWakeTestFragment.TAG) as? SaiWakeTestFragment
        val rest = fragmentManager.findFragmentByTag("SAI_REST") as? SaiRestFragment
        val home = fragmentManager.findFragmentByTag("SAI_DEMO_HOME") as? SaiDemoHomeFragment
        if(vehicleBlocked) finish()
        else if (rest != null) rest.navigateBack()
        else if (mode != null) mode.navigateBack()
        else if (home != null) home.back()
        else if (SaiNapLink.isRunning || fragmentManager.findFragmentByTag("SAI_RITUAL_RECORD")!=null) SaiNavigation.home(this)
        else super.onBackPressed()
    }

    /** 냅핑 모드의 기존 시간 설정 완료 콜백에서 호출한다. 냅핑 화면 자체는 만들지 않는다. */
    fun startNap(minutes: Int, onNapEnd: (String) -> Unit = {}): Boolean =
        SaiNapLink.startNap(this, minutes, onNapEnd)

    override fun openSaiHome() { openSaiPage("HOME",null) }
    override fun openSaiPage(page:String,extras:Bundle?) {
        pendingPage=page; pendingPageExtras=extras?.let { Bundle(it) }
        if(vehicleBlocked) { pendingSafety=true; deliverAppSafety(); return }
        if (!visible || fragmentManager.isStateSaved) { pendingHome = true; return }
        pendingHome = false
        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        // 통합 시 이 replace 목적지만 통합 HomeFragment로 교체한다.
        val args=Bundle(extras?:Bundle()).apply { putString("page",page) }
        fragmentManager.beginTransaction().replace(android.R.id.content, SaiDemoHomeFragment().apply { arguments=args }, "SAI_DEMO_HOME").commit()
    }
    override fun onNapLinkChanged() {
        (fragmentManager.findFragmentByTag("SAI_REST") as? SaiRestFragment)?.refreshNap()
    }
}

/** 통합 홈이 준비되기 전의 기존 데모 홈. 새 냅핑 UI는 추가하지 않는다. */
