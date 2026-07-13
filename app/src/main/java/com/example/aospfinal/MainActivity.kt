package com.example.aospfinal

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.aaos.cand.ICanService

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private var canService: ICanService? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val SENSOR_MIN_TEMP = 0.0   // adjust to real range
        private const val SENSOR_MAX_TEMP = 100.0 // adjust to real range
    }

    private fun sensorValueToTemperature(sensorValue: Int): Double {
        return SENSOR_MIN_TEMP + (sensorValue / 4095.0) * (SENSOR_MAX_TEMP - SENSOR_MIN_TEMP)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            canService?.getLastCanData()?.let { data ->
                val temp = sensorValueToTemperature(data.sensorValue)
//                statusText.text = "led=${data.ledStatus} mode=${data.systemMode} " +
//                        "error=${data.errorStatus} button=${data.buttonState} " +
////                        "sensor=${data.sensorValue} threshold=${data.thresholdFlag}"
//                        "temp=%.1f°C threshold=${data.thresholdFlag}".format(temp)
                statusText.text = "LED:       ${if (data.ledStatus) "ON" else "OFF"}\n" +
                        "Mode:      ${if (data.systemMode == 1) "BLINK" else "NORMAL"}\n" +
                        "Error:     ${data.errorStatus}\n" +
                        "Button:    ${if (data.buttonState) "PRESSED" else "RELEASED"}\n" +
                        "Temp:      %.1f°C\n".format(sensorValueToTemperature(data.sensorValue)) +
                        "Threshold: ${if (data.thresholdFlag) "EXCEEDED" else "OK"}"
            }
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        canService = getCanService()

        if (canService == null) {
            statusText.text = "cand service not found"
        } else {
            handler.post(pollRunnable)
        }

        findViewById<Button>(R.id.ledOnButton).setOnClickListener {
            canService?.sendLedCommand(1, 0)
        }
        findViewById<Button>(R.id.ledOffButton).setOnClickListener {
            canService?.sendLedCommand(0, 0)
        }
        findViewById<Button>(R.id.blinkButton).setOnClickListener {
            canService?.sendLedCommand(1, 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
    }

    private fun getCanService(): ICanService? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "com.aaos.cand.ICanService/default") as? IBinder
            binder?.let { ICanService.Stub.asInterface(it) }
        } catch (e: Exception) {
            Log.e("CanDemo", "Failed to get cand service", e)
            null
        }
    }
}