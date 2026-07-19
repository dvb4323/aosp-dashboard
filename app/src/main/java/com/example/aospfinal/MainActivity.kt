package com.example.aospfinal

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var connectionStatus: TextView
    private lateinit var dashboardTabButton: Button
    private lateinit var diagnosticsTabButton: Button
    private lateinit var dashboardContent: View
    private lateinit var diagnosticsContent: View

    private lateinit var triangleIcon: ImageView
    private lateinit var headlightLeft: View
    private lateinit var headlightRight: View

    private lateinit var speedometerView: SpeedometerView
    private lateinit var speedValue: TextView
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

    private lateinit var ledOnButton: Button
    private lateinit var ledOffButton: Button
    private lateinit var blinkButton: Button

    private val canClient = CanServiceClient()
    private val debugLog = DebugFrameLog()
    private val handler = Handler(Looper.getMainLooper())

    private var isFirstPoll = true
    private var lastThresholdFlag = false
    private var triangleBlinkAnimator: ObjectAnimator? = null
    private var headlightBlinkAnimatorLeft: ObjectAnimator? = null
    private var headlightBlinkAnimatorRight: ObjectAnimator? = null

    private var currentLed = false
    private var currentMode = 0

    companion object {
        private const val SENSOR_MIN_TEMP = 0.0
        private const val SENSOR_MAX_TEMP = 100.0
        private const val SENSOR_THRESHOLD_RAW = 2048
        private const val SPEED_MIN_KMH = 0.0
        private const val SPEED_MAX_KMH = 220.0
    }

    private fun sensorValueToTemperature(sensorValue: Int): Double {
        return SENSOR_MIN_TEMP + (sensorValue / 4095.0) * (SENSOR_MAX_TEMP - SENSOR_MIN_TEMP)
    }

    private fun sensorValueToSpeed(sensorValue: Int): Double {
        return SPEED_MIN_KMH + (sensorValue / 4095.0) * (SPEED_MAX_KMH - SPEED_MIN_KMH)
    }

    private fun refreshReadDebug() { readDebugText.text = debugLog.readText() }
    private fun refreshWriteDebug() { writeDebugText.text = debugLog.writeText() }

    private fun setConnected(connected: Boolean) {
        if (connected) {
            connectionStatus.text = "Connected"
            connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.led_on_color))
        } else {
            connectionStatus.text = "Disconnected"
            connectionStatus.setTextColor(ContextCompat.getColor(this, R.color.car_alert_color))
        }
    }

    private fun startTriangleAlert() {
        triangleIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.car_alert_color))
        if (triangleBlinkAnimator == null) {
            triangleBlinkAnimator = ObjectAnimator.ofFloat(triangleIcon, "alpha", 1f, 0.25f).apply {
                duration = 400
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    private fun stopTriangleAlert() {
        triangleBlinkAnimator?.cancel()
        triangleBlinkAnimator = null
        triangleIcon.alpha = 1f
        triangleIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.triangle_idle_color))
    }

    private fun updateHeadlights(led: Boolean, mode: Int) {
        headlightBlinkAnimatorLeft?.cancel(); headlightBlinkAnimatorLeft = null
        headlightBlinkAnimatorRight?.cancel(); headlightBlinkAnimatorRight = null
        headlightLeft.alpha = 1f
        headlightRight.alpha = 1f

        val offColor = ContextCompat.getColor(this, R.color.headlight_off_color)
        val onColor = ContextCompat.getColor(this, R.color.headlight_on_color)

        if (!led) {
            headlightLeft.backgroundTintList = ColorStateList.valueOf(offColor)
            headlightRight.backgroundTintList = ColorStateList.valueOf(offColor)
        } else if (mode == 1) {
            val hazardColor = ContextCompat.getColor(this, R.color.hazard_color)
            headlightLeft.backgroundTintList = ColorStateList.valueOf(hazardColor)
            headlightRight.backgroundTintList = ColorStateList.valueOf(hazardColor)
            headlightBlinkAnimatorLeft = ObjectAnimator.ofFloat(headlightLeft, "alpha", 1f, 0.15f).apply {
                duration = 500
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
            headlightBlinkAnimatorRight = ObjectAnimator.ofFloat(headlightRight, "alpha", 1f, 0.15f).apply {
                duration = 500
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        } else {
            headlightLeft.backgroundTintList = ColorStateList.valueOf(onColor)
            headlightRight.backgroundTintList = ColorStateList.valueOf(onColor)
        }
    }

    private fun updateActionButtonHighlight() {
        val accentColor = ContextCompat.getColor(this, R.color.blink_color)
        val mutedColor = ContextCompat.getColor(this, R.color.icon_muted_color)
        val buttons = listOf(ledOnButton, ledOffButton, blinkButton)

        buttons.forEach { b ->
            b.setBackgroundResource(R.drawable.action_button_bg)
            b.setTextColor(mutedColor)
            b.compoundDrawableTintList = ColorStateList.valueOf(mutedColor)
        }

        val activeButton = when {
            !currentLed -> ledOffButton
            currentMode == 1 -> blinkButton
            else -> ledOnButton
        }
        activeButton.setBackgroundResource(R.drawable.action_button_active_bg)
        activeButton.setTextColor(accentColor)
        activeButton.compoundDrawableTintList = ColorStateList.valueOf(accentColor)
    }

    private fun applyState(led: Boolean, mode: Int) {
        currentLed = led
        currentMode = mode
        updateHeadlights(led, mode)
        updateActionButtonHighlight()
    }

    private val pollRunnable: Runnable = object : Runnable {
        override fun run() {
            canClient.getLastCanData { data ->
                if (data != null) {
                    setConnected(true)
                    val temp = sensorValueToTemperature(data.sensorValue)
                    val speed = sensorValueToSpeed(data.sensorValue)

                    tempValue.text = "%.1f°C".format(temp)
                    tempProgressBar.progress = temp.toInt().coerceIn(0, 100)
                    speedValue.text = speed.toInt().toString()
                    speedometerView.setProgress((speed / SPEED_MAX_KMH).toFloat())

                    ledStatusValue.text = if (data.ledStatus) "LED on" else "LED off"
                    modeValue.text = if (data.systemMode == 1) "Hazard" else "Normal"
                    buttonValue.text = if (data.buttonState) "AC on" else "AC off"
                    errorValue.text = if (data.errorStatus == 0) "OK" else data.errorStatus.toString()

                    if (data.ledStatus != currentLed || data.systemMode != currentMode) {
                        applyState(data.ledStatus, data.systemMode)
                    }

                    debugLog.putReadBroadcast(
                        data.ledStatus, data.systemMode, data.errorStatus,
                        data.buttonState, data.sensorValue, data.thresholdFlag
                    )
                    refreshReadDebug()

                    if (isFirstPoll) {
                        lastThresholdFlag = false
                        isFirstPoll = false
                    }

                    if (data.thresholdFlag && !lastThresholdFlag) {
                        debugLog.putWriteCommand(1, 1)
                        refreshWriteDebug()
                        startTriangleAlert()
                        lastThresholdFlag = true
                        canClient.sendLedCommand(1, 1)
                    } else if (!data.thresholdFlag && lastThresholdFlag) {
                        debugLog.putWriteCommand(0, 0)
                        refreshWriteDebug()
                        stopTriangleAlert()
                        lastThresholdFlag = false
                        canClient.sendLedCommand(0, 0)
                    }
                } else {
                    setConnected(false)
                }
                handler.postDelayed(pollRunnable, 200)
            }
        }
    }

    private fun runUdsCall(
        button: Button,
        label: String,
        isRead: Boolean,
        call: (com.aaos.cand.ICanService) -> com.aaos.cand.UdsResult,
        onSuccess: ((com.aaos.cand.UdsResult) -> Unit)? = null
    ) {
        button.isEnabled = false
        canClient.callUds(label, call) { result ->
            if (result != null) {
                if (isRead) debugLog.putUdsRead(label, result.frameLog.trimEnd())
                else debugLog.putUdsWrite(label, result.frameLog.trimEnd())
                if (isRead) refreshReadDebug() else refreshWriteDebug()
                if (result.success) onSuccess?.invoke(result)
            } else {
                if (isRead) debugLog.putUdsRead(label, "[$label] call failed")
                else debugLog.putUdsWrite(label, "[$label] call failed")
                if (isRead) refreshReadDebug() else refreshWriteDebug()
            }
            button.isEnabled = true
        }
    }

    private fun showDashboard() {
        dashboardContent.visibility = View.VISIBLE
        diagnosticsContent.visibility = View.GONE
        dashboardTabButton.setBackgroundResource(R.drawable.button_blink)
        dashboardTabButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        diagnosticsTabButton.setBackgroundResource(R.drawable.status_card_bg)
        diagnosticsTabButton.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
    }

    private fun showDiagnostics() {
        dashboardContent.visibility = View.GONE
        diagnosticsContent.visibility = View.VISIBLE
        diagnosticsTabButton.setBackgroundResource(R.drawable.button_blink)
        diagnosticsTabButton.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        dashboardTabButton.setBackgroundResource(R.drawable.status_card_bg)
        dashboardTabButton.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionStatus = findViewById(R.id.connectionStatus)
        dashboardTabButton = findViewById(R.id.dashboardTabButton)
        diagnosticsTabButton = findViewById(R.id.diagnosticsTabButton)
        dashboardContent = findViewById(R.id.dashboardContent)
        diagnosticsContent = findViewById(R.id.diagnosticsContent)

        triangleIcon = findViewById(R.id.triangleIcon)
        headlightLeft = findViewById(R.id.headlightLeft)
        headlightRight = findViewById(R.id.headlightRight)

        speedometerView = findViewById(R.id.speedometerView)
        speedValue = findViewById(R.id.speedValue)
        speedometerView.setTrackColor(ContextCompat.getColor(this, R.color.speedo_track_color))
        speedometerView.setFillColor(ContextCompat.getColor(this, R.color.speedo_fill_color))

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

        ledOnButton = findViewById(R.id.ledOnButton)
        ledOffButton = findViewById(R.id.ledOffButton)
        blinkButton = findViewById(R.id.blinkButton)

        thresholdLabel.text = "Alert above %.1f°C".format(sensorValueToTemperature(SENSOR_THRESHOLD_RAW))
        stopTriangleAlert()
        applyState(false, 0)
        showDashboard()

        setConnected(canClient.connect())
        handler.post(pollRunnable)

        dashboardTabButton.setOnClickListener { showDashboard() }
        diagnosticsTabButton.setOnClickListener { showDiagnostics() }

        ledOnButton.setOnClickListener {
            applyState(true, 0)
            debugLog.putWriteCommand(1, 0)
            refreshWriteDebug()
            canClient.sendLedCommand(1, 0)
        }
        ledOffButton.setOnClickListener {
            applyState(false, 0)
            debugLog.putWriteCommand(0, 0)
            refreshWriteDebug()
            canClient.sendLedCommand(0, 0)
        }
        blinkButton.setOnClickListener {
            applyState(true, 1)
            debugLog.putWriteCommand(1, 1)
            refreshWriteDebug()
            canClient.sendLedCommand(1, 1)
        }

        findViewById<Button>(R.id.readVinButton).setOnClickListener { button ->
            runUdsCall(button as Button, "VIN", true, { it.readVinUds() }) { result ->
                vinValue.text = result.value
            }
        }
        findViewById<Button>(R.id.readStatusUdsButton).setOnClickListener { button ->
            runUdsCall(button as Button, "Status", true, { it.readStatusUds() })
        }
        findViewById<Button>(R.id.readSensorUdsButton).setOnClickListener { button ->
            runUdsCall(button as Button, "Sensor", true, { it.readSensorUds() })
        }
        findViewById<Button>(R.id.writeLedUdsButton).setOnClickListener { button ->
            val next = if (currentLed) 0 else 1
            runUdsCall(button as Button, "Write LED", false, { it.writeLedUds(next) })
        }
        findViewById<Button>(R.id.writeModeUdsButton).setOnClickListener { button ->
            val next = if (currentMode == 1) 0 else 1
            runUdsCall(button as Button, "Write Mode", false, { it.writeModeUds(next) })
        }

        findViewById<Button>(R.id.readDebugButton).setOnClickListener {
            debugLog.clearRead()
            refreshReadDebug()
        }
        findViewById<Button>(R.id.writeDebugButton).setOnClickListener {
            debugLog.clearWrite()
            refreshWriteDebug()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        triangleBlinkAnimator?.cancel()
        headlightBlinkAnimatorLeft?.cancel()
        headlightBlinkAnimatorRight?.cancel()
    }
}