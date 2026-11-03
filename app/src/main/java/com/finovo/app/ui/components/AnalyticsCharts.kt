package com.finovo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Analytics
import com.finovo.app.util.Bucket
import com.finovo.app.util.CategoryStat
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel
import com.finovo.app.util.str
import com.finovo.app.util.AppLocale
import com.finovo.app.util.plurals

/** Label step so axes stay readable on small screens. */
private fun labelStep(count: Int): Int = when {
    count <= 8 -> 1
    count <= 16 -> 2
    count <= 40 -> 4
    else -> 8
}

/** More bars than this can neither be drawn nor read - show a hint instead. */
private const val MAX_BARS = 120

@Composable
private fun ChartTooLarge() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            S("chart_range_too_large", "Zeitraum zu groß für diese Darstellung. Bitte eine gröbere Gruppierung wählen."),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted,
            textAlign = TextAlign.Center
        )
    }
}

private fun bucketLabel(bucket: Bucket, short: Boolean): String =
    if (short) bucket.label else bucket.label

// ---------------------------------------------------------------------------
// Grouped bar chart: income vs expenses per bucket (tappable)
// ---------------------------------------------------------------------------

@Composable
fun BarIncomeExpenseChart(
    buckets: List<Bucket>,
    onSelect: (Bucket) -> Unit,
    symbol: String,
    modifier: Modifier = Modifier,
    granularityLabel: String = ""
) {
    if (buckets.isEmpty()) {
        ChartEmpty(S("keine_daten_im_gewaehlten_zeitraum", "Keine Daten im gewählten Zeitraum."))
        return
    }
    if (buckets.size > MAX_BARS) {
        ChartTooLarge()
        return
    }
    val max = buckets.maxOf { maxOf(it.incomeCents, it.expenseCents) }.coerceAtLeast(1)
    var selected by remember { mutableStateOf<Bucket?>(null) }
    val step = labelStep(buckets.size)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            buckets.forEachIndexed { index, bucket ->
                val highlighted = selected?.key == bucket.key
                val incomeRatio = (bucket.incomeCents.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                val expenseRatio = (bucket.expenseCents.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                val animIncome by animateFloatAsState(
                    targetValue = incomeRatio, animationSpec = tween(450), label = S("in_index", "in%1\$s", index)
                )
                val animExpense by animateFloatAsState(
                    targetValue = expenseRatio, animationSpec = tween(450), label = S("ex_index", "ex%1\$s", index)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            selected = bucket
                            onSelect(bucket)
                        }
                        .then(
                            if (highlighted) {
                                Modifier.background(
                                    appColors().cardAlt,
                                    RoundedCornerShape(8.dp)
                                )
                            } else Modifier
                        )
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ChartBar(
                            ratio = animIncome,
                            color = appColors().income,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        ChartBar(
                            ratio = animExpense,
                            color = appColors().expense,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            buckets.forEachIndexed { index, bucket ->
                Text(
                    text = if (index % step == 0 || index == buckets.lastIndex) bucket.label else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            LegendDot(appColors().income, S("einnahmen_2", "Einnahmen"))
            Spacer(Modifier.width(18.dp))
            LegendDot(appColors().expense, S("ausgaben_2", "Ausgaben"))
        }
    }
}

@Composable
private fun ChartBar(ratio: Float, color: Color, modifier: Modifier = Modifier) {
    val safe = if (ratio <= 0f) 0f else ratio.coerceIn(0.02f, 1f)
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(safe)
        ) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(color, color.copy(alpha = 0.72f))
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    size.minDimension / 2, size.minDimension / 2
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Trend / line chart
// ---------------------------------------------------------------------------

@Composable
fun TrendLineChart(
    buckets: List<Bucket>,
    onSelect: (Bucket) -> Unit,
    symbol: String,
    averageCents: Long,
    modifier: Modifier = Modifier,
    useIncome: Boolean = false
) {
    if (buckets.isEmpty()) {
        ChartEmpty(S("keine_daten_fuer_den_trend_verfueg", "Keine Daten für den Trend verfügbar."))
        return
    }
    if (buckets.size > MAX_BARS) {
        ChartTooLarge()
        return
    }
    val values = buckets.map { if (useIncome) it.incomeCents else it.expenseCents }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(averageCents).coerceAtLeast(1)
    val lineColor = if (useIncome) appColors().income else appColors().expense
    val step = labelStep(buckets.size)
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = tween(600), label = S("line", "line"))
    val mutedColor = appColors().textMuted

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (buckets.size == 1) {
                    val y = size.height - (values[0].toFloat() / max.toFloat()) * size.height
                    drawCircle(color = lineColor, radius = 8.dp.toPx(), center = Offset(size.width / 2, y))
                    return@Canvas
                }
                val dx = size.width / (buckets.size - 1).toFloat()
                val points = values.mapIndexed { index, value ->
                    Offset(
                        x = index * dx,
                        y = size.height - (value.toFloat() / max.toFloat()) * (size.height - 12.dp.toPx())
                    )
                }
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                }
                val fill = Path().apply {
                    addPath(path)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    brush = Brush.verticalGradient(
                        listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
                    ),
                    path = fill
                )
                drawPath(
                    color = lineColor,
                    path = path,
                    style = Stroke(width = 3.dp.toPx() * progress)
                )
                points.forEach { point ->
                    drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = point)
                }
                // average line
                val avgY = size.height -
                    (averageCents.toFloat() / max.toFloat()) * (size.height - 12.dp.toPx())
                drawLine(
                    color = mutedColor.copy(alpha = 0.6f),
                    start = Offset(0f, avgY),
                    end = Offset(size.width, avgY),
                    strokeWidth = 1.5f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(8f, 8f), 0f
                    )
                )
            }
            // tap targets
            Row(modifier = Modifier.fillMaxSize()) {
                buckets.forEach { bucket ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(bucket) }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            buckets.forEachIndexed { index, bucket ->
                Text(
                    text = if (index % step == 0 || index == buckets.lastIndex) bucket.label else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Donut with tappable slices
// ---------------------------------------------------------------------------

@Composable
fun AnalyticsDonut(
    stats: List<CategoryStat>,
    centerLabel: String,
    centerValue: String,
    onSelect: (CategoryStat) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = appColors().chartPalette
) {
    if (stats.isEmpty()) {
        ChartEmpty(S("keine_daten_fuer_die_verteilung_vo", "Keine Daten für die Verteilung vorhanden."))
        return
    }
    val sum = stats.sumOf { it.cents }.coerceAtLeast(1)
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = tween(600), label = S("donut", "donut"))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stats) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = min(size.width, size.height) / 2f
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (distance < radius * 0.45f || distance > radius) return@detectTapGestures
                        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var start = 0f
                        for (stat in stats) {
                            val sweep = (stat.cents.toFloat() / sum.toFloat()) * 360f
                            if (angle >= start && angle < start + sweep) {
                                onSelect(stat)
                                return@detectTapGestures
                            }
                            start += sweep
                        }
                    }
                }
        ) {
            val strokeWidth = 30.dp.toPx()
            val diameter = min(size.width, size.height) - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            var startAngle = -90f
            stats.forEachIndexed { index, stat ->
                val sweep = (stat.cents.toFloat() / sum.toFloat()) * 360f * progress
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0f),
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

// ---------------------------------------------------------------------------
// Category ranking bars (tappable)
// ---------------------------------------------------------------------------

@Composable
fun CategoryRankingBars(
    stats: List<CategoryStat>,
    symbol: String,
    onSelect: (CategoryStat) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = appColors().chartPalette
) {
    if (stats.isEmpty()) {
        ChartEmpty(S("keine_kategorien_im_gewaehlten_zei", "Keine Kategorien im gewählten Zeitraum."))
        return
    }
    val max = stats.maxOf { it.cents }.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        stats.forEachIndexed { index, stat ->
            val ratio = (stat.cents.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            val width by animateFloatAsState(
                targetValue = ratio, animationSpec = tween(450), label = S("bar_index", "bar%1\$s", index)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(stat) }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stat.emoji, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            categoryLabel(stat.name),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            Money.format(stat.cents, symbol),
                            style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(9.dp)
                            .background(appColors().cardAlt, RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(width.coerceIn(0.01f, 1f))
                                .height(9.dp)
                                .background(
                                    colors[index % colors.size],
                                    RoundedCornerShape(5.dp)
                                )
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${"%.1f".format(AppLocale.locale(), stat.percent * 100)} % · " +
                            plurals("pl_bookings", stat.count, "%1\$d Buchung", "%1\$d Buchungen"),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Net / savings chart with zero line
// ---------------------------------------------------------------------------

@Composable
fun NetBarsChart(
    buckets: List<Bucket>,
    onSelect: (Bucket) -> Unit,
    symbol: String,
    modifier: Modifier = Modifier
) {
    if (buckets.isEmpty()) {
        ChartEmpty(S("keine_daten_verfuegbar", "Keine Daten verfügbar."))
        return
    }
    if (buckets.size > MAX_BARS) {
        ChartTooLarge()
        return
    }
    val maxAbs = buckets.maxOf { kotlin.math.abs(it.netCents) }.coerceAtLeast(1)
    val step = labelStep(buckets.size)
    val dividerColor = appColors().divider
    val incomeColor = appColors().income
    val expenseColor = appColors().expense
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val zeroY = size.height / 2f
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.5f
                )
                val slot = size.width / buckets.size
                buckets.forEachIndexed { index, bucket ->
                    val ratio = bucket.netCents.toFloat() / maxAbs.toFloat()
                    val barHeight = (kotlin.math.abs(ratio) * (size.height / 2f - 10.dp.toPx()))
                        .coerceAtLeast(2f)
                    val color = if (bucket.netCents >= 0) incomeColor else expenseColor
                    val left = index * slot + slot * 0.22f
                    val width = slot * 0.56f
                    val top = if (ratio >= 0) zeroY - barHeight else zeroY
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(width, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
            Row(modifier = Modifier.fillMaxSize()) {
                buckets.forEach { bucket ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(bucket) }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            buckets.forEachIndexed { index, bucket ->
                Text(
                    text = if (index % step == 0 || index == buckets.lastIndex) bucket.label else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChartEmpty(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().textMuted,
            textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(color = color) }
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
    }
}

/** Compact detail line for a selected bucket. */
@Composable
fun BucketDetail(bucket: Bucket, symbol: String, granularityLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors().cardAlt, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            S("granularitylabel_dates_full_bucket_sta", "%2\$s: %1\$s", Dates.full(bucket.start), granularityLabel) +
                if (bucket.start != bucket.end) " – ${Dates.full(bucket.end)}" else "",
            style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                S("einnahmen_money_format_bucket_incomece", "Einnahmen %1\$s", Money.format(bucket.incomeCents, symbol)),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().income,
                modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
            Text(
                S("ausgaben_money_format_bucket_expensece", "Ausgaben %1\$s", Money.format(bucket.expenseCents, symbol)),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().expense,
                modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            S("uebrig_money_format_bucket_netcents_sy", "Übrig %1\$s", Money.format(bucket.netCents, symbol)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

fun granularityLabelGerman(granularity: com.finovo.app.util.Granularity): String = when (granularity) {
    com.finovo.app.util.Granularity.DAILY -> str("gran_day", "Tag")
    com.finovo.app.util.Granularity.WEEKLY -> str("gran_week", "Woche")
    com.finovo.app.util.Granularity.MONTHLY -> str("monat", "Monat")
    com.finovo.app.util.Granularity.YEARLY -> str("jahr", "Jahr")
}
