package tech.idct.weighttracker.data.account

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
 * is on; restore only ever happens when the user asks (the user may prefer to
 * start from scratch, which is what "clear backed-up data" is for).
 *
 * A wholesale replace can only ever be safe if it replaces something this device
 * itself wrote, so every upload first checks that the row still carries the
 * timestamp of our last one. Anything else is a [Result.Conflict] — another
 * device has written since — and the user decides, rather than the last writer
 * silently winning.
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
        val version: Int = PAYLOAD_VERSION,
        val entries: List<BackupEntry>,
        val tombstones: List<String>,
        val plan: BackupPlan? = null,
        /**
         * Which plan's finish has already been celebrated. It travels with the
         * plan, or restoring a finished one replays the trophy on the new device.
         */
        val celebratedPlanKey: String? = null,
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wt_backup", Context.MODE_PRIVATE)

    /** Epoch millis the stored backup carries, or null if there is none. Server time. */
    private val _lastBackupAt = MutableStateFlow(prefs.getLong("lastBackupAt", 0L).takeIf { it > 0 })
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt

    /**
     * The timestamp of the row this device is entitled to replace — set when we
     * write it, and when a restore makes its content ours. Deliberately separate
     * from [lastBackupAt], which is only what the screen displays: adopting a
     * foreign timestamp for display must not also grant permission to overwrite it.
     */
    private var ownedAt: Long?
        get() = prefs.getLong("ownedAt", 0L).takeIf { it > 0 }
        set(value) {
            prefs.edit().putLong("ownedAt", value ?: 0L).apply()
        }

    sealed interface Result {
        data class Ok(val at: Long?) : Result
        data class Error(val message: String) : Result

        /**
         * The stored backup was not written by this device's last upload, so
         * replacing it would discard someone else's data.
         */
        data class Conflict(val at: Long?, val entryCount: Int) : Result
    }

    private sealed interface Remote {
        data class Present(val at: Long?, val entryCount: Int) : Remote
        data object Absent : Remote
        data class Unreachable(val message: String) : Remote
    }

    // ---- upload ------------------------------------------------------------

    /**
     * [force] skips the conflict check, for when the user has been shown what is
     * up there and chosen to replace it.
     */
    suspend fun backupNow(force: Boolean = false): Result {
        // Read the database before suspending on anything else. Fetching a token can
        // take a full network round trip, and Delete-all-data running in that window
        // used to turn this into an upload of the empty database.
        val payload = snapshot()

        val token = auth.accessToken() ?: return Result.Error("Sign in to back up")
        val userId = auth.session.value?.userId ?: return Result.Error("Sign in to back up")

        if (!force) {
            when (val remote = remote(token)) {
                is Remote.Unreachable -> return Result.Error(remote.message)
                is Remote.Absent -> Unit
                is Remote.Present ->
                    if (remote.at != ownedAt) {
                        return Result.Conflict(remote.at, remote.entryCount)
                    }
            }
        }

        val row = buildJsonObject {
            put("user_id", JsonPrimitive(userId))
            put("payload", payload)
        }
        val r = client.call(
            "/rest/v1/backups?on_conflict=user_id",
            JsonArray(listOf(row)),
            token = token,
            prefer = "resolution=merge-duplicates,return=representation",
        )
        if (!r.ok) {
            return Result.Error(
                if (r.status == 0) "Offline — will retry" else "Upload failed (${r.status})"
            )
        }
        val at = r.body.jsonArray.firstOrNull()?.jsonObject
            ?.get("updated_at")?.jsonPrimitive?.content?.toEpochMilli()
        storeLastBackup(at)
        return Result.Ok(at)
    }

    private suspend fun snapshot(): JsonElement = SupabaseClient.json.encodeToJsonElement(
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
            celebratedPlanKey = repo.settings().celebratedPlanKey,
        ),
    )

    private suspend fun remote(token: String): Remote {
        val r = client.call("/rest/v1/backups?select=payload,updated_at", token = token)
        if (!r.ok) {
            return Remote.Unreachable(
                if (r.status == 0) "Offline — will retry" else "Couldn't check the backup (${r.status})"
            )
        }
        val row = r.body.jsonArray.firstOrNull()?.jsonObject ?: return Remote.Absent
        val entries = row["payload"]?.jsonObject?.get("entries")?.jsonArray?.size ?: 0
        return Remote.Present(
            at = row["updated_at"]?.jsonPrimitive?.content?.toEpochMilli(),
            entryCount = entries,
        )
    }

    /** The server's word on when the last backup happened; null means none exists. */
    suspend fun fetchLastBackupAt(): Long? {
        val token = auth.accessToken() ?: return null
        return when (val remote = remote(token)) {
            is Remote.Unreachable -> _lastBackupAt.value
            is Remote.Absent -> {
                storeLastBackup(null)
                null
            }
            // Display only — ownedAt is untouched, so a row another device wrote is
            // still a conflict on the next upload.
            is Remote.Present -> remote.at.also { showLastBackup(it) }
        }
    }

    /** What is stored for this account, for the screen to describe. */
    suspend fun describeRemote(): Pair<Long?, Int>? {
        val token = auth.accessToken() ?: return null
        return (remote(token) as? Remote.Present)?.let { it.at to it.entryCount }
    }

    // ---- download ----------------------------------------------------------

    /** Replaces everything local with the snapshot. Only ever user-initiated. */
    suspend fun restore(): Result {
        val token = auth.accessToken() ?: return Result.Error("Sign in to restore")
        val r = client.call("/rest/v1/backups?select=payload,updated_at", token = token)
        if (!r.ok) {
            return Result.Error(
                if (r.status == 0) "Couldn't reach the server — check your connection"
                else "Restore failed (${r.status})"
            )
        }
        val row = r.body.jsonArray.firstOrNull()?.jsonObject
            ?: return Result.Error("There's no backup for this account yet")
        val payload = try {
            SupabaseClient.json.decodeFromJsonElement(
                BackupPayload.serializer(),
                row["payload"] ?: return Result.Error("The backup is empty"),
            )
        } catch (e: Exception) {
            return Result.Error("This backup can't be read by this version of the app")
        }
        // The version was written but never read, and the decoder ignores unknown
        // keys, so a newer backup used to decode "successfully" with its extra
        // fields dropped — and the upload that follows a restore would then
        // overwrite the real one with that reduced copy.
        if (payload.version > PAYLOAD_VERSION) {
            return Result.Error("This backup was made by a newer version of the app")
        }

        // All or nothing. Dropping unreadable rows silently restored less than the
        // backup held, called it a success, and then uploaded the smaller set back.
        val entries = try {
            payload.entries.map { entry ->
                WeightEntry(
                    date = LocalDate.parse(entry.date),
                    kg = entry.kg,
                    source = EntrySource.valueOf(entry.source),
                    hcRecordId = entry.hcRecordId,
                    recordedAt = entry.recordedAt,
                )
            }
        } catch (e: Exception) {
            return Result.Error("Part of this backup is unreadable — nothing was changed")
        }
        val tombstones = try {
            payload.tombstones.map { LocalDate.parse(it).toEpochDay() }
        } catch (e: Exception) {
            return Result.Error("Part of this backup is unreadable — nothing was changed")
        }
        val plan = try {
            payload.plan?.let {
                Plan(
                    startDate = LocalDate.parse(it.startDate),
                    startKg = it.startKg,
                    targetKg = it.targetKg,
                    mode = PlanMode.valueOf(it.mode),
                    targetDate = it.targetDate?.let(LocalDate::parse),
                    ratePerWeek = it.ratePerWeek,
                )
            }
        } catch (e: Exception) {
            return Result.Error("Part of this backup is unreadable — nothing was changed")
        }

        repo.replaceAllFromBackup(
            entries = entries,
            tombstoneEpochDays = tombstones,
            plan = plan,
            celebratedPlanKey = payload.celebratedPlanKey,
        )
        // What is on the server is now exactly what is on this phone, so replacing
        // that row later loses nothing.
        storeLastBackup(row["updated_at"]?.jsonPrimitive?.content?.toEpochMilli())
        return Result.Ok(_lastBackupAt.value)
    }

    /** Deletes the row so the user can start from scratch. */
    suspend fun clear(): Result {
        val token = auth.accessToken() ?: return Result.Error("Sign in first")
        val userId = auth.session.value?.userId ?: return Result.Error("Sign in first")
        val r = client.call("/rest/v1/backups?user_id=eq.$userId", token = token, method = "DELETE")
        if (!r.ok) {
            return Result.Error(
                if (r.status == 0) "Couldn't reach the server — check your connection"
                else "Delete failed (${r.status})"
            )
        }
        storeLastBackup(null)
        return Result.Ok(null)
    }

    /** Called when the account changes hands or goes away. */
    fun forgetLocalState() = storeLastBackup(null)

    /** Records a row this device owns: both displayed and claimable. */
    private fun storeLastBackup(at: Long?) {
        showLastBackup(at)
        ownedAt = at
    }

    private fun showLastBackup(at: Long?) {
        prefs.edit().putLong("lastBackupAt", at ?: 0L).apply()
        _lastBackupAt.value = at
    }
}

/** Bump when the payload gains a field an older build could not preserve. */
private const val PAYLOAD_VERSION = 2

private fun String.toEpochMilli(): Long? =
    runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
