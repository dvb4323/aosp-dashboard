package com.example.aospfinal

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aaos.cand.ICanService
import com.aaos.cand.UdsResult

class MainActivity : Activity() {
    private lateinit var connectionStatus: TextView
    private lateinit var carIndicator: android.view.View
    private lateinit var tempValue: TextView
    private lateinit var tempProgressBar: ProgressBar
    private lateinit var thresholdLabel: TextView
    private lateinit var ledStatusValue: TextView
    private lateinit var modeValue: TextView
    private lateinit var buttonValue: TextView
    private lateinit var errorValue: TextView
    private lateinit var vinValue: TextView
    private lateinit var readDebugText: TextView
    private lateinit var writeDebugText: TextView

    private var canService: ICanService? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isFirstPoll = true
    private var lastThresholdFlag = false
    private var carBlinkAnimator: ObjectAnimator? = null

    private var currentLed = false
    private var currentMode = 0

    private val readLines = LinkedHashMap<String, String>()
    private val writeLines = LinkedHashMap<String, String>()

    companion object {
        private const val SENSOR_MIN_TEMP = 0.0
        private const val SENSOR_MAX_TEMP = 100.0
        private const val SENSOR_THRESHOLD_RAW = 2048
    }

    private fun sensorValueToTemperature(sensorValue: Int): Double {
        return SENSOR_MIN_TEMP + (sensorValue / 4095.0) * (SENSOR_MAX_TEMP - SENSOR_MIN_TEMP)
    }

    private fun refreshReadDebug() {
        readDebugText.text = if (readLines.isEmpty()) "No frames yet" else readLines.values.joinToString("\n")
    }

    private fun refreshWriteDebug() {
        writeDebugText.text = if (writeLines.isEmpty()) "No frames yet" else writeLines.values.joinToString("\n")
    }

    private fun formatReadBroadcastLines(led: Boolean, mode: Int, error: Int, button: Boolean, sensor: Int, threshold: Boolean) {
        val b0 = if (led) 1 else 0
        val b3 = if (button) 1 else 0
        readLines["0x100"] = "RX 0x100: %02X %02X %02X %02X 00 00 00 00".format(b0, mode, error, b3)
        val hi = (sensor shr 8) and 0xFF
        val lo = sensor and 0xFF
        val thFlag = if (threshold) 1 else 0
        readLines["0x101"] = "RX 0x101: %02X %02X %02X 00 00 00 00 00".format(hi, lo, thFlag)
    }

    private fun formatWriteCommandLine(led: Int, mode: Int) {
        writeLines["Command"] = "TX 0x200: %02X %02X 00 00 00 00 00 00".format(led, mode)
    }

    private fun setConnected(connected: Boolean) {
        if (connected) {
            connectionStatus.text = "Connected"
            connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.led_on_color))
        } else {
            connectionStatus.text = "Disconnected"
            connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.car_alert_color))
        }
    }

    private fun startCarAlert() {
        carIndicator.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.car_alert_color))
        if (carBlinkAnimator == null) {
            carBlinkAnimator = ObjectAnimator.ofFloat(carIndicator, "alpha", 1f, 0.3f).apply {
                duration = 400
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    private fun stopCarAlert() {
        carBlinkAnimator?.cancel()
        carBlinkAnimator = null
        carIndicator.alpha = 1f
        carIndicator.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.car_normal_color))
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                canService?.getLastCanData()?.let { data ->
                    setConnected(true)
                    val temp = sensorValueToTemperature(data.sensorValue)

                    tempValue.text = "%.1f°C".format(temp)
                    tempProgressBar.progress = temp.toInt().coerceIn(0, 100)
                    ledStatusValue.text = if (data.ledStatus) "On" else "Off"
                    modeValue.text = if (data.systemMode == 1) "Blink" else "Normal"
                    buttonValue.text = if (data.buttonState) "Pressed" else "Released"
                    errorValue.text = if (data.errorStatus == 0) "OK" else data.errorStatus.toString()

                    currentLed = data.ledStatus
                    currentMode = data.systemMode


                    formatReadBroadcastLines(
                        data.ledStatus, data.systemMode, data.errorStatus,
                        data.buttonState, data.sensorValue, data.thresholdFlag
                    )
                    refreshReadDebug()


                    if (isFirstPoll) {
                        lastThresholdFlag = false
                        isFirstPoll = false
                    }

                    if (data.thresholdFlag && !lastThresholdFlag) {
                        canService?.sendLedCommand(1, 1)
                        formatWriteCommandLine(1, 1)
                        refreshWriteDebug()
                        startCarAlert()
                        lastThresholdFlag = true
                    } else if (!data.thresholdFlag && lastThresholdFlag) {
                        canService?.sendLedCommand(0, 0)
                        formatWriteCommandLine(0, 0)
                        refreshWriteDebug()
                        stopCarAlert()
                        lastThresholdFlag = false
                    }
                } ?: setConnected(false)
            } catch (e: Exception) {
                Log.e("CanDemo", "Poll failed", e)
                setConnected(false)
            }
            handler.postDelayed(this, 200)
        }
    }

    private fun runUdsCall(
        button: Button,
        label: String,
        targetLines: MutableMap<String, String>,
        refresh: () -> Unit,
        call: (ICanService) -> UdsResult,
        onSuccess: ((UdsResult) -> Unit)? = null
    ) {
        val service = canService
        if (service == null) {
            targetLines[label] = "[$label] service not connected"
            refresh()
            return
        }
        button.isEnabled = false
        Thread {
            val result = try {
                call(service)
            } catch (e: Exception) {
                Log.e("CanDemo", "$label failed", e)
                null
            }
            runOnUiThread {
                if (result != null) {
                    targetLines[label] = result.frameLog.trimEnd()
                    refresh()
                    if (result.success) onSuccess?.invoke(result)
                } else {
                    targetLines[label] = "[$label] call failed"
                    refresh()
                }
                button.isEnabled = true
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionStatus = findViewById(R.id.connectionStatus)
        carIndicator = findViewById(R.id.carIndicator)
        tempValue = findViewById(R.id.tempValue)
        tempProgressBar = findViewById(R.id.tempProgressBar)
        thresholdLabel = findViewById(R.id.thresholdLabel)
        ledStatusValue = findViewById(R.id.ledStatusValue)
        modeValue = findViewById(R.id.modeValue)
        buttonValue = findViewById(R.id.buttonValue)
        errorValue = findViewById(R.id.errorValue)
        vinValue = findViewById(R.id.vinValue)
        readDebugText = findViewById(R.id.readDebugText)
        writeDebugText = findViewById(R.id.writeDebugText)

        thresholdLabel.text = "Alert above %.1f°C".format(sensorValueToTemperature(SENSOR_THRESHOLD_RAW))

        canService = getCanService()
        setConnected(canService != null)
        stopCarAlert()
        handler.post(pollRunnable)

        findViewById<Button>(R.id.ledOnButton).setOnClickListener {
            canService?.sendLedCommand(1, 0)
            formatWriteCommandLine(1, 0)
            refreshWriteDebug()
        }
        findViewById<Button>(R.id.ledOffButton).setOnClickListener {
            canService?.sendLedCommand(0, 0)
            formatWriteCommandLine(0, 0)
            refreshWriteDebug()
        }
        findViewById<Button>(R.id.blinkButton).setOnClickListener {
            canService?.sendLedCommand(1, 1)
            formatWriteCommandLine(1, 1)
            refreshWriteDebug()
        }

        findViewById<Button>(R.id.readVinButton).setOnClickListener { button ->
            runUdsCall(button as Button, "VIN", readLines, ::refreshReadDebug, { it.readVinUds() }) { result ->
                vinValue.text = result.value
            }
        }

        findViewById<Button>(R.id.readStatusUdsButton).setOnClickListener { button ->
            runUdsCall(button as Button, "Status", readLines, ::refreshReadDebug, { it.readStatusUds() })
        }
        findViewById<Button>(R.id.readSensorUdsButton).setOnClickListener { button ->
            runUdsCall(button as Button, "Sensor", readLines, ::refreshReadDebug, { it.readSensorUds() })
        }
        findViewById<Button>(R.id.writeLedUdsButton).setOnClickListener { button ->
            val next = if (currentLed) 0 else 1
            runUdsCall(button as Button, "Write LED", writeLines, ::refreshWriteDebug, { it.writeLedUds(next) })
        }
        findViewById<Button>(R.id.writeModeUdsButton).setOnClickListener { button ->
            val next = if (currentMode == 1) 0 else 1
            runUdsCall(button as Button, "Write Mode", writeLines, ::refreshWriteDebug, { it.writeModeUds(next) })
        }

        findViewById<Button>(R.id.readDebugButton).setOnClickListener {
            readLines.clear()
            refreshReadDebug()
        }
        findViewById<Button>(R.id.writeDebugButton).setOnClickListener {
            writeLines.clear()
            refreshWriteDebug()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        carBlinkAnimator?.cancel()
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