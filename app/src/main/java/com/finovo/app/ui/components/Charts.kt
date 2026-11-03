package com.finovo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.data.CategoryTotal
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.MonthPoint
import com.finovo.app.util.Money
import com.finovo.app.util.Dates
import com.finovo.app.util.Stats
import kotlin.math.min
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import androidx.compose.ui.geometry.CornerRadius
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel

/** Clean donut chart with the total amount in the middle. */
@Composable
fun DonutChart(
    totals: List<CategoryTotal>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = appColors().chartPalette
) {
    if (totals.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                S("noch_keine_ausgaben_in_diesem_zeitraum", "Noch keine Ausgaben in diesem Zeitraum."),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors().textMuted,
                textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
        return
    }
    val palette = colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 30.dp.toPx()
            val diameter = min(size.width, size.height) - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            var startAngle = -90f
            val sum = totals.sumOf { it.cents }.coerceAtLeast(1)
            totals.forEachIndexed { index, total ->
                val sweep = (total.cents.toFloat() / sum.toFloat()) * 360f
                drawArc(
                    color = palette[index % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep - 1.6f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth)
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerLabel,
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
            Text(
                centerValue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoryLegend(
    totals: List<CategoryTotal>,
    symbol: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = appColors().chartPalette
) {
    Column(modifier = modifier.fillMaxWidth()) {
        totals.forEachIndexed { index, total ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .padding(end = 0.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = colors[index % colors.size])
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(total.emoji, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    categoryLabel(total.name),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    String.format(java.util.Locale.GERMANY, "%.0f%%", total.percent * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    modifier = Modifier.padding(end = 10.dp)
                )
                AmountText(
                    cents = total.cents,
                    symbol = symbol,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

/** Grouped bars: income vs. expenses per month. */
@Composable
fun MonthlyBarsChart(
    series: List<MonthPoint>,
    modifier: Modifier = Modifier,
    incomeColor: Color = appColors().income,
    expenseColor: Color = appColors().expense
) {
    if (series.isEmpty()) return
    val max = series.maxOf { maxOf(it.incomeCents, it.expenseCents) }.coerceAtLeast(100)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            series.forEach { point ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Bar(
                            value = point.incomeCents / max.toFloat(),
                            color = incomeColor,
                            modifier = Modifier
                                .width(13.dp)
                                .height(120.dp)
                        )
                        Bar(
                            value = point.expenseCents / max.toFloat(),
                            color = expenseColor,
                            modifier = Modifier
                                .width(13.dp)
                                .height(120.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            series.forEach { point ->
                Text(
                    Dates.chartMonth(point.month.atDay(1)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            LegendDot(incomeColor, S("einnahmen_3", "Einnahmen"))
            Spacer(Modifier.width(18.dp))
            LegendDot(expenseColor, S("ausgaben_3", "Ausgaben"))
        }
    }
}

@Composable
private fun Bar(value: Float, color: Color, modifier: Modifier = Modifier) {
    val safe = value.coerceIn(0.02f, 1f)
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(fraction = safe)
        ) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(color, color.copy(alpha = 0.75f))
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    size.minDimension / 2,
                    size.minDimension / 2
                )
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .padding(1.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(color = color) }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
    }
}

/** Simple horizontal bar used for the budget card. */
@Composable
fun HorizontalBar(
    percent: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = appColors().divider
    val safe = percent.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = trackColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    size.minDimension / 2,
                    size.minDimension / 2
                )
            )
            if (safe > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(size.width * safe, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        size.minDimension / 2,
                        size.minDimension / 2
                    )
                )
            }
        }
    }
}
