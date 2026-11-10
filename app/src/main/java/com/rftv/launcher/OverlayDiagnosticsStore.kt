package com.rftv.launcher

import java.util.ArrayDeque

/**
 * Process-local diagnostics only. No disk writes, permissions, or network access.
 * Mutations happen from MainActivity's main-thread callbacks; synchronization also
 * keeps snapshots safe if a background callback is added in the future.
 */
internal data class OverlayDiagnosticRecord(
    val overlay: String,
    val temperature: String,
    val openCount: Int,
    val javaHeapBeforeBytes: Long,
    var javaHeapAfterBytes: Long = 0L,
    val nativeHeapBeforeBytes: Long,
    var nativeHeapAfterBytes: Long = 0L,
    var captureMs: Long = 0L,
    var openToVisibleMs: Long = 0L,
    var sourceBitmapBytes: Int = 0,
    var blurMs: Long = -1L,
    var blurredBitmapBytes: Int = 0,
    var blurSucceeded: Boolean = false
)

internal object OverlayDiagnosticsStore {
    private const val CAPACITY = 10
    private val records = ArrayDeque<OverlayDiagnosticRecord>()
    private val seenOverlays = mutableSetOf<String>()
    private var totalOpenCount = 0

    @Synchronized
    fun begin(overlay: String, javaHeapBeforeBytes: Long, nativeHeapBeforeBytes: Long): OverlayDiagnosticRecord {
        totalOpenCount += 1
        val temperature = if (seenOverlays.add(overlay)) "cold" else "repeat"
        return OverlayDiagnosticRecord(
            overlay = overlay,
            temperature = temperature,
            openCount = totalOpenCount,
            javaHeapBeforeBytes = javaHeapBeforeBytes,
            nativeHeapBeforeBytes = nativeHeapBeforeBytes
        ).also {
            if (records.size == CAPACITY) records.removeFirst()
            records.addLast(it)
        }
    }

    @Synchronized
    fun opened(
        record: OverlayDiagnosticRecord,
        captureMs: Long,
        openToVisibleMs: Long,
        sourceBitmapBytes: Int,
        javaHeapAfterBytes: Long,
        nativeHeapAfterBytes: Long
    ) {
        if (!records.contains(record)) return
        record.captureMs = captureMs
        record.openToVisibleMs = openToVisibleMs
        record.sourceBitmapBytes = sourceBitmapBytes
        record.javaHeapAfterBytes = javaHeapAfterBytes
        record.nativeHeapAfterBytes = nativeHeapAfterBytes
    }

    @Synchronized
    fun blurCompleted(record: OverlayDiagnosticRecord, blurMs: Long, blurredBitmapBytes: Int, success: Boolean) {
        if (!records.contains(record)) return
        record.blurMs = blurMs
        record.blurredBitmapBytes = blurredBitmapBytes
        record.blurSucceeded = success
    }

    @Synchronized
    fun latest(): List<OverlayDiagnosticRecord> = records.toList().asReversed().map { it.copy() }

    @Synchronized
    fun clear() {
        records.clear()
    }
}
