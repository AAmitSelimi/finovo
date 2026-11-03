package com.finovo.app.util

import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType

/** Default categories that every fresh installation receives. */
object DefaultCategories {

    val expenses = listOf(
        "Miete" to "🏠",
        "Strom" to "⚡",
        "Gas" to "🔥",
        "Auto" to "🚗",
        "Tanken" to "⛽",
        "Handy" to "📱",
        "Internet" to "🌐",
        "Lebensmittel" to "🛒",
        "Versicherung" to "🛡️",
        "Kredit" to "💳",
        "Familie" to "👨‍👩‍👧",
        "Shopping" to "🛍️",
        "Gesundheit" to "🏥",
        "Wasser" to "💧",
        "Telefon" to "📱",
        "Kleidung" to "👕",
        "Freizeit" to "🎡",
        "Abos" to "🔁",
        "Reisen" to "✈️",
        "Schule/Kinder" to "🏫",
        "Sonstiges" to "📦"
    )

    val incomes = listOf(
        "Gehalt" to "💼",
        "Kindergeld" to "👨‍👩‍👧",
        "Sonstige Einnahmen" to "💰"
    )

    /** Categories that count as fixed costs (Fixkosten). */
    private val fixed = setOf("Miete", "Strom", "Gas", "Versicherung", "Kredit", "Internet", "Handy")

    fun build(): List<CategoryEntity> {
        val out = ArrayList<CategoryEntity>()
        expenses.forEachIndexed { index, (name, emoji) ->
            out.add(
                CategoryEntity(
                    name = name,
                    emoji = emoji,
                    type = CategoryType.EXPENSE.name,
                    isFixed = name in fixed,
                    isCustom = false,
                    sortOrder = index
                )
            )
        }
        incomes.forEachIndexed { index, (name, emoji) ->
            out.add(
                CategoryEntity(
                    name = name,
                    emoji = emoji,
                    type = CategoryType.INCOME.name,
                    isFixed = false,
                    isCustom = false,
                    sortOrder = index
                )
            )
        }
        return out
    }
}
