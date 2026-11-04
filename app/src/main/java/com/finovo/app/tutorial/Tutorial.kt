package com.finovo.app.tutorial

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.finovo.app.ui.Route
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ProgressBar
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.primaryGradient
import com.finovo.app.ui.theme.FinovoBackground
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.S

// ---------------------------------------------------------------------------
// Modell
// ---------------------------------------------------------------------------

/** Bestehende App-Aktion, die ein Schritt direkt ausloesen kann. */
enum class TutorialAction { NONE, QUICK_ADD, ADD_EXPENSE, ADD_INCOME, ADD_BILL, ADD_RECURRING }

data class TutorialStepDef(val icon: ImageVector, val action: TutorialAction = TutorialAction.NONE)

/**
 * Eine Modul-Anleitung. Alle Texte stehen in den String-Ressourcen:
 * tut_<id>_title, tut_<id>_flow, tut_<id>_s<n>_t, tut_<id>_s<n>_b.
 */
data class TutorialDef(
    val id: String,
    val route: Route,
    val icon: ImageVector,
    val group: String,
    val fallbackTitle: String,
    val related: List<String>,
    val steps: List<TutorialStepDef>
) {
    /** Schritte plus die abschliessende Seite "So haengt es zusammen". */
    val pageCount: Int get() = steps.size + 1
}

/** Bruecke zur App: Navigation und bestehende Aktionen. Ohne Host (z. B. in Tests) bleiben diese Knoepfe aus. */
class TutorialHost(
    val current: () -> Route,
    val navigate: (Route) -> Unit,
    val perform: (TutorialAction) -> Unit
)

val LocalTutorialHost = staticCompositionLocalOf<TutorialHost?> { null }

// ---------------------------------------------------------------------------
// Fortschritt (SharedPreferences, keine Datenbank-Aenderung)
// ---------------------------------------------------------------------------

object TutorialProgress {
    private const val PREFS = "finovo_tutorials"

    /** Wird bei jeder Aenderung erhoeht, damit Compose neu zeichnet. */
    var version by mutableIntStateOf(0)
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastPage(ctx: Context, id: String): Int = prefs(ctx).getInt("${id}_page", 0)
    fun isDone(ctx: Context, id: String): Boolean = prefs(ctx).getBoolean("${id}_done", false)
    fun isStarted(ctx: Context, id: String): Boolean = prefs(ctx).contains("${id}_page")

    fun savePage(ctx: Context, id: String, page: Int) {
        prefs(ctx).edit().putInt("${id}_page", page).apply()
        version++
    }

    fun complete(ctx: Context, id: String) {
        prefs(ctx).edit().putBoolean("${id}_done", true).putInt("${id}_page", 0).apply()
        version++
    }

    fun reset(ctx: Context, id: String) {
        prefs(ctx).edit().remove("${id}_done").remove("${id}_page").apply()
        version++
    }

    fun resetAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        version++
    }

    fun doneCount(ctx: Context): Int = TutorialCatalog.all.count { isDone(ctx, it.id) }
}

@Composable
fun tutorialTitle(def: TutorialDef): String = S("tut_${def.id}_title", def.fallbackTitle)

@Composable
fun groupTitle(group: String): String = when (group) {
    "basics" -> S("tut_group_basics", "Grundlagen")
    "planning" -> S("tut_group_planning", "Planen")
    "analysis" -> S("tut_group_analysis", "Auswerten")
    "documents" -> S("tut_group_documents", "Belege und Suche")
    "data" -> S("tut_group_data", "Daten")
    else -> S("tut_group_settings", "Einstellungen")
}

// ---------------------------------------------------------------------------
// Knopf "So funktioniert dieses Modul"
// ---------------------------------------------------------------------------

@Composable
fun TutorialHelpButton(tutorialId: String, modifier: Modifier = Modifier) {
    val def = TutorialCatalog.get(tutorialId) ?: return
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = TutorialProgress.version
    val done = TutorialProgress.isDone(context, def.id)
    var open by remember { mutableStateOf(false) }
    val label = S("tut_how", "So funktioniert dieses Modul")
    Box(modifier = modifier) {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.semantics { contentDescription = label }
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        if (!done) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(appColors().cyan)
            )
        }
    }
    if (open) TutorialDialog(startId = def.id, onDismiss = { open = false })
}

// ---------------------------------------------------------------------------
// Schritt-fuer-Schritt-Dialog
// ---------------------------------------------------------------------------

@Composable
fun TutorialDialog(startId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val host = LocalTutorialHost.current
    var id by remember { mutableStateOf(startId) }
    val def = TutorialCatalog.get(id) ?: return
    var page by remember(id) {
        mutableIntStateOf(
            if (TutorialProgress.isDone(context, id)) 0
            else TutorialProgress.lastPage(context, id).coerceIn(0, def.pageCount - 1)
        )
    }
    LaunchedEffect(id, page) { TutorialProgress.savePage(context, id, page) }
    val last = page == def.pageCount - 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FinovoBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Kopf: Modul, Ueberspringen
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(def.icon, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        tutorialTitle(def),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!last) {
                        TextButton(onClick = onDismiss) { Text(S("tut_skip", "Überspringen")) }
                    } else {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = S("tut_close", "Schließen"))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Fortschritt
                val progress by animateFloatAsState(
                    targetValue = (page + 1f) / def.pageCount,
                    animationSpec = tween(300),
                    label = "tutorialProgress"
                )
                ProgressBar(progress = progress, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(
                    S("tut_step", "Schritt %1\$d von %2\$d", page + 1, def.pageCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = appColors().textMuted
                )

                // Inhalt
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(260)) { it * dir / 4 } + fadeIn(tween(260)))
                            .togetherWith(slideOutHorizontally(tween(200)) { -it * dir / 4 } + fadeOut(tween(200)))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "tutorialPage"
                ) { p ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (p < def.steps.size) {
                            val step = def.steps[p]
                            Illustration(step.icon, p + 1)
                            Spacer(Modifier.height(22.dp))
                            Text(
                                S("tut_${def.id}_s${p + 1}_t", ""),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                S("tut_${def.id}_s${p + 1}_b", ""),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            if (step.action != TutorialAction.NONE && host != null) {
                                Spacer(Modifier.height(20.dp))
                                SecondaryButton(
                                    text = S("tut_try", "Jetzt ausprobieren"),
                                    onClick = {
                                        onDismiss()
                                        host.perform(step.action)
                                    }
                                )
                            }
                        } else {
                            ConnectionsPage(def, host, onOpenTutorial = { id = it }, onDismiss = onDismiss)
                        }
                    }
                }

                // Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SecondaryButton(
                        text = S("tut_back", "Zurück"),
                        onClick = { if (page > 0) page-- },
                        enabled = page > 0,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = if (last) S("tut_finish", "Fertig") else S("tut_next", "Weiter"),
                        onClick = {
                            if (last) {
                                TutorialProgress.complete(context, def.id)
                                onDismiss()
                            } else page++
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionsPage(
    def: TutorialDef,
    host: TutorialHost?,
    onOpenTutorial: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Illustration(Icons.Outlined.Hub, null)
    Spacer(Modifier.height(22.dp))
    Text(
        S("tut_connected", "So hängt es zusammen"),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(10.dp))
    Text(
        S("tut_${def.id}_flow", ""),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    if (def.related.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Text(
            S("tut_related", "Verbundene Module"),
            style = MaterialTheme.typography.titleSmall,
            color = appColors().textMuted
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            def.related.mapNotNull { TutorialCatalog.get(it) }.forEach { r ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(appColors().card)
                        .border(1.dp, appColors().glow.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable { onOpenTutorial(r.id) }
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(r.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tutorialTitle(r), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if (host != null && host.current().key != def.route.key) {
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = S("tut_open", "Zum Modul"),
            onClick = {
                onDismiss()
                host.navigate(def.route)
            }
        )
    }
}

@Composable
private fun Illustration(icon: ImageVector, number: Int?) {
    val glow = appColors().glow
    val cyan = appColors().cyan
    Box(
        modifier = Modifier
            .size(170.dp)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.40f), Color.Transparent)))
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(CircleShape)
                .background(primaryGradient())
                .border(2.dp, Brush.sweepGradient(listOf(glow, cyan, glow)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        if (number != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 22.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(cyan),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00242C))
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(appColors().glow.copy(alpha = if (appColors().isDark) 0.22f else 0.10f))
            .border(1.dp, appColors().glow.copy(alpha = 0.4f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.55f))
    }
}

// ---------------------------------------------------------------------------
// Uebersicht aller Anleitungen
// ---------------------------------------------------------------------------

@Composable
fun TutorialsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = TutorialProgress.version
    var openId by remember { mutableStateOf<String?>(null) }
    val total = TutorialCatalog.all.size
    val done = TutorialProgress.doneCount(context)

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            ScreenTopBar(
                title = S("tut_overview", "Anleitungen"),
                subtitle = S("tut_overview_sub", "Schritt für Schritt durch jedes Modul"),
                onBack = onBack
            )
        }
        item {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        S("tut_total", "%1\$d von %2\$d Anleitungen abgeschlossen", done, total),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(
                        progress = if (total > 0) done.toFloat() / total else 0f,
                        color = appColors().income
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { TutorialProgress.resetAll(context) }) {
                        Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(S("tut_reset_all", "Fortschritt zurücksetzen"))
                    }
                }
            }
        }
        TutorialCatalog.groups.forEach { group ->
            val defs = TutorialCatalog.all.filter { it.group == group }
            item {
                Text(
                    groupTitle(group),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp)
                )
            }
            items(defs.size) { i ->
                val def = defs[i]
                TutorialRow(def, onOpen = { openId = def.id })
            }
        }
    }
    openId?.let { TutorialDialog(startId = it, onDismiss = { openId = null }) }
}

@Composable
private fun TutorialRow(def: TutorialDef, onOpen: () -> Unit) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = TutorialProgress.version
    val done = TutorialProgress.isDone(context, def.id)
    val started = TutorialProgress.isStarted(context, def.id)
    val page = TutorialProgress.lastPage(context, def.id)
    val status = when {
        done -> S("tut_status_done", "Abgeschlossen")
        started && page > 0 -> S("tut_step", "Schritt %1\$d von %2\$d", page + 1, def.pageCount)
        else -> S("tut_status_new", "Neu")
    }
    AppCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(def.icon, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tutorialTitle(def), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done) appColors().income else appColors().textMuted
                )
            }
            if (done) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = appColors().income)
                IconButton(onClick = { TutorialProgress.reset(context, def.id) }) {
                    Icon(Icons.Outlined.Replay, contentDescription = S("tut_restart", "Neu starten"), tint = appColors().textMuted)
                }
            }
        }
    }
}
