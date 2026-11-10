package dev.pivisolutions.dictus.model

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * The catalog's descriptions and quality labels are string resources (#236): an English UI
 * reads English, a French UI reads accented French.
 */
@RunWith(RobolectricTestRunner::class)
class ModelCatalogStringsTest {

    private fun contextIn(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private fun descriptions(context: Context) = ModelCatalog.ALL.associate { it.key to context.getString(it.descriptionRes) }

    private fun qualityLabels(context: Context) = ModelCatalog.ALL.associate { it.key to context.getString(it.qualityLabelRes) }

    @Test
    fun `descriptions resolve in English`() {
        assertEquals(
            mapOf(
                "tiny" to "Fast and light",
                "base" to "Accurate and balanced",
                "small" to "High accuracy",
                "small-q5_1" to "Best trade-off",
                "medium-q5_0" to "Best accuracy",
                "parakeet-ctc-110m-int8" to "Fast and accurate",
                "parakeet-tdt-0.6b-v3" to "Automatic language detection",
            ),
            descriptions(contextIn(Locale.ENGLISH)),
        )
    }

    @Test
    fun `descriptions resolve in accented French`() {
        assertEquals(
            mapOf(
                "tiny" to "Rapide et léger",
                "base" to "Précis et équilibré",
                "small" to "Haute précision",
                "small-q5_1" to "Meilleur compromis",
                "medium-q5_0" to "Meilleure précision",
                "parakeet-ctc-110m-int8" to "Rapide et précis",
                "parakeet-tdt-0.6b-v3" to "Détection automatique de la langue",
            ),
            descriptions(contextIn(Locale.FRENCH)),
        )
    }

    @Test
    fun `quality labels resolve in English and French`() {
        assertEquals(
            mapOf(
                "tiny" to "Fast", "base" to "Balanced", "small" to "Accurate", "small-q5_1" to "Accurate",
                "medium-q5_0" to "Accurate", "parakeet-ctc-110m-int8" to "Fast", "parakeet-tdt-0.6b-v3" to "Premium",
            ),
            qualityLabels(contextIn(Locale.ENGLISH)),
        )
        assertEquals(
            mapOf(
                "tiny" to "Rapide", "base" to "Équilibré", "small" to "Précis", "small-q5_1" to "Précis",
                "medium-q5_0" to "Précis", "parakeet-ctc-110m-int8" to "Rapide", "parakeet-tdt-0.6b-v3" to "Premium",
            ),
            qualityLabels(contextIn(Locale.FRENCH)),
        )
    }
}
