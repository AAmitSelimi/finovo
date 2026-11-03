package com.finovo.app.util

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.finovo.app.ui.theme.appColors

/**
 * Hinweis auf einen abgefangenen Fehler - direkt auf dem Bildschirm, auf dem
 * er passiert ist.
 *
 * Die App stuerzt dabei nicht ab: [CrashGuard.installMainLoopGuard] faengt den
 * Fehler ab, schreibt ihn in das Fehlerprotokoll und die Ansicht kann hier
 * genau zeigen, was schiefgelaufen ist. Kopieren genuegt, um den Fehler
 * gezielt beheben zu koennen - damit wird nie geraten.
 */
@Composable
fun FehlerHinweis(modifier: Modifier = Modifier) {
    val anzahl by CrashGuard.crashCount.collectAsState()
    if (anzahl <= 0) return

    val context = LocalContext.current
    val protokoll = remember(context, anzahl) { CrashGuard.readLog(context).takeLast(2000) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = S("crash_hint_title", "Beim letzten Vorgang ist ein Fehler aufgetreten."),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = S(
                    "crash_hint_body",
                    "Deine Daten sind sicher. Das Protokoll unten zeigt die genaue Ursache."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = S("crash_log_head", "Fehlerprotokoll"),
                style = MaterialTheme.typography.labelMedium,
                color = appColors().textMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (protokoll.isBlank()) {
                    S("crash_empty", "Kein Fehlerprotokoll vorhanden.")
                } else protokoll,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = appColors().textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                TextButton(onClick = { CrashGuard.copyLog(context) }) {
                    Text(S("crash_copy", "Protokoll kopieren"))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { CrashGuard.clearLog() }) {
                    Text(S("crash_clear", "Protokoll löschen"))
                }
            }
        }
    }
}
