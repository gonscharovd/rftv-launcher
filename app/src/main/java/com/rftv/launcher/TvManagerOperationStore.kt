package com.rftv.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Durable before/after snapshots for package changes performed by RFTV.
 * Writes are synchronous by design: a change must never start before its
 * original state has been committed and read back successfully.
 */
internal class TvManagerOperationStore(context: Context) {
    data class Snapshot(
        val operationId: String,
        val packageName: String,
        val userId: Int,
        val originalEnabledSetting: Int,
        val manifestEnabled: Boolean,
        val originalHidden: Boolean,
        val provider: String,
        val deviceFingerprint: String,
        val createdAt: Long
    )

    data class JournalEntry(
        val operationId: String,
        val packageName: String,
        val action: String,
        val beforeState: Int?,
        val afterState: Int?,
        val provider: String,
        val timestamp: Long,
        val result: String
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE
    )

    @Synchronized
    fun saveSnapshotBeforeChange(snapshot: Snapshot): Boolean {
        if (!PACKAGE_NAME.matches(snapshot.packageName) ||
            snapshot.operationId.isBlank() ||
            snapshot.userId != 0 ||
            snapshot.originalEnabledSetting !in 0..4 ||
            snapshot.provider.isBlank() ||
            snapshot.deviceFingerprint.isBlank()
        ) return false
        val active = readSnapshots()
        if (active.has(snapshot.packageName)) return false
        active.put(snapshot.packageName, snapshot.toJson())
        val entry = JournalEntry(
            snapshot.operationId, snapshot.packageName, "SNAPSHOT_SAVED",
            snapshot.originalEnabledSetting, snapshot.originalEnabledSetting,
            snapshot.provider, snapshot.createdAt, "Исходное состояние сохранено"
        )
        val journal = readJournal().put(entry.toJson())
        if (!preferences.edit()
                .putString(KEY_ACTIVE, active.toString())
                .putString(KEY_JOURNAL, journal.toString())
                .commit()) return false
        val stored = readSnapshots().optJSONObject(snapshot.packageName) ?: return false
        return stored.optString("operationId") == snapshot.operationId &&
            stored.optString("packageName") == snapshot.packageName &&
            stored.optInt("userId", -1) == snapshot.userId &&
            stored.optInt("originalEnabledSetting", Int.MIN_VALUE) == snapshot.originalEnabledSetting &&
            stored.optString("deviceFingerprint") == snapshot.deviceFingerprint
    }

    @Synchronized
    fun activeSnapshots(): List<Snapshot> {
        val active = readSnapshots()
        return active.keys().asSequence().mapNotNull { key ->
            active.optJSONObject(key)?.toSnapshot()?.takeIf { it.packageName == key }
        }.toList()
    }

    @Synchronized
    fun activeSnapshot(packageName: String): Snapshot? =
        readSnapshots().optJSONObject(packageName)?.toSnapshot()
            ?.takeIf { it.packageName == packageName }

    @Synchronized
    fun recordApplied(snapshot: Snapshot, resultingEnabledSetting: Int, verified: Boolean): Boolean {
        if (!verified) return appendEvent(snapshot, "DISABLE_FAILED", null, "Состояние отключения не подтверждено")
        return appendEvent(snapshot, "DISABLED", resultingEnabledSetting, "Отключение подтверждено")
    }

    @Synchronized
    fun recordFailure(snapshot: Snapshot, message: String): Boolean =
        appendEvent(snapshot, "DISABLE_FAILED", null, message)

    @Synchronized
    fun recordRestored(snapshot: Snapshot, resultingEnabledSetting: Int, verified: Boolean): Boolean {
        if (!verified) return appendEvent(snapshot, "RESTORE_FAILED", resultingEnabledSetting, "Исходное состояние не подтверждено")
        val active = readSnapshots()
        val stored = active.optJSONObject(snapshot.packageName) ?: return false
        if (stored.optString("operationId") != snapshot.operationId) return false
        active.remove(snapshot.packageName)
        val journal = readJournal().put(
            JournalEntry(
                snapshot.operationId, snapshot.packageName, "RESTORED",
                null, resultingEnabledSetting, snapshot.provider,
                System.currentTimeMillis(), "Исходное состояние восстановлено"
            ).toJson()
        )
        return preferences.edit()
            .putString(KEY_ACTIVE, active.toString())
            .putString(KEY_JOURNAL, journal.toString())
            .commit()
    }

    @Synchronized
    fun recordRollback(snapshot: Snapshot, restored: Boolean, details: String): Boolean =
        appendEvent(
            snapshot,
            if (restored) "ROLLBACK_RESTORED" else "ROLLBACK_FAILED",
            if (restored) snapshot.originalEnabledSetting else null,
            details
        )

    @Synchronized
    fun journal(): List<JournalEntry> {
        val entries = readJournal()
        return (0 until entries.length()).mapNotNull { index ->
            entries.optJSONObject(index)?.toJournalEntry()
        }.asReversed()
    }

    private fun appendEvent(
        snapshot: Snapshot,
        action: String,
        afterState: Int?,
        result: String
    ): Boolean {
        val journal = readJournal().put(
            JournalEntry(
                snapshot.operationId, snapshot.packageName, action,
                snapshot.originalEnabledSetting, afterState, snapshot.provider,
                System.currentTimeMillis(), result
            ).toJson()
        )
        return preferences.edit().putString(KEY_JOURNAL, journal.toString()).commit()
    }

    private fun readSnapshots(): JSONObject = try {
        JSONObject(preferences.getString(KEY_ACTIVE, "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    private fun readJournal(): JSONArray = try {
        JSONArray(preferences.getString(KEY_JOURNAL, "[]") ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }

    private fun Snapshot.toJson() = JSONObject().apply {
        put("operationId", operationId)
        put("packageName", packageName)
        put("userId", userId)
        put("originalEnabledSetting", originalEnabledSetting)
        put("manifestEnabled", manifestEnabled)
        put("originalHidden", originalHidden)
        put("provider", provider)
        put("deviceFingerprint", deviceFingerprint)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toSnapshot() = Snapshot(
        operationId = optString("operationId"),
        packageName = optString("packageName"),
        userId = optInt("userId", -1),
        originalEnabledSetting = optInt("originalEnabledSetting", Int.MIN_VALUE),
        manifestEnabled = optBoolean("manifestEnabled", true),
        originalHidden = optBoolean("originalHidden", false),
        provider = optString("provider"),
        deviceFingerprint = optString("deviceFingerprint"),
        createdAt = optLong("createdAt", 0L)
    )

    private fun JournalEntry.toJson() = JSONObject().apply {
        put("operationId", operationId)
        put("packageName", packageName)
        put("action", action)
        if (beforeState == null) put("beforeState", JSONObject.NULL) else put("beforeState", beforeState)
        if (afterState == null) put("afterState", JSONObject.NULL) else put("afterState", afterState)
        put("provider", provider)
        put("timestamp", timestamp)
        put("result", result)
    }

    private fun JSONObject.toJournalEntry() = JournalEntry(
        operationId = optString("operationId"),
        packageName = optString("packageName"),
        action = optString("action"),
        beforeState = if (isNull("beforeState")) null else optInt("beforeState"),
        afterState = if (isNull("afterState")) null else optInt("afterState"),
        provider = optString("provider"),
        timestamp = optLong("timestamp"),
        result = optString("result")
    )

    companion object {
        private const val PREFERENCES_NAME = "rftv_tv_manager_operations"
        private const val KEY_ACTIVE = "active_snapshots"
        private const val KEY_JOURNAL = "operation_journal"
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

        fun newOperationId(): String = UUID.randomUUID().toString()
    }
}
