package com.example.aospfinal

import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.aaos.cand.CanData
import com.aaos.cand.ICanService
import com.aaos.cand.UdsResult

class CanServiceClient {
    private var service: ICanService? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect(): Boolean {
        service = try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "com.aaos.cand.ICanService/default") as? IBinder
            binder?.let { ICanService.Stub.asInterface(it) }
        } catch (e: Exception) {
            Log.e("CanDemo", "Failed to get cand service", e)
            null
        }
        return service != null
    }

    fun getLastCanData(onResult: (CanData?) -> Unit) {
        val s = service
        Thread {
            val result = try {
                s?.getLastCanData()
            } catch (e: Exception) {
                Log.e("CanDemo", "getLastCanData failed", e)
                null
            }
            mainHandler.post { onResult(result) }
        }.start()
    }

    fun sendLedCommand(led: Int, mode: Int) {
        val s = service
        Thread {
            try {
                s?.sendLedCommand(led, mode)
            } catch (e: Exception) {
                Log.e("CanDemo", "sendLedCommand failed", e)
            }
        }.start()
    }

    fun callUds(
        label: String,
        call: (ICanService) -> UdsResult,
        onResult: (UdsResult?) -> Unit
    ) {
        val s = service
        if (s == null) {
            onResult(null)
            return
        }
        Thread {
            val result = try {
                call(s)
            } catch (e: Exception) {
                Log.e("CanDemo", "$label failed", e)
                null
            }
            mainHandler.post { onResult(result) }
        }.start()
    }
}