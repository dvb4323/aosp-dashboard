package com.example.aospfinal

class DebugFrameLog {
    private val readLines = LinkedHashMap<String, String>()
    private val writeLines = LinkedHashMap<String, String>()

    fun readText(): String =
        if (readLines.isEmpty()) "No frames yet" else readLines.values.joinToString("\n")

    fun writeText(): String =
        if (writeLines.isEmpty()) "No frames yet" else writeLines.values.joinToString("\n")

    fun clearRead() = readLines.clear()
    fun clearWrite() = writeLines.clear()

    fun putUdsRead(label: String, frameLog: String) { readLines[label] = frameLog }
    fun putUdsWrite(label: String, frameLog: String) { writeLines[label] = frameLog }

    fun putReadBroadcast(led: Boolean, mode: Int, error: Int, button: Boolean, sensor: Int, threshold: Boolean) {
        val b0 = if (led) 1 else 0
        val b3 = if (button) 1 else 0
        readLines["0x100"] = "RX 0x100: %02X %02X %02X %02X 00 00 00 00".format(b0, mode, error, b3)
        val hi = (sensor shr 8) and 0xFF
        val lo = sensor and 0xFF
        val thFlag = if (threshold) 1 else 0
        readLines["0x101"] = "RX 0x101: %02X %02X %02X 00 00 00 00 00".format(hi, lo, thFlag)
    }

    fun putWriteCommand(led: Int, mode: Int) {
        writeLines["Command"] = "TX 0x200: %02X %02X 00 00 00 00 00 00".format(led, mode)
    }
}