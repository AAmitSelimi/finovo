package com.finovo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillPaymentEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import java.time.LocalDate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import androidx.compose.ui.text.font.FontWeight
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel
import com.finovo.app.util.str

@Composable
fun ExpenseRow(
    expense: ExpenseEntity,
    category: CategoryEntity?,
    symbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors().card)
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(category?.emoji ?: "📦")
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                categoryLabel(category?.name ?: "Sonstiges"),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Dates.full(Dates.fromEpochDay(expense.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
                if (!expense.paid) {
                    Spacer(Modifier.width(6.dp))
                    Pill(S("pill_open", "Offen"), appColors().warning, appColors().warningContainer)
                }
                if (expense.recurringId != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill("↻", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                }
            }
            if (expense.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    expense.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                cents = -expense.amountCents,
                symbol = symbol,
                style = MaterialTheme.typography.titleSmall,
                color = appColors().expense
            )
            Row {
                IconActionButton(Icons.Default.Edit, str("bearbeiten", "Bearbeiten"), onEdit, tint = appColors().textMuted)
                IconActionButton(Icons.Default.Delete, S("loeschen", "Löschen"), onDelete, tint = appColors().textMuted)
            }
        }
    }
}

@Composable
fun IncomeRow(
    income: IncomeEntity,
    category: CategoryEntity?,
    symbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors().card)
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(category?.emoji ?: "💰")
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                categoryLabel(category?.name ?: S("sonstige_einnahmen", "Sonstige Einnahmen")),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Dates.full(Dates.fromEpochDay(income.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
                if (income.recurringId != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill("↻", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                }
            }
            if (income.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    income.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                cents = income.amountCents,
                symbol = symbol,
                style = MaterialTheme.typography.titleSmall,
                color = appColors().income
            )
            Row {
                IconActionButton(Icons.Default.Edit, str("bearbeiten", "Bearbeiten"), onEdit, tint = appColors().textMuted)
                IconActionButton(Icons.Default.Delete, S("loeschen_2", "Löschen"), onDelete, tint = appColors().textMuted)
            }
        }
    }
}

@Composable
fun BillRow(
    bill: BillEntity,
    category: CategoryEntity?,
    symbol: String,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overdue = bill.status != "PAID" && bill.dueDate < today.toEpochDay()
    val days = bill.dueDate - today.toEpochDay()
    val statusText = when {
        bill.status == "PAID" -> str("bill_paid", "Bezahlt")
        overdue -> str("due_overdue", "Überfällig")
        days == 0L -> str("due_today", "Heute fällig")
        days == 1L -> str("due_tomorrow", "Morgen fällig")
        else -> plurals("pl_in_days", days.toInt(), "in %1\$d Tag", "in %1\$d Tagen")
    }
    val statusColor = when {
        bill.status == "PAID" -> appColors().income
        overdue -> appColors().expense
        days <= 3 -> appColors().warning
        else -> appColors().textMuted
    }
    val statusBackground = when {
        bill.status == "PAID" -> appColors().incomeContainer
        overdue -> appColors().expenseContainer
        days <= 3 -> appColors().warningContainer
        else -> appColors().cardAlt
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors().card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(category?.emoji ?: "🧾")
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                bill.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Dates.short(Dates.fromEpochDay(bill.dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
                Spacer(Modifier.width(6.dp))
                Pill(statusText, statusColor, statusBackground)
                if (bill.isRecurring) {
                    Spacer(Modifier.width(6.dp))
                    Pill("↻", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        AutoSizeAmountText(
            cents = bill.amountCents,
            symbol = symbol,
            maxFontSize = MaterialTheme.typography.titleSmall.fontSize,
            minFontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (bill.status == "PAID") appColors().income else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 120.dp)
        )
    }
}

@Composable
fun PaymentRow(
    payment: BillPaymentEntity,
    symbol: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors().card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(appColors().incomeContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = appColors().income, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                payment.billName.ifBlank { S("rechnung", "Rechnung") },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                S("bezahlt_am_dates_full_dates_fromepochd", "Bezahlt am %1\$s", Dates.full(Dates.fromEpochDay(payment.paidDate))),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        }
        Spacer(Modifier.width(8.dp))
        AutoSizeAmountText(
            cents = payment.amountCents,
            symbol = symbol,
            maxFontSize = MaterialTheme.typography.titleSmall.fontSize,
            minFontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = appColors().income,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 112.dp)
        )
        IconActionButton(Icons.Default.Delete, str("action_remove_payment", "Zahlung entfernen"), onDelete, tint = appColors().textMuted)
    }
}

@Composable
fun TxRow(item: com.finovo.app.util.TxItem, symbol: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors().card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(item.emoji)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                Dates.full(item.date) + (if (item.note.isNotBlank()) " · ${item.note}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        AutoSizeAmountText(
            cents = if (item.isIncome) item.amountCents else -item.amountCents,
            symbol = symbol,
            signed = true,
            maxFontSize = MaterialTheme.typography.titleSmall.fontSize,
            minFontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (item.isIncome) appColors().income else appColors().expense,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 128.dp)
        )
    }
}

@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = appColors().textMuted,
        modifier = modifier.padding(horizontal = 22.dp, vertical = 8.dp)
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(appColors().card)
            .padding(vertical = 8.dp)
    ) {
        content()
    }
}
