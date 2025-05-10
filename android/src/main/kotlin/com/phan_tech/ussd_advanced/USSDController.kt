package com.phan_tech.ussd_advanced

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import android.util.Log

val mapM = hashMapOf(
    "KEY_LOGIN" to listOf("espere", "waiting", "loading", "esperando"),
    "KEY_ERROR" to listOf("problema", "problem", "error", "null"))

@SuppressLint("StaticFieldLeak")
object USSDController : USSDInterface, USSDApi {
    private val TAG = "USSDController"
    
    internal const val KEY_LOGIN = "KEY_LOGIN"
    internal const val KEY_ERROR = "KEY_ERROR"

    private val simSlotName = arrayOf("extra_asus_dial_use_dualsim",
            "com.android.phone.extra.slot", "slot", "simslot", "sim_slot", "subscription",
            "Subscription", "phone", "com.android.phone.DialingMode", "simSlot", "slot_id",
            "simId", "simnum", "phone_type", "slotId", "slotIdx")

    @Volatile
    private var _context: Context? = null
    
    // Modified context property with public getter and setter
    var context: Context?
        get() = _context
        set(value) {
            Log.d(TAG, "Context updated: ${value?.javaClass?.simpleName}")
            _context = value
        }

    var map: HashMap<String, List<String>> = mapM
        private set

    lateinit var callbackInvoke: CallbackInvoke

    var callbackMessage: ((AccessibilityEvent) -> Unit)? = null
        private set

    var isRunning: Boolean? = false
        private set

    var sendType: Boolean? = false
        private set

    private var ussdInterface: USSDInterface? = null

    init {
        ussdInterface = this
    }

    @Synchronized
    fun requireContext(): Context {
        return context ?: throw IllegalStateException("USSDController context not initialized")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun callUSSDInvoke(context: Context, ussdPhoneNumber: String,
                              callbackInvoke: CallbackInvoke) {
        this.context = context
        callUSSDInvoke(context, ussdPhoneNumber, 0, callbackInvoke)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    override fun callUSSDInvoke(context: Context, ussdPhoneNumber: String, simSlot: Int,
                              callbackInvoke: CallbackInvoke) {
        sendType = false
        this.context = context
        this.callbackInvoke = callbackInvoke
        
        try {
            if (verifyAccessibilityAccess(context)) {
                dialUp(ussdPhoneNumber, simSlot)
            } else {
                this.callbackInvoke.over("Check your accessibility")
            }
        } catch (e: IllegalStateException) {
            callbackInvoke.over("Context error: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun dialUp(ussdPhoneNumber: String, simSlot: Int) {
        val ctx = requireContext()
        
        when {
            !map.containsKey(KEY_LOGIN) || !map.containsKey(KEY_ERROR) ->
                callbackInvoke.over("Bad Mapping structure")
            ussdPhoneNumber.isEmpty() -> callbackInvoke.over("Bad ussd number")
            else -> {
                val phone = Uri.encode("#")?.let {
                    ussdPhoneNumber.replace("#", it)
                }
                isRunning = true
                ctx.startActivity(getActionCallIntent(Uri.parse("tel:$phone"), simSlot))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    private fun getActionCallIntent(uri: Uri?, simSlot: Int): Intent {
        val context = requireContext()
        val telcomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return Intent(Intent.ACTION_CALL, uri).apply {
            simSlotName.map { sim -> putExtra(sim, simSlot) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("com.android.phone.force.slot", true)
            putExtra("Cdma_Supp", true)
            telcomManager?.callCapablePhoneAccounts?.let { handles ->
                if (handles.size > simSlot)
                    putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", handles[simSlot])
            }
        }
    }

    override fun sendData(text: String) = USSDServiceKT.send(text)
    override fun sendData2(text: String, event: AccessibilityEvent) = USSDServiceKT.send2(text, event)

    override fun stopRunning() {
        isRunning = false
    }

    override fun send(text: String, callbackMessage: (AccessibilityEvent) -> Unit) {
        this.callbackMessage = callbackMessage
        sendType = true
        ussdInterface?.sendData(text)
    }
    
    override fun send2(text: String, event: AccessibilityEvent, callbackMessage: (AccessibilityEvent) -> Unit) {
        this.callbackMessage = callbackMessage
        sendType = true
        ussdInterface?.sendData2(text, event)
    }

    override fun cancel() = USSDServiceKT.cancel()
    override fun cancel2(event: AccessibilityEvent) = USSDServiceKT.cancel2(event)

    interface CallbackInvoke {
        fun responseInvoke(event: AccessibilityEvent)
        fun over(message: String)
    }

    override fun verifyAccessibilityAccess(context: Context): Boolean =
            isAccessibilityServicesEnable(context).also {
                if (!it && context is Activity) openSettingsAccessibility(context)
            }

    private fun openSettingsAccessibility(activity: Activity) {
        activity.startActivityForResult(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 1)
    }

    private fun isAccessibilityServicesEnable(context: Context): Boolean {
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.apply {
            installedAccessibilityServiceList.forEach { service ->
                if (service.id.contains(context.packageName) &&
                        Settings.Secure.getInt(context.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1){
                    Settings.Secure.getString(context.applicationContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.let {
                        if (it.split(':').contains(service.id)) return true
                    }
                } else if(service.id.contains(context.packageName) && 
                          Settings.Secure.getString(context.applicationContext.contentResolver, 
                                                   Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                              .toString().contains(service.id)){
                    return true
                }
            }
        }
        return false
    }
}