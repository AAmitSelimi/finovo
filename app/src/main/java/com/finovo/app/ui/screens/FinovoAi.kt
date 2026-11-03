package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finovo.app.ai.AiAnswer
import com.finovo.app.ai.AiBlock
import com.finovo.app.ai.AiSection
import com.finovo.app.ai.AiServiceLayer
import com.finovo.app.ai.AiSource
import com.finovo.app.AppViewModel
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.S
import com.finovo.app.util.str

/** Begrüssungstext - wird auch nach dem Löschen des Verlaufs benutzt. */
private const val BEGRUESSUNG =
    "Hallo! Ich werte nur deine echten Finovo-Daten aus – offline auf diesem Gerät. " +
        "Stell mir eine Frage oder tippe auf eine der Fragen oben."

/** Eine Nachricht im Verlauf. */
private data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val answer: AiAnswer? = null
)

/**
 * 🤖 Finovo AI - Chat-Oberfläche.
 *
 * Alle Antworten entstehen aus den echten Finanzdaten des Nutzers
 * (Datenbank -> zusammenfassender Dienst -> strukturierte Zusammenfassung ->
 * KI). Die KI ist schreibgeschützt: sie kann nichts anlegen, ändern oder
 * löschen.
 */
@Composable
fun FinovoAiScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    var verlauf by remember { mutableStateOf(listOf<ChatMessage>()) }
    var eingabe by remember { mutableStateOf("") }
    var rechnet by remember { mutableStateOf(false) }
    /** Befehl, der noch vom Nutzer bestätigt werden muss (Modul 28). */
    var ausstehenderBefehl by remember {
        mutableStateOf<com.finovo.app.ai.AiCommands.Befehl?>(null)
    }
    val listenZustand = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (verlauf.isEmpty()) {
            verlauf = listOf(ChatMessage(str("ai_greeting", BEGRUESSUNG), fromUser = false))
        }
    }

    LaunchedEffect(verlauf.size) {
        if (verlauf.isNotEmpty()) {
            runCatching { listenZustand.animateScrollToItem(verlauf.lastIndex) }
        }
    }

    fun senden(frage: String) {
        val text = frage.trim()
        if (text.isBlank() || rechnet) return
        verlauf = verlauf + ChatMessage(text, fromUser = true)
        eingabe = ""

        // Modul 28: Befehle erkennen - Datenänderungen nur mit Bestätigung.
        val befehl = com.finovo.app.ai.AiCommands.erkennen(text)
        if (befehl != null) {
            when (befehl) {
                is com.finovo.app.ai.AiCommands.Befehl.ZeigeVormonat,
                is com.finovo.app.ai.AiCommands.Befehl.ZeigeMonat -> {
                    rechnet = true
                    viewModel.askAiShowCommand(befehl) { antwort ->
                        verlauf = verlauf + ChatMessage(antwort.text, fromUser = false, answer = antwort)
                        rechnet = false
                    }
                }

                else -> {
                    ausstehenderBefehl = befehl
                    verlauf = verlauf + ChatMessage(
                        com.finovo.app.ai.AiCommands.bestaetigung(befehl, viewModel.waehrung),
                        fromUser = false
                    )
                }
            }
            return
        }

        rechnet = true
        viewModel.askFinovoAi(text) { antwort ->
            verlauf = verlauf + ChatMessage(antwort.text, fromUser = false, answer = antwort)
            rechnet = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("ai_title", "Finovo AI"),
            subtitle = S("ai_subtitle", "Deine Finanzen verstehen. Einfacher planen."),
            onBack = onBack,
            actions = {
                if (verlauf.size > 1) {
                    IconButton(onClick = {
                        verlauf = listOf(
                            ChatMessage(str("ai_greeting", BEGRUESSUNG), fromUser = false)
                        )
                        ausstehenderBefehl = null
                        viewModel.resetFinovoAi()
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = S("ai_clear", "Verlauf löschen"),
                            tint = appColors().textMuted
                        )
                    }
                }
            }
        )

        // ------------------------------------------------ Schnellfragen
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SchnellFrage("💰", "ai_quick_saving", "Wo kann ich sparen?") { senden(it) }
            SchnellFrage("📊", "ai_quick_where", "Wo geht mein Geld hin?") { senden(it) }
            SchnellFrage("🧾", "ai_quick_open_bills", "Welche Rechnungen sind offen?") { senden(it) }
            SchnellFrage("📅", "ai_quick_upcoming", "Was muss ich bald bezahlen?") { senden(it) }
            SchnellFrage("📈", "ai_quick_month", "Analysiere meinen Monat") { senden(it) }
            SchnellFrage("🔄", "ai_quick_recurring", "Welche Zahlungen sind wiederkehrend?") { senden(it) }
            SchnellFrage(
                "⚖️", "ai_quick_compare",
                "Vergleiche diesen Monat mit dem letzten Monat"
            ) { senden(it) }
        }

        // ------------------------------- Datenschutz (transparent, immer sichtbar)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = appColors().cardAlt)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    S("ai_privacy_title", "\uD83D\uDD12 Datenschutz"),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    S(
                        "ai_privacy_body",
                        "Alle Antworten werden auf diesem Gerät aus deinen Finovo-Daten berechnet. Es werden keine Daten an einen externen Dienst gesendet."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    S(
                        "ai_external_state",
                        "Externe KI: nicht konfiguriert \u2013 Finovo AI bleibt offline."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
        }

        // ---------------------------------------------------- Verlauf
        LazyColumn(
            state = listenZustand,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(verlauf) { nachricht ->
                if (nachricht.fromUser) {
                    NutzerBlase(nachricht.text)
                } else {
                    KiBlase(nachricht)
                }
            }
            if (rechnet) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            S("ai_thinking", "Finovo AI rechnet …"),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }
        }

        // ---------------------------------------------- Datenschutz-Zeile
        Text(
            text = if (AiServiceLayer.sendsDataExternally()) {
                S("ai_using_external", "⚠️ Externe KI aktiv – es werden zusammengefasste Finanzdaten übertragen.")
            } else {
                S("ai_privacy_footer", "🔒 Alle Antworten werden auf diesem Gerät berechnet. Keine Daten verlassen das Handy.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ------------------------------ Bestätigung eines Befehls (Modul 28)
        ausstehenderBefehl?.let { befehl ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        S("ai_cmd_confirm_title", "Bestätigung erforderlich"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        com.finovo.app.ai.AiCommands.bestaetigung(befehl, viewModel.waehrung),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Button(
                            onClick = {
                                val auszufuehren = befehl
                                ausstehenderBefehl = null
                                rechnet = true
                                viewModel.runAiCommand(auszufuehren) { ergebnis ->
                                    verlauf = verlauf + ChatMessage(ergebnis, fromUser = false)
                                    rechnet = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(S("ai_cmd_yes", "Ja, speichern"))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                ausstehenderBefehl = null
                                verlauf = verlauf + ChatMessage(
                                    str("ai_cmd_cancelled", "Abgebrochen – es wurde nichts gespeichert."),
                                    fromUser = false
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(S("ai_cmd_no", "Abbrechen"))
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------- Eingabe
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTextField(
                value = eingabe,
                onValueChange = { eingabe = it },
                label = S("ai_input_hint", "Frag mich etwas zu deinen Finanzen …"),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { senden(eingabe) },
                enabled = eingabe.isNotBlank() && !rechnet
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = S("ai_send", "Senden"),
                    tint = if (eingabe.isNotBlank() && !rechnet) {
                        MaterialTheme.colorScheme.primary
                    } else appColors().textMuted
                )
            }
        }
    }
}

@Composable
private fun SchnellFrage(
    emoji: String,
    schluessel: String,
    standard: String,
    onClick: (String) -> Unit
) {
    val frage = S(schluessel, standard)
    FilterChip(
        selected = false,
        onClick = { onClick(frage) },
        label = {
            Text(
                "$emoji $frage",
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall
            )
        },
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun NutzerBlase(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun KiBlase(nachricht: ChatMessage) {
    val farben = appColors()
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = farben.card),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Finovo AI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                val antwort = nachricht.answer
                if (antwort == null) {
                    Text(nachricht.text, style = MaterialTheme.typography.bodyMedium)
                } else {
                    antwort.blocks.forEach { block ->
                        when (block) {
                            is AiBlock.Heading -> {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    listOf(block.emoji, block.text)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" "),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            is AiBlock.Text -> {
                                Abschnitt(block.section)
                                Text(
                                    block.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            is AiBlock.Row -> {
                                Abschnitt(block.section)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        block.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        block.value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            AiBlock.Gap -> Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                // Quelle der Antwort - niemals als "offline" ausgeben, wenn
                // tatsächlich ein externer Dienst genutzt wurde.
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (nachricht.answer?.source == AiSource.REMOTE) {
                        S("ai_source_remote", "Externe KI")
                    } else {
                        S("ai_source_local", "Auf dem Gerät berechnet (offline)")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = farben.textMuted
                )
            }
        }
    }
}

/** Kleine Überschrift über einem Block, damit Daten und Einschätzung trennbar bleiben. */
@Composable
private fun Abschnitt(section: AiSection?) {
    if (section == null) return
    Spacer(modifier = Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .background(
                    color = when (section) {
                        AiSection.FACTS -> MaterialTheme.colorScheme.primary
                        AiSection.CALCULATED -> appColors().income
                        AiSection.ESTIMATE -> appColors().warning
                        AiSection.SUGGESTION -> appColors().textMuted
                    },
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            section.label(),
            style = MaterialTheme.typography.labelSmall,
            color = appColors().textMuted
        )
    }
}
