package tech.idct.weighttracker.data.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Health Connect access, as described in section 2 and section 4. Read is required
 * for any sync; write is optional and only lets manual entries flow back out.
 */
class HealthConnectManager(private val context: Context) {

    /** The background grant is a separate ask, on its own screen (section 7). */
    companion object {
        const val PERMISSION_READ_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        val READ_PERMISSION: String = HealthPermission.getReadPermission(WeightRecord::class)
        val WRITE_PERMISSION: String = HealthPermission.getWritePermission(WeightRecord::class)

        /** What the Health Connect screen asks for: read required, write optional. */
        val FOREGROUND_PERMISSIONS = setOf(READ_PERMISSION, WRITE_PERMISSION)

        val BACKGROUND_PERMISSIONS = setOf(PERMISSION_READ_BACKGROUND)

        /**
         * A first guess, for the frame before the real answer arrives. Android 15
         * carries a Health Connect new enough to read in the background; what an
         * Android 14 phone carries depends on a module update, which is exactly
         * what [HealthConnectManager.backgroundReadSupported] goes and asks.
         */
        val backgroundReadLikely: Boolean get() = Build.VERSION.SDK_INT >= 35
    }

    /**
     * Section 2: whether this phone can read health data while the app is in the
     * background — the gate on the whole of background sync, so getting it wrong
     * costs the feature entirely.
     *
     * Asked of Health Connect rather than inferred from the SDK level. Background
     * reads arrived in the Health Connect module, not in the platform: the version
     * map in the Jetpack SDK puts the feature at API 34 with U extension 13, which
     * an Android 14 phone gets from a module update and may well already have.
     * `SDK_INT >= 35` therefore told a large class of phones the feature was
     * impossible for them — the screen even said so in as many words — when their
     * own Health Connect was ready to grant it. The SDK check survives only as the
     * fallback for when the client cannot be reached at all.
     */
    val backgroundReadSupported: Boolean
        get() {
            val hc = client ?: return false
            return runCatching {
                hc.features.getFeatureStatus(
                    HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
                ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            }.getOrDefault(backgroundReadLikely)
        }

    val availability: Int get() = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean get() = availability == HealthConnectClient.SDK_AVAILABLE

    val client: HealthConnectClient? get() = if (isAvailable) HealthConnectClient.getOrCreate(context) else null

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    private suspend fun granted(): Set<String> =
        client?.permissionController?.getGrantedPermissions() ?: emptySet()

    suspend fun hasReadPermission(): Boolean = READ_PERMISSION in granted()

    suspend fun hasWritePermission(): Boolean = WRITE_PERMISSION in granted()

    suspend fun hasBackgroundPermission(): Boolean =
        backgroundReadSupported && PERMISSION_READ_BACKGROUND in granted()

    /**
     * Section 4 rule 3: several records on the same day means taking the earliest of
     * the day, on the assumption of a morning weigh-in. That is a real assumption,
     * not a neutral choice, and it is noted in the changelog.
     */
    suspend fun readWeights(from: LocalDate, to: LocalDate): List<WeightEntry> {
        val hc = client ?: return emptyList()
        if (!hasReadPermission()) return emptyList()

        val zone = ZoneId.systemDefault()
        val records = mutableListOf<WeightRecord>()
        var token: String? = null
        do {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDateTime.of(from, java.time.LocalTime.MIN),
                        LocalDateTime.of(to.plusDays(1), java.time.LocalTime.MIN),
                    ),
                    pageToken = token,
                )
            )
            records += response.records
            token = response.pageToken
        } while (token != null)

        return records
            .groupBy { record ->
                val offset = record.zoneOffset ?: zone.rules.getOffset(record.time)
                record.time.atOffset(offset).toLocalDate()
            }
            .mapNotNull { (date, dayRecords) ->
                val earliest = dayRecords.minByOrNull { it.time } ?: return@mapNotNull null
                WeightEntry(
                    date = date,
                    kg = earliest.weight.inKilograms.toFloat(),
                    source = EntrySource.HEALTH_CONNECT,
                    hcRecordId = earliest.metadata.id,
                    recordedAt = earliest.time.toEpochMilli(),
                )
            }
            .sortedBy { it.date }
    }

    /**
     * Optional write-back of a manual entry. Fails quietly: the entry is already
     * saved locally, and the local database is the source of truth.
     */
    suspend fun writeWeight(date: LocalDate, kg: Float, atTime: Instant? = null): Boolean {
        val hc = client ?: return false
        if (!hasWritePermission()) return false
        return runCatching {
            val zone = ZoneId.systemDefault()
            // Today's entry is stamped now; a backdated one is stamped 08:00, the same
            // morning-weigh-in assumption the read path makes (section 4 rule 3).
            val instant = atTime
                ?: if (date == LocalDate.now()) Instant.now()
                else date.atTime(8, 0).atZone(zone).toInstant()
            hc.insertRecords(
                listOf(
                    WeightRecord(
                        time = instant,
                        zoneOffset = zone.rules.getOffset(instant),
                        weight = Mass.kilograms(kg.toDouble()),
                        metadata = Metadata.manualEntry(),
                    )
                )
            )
            true
        }.getOrDefault(false)
    }
}
