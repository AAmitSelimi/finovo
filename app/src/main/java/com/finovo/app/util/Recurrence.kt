package com.finovo.app.util

import com.finovo.app.data.Frequency
import com.finovo.app.data.RecurringRuleEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import com.finovo.app.util.str
import com.finovo.app.util.plurals

/**
 * Pure recurrence math.
 *
 * Occurrences are always derived from the rule (never stored in advance for bills),
 * and generated rows for expenses/income are protected by a unique
 * (recurringId, occurrenceDate) index, so a payment can never be duplicated.
 */
data class RecurrenceSpec(
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val customDays: Int = 30,
    val dayOfMonth: Int = 1,
    val monthOfYear: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null
) {
    companion object {
        fun from(rule: RecurringRuleEntity) = RecurrenceSpec(
            frequency = runCatching { Frequency.valueOf(rule.frequency) }.getOrDefault(Frequency.MONTHLY),
            interval = rule.interval.coerceAtLeast(1),
            customDays = rule.customDays.coerceAtLeast(1),
            dayOfMonth = rule.dayOfMonth.coerceIn(1, 31),
            monthOfYear = rule.monthOfYear.coerceIn(1, 12),
            startDate = Dates.fromEpochDay(rule.startDate),
            endDate = rule.endDate?.let { Dates.fromEpochDay(it) }
        )
    }
}

object Recurrence {

    private const val MAX_RESULTS = 400

    fun stepDays(spec: RecurrenceSpec): Long = when (spec.frequency) {
        Frequency.DAILY -> spec.interval.coerceAtLeast(1).toLong()
        Frequency.WEEKLY -> spec.interval.coerceAtLeast(1).toLong() * 7L
        Frequency.CUSTOM -> spec.customDays.coerceAtLeast(1).toLong()
        Frequency.MONTHLY, Frequency.YEARLY -> 0L
    }

    fun occurrences(
        spec: RecurrenceSpec,
        fromInclusive: LocalDate,
        toInclusive: LocalDate
    ): List<LocalDate> {
        if (toInclusive < fromInclusive) return emptyList()
        val hardEnd = spec.endDate?.let { if (it < toInclusive) it else toInclusive } ?: toInclusive
        val start = maxOf(fromInclusive, spec.startDate)
        if (hardEnd < start) return emptyList()

        val out = ArrayList<LocalDate>()
        when (spec.frequency) {
            Frequency.DAILY, Frequency.WEEKLY, Frequency.CUSTOM -> {
                val step = stepDays(spec)
                var current = spec.startDate
                if (current < start) {
                    val diff = ChronoUnit.DAYS.between(current, start)
                    val steps = diff / step + if (diff % step == 0L) 0L else 1L
                    current = Dates.plusTage(current, Dates.mal(steps, step))
                }
                while (current <= hardEnd && out.size < MAX_RESULTS * 10) {
                    out.add(current)
                    val naechstes = Dates.plusTage(current, step)
                    if (naechstes <= current) break // Kalenderende erreicht
                    current = naechstes
                }
            }

            Frequency.MONTHLY -> {
                val base = YearMonth.from(spec.startDate)
                val interval = spec.interval.coerceAtLeast(1)
                var n = 0
                while (n < 1200 && out.size < MAX_RESULTS * 10) {
                    val ym = YearMonth.from(
                        Dates.plusMonate(base.atDay(1), Dates.mal(n.toLong(), interval.toLong()))
                    )
                    val day = min(spec.dayOfMonth, ym.lengthOfMonth())
                    val date = ym.atDay(day)
                    if (date > hardEnd) break
                    if (date >= start && date >= spec.startDate) out.add(date)
                    n++
                }
            }

            Frequency.YEARLY -> {
                val interval = spec.interval.coerceAtLeast(1)
                var n = 0
                while (n < 200 && out.size < MAX_RESULTS * 10) {
                    val year = runCatching {
                        Math.addExact(
                            spec.startDate.year,
                            Math.toIntExact(Dates.mal(n.toLong(), interval.toLong()))
                        )
                    }.getOrNull() ?: break
                    val date = runCatching {
                        val length = YearMonth.of(year, spec.monthOfYear).lengthOfMonth()
                        LocalDate.of(year, spec.monthOfYear, min(spec.dayOfMonth, length))
                    }.getOrNull() ?: break
                    if (date > hardEnd) break
                    if (date >= start && date >= spec.startDate) out.add(date)
                    n++
                }
            }
        }
        return out
    }

    /** First occurrence strictly after [after] (null if the rule has ended). */
    fun nextAfter(spec: RecurrenceSpec, after: LocalDate): LocalDate? {
        val horizon = Dates.plusJahre(after, 30)
        if (spec.endDate != null && spec.endDate <= after) return null
        return occurrences(spec, after.plusDays(1), horizon).firstOrNull()
    }

    /** Next occurrence on or after [from]. */
    fun nextOnOrAfter(spec: RecurrenceSpec, from: LocalDate): LocalDate? {
        val horizon = Dates.plusJahre(from, 30)
        return occurrences(spec, from, horizon).firstOrNull()
    }

    fun nextOccurrences(spec: RecurrenceSpec, from: LocalDate, count: Int): List<LocalDate> {
        val horizon = from.plusYears(30)
        return occurrences(spec, from, horizon).take(count)
    }

    /** Human readable German description, e.g. str("jeden_monat_am_1", "Jeden Monat am 1.") */
    fun describe(spec: RecurrenceSpec): String {
        val interval = spec.interval.coerceAtLeast(1)
        return when (spec.frequency) {
            Frequency.DAILY -> if (interval == 1) str("rec_daily", "Täglich") else plurals("pl_every_n_days", interval, "Alle %1\$d Tag", "Alle %1\$d Tage")
            Frequency.WEEKLY -> if (interval == 1) str("rec_weekly", "Wöchentlich") else str("rec_every_n_weeks", "Alle %1\$s Wochen", interval)
            Frequency.MONTHLY -> if (interval == 1) str("jeden_monat_am", "Jeden Monat am %1\$s.", spec.dayOfMonth)
            else str("rec_every_n_months_on", "Alle %1\$s Monate am %2\$s.", interval, spec.dayOfMonth)
            Frequency.YEARLY -> if (interval == 1) str("rec_yearly_on", "Jährlich am %1\$s.%2\$s.", spec.dayOfMonth, spec.monthOfYear)
            else str("rec_every_n_years_on", "Alle %1\$s Jahre am %2\$s.%3\$s.", interval, spec.dayOfMonth, spec.monthOfYear)
            Frequency.CUSTOM -> plurals("pl_every_n_days", spec.customDays.coerceAtLeast(1), "Alle %1\$d Tag", "Alle %1\$d Tage")
        }
    }

    fun frequencyLabel(frequency: Frequency): String = when (frequency) {
        Frequency.DAILY -> str("rec_daily", "Täglich")
        Frequency.WEEKLY -> str("rec_weekly", "Wöchentlich")
        Frequency.MONTHLY -> str("monatlich_3", "Monatlich")
        Frequency.YEARLY -> str("rec_yearly", "Jährlich")
        Frequency.CUSTOM -> str("rec_custom", "Benutzerdefiniert")
    }
}
