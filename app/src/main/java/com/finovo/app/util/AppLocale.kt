package com.finovo.app.util

import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language handling.
 *
 * The selected language is stored in a tiny SharedPreferences file so it is
 * available synchronously inside [android.app.Activity.attachBaseContext] -
 * before the database is opened. Stored user data is never touched by this.
 */
object AppLocale {

    const val DEFAULT = "de"
    val SUPPORTED = listOf("de", "en", "it", "sq", "tr")

    private const val PREFS = "finovo_locale"
    private const val KEY = "language"

    /** Language currently used for formatting numbers, dates and currency. */
    @Volatile
    private var current: String = DEFAULT

    /** Language of the device, when we support it - otherwise German. */
    fun systemDefault(): String {
        val config = android.content.res.Resources.getSystem().configuration
        val first = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0)
        } else {
            @Suppress("DEPRECATION") config.locale
        }
        val tag = (first?.language ?: DEFAULT).lowercase(Locale.ROOT)
        return tag.takeIf { it in SUPPORTED } ?: DEFAULT
    }

    /** Stored choice, otherwise the device language, otherwise German. */
    fun resolve(context: Context): String {
        val stored = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        if (!stored.isNullOrBlank() && stored in SUPPORTED) return stored
        return systemDefault()
    }

    fun save(context: Context, code: String) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, code).apply()
        }
        refreshAppResources(context)
        apply(code)
    }

    /** Remembers the language for formatting (numbers, dates, currency). */
    fun apply(code: String) {
        current = code.takeIf { it in SUPPORTED } ?: DEFAULT
    }

    /** Active language code. Defaults to German (also inside unit tests). */
    fun current(): String = current

    fun locale(): Locale = Locale.forLanguageTag(current())

    /** Wraps a context so every resource lookup uses the chosen language. */
    fun wrap(context: Context, code: String): Context {
        apply(code)
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
            config.setLocale(locale)
        } else {
            @Suppress("DEPRECATION") config.setLocale(locale)
        }
        return context.createConfigurationContext(config)
    }

    /** Native name of a language, e.g. "Deutsch", "Shqip". */
    fun nativeName(code: String): String = when (code) {
        "de" -> "Deutsch"
        "en" -> "English"
        "it" -> "Italiano"
        "sq" -> "Shqip"
        "tr" -> "Türkçe"
        else -> code
    }
}
