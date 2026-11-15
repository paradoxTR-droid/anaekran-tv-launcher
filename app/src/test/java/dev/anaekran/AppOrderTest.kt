package dev.anaekran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.text.Collator
import java.util.Locale

class AppOrderTest {
    private val collator = Collator.getInstance(Locale.forLanguageTag("tr"))
    private val tr = Comparator<String> { x, y -> collator.compare(x, y) }
    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, "$pkg.Main", label)
    private val apps = listOf(app("c", "Cam"), app("a", "Ödev"), app("b", "Çay"), app("d", "Zap"), app("e", "Oyun"))

    @Test fun othersAlphabeticalTurkish() =
        assertEquals(listOf("Cam", "Çay", "Oyun", "Ödev", "Zap"), splitRows(apps, emptyList(), emptySet(), tr).others.map { it.label })

    @Test fun staleHiddenIgnored() =
        assertEquals(splitRows(apps, emptyList(), emptySet(), tr), splitRows(apps, emptyList(), setOf("gone2"), tr))

    @Test fun allHiddenGivesEmpty() =
        assertEquals(Rows(emptyList(), emptyList()), splitRows(apps, listOf("a"), apps.map { it.packageName }.toSet(), tr))

    @Test fun splitFavoritesInPinnedOrder() {
        val r = splitRows(apps, listOf("d", "a"), emptySet(), tr)
        assertEquals(listOf("d", "a"), r.favorites.map { it.packageName })
        assertEquals(listOf("Cam", "Çay", "Oyun"), r.others.map { it.label })
    }

    @Test fun splitHiddenFavoriteExcluded() {
        val r = splitRows(apps, listOf("a"), setOf("a"), tr)
        assertFalse((r.favorites + r.others).any { it.packageName == "a" })
    }

    @Test fun splitStalePinnedIgnored() =
        assertEquals(listOf("a"), splitRows(apps, listOf("gone", "a"), emptySet(), tr).favorites.map { it.packageName })

    @Test fun defaultsOnlyInstalled() =
        assertEquals(
            listOf("com.netflix.ninja", "com.exxen.android"),
            defaultFavorites(setOf("com.exxen.android", "com.netflix.ninja", "x")),
        )
}
