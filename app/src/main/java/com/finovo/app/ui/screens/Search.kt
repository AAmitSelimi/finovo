package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.components.ChipRow
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.SearchHit
import com.finovo.app.util.SearchKind
import com.finovo.app.util.S
import com.finovo.app.util.plurals

@Composable
fun SearchScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val erweitert by viewModel.erweiterteTreffer.collectAsState()

    var filterIndex by remember { mutableStateOf(0) }
    val filters = listOf(S("alle_8", "Alle"), S("ausgaben_7", "Ausgaben"), S("einnahmen_8", "Einnahmen"), S("rechnungen_3", "Rechnungen"))
    val visible = when (filterIndex) {
        1 -> results.filter { it.kind == SearchKind.EXPENSE }
        2 -> results.filter { it.kind == SearchKind.INCOME }
        3 -> results.filter { it.kind == SearchKind.BILL }
        else -> results
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar( tutorialId = "search", title = S("search", "Suche"), subtitle = S("search_sub", "Alles durchsuchen"), onBack = onBack)
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    viewModel.searchQuery.value = it
                    viewModel.erweiterteSuche(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(S("haendler_betrag_kategorie_notiz_2", "Händler, Betrag, Kategorie, Notiz …")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = S("leeren", "Leeren"))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            ChipRow(
                items = filters,
                selectedIndex = filterIndex,
                onSelect = { filterIndex = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        when {
            query.trim().length < 2 -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(appColors().cardAlt),
                            contentAlignment = Alignment.Center
                        ) { Text(S("ud83d_udd0d", "\uD83D\uDD0D"), fontSize = 34.sp) }
                        Spacer(Modifier.height(16.dp))
                        Text(S("wonach_suchst_du", "Wonach suchst du?"), style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            S("gib_mindestens_zwei_zeichen_ein_gesuch", "Gib mindestens zwei Zeichen ein – gesucht wird in Händler, Notiz, Kategorie, Betrag und Datum."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            visible.isEmpty() && erweitert.isEmpty() -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(S("ud83d_ude45", "\uD83D\uDE45"), fontSize = 34.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            S("keine_treffer_fuer_query", "Keine Treffer für „%1\$s“", query),
                            style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            else -> {
                item {
                    Text(
                        plurals("pl_hits", visible.size, "%1\$d Treffer", "%1\$d Treffer"),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
                items(visible, key = { "${it.kind}_${it.id}" }) { hit ->
                    SearchHitRow(
                        hit = hit,
                        symbol = symbol,
                        onClick = {
                            when (hit.kind) {
                                SearchKind.EXPENSE -> viewModel.openExpenseSheet(
                                    viewModel.expenses.value.firstOrNull { it.id == hit.id }
                                )
                                SearchKind.INCOME -> viewModel.openIncomeSheet(
                                    viewModel.incomes.value.firstOrNull { it.id == hit.id }
                                )
                                SearchKind.BILL -> viewModel.openBillSheet(
                                    viewModel.bills.value.firstOrNull { it.id == hit.id }
                                )
                            }
                        }
                    )
                }
                val extra = erweitert.filter {
                    it.gruppe == com.finovo.app.util.Suche.Gruppe.ABO ||
                        it.gruppe == com.finovo.app.util.Suche.Gruppe.ZIEL ||
                        it.gruppe == com.finovo.app.util.Suche.Gruppe.DOKUMENT ||
                        it.gruppe == com.finovo.app.util.Suche.Gruppe.KATEGORIE
                }
                if (extra.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = S("search_more", "Weitere Treffer"),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                    items(extra) { treffer ->
                        AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(treffer.titel, fontWeight = FontWeight.Medium)
                                    Text(
                                        treffer.untertitel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appColors().textMuted
                                    )
                                }
                                treffer.cents?.let {
                                    Text(
                                        Money.format(it, symbol),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(
    hit: SearchHit,
    symbol: String,
    onClick: () -> Unit
) {
    val emoji = when (hit.kind) {
        SearchKind.EXPENSE -> "\uD83D\uDCB8"
        SearchKind.INCOME -> "\uD83D\uDCB0"
        SearchKind.BILL -> "\uD83E\uDDFE"
    }
    AppCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(appColors().cardAlt),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    hit.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    hit.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                Money.format(hit.cents, symbol),
                style = MaterialTheme.typography.titleSmall,
                color = if (hit.cents < 0) appColors().expense else appColors().income,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
    }
}
