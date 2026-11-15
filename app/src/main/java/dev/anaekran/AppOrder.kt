package dev.anaekran

data class AppEntry(val packageName: String, val activityName: String, val label: String)

data class Rows(val favorites: List<AppEntry>, val others: List<AppEntry>)

/** Favoriler sabitlenme sırasıyla; diğerleri etikete göre. Gizlenenler ve kurulu olmayanlar iki satırda da yok. */
fun splitRows(apps: List<AppEntry>, pinned: List<String>, hidden: Set<String>, compare: Comparator<String>): Rows {
    val visible = apps.filter { it.packageName !in hidden }
    val byPkg = visible.associateBy { it.packageName }
    val favorites = pinned.distinct().mapNotNull { byPkg[it] }
    val favSet = favorites.toSet()
    return Rows(favorites, visible.filter { it !in favSet }.sortedWith(compareBy(compare) { it.label }))
}

val DEFAULT_FAVORITES = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus", "com.exxen.android")

fun defaultFavorites(installed: Set<String>): List<String> = DEFAULT_FAVORITES.filter { it in installed }
