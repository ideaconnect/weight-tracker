package tech.idct.weighttracker.data.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
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
         * Section 2: background health reads need Android 15 (API 35) or the Health
         * Connect APK equivalent, so the feature degrades gracefully below it.
         */
        val backgroundReadSupported: Boolean get() = Build.VERSION.SDK_INT >= 35
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
