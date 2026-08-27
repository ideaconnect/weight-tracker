package tech.idct.weighttracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.Units
import tech.idct.weighttracker.domain.WeightEntry
import tech.idct.weighttracker.domain.WeightUnit
import tech.idct.weighttracker.ui.AppUiState
import tech.idct.weighttracker.ui.Format
import tech.idct.weighttracker.ui.components.WtRowGroup
import tech.idct.weighttracker.ui.theme.RobotoMono
import tech.idct.weighttracker.ui.theme.WtDimens
import tech.idct.weighttracker.ui.theme.WtTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

private data class HistoryRow(
    val entry: WeightEntry,
    /** Day-over-day change against the previous entry, or null for the first one. */
    val delta: Float?,
)

private data class WeekGroup(
    val label: String,
    val averageKg: Float,
    val rows: List<HistoryRow>,
)

/**
 * Section 7 History: grouped by week, newest first. Each group header shows the
 * date range and the weekly average.
 */
@Composable
fun HistoryScreen(
    state: AppUiState,
    onEdit: (WeightEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WtTheme.colors
    val unit = state.settings.unit
    val groups = remember(state.entries) { groupByWeek(state.entries) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = WtDimens.screenPadding,
            end = WtDimens.screenPadding,
            top = 10.dp,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(WtDimens.cardGap),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("History", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text(
                    "${state.entries.size} " + if (state.entries.size == 1) "entry" else "entries",
                    style = TextStyle(fontSize = 12.sp),
                    color = colors.muted,
                )
            }
        }

        if (groups.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(WtDimens.cardRadiusLarge))
                        .background(colors.surface)
                        .border(
                            WtDimens.hairline,
                            colors.outline,
                            RoundedCornerShape(WtDimens.cardRadiusLarge),
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Nothing logged yet", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(
                        "Weights you log, and any Health Connect fills in, appear here week by week.",
                        style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                        color = colors.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        items(groups, key = { it.label }) { group ->
            WtRowGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceAlt)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group.label,
                        style = TextStyle(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp,
                        ),
                        color = colors.onSurface,
                    )
                    Text(
                        "avg ${Units.formatWithUnit(group.averageKg, unit)}",
                        style = TextStyle(fontFamily = RobotoMono, fontSize = 11.5.sp),
                        color = colors.muted,
                    )
                }
                group.rows.forEach { row ->
                    EntryRow(row = row, unit = unit, onClick = { onEdit(row.entry) })
                }
            }
        }
    }
}

@Composable
private fun EntryRow(row: HistoryRow, unit: WeightUnit, onClick: () -> Unit) {
    val colors = WtTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clip(RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            row.entry.date.format(Format.monthDay),
            style = TextStyle(fontFamily = RobotoMono, fontSize = 12.5.sp),
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Fixed dp against sp text wraps a date onto two lines at a large font
            // scale; the spacer below absorbs whatever these need.
            modifier = Modifier.widthIn(min = 50.dp),
        )
        Text(
            Units.format(row.entry.kg, unit),
            style = TextStyle(fontSize = 15.sp),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        // Section 7: day-over-day delta, green down, amber up.
        Text(
            row.delta?.let { delta ->
                val shown = Units.format(abs(delta), unit)
                when {
                    shown.toFloatOrNull() == 0f -> shown
                    delta > 0 -> "+$shown"
                    else -> "−$shown"
                }
            } ?: "—",
            style = TextStyle(fontFamily = RobotoMono, fontSize = 12.5.sp),
            color = when {
                row.delta == null -> colors.muted
                abs(row.delta) < 0.05f -> colors.muted
                row.delta < 0f -> colors.onTrack
                else -> colors.behind
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 52.dp),
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (row.entry.source == EntrySource.MANUAL) colors.surface else colors.surfaceAlt)
                .border(WtDimens.hairline, colors.outline, RoundedCornerShape(9.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                if (row.entry.source == EntrySource.MANUAL) "Manual" else "Sync",
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = colors.muted,
            )
        }
    }
}

/** Weeks run Monday to Sunday, newest first. */
private fun groupByWeek(entries: List<WeightEntry>): List<WeekGroup> {
    if (entries.isEmpty()) return emptyList()
    val rows = entries.mapIndexed { index, entry ->
        HistoryRow(entry, if (index == 0) null else entry.kg - entries[index - 1].kg)
    }
    return rows
        .groupBy { it.entry.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap(compareByDescending<LocalDate> { it })
        .map { (weekStart, weekRows) ->
            val ordered = weekRows.sortedByDescending { it.entry.date }
            WeekGroup(
                label = "${weekStart.format(Format.monthDay)} – ${weekStart.plusDays(6).format(Format.monthDay)}",
                averageKg = weekRows.map { it.entry.kg }.average().toFloat(),
                rows = ordered,
            )
        }
}
