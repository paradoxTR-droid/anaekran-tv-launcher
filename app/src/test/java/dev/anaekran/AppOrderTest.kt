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

    @Test fun alphabeticalTurkish() =
        assertEquals(listOf("Cam", "Çay", "Oyun", "Ödev", "Zap"), arrange(apps, emptyList(), emptySet(), tr).map { it.label })

    @Test fun pinnedFirstInPinnedOrder() =
        assertEquals(listOf("b", "a", "c", "e", "d"), arrange(apps, listOf("b", "a"), emptySet(), tr).map { it.packageName })

    @Test fun hiddenRemoved() =
        assertFalse(arrange(apps, emptyList(), setOf("c"), tr).any { it.packageName == "c" })

    @Test fun staleKeysIgnored() =
        assertEquals(arrange(apps, emptyList(), emptySet(), tr), arrange(apps, listOf("gone"), setOf("gone2"), tr))

    @Test fun allHiddenGivesEmpty() =
        assertEquals(emptyList<AppEntry>(), arrange(apps, emptyList(), apps.map { it.packageName }.toSet(), tr))

    @Test fun pinFirstMovesExisting() =
        assertEquals(listOf("c", "a", "b"), pinFirst(listOf("a", "b", "c"), "c"))
}
