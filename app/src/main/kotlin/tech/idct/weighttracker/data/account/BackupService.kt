package tech.idct.weighttracker.data.account

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.idct.weighttracker.data.repo.WeightRepository
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.Plan
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.WeightEntry
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Backup is one row per account: the whole local state as a JSON snapshot,
 * replaced wholesale on every upload. Uploads happen automatically while backup
 * is on; restore only ever happens when the user asks (§ the user may prefer to
 * start from scratch, which is what "clear backed-up data" is for).
 */
class BackupService(
    context: Context,
    private val client: SupabaseClient,
    private val auth: SupabaseAuth,
    private val repo: WeightRepository,
) {

    @Serializable
    private data class BackupEntry(
        val date: String,
        val kg: Float,
        val source: String,
        val hcRecordId: String? = null,
        val recordedAt: Long? = null,
    )

    @Serializable
    private data class BackupPlan(
        val startDate: String,
        val startKg: Float,
        val targetKg: Float,
        val mode: String,
        val targetDate: String? = null,
        val ratePerWeek: Float? = null,
    )

    @Serializable
    private data class BackupPayload(
        val version: Int = 1,
        val entries: List<BackupEntry>,
        val tombstones: List<String>,
        val plan: BackupPlan? = null,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wt_backup", Context.MODE_PRIVATE)

    /** Epoch millis of the last successful upload, or null. Server time. */
    private val _lastBackupAt = MutableStateFlow(prefs.getLong("lastBackupAt", 0L).takeIf { it > 0 })
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt

    sealed interface Result {
        data class Ok(val at: Long?) : Result
        data class Error(val message: String) : Result
    }

    suspend fun backupNow(): Result {
        val token = auth.accessToken() ?: return Result.Error("Sign in to back up")
        val userId = auth.session.value?.userId ?: return Result.Error("Sign in to back up")

        val payload = SupabaseClient.json.encodeToJsonElement(
            BackupPayload.serializer(),
            BackupPayload(
                entries = repo.entries().map { entry ->
                    BackupEntry(
                        date = entry.date.toString(),
                        kg = entry.kg,
                        source = entry.source.name,
                        hcRecordId = entry.hcRecordId,
                        recordedAt = entry.recordedAt,
                    )
                },
                tombstones = repo.tombstoneDates().map { LocalDate.ofEpochDay(it).toString() },
                plan = repo.plan()?.let { plan ->
                    BackupPlan(
                        startDate = plan.startDate.toString(),
                        startKg = plan.startKg,
                        targetKg = plan.targetKg,
                        mode = plan.mode.name,
                        targetDate = plan.targetDate?.toString(),
                        ratePerWeek = plan.ratePerWeek,
                    )
                },
            ),
        )

        val row = kotlinx.serialization.json.buildJsonObject {
            put("user_id", kotlinx.serialization.json.JsonPrimitive(userId))
            put("payload", payload)
        }
        val r = client.call(
            "/rest/v1/backups?on_conflict=user_id",
            JsonArray(listOf(row)),
            token = token,
            prefer = "resolution=merge-duplicates,return=representation",
        )
        if (!r.ok) return Result.Error(if (r.status == 0) "Offline — will retry" else "Upload failed (${r.status})")
        val at = r.body.jsonArray.firstOrNull()?.jsonObject
            ?.get("updated_at")?.jsonPrimitive?.content?.toEpochMilli()
        storeLastBackup(at)
        return Result.Ok(at)
    }

    /** The server's word on when the last backup happened; null means none exists. */
    suspend fun fetchLastBackupAt(): Long? {
        val token = auth.accessToken() ?: return null
        val r = client.call("/rest/v1/backups?select=updated_at", token = token)
        if (!r.ok) return _lastBackupAt.value
        val at = r.body.jsonArray.firstOrNull()?.jsonObject
            ?.get("updated_at")?.jsonPrimitive?.content?.toEpochMilli()
        storeLastBackup(at)
        return at
    }

    /** Replaces everything local with the snapshot. Only ever user-initiated. */
    suspend fun restore(): Result {
        val token = auth.accessToken() ?: return Result.Error("Sign in to restore")
        val r = client.call("/rest/v1/backups?select=payload,updated_at", token = token)
        if (!r.ok) return Result.Error(if (r.status == 0) "Couldn't reach the server — check your connection" else "Restore failed (${r.status})")
        val row = r.body.jsonArray.firstOrNull()?.jsonObject
            ?: return Result.Error("There's no backup for this account yet")
        val payload = try {
            SupabaseClient.json.decodeFromJsonElement(
                BackupPayload.serializer(),
                row["payload"] ?: return Result.Error("The backup is empty"),
            )
        } catch (e: Exception) {
            return Result.Error("This backup was made by a newer version of the app")
        }

        repo.replaceAllFromBackup(
            entries = payload.entries.mapNotNull { entry ->
                runCatching {
                    WeightEntry(
                        date = LocalDate.parse(entry.date),
                        kg = entry.kg,
                        source = EntrySource.valueOf(entry.source),
                        hcRecordId = entry.hcRecordId,
                        recordedAt = entry.recordedAt,
                    )
                }.getOrNull()
            },
            tombstoneEpochDays = payload.tombstones.mapNotNull {
                runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull()
            },
            plan = payload.plan?.let { plan ->
                runCatching {
                    Plan(
                        startDate = LocalDate.parse(plan.startDate),
                        startKg = plan.startKg,
                        targetKg = plan.targetKg,
                        mode = PlanMode.valueOf(plan.mode),
                        targetDate = plan.targetDate?.let(LocalDate::parse),
                        ratePerWeek = plan.ratePerWeek,
                    )
                }.getOrNull()
            },
        )
        storeLastBackup(row["updated_at"]?.jsonPrimitive?.content?.toEpochMilli())
        return Result.Ok(_lastBackupAt.value)
    }

    /** Deletes the row so the user can start from scratch. */
    suspend fun clear(): Result {
        val token = auth.accessToken() ?: return Result.Error("Sign in first")
        val userId = auth.session.value?.userId ?: return Result.Error("Sign in first")
        val r = client.call("/rest/v1/backups?user_id=eq.$userId", token = token, method = "DELETE")
        if (!r.ok) return Result.Error(if (r.status == 0) "Couldn't reach the server — check your connection" else "Delete failed (${r.status})")
        storeLastBackup(null)
        return Result.Ok(null)
    }

    /** Called when the account changes hands or goes away. */
    fun forgetLocalState() = storeLastBackup(null)

    private fun storeLastBackup(at: Long?) {
        prefs.edit().putLong("lastBackupAt", at ?: 0L).apply()
        _lastBackupAt.value = at
    }
}

private fun String.toEpochMilli(): Long? =
    runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
