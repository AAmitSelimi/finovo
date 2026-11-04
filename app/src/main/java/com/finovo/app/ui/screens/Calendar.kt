package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.CalendarEntry
import com.finovo.app.util.CalendarKind
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import com.finovo.app.util.S
import com.finovo.app.util.str

private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

@Composable
fun CalendarScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val month by viewModel.calendarMonth.collectAsState()
    val entries by viewModel.calendarEntries.collectAsState()
    val buckets by viewModel.calendarBuckets.collectAsState()
    val balance by viewModel.calendarBalance.collectAsState()

    var selectedDay by remember { mutableStateOf<Long?>(null) }

    val income = entries.filter { it.isIncome }.sumOf { it.cents }
    val expense = entries.filter { !it.isIncome }.sumOf { it.cents }
    val today = Dates.today().toEpochDay()
    val selectedEntries = selectedDay?.let { day -> entries.filter { it.epochDay == day } } ?: emptyList()
    val endBalance = balance.lastOrNull()?.second ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar( tutorialId = "calendar", title = S("calendar", "Kalender"), subtitle = S("calendar_sub", "Erwartete Einnahmen & Ausgaben"), onBack = onBack)
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.shiftCalendarMonth(-1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = S("zurueck_2", "Zurück"))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Dates.monthYear(month.atDay(1)),
                        style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        S("entries_size_eintraege", "%1\$s Einträge", entries.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                }
                IconButton(onClick = { viewModel.shiftCalendarMonth(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = S("weiter_2", "Weiter"))
                }
            }
        }

        item {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(S("erwartete_einnahmen", "Erwartete Einnahmen"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                            Text(Money.format(income, symbol), style = MaterialTheme.typography.titleLarge, color = appColors().income,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(S("erwartete_ausgaben", "Erwartete Ausgaben"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                            Text(Money.format(expense, symbol), style = MaterialTheme.typography.titleLarge, color = appColors().expense,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(S("erwarteter_saldo_am_monatsende", "Erwarteter Saldo am Monatsende"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                        Text(
                            Money.format(income - expense, symbol),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (income - expense >= 0) appColors().income else appColors().expense,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        S("prognose_inkl_vorhandenem_guthaben_mon", "Prognose inkl. vorhandenem Guthaben: %1\$s", Money.format(endBalance, symbol)),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                }
            }
        }

        item {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        WEEKDAYS.forEach { d ->
                            Text(
                                d,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val first = month.atDay(1)
                    val offset = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
                    val days = month.lengthOfMonth()
                    var dayCounter = 1
                    for (week in 0..5) {
                        if (dayCounter > days) break
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (col in 0..6) {
                                val index = week * 7 + col
                                if (index < offset || dayCounter > days) {
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                } else {
                                    val day = dayCounter
                                    val epochDay = month.atDay(day).toEpochDay()
                                    val bucket = buckets[epochDay]
                                    val net = bucket?.netCents ?: 0L
                                    val isSelected = selectedDay == epochDay
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                    epochDay == today -> appColors().cardAlt
                                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                                }
                                            )
                                            .clickable { selectedDay = if (isSelected) null else epochDay },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                day.toString(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (epochDay == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (bucket != null) {
                                                Text(
                                                    if (kotlin.math.abs(net) >= 100_000) Money.formatCompact(net) else Money.format(net, "", withSymbol = false),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 8.sp,
                                                    color = if (net >= 0) appColors().income else appColors().expense,
                                                    maxLines = 1
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (net >= 0) appColors().income else appColors().expense
                                                        )
                                                )
                                            }
                                        }
                                    }
                                    dayCounter++
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedEntries.isNotEmpty()) {
            item {
                SectionHeader(title = S("entries_on_day", "Einträge am %1\$s", selectedDay?.let { Dates.full(Dates.fromEpochDay(it)) } ?: ""))
            }
            items(selectedEntries) { entry ->
                CalendarEntryRow(entry = entry, symbol = symbol)
            }
        } else {
            item {
                SectionHeader(title = S("alle_termine_im_monat", "Alle Termine im Monat"))
            }
            if (entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            S("fuer_diesen_monat_sind_keine_termine_g", "Für diesen Monat sind keine Termine geplant."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted,
                            textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                items(entries.take(40)) { entry ->
                    CalendarEntryRow(entry = entry, symbol = symbol)
                }
            }
        }
    }
}

@Composable
private fun CalendarEntryRow(entry: CalendarEntry, symbol: String) {
    val emoji = when (entry.kind) {
        CalendarKind.BILL -> "\uD83E\uDDFE"
        CalendarKind.RECURRING_INCOME -> "\uD83D\uDD04"
        CalendarKind.RECURRING_EXPENSE -> "\uD83D\uDD04"
        CalendarKind.SUBSCRIPTION -> "\uD83D\uDD01"
        CalendarKind.TRANSACTION -> entry.emoji.ifBlank { "\uD83D\uDCB8" }
    }
    val kindLabel = when (entry.kind) {
        CalendarKind.BILL -> S("rechnung_2", "Rechnung")
        CalendarKind.RECURRING_INCOME -> str("fallback_recurring", "Wiederkehrend")
        CalendarKind.RECURRING_EXPENSE -> str("fallback_recurring", "Wiederkehrend")
        CalendarKind.SUBSCRIPTION -> "Abo"
        CalendarKind.TRANSACTION -> "Gebucht"
    }
    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors().cardAlt),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 17.sp) }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    S("dates_short_dates_fromepochday_entry_e", "%1\$s · %2\$s", Dates.short(Dates.fromEpochDay(entry.epochDay)), kindLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
            Text(
                (if (entry.isIncome) "+" else "−") + Money.format(entry.cents, symbol),
                style = MaterialTheme.typography.titleSmall,
                color = if (entry.isIncome) appColors().income else appColors().expense,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
    }
}
