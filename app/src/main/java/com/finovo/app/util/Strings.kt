package com.finovo.app.util

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Localisation of Finovo.
 *
 * All user visible text lives in Android string resources:
 *   res/values/strings.xml      -> German   (default)
 *   res/values-en/strings.xml   -> English
 *   res/values-it/strings.xml   -> Italian
 *   res/values-sq/strings.xml   -> Albanian
 *
 * [S] resolves a key against the resources of the *locale wrapped* context,
 * so switching the language updates every screen immediately. The Kotlin
 * fallback keeps the UI readable in case a key is ever missing.
 */
enum class Language(val code: String, val label: String) {
    DE("de", "Deutsch"),
    EN("en", "English"),
    SQ("sq", "Shqip"),
    IT("it", "Italiano"),
    TR("tr", "Türkçe");

    companion object {
        fun fromCode(code: String): Language =
            entries.firstOrNull { it.code == code } ?: DE
    }
}

val LocalLanguage = staticCompositionLocalOf { AppLocale.DEFAULT }

@Composable
fun ProvideLanguage(languageCode: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    AppLocale.apply(languageCode)
    appResources = AppLocale.wrap(context, languageCode).resources
    CompositionLocalProvider(LocalLanguage provides languageCode, content = content)
}

/** Cache of R.string name -> id, rebuilt whenever the locale changes. */
private object ResIndex {
    private var tag: String? = null
    private var ids: Map<String, Int> = emptyMap()

    private fun tagOf(res: Resources): String =
        res.configuration.locales.toLanguageTags()

    private const val PACKAGE = "com.finovo.app"

    fun id(res: Resources, key: String): Int {
        val t = tagOf(res)
        if (t != tag) {
            ids = emptyMap()
            tag = t
        }
        ids[key]?.let { return it }
        if (ids.isEmpty()) {
            ids = runCatching {
                com.finovo.app.R.string::class.java.fields.associate { f -> f.name to f.getInt(null) }
            }.getOrDefault(emptyMap())
            ids[key]?.let { return it }
        }
        // Fallback, falls die R-Klasse nicht mehr aufloesbar ist
        return res.getIdentifier(key, "string", PACKAGE)
    }
}

private fun format(template: String, args: Array<out Any>): String {
    var out = template
    args.forEachIndexed { index, arg ->
        val value = arg.toString()
        out = out.replace("%" + (index + 1) + "\$d", value)
            .replace("%" + (index + 1) + "\$s", value)
    }
    return out
}

private fun resolve(res: Resources?, key: String, fallback: String, args: Array<out Any>): String {
    if (res == null || key.isBlank()) return finish(fallback, args)
    val id = ResIndex.id(res, key)
    val raw = if (id != 0) runCatching { res.getString(id) }.getOrNull() else null
    return finish(raw ?: fallback, args)
}

private fun finish(text: String, args: Array<out Any>): String {
    val value = if (args.isEmpty()) text else format(text, args)
    return if (value.contains("%%")) value.replace("%%", "%") else value
}

/** Translated string (Compose). Fallback is the German source text. */
@Composable
fun S(key: String, fallback: String = "", vararg args: Any): String =
    resolve(LocalContext.current.resources, key, fallback, args)

/** Translated string outside of Compose (notifications, export, workers). */
fun S(context: Context, key: String, fallback: String = "", vararg args: Any): String =
    resolve(context.resources, key, fallback, args)

/**
 * Translated string for plain (non-Compose) code such as formatters,
 * rule descriptions or analytics labels.
 */
fun str(key: String, fallback: String = "", vararg args: Any): String =
    resolve(appResources, key, fallback, args)

/** Translated string from raw resources. */
fun S(res: Resources?, key: String, fallback: String = "", vararg args: Any): String =
    resolve(res, key, fallback, args)

// ------------------------------------------------------------------ plurals

/**
 * Quantity aware text, e.g. "1 receipt" / "3 receipts".
 * Uses <plurals name="key"> from the resources and falls back to the German
 * singular/plural text when the resource is missing.
 */
fun plurals(key: String, count: Int, one: String, many: String, vararg extra: Any): String {
    val res = appResources
    if (res != null) {
        val id = res.getIdentifier(key, "plurals", "com.finovo.app")
        if (id != 0) {
            val text = runCatching { res.getQuantityString(id, count, count, *extra) }.getOrNull()
            if (!text.isNullOrBlank()) return finish(text, emptyArray())
        }
    }
    val args = ArrayList<Any>(extra.size + 1)
    args.add(count)
    args.addAll(extra)
    return finish(if (count == 1) one else many, args.toTypedArray())
}

// ---------------------------------------------------------------- categories

/**
 * Built-in categories are stored with their German name as a stable id.
 * Only the displayed label is localised - user data is never rewritten.
 */
private val CATEGORY_KEYS = mapOf(
    "Miete" to "cat_miete",
    "Strom" to "cat_strom",
    "Gas" to "cat_gas",
    "Auto" to "cat_auto",
    "Tanken" to "cat_tanken",
    "Handy" to "cat_handy",
    "Internet" to "cat_internet",
    "Lebensmittel" to "cat_lebensmittel",
    "Versicherung" to "cat_versicherung",
    "Kredit" to "cat_kredit",
    "Familie" to "cat_familie",
    "Shopping" to "cat_shopping",
    "Gesundheit" to "cat_gesundheit",
    "Wasser" to "cat_wasser",
    "Telefon" to "cat_telefon",
    "Kleidung" to "cat_kleidung",
    "Freizeit" to "cat_freizeit",
    "Abos" to "cat_abos",
    "Reisen" to "cat_reisen",
    "Schule/Kinder" to "cat_schule_kinder",
    "Sonstiges" to "cat_sonstiges",
    "Gehalt" to "cat_gehalt",
    "Kindergeld" to "cat_kindergeld",
    "Sonstige Einnahmen" to "cat_sonstige_einnahmen"
)

fun categoryKey(name: String): String? = CATEGORY_KEYS[name]

/** Application resources, attached once in [com.finovo.app.FinovoApp.onCreate]. */
@Volatile
private var appResources: Resources? = null

fun attachAppResources(context: Context) {
    refreshAppResources(context)
}

/**
 * Stores the resources of the *locale wrapped* context. Called at start-up and
 * every time the language changes, so plain (non Compose) code such as [str]
 * and [categoryLabel] always uses the language the user selected.
 */
fun refreshAppResources(context: Context) {
    val code = AppLocale.resolve(context)
    AppLocale.apply(code)
    appResources = AppLocale.wrap(context, code).resources
}

/**
 * Display name of a category: localised for the built-in ones, unchanged for
 * categories the user created. Works inside and outside of Compose.
 */
fun categoryLabel(name: String): String {
    val key = CATEGORY_KEYS[name] ?: return name
    val res = appResources
    return if (res == null) name else resolve(res, key, name, emptyArray())
}

/** Display name of a category outside of Compose. */
fun categoryLabel(context: Context, name: String): String {
    val key = CATEGORY_KEYS[name] ?: return name
    return S(context, key, name)
}
