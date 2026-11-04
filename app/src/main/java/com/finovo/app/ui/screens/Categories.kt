package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.ui.components.CategoryBadge
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.IconActionButton
import com.finovo.app.ui.components.Pill
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.theme.appColors
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel
import com.finovo.app.util.str

private val emojiChoices = listOf(
    "🏠", "⚡", "🔥", "🚗", "⛽", "📱", "🌐", "🛒", "🛡️", "💳",
    "👨‍👩‍👧", "🛍️", "🏥", "📦", "💼", "💰", "🎁", "🍽️", "🎓", "🐾",
    "🏦", "✈️", "🎬", "🎵", "⚽", "💊", "🧾", "🔧", "🧹", "☕",
    "🧸", "👕", "💻", "🪑", "🌱", "🎯", "🔒", "📈", "📉", "⭐"
)

@Composable
fun CategoriesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    var type by remember { mutableStateOf(CategoryType.EXPENSE) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    /** Modul 12: Unterkategorien werden eingerückt unter ihrer Hauptkategorie gezeigt. */
    val visible = remember(categories, type) {
        val alle = categories.filter { it.type == type.name }
        val zeilen = ArrayList<KategorieZeile>()
        alle.filter { it.parentId == 0L }.forEach { ober ->
            zeilen += KategorieZeile(ober, 0)
            com.finovo.app.util.SmartCategories.unterkategorien(alle, ober.id).forEach { unter ->
                zeilen += KategorieZeile(unter, 1)
            }
        }
        // Kategorien, deren Hauptkategorie fehlt, dürfen nicht verschwinden
        val bekannt = zeilen.map { it.kategorie.id }.toSet()
        alle.filter { it.id !in bekannt }.forEach { zeilen += KategorieZeile(it, 0) }
        zeilen
    }

    /** Mögliche Hauptkategorien für eine Unterkategorie. */
    val hauptkategorien = remember(categories, type) {
        categories.filter { it.type == type.name }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar( tutorialId = "categories", title = S("kategorien", "Kategorien"), subtitle = S("eigene_kategorien_verwalten", "Eigene Kategorien verwalten"), onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = type == CategoryType.EXPENSE,
                onClick = { type = CategoryType.EXPENSE },
                label = { Text(S("ausgaben", "Ausgaben"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            FilterChip(
                selected = type == CategoryType.INCOME,
                onClick = { type = CategoryType.INCOME },
                label = { Text(S("einnahmen", "Einnahmen"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            Spacer(Modifier.weight(1f))
        }

        if (visible.isEmpty()) {
            EmptyState(
                emoji = "🗂️",
                title = S("keine_kategorien", "Keine Kategorien"),
                message = S("lege_eine_kategorie_an_um_buchunge", "Lege eine Kategorie an, um Buchungen zu ordnen."),
                actionLabel = S("kategorie_2", "+ Kategorie"),
                onAction = { showAdd = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(visible, key = { "c${it.kategorie.id}" }) { zeile ->
                    val category = zeile.kategorie
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (20 + zeile.ebene * 18).dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
                            .premiumSurface(RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryBadge(category.emoji)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (zeile.ebene > 0) {
                                    Text(
                                        "\u21B3",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appColors().textMuted,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                                Text(categoryLabel(category.name), style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                                if (category.isFixed) {
                                    Spacer(Modifier.width(8.dp))
                                    Pill(
                                        str("filter_fixed_costs", "Fixkosten"),
                                        appColors().warning,
                                        appColors().warningContainer
                                    )
                                }
                            }
                            if (category.type == CategoryType.EXPENSE.name) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        S("fixkosten", "Fixkosten"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appColors().textMuted
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Switch(
                                        checked = category.isFixed,
                                        onCheckedChange = {
                                            viewModel.updateCategory(category.copy(isFixed = it))
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                        IconActionButton(
                            icon = Icons.Default.Edit,
                            description = S("bearbeiten", "Bearbeiten"),
                            onClick = { editing = category },
                            tint = appColors().textMuted
                        )
                        IconActionButton(
                            icon = Icons.Default.Delete,
                            description = S("loeschen_2", "Löschen"),
                            onClick = { viewModel.deleteCategory(category) },
                            tint = appColors().textMuted
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = S("neue_kategorie_2", "+ Neue Kategorie"),
                        onClick = { showAdd = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        S("kategorien_die_noch_verwendet_werden_k", "Kategorien, die noch verwendet werden, können nicht gelöscht werden."),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showAdd) {
        CategoryDialog(
            title = S("neue_kategorie", "Neue Kategorie"),
            initialName = "",
            initialEmoji = "📦",
            initialFixed = false,
            showFixed = type == CategoryType.EXPENSE,
            elternKandidaten = hauptkategorien,
            initialElternId = 0L,
            nameBelegt = { elternId, name ->
                categories.any {
                    it.type == type.name && it.parentId == elternId &&
                        it.name.equals(name, ignoreCase = true)
                }
            },
            confirmLabel = S("hinzufuegen", "Hinzufügen"),
            onDismiss = { showAdd = false },
            onConfirm = { name, emoji, fixed, elternId ->
                viewModel.addCategory(name, emoji, type, fixed, elternId)
                showAdd = false
            }
        )
    }

    editing?.let { category ->
        CategoryDialog(
            title = S("kategorie_bearbeiten", "Kategorie bearbeiten"),
            initialName = category.name,
            initialEmoji = category.emoji,
            initialFixed = category.isFixed,
            showFixed = category.type == CategoryType.EXPENSE.name,
            elternKandidaten = hauptkategorien.filter {
                it.id != category.id && it.parentId != category.id
            },
            initialElternId = category.parentId,
            nameBelegt = { elternId, name ->
                categories.any {
                    it.id != category.id && it.type == category.type &&
                        it.parentId == elternId && it.name.equals(name, ignoreCase = true)
                }
            },
            confirmLabel = S("speichern", "Speichern"),
            onDismiss = { editing = null },
            onConfirm = { name, emoji, fixed, elternId ->
                viewModel.updateCategory(
                    category.copy(name = name, emoji = emoji, isFixed = fixed, parentId = elternId)
                )
                editing = null
            }
        )
    }
}

private data class KategorieZeile(val kategorie: CategoryEntity, val ebene: Int)

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialEmoji: String,
    initialFixed: Boolean,
    showFixed: Boolean,
    confirmLabel: String,
    elternKandidaten: List<CategoryEntity>,
    initialElternId: Long,
    nameBelegt: (Long, String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    var fixed by remember { mutableStateOf(initialFixed) }
    var elternId by remember { mutableStateOf(initialElternId) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(S("name", "Name"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(S("symbol", "Symbol"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(emojiChoices) { choice ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (choice == emoji) MaterialTheme.colorScheme.primaryContainer
                                    else appColors().cardAlt
                                )
                                .then(
                                    if (choice == emoji) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(10.dp)
                                        )
                                    } else Modifier
                                )
                                .clickable { emoji = choice },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(choice, fontSize = 20.sp)
                        }
                    }
                }
                if (elternKandidaten.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    com.finovo.app.ui.components.DropdownField(
                        label = S("cat_parent", "Gehört zu"),
                        options = listOf(0L) + elternKandidaten.map { it.id },
                        selected = elternId,
                        optionLabel = { id ->
                            elternKandidaten.firstOrNull { it.id == id }?.name
                                ?: str("cat_top_level", "Hauptkategorie")
                        },
                        onSelect = { elternId = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showFixed) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            S("als_fixkosten_zaehlen", "Als Fixkosten zählen"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = fixed,
                            onCheckedChange = { fixed = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sauber = name.trim()
                if (sauber.length < 2) {
                    error = str("bitte_gib_einen_namen_mit_mindeste", "Bitte gib einen Namen mit mindestens 2 Zeichen ein.")
                    return@TextButton
                }
                if (nameBelegt(elternId, sauber)) {
                    error = str("diese_kategorie_existiert_bereits", "Diese Kategorie existiert bereits.")
                    return@TextButton
                }
                onConfirm(sauber, emoji, fixed, elternId)
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S("abbrechen_5", "Abbrechen")) } }
    )
}
