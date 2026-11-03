package com.finovo.app.util

import com.finovo.app.data.ExpenseEntity
import com.finovo.app.util.str

// ---------------------------------------------------------------------------
// Car module (optional costs around a vehicle)
// ---------------------------------------------------------------------------

object CarCosts {

    data class Type(val key: String, val name: String, val emoji: String, val nameKey: String)

    val types: List<Type> = listOf(
        Type("fuel", "Sprit", "\u26FD", "mod_fuel"),
        Type("insurance", "Versicherung", "\uD83D\uDEE1", "mod_insurance"),
        Type("tax", "Kfz-Steuer", "\uD83C\uDFDB", "mod_tax"),
        Type("repair", "Reparatur", "\uD83D\uDD27", "mod_repair"),
        Type("service", "Wartung", "\uD83D\uDEE0", "mod_service"),
        Type("tuv", "TÜV / AU", "\uD83D\uDCCB", "mod_tuv"),
        Type("tires", "Reifen", "\uD83C\uDFDE", "mod_tires"),
        Type("oil", "Ölwechsel", "\uD83D\uDEE2", "mod_oil"),
        Type("wash", "Wäsche", "\uD83E\uDDFC", "mod_wash"),
        Type("financing", "Finanzierung", "\uD83D\uDE97", "mod_financing")
    )

    val names: Set<String> = types.map { it.name }.toSet()

    /** Localised display name; the stored value stays German. */
    fun labelFor(name: String): String {
        val type = types.firstOrNull { it.name == name } ?: return name
        return str(type.nameKey, type.name)
    }

    fun emojiFor(name: String): String = types.firstOrNull { it.name == name }?.emoji ?: "\uD83D\uDE97"
}

// ---------------------------------------------------------------------------
// Summaries for the new modules
// ---------------------------------------------------------------------------

data class AreaTotals(
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val balanceCents: Long = 0,
    val count: Int = 0
)

data class GoalSummary(
    val count: Int = 0,
    val targetCents: Long = 0,
    val savedCents: Long = 0
) {
    val percent: Float
        get() = if (targetCents <= 0L) 0f else (savedCents.toFloat() / targetCents.toFloat()).coerceIn(0f, 1f)
    val remainingCents: Long
        get() = (targetCents - savedCents).coerceAtLeast(0)
}

data class SubscriptionSummary(
    val count: Int = 0,
    val monthlyCents: Long = 0,
    val yearlyCents: Long = 0
)

data class CategoryBudgetProgress(
    val categoryId: Long = 0,
    val name: String = "",
    val emoji: String = "",
    val limitCents: Long = 0,
    val spentCents: Long = 0,
    val percent: Float = 0f
) {
    val over: Boolean get() = spentCents > limitCents
    val near: Boolean get() = !over && percent >= 0.8f
}

data class CarTotal(
    val categoryId: Long = 0,
    val name: String = "",
    val emoji: String = "",
    val monthlyCents: Long = 0,
    val yearlyCents: Long = 0,
    val count: Int = 0
)

data class CarSummary(
    val available: Boolean = false,
    val monthlyCents: Long = 0,
    val yearlyCents: Long = 0,
    val entries: List<CarTotal> = emptyList(),
    val lastEntries: List<ExpenseEntity> = emptyList()
)

// ---------------------------------------------------------------------------
// Global search
// ---------------------------------------------------------------------------

enum class SearchKind { EXPENSE, INCOME, BILL }

data class SearchHit(
    val id: Long = 0,
    val kind: SearchKind = SearchKind.EXPENSE,
    val title: String = "",
    val subtitle: String = "",
    val cents: Long = 0,
    val date: Long = 0
)

// ---------------------------------------------------------------------------
// Saving-goal helpers
// ---------------------------------------------------------------------------

object Goals {

    /** How much has to be put aside every month to reach the target in time. */
    fun requiredMonthlyCents(remainingCents: Long, targetEpochDay: Long?, from: java.time.LocalDate): Long {
        if (remainingCents <= 0L) return 0L
        val target = targetEpochDay?.let { Dates.fromEpochDay(it) }
        val months = if (target == null) 12L else {
            val total = (target.year - from.year) * 12L + (target.monthValue - from.monthValue)
            if (target.dayOfMonth >= from.dayOfMonth) total + 1 else total
        }.coerceAtLeast(1L)
        val whole = remainingCents / months
        return if (remainingCents % months == 0L) whole else whole + 1L
    }

    fun daysLeft(targetEpochDay: Long?, from: java.time.LocalDate): Long? =
        targetEpochDay?.let { Dates.daysUntil(Dates.fromEpochDay(it), from) }
}
