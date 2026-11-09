package hr.abalarm.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import hr.abalarm.app.data.AppLanguage
import java.util.Locale

/**
 * Jezik aplikacije neovisno o jeziku sustava.
 *
 * - Android 13+: sustavni "per-app language" (AppCompat poziva LocaleManager). Jezik se vidi i u
 *   Postavke sustava → Aplikacije → AB Alarm → Jezik.
 * - Android 8–12: AppCompat sam pamti odabir (autoStoreLocales u manifestu) i primjenjuje ga na
 *   AppCompatActivity. Kontekst aplikacije (receiveri, servis) tada NIJE preveden, pa za tekstove
 *   obavijesti koristimo [localizedContext] s jezikom spremljenim u DataStoreu.
 */
object AppLocale {

    /** Primijeni jezik; otvorene aktivnosti se ponovno kreiraju. Pozivati na glavnoj niti. */
    fun apply(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Jezik koji je trenutno postavljen u AppCompatu / sustavu (korisnik ga na Androidu 13+ može
     * promijeniti i u postavkama sustava). Pouzdano unutar aktivnosti.
     */
    fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.SYSTEM
        return AppLanguage.fromTag(locales[0]?.language)
    }

    /** Kontekst s jezikom aplikacije – za tekstove koji nastaju izvan aktivnosti. */
    fun localizedContext(context: Context, language: AppLanguage): Context {
        // Na Androidu 13+ sustav već prevodi i kontekst aplikacije.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || language == AppLanguage.SYSTEM) {
            return context
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language.tag))
        return context.createConfigurationContext(config)
    }
}
