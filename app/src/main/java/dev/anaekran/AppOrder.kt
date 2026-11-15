package dev.anaekran

data class AppEntry(val packageName: String, val activityName: String, val label: String, val removable: Boolean = false)

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

/** Favoriyi komşusuyla yer değiştirir; uçta ya da listede yoksa liste aynen döner. */
fun moveFavorite(pinned: List<String>, pkg: String, delta: Int): List<String> {
    val i = pinned.indexOf(pkg)
    val j = i + delta
    if (i < 0 || j !in pinned.indices) return pinned
    return pinned.toMutableList().apply { this[i] = this[j]; this[j] = pkg }
}

enum class MenuAction { OPEN, FAV_ADD, FAV_REMOVE, MOVE_LEFT, MOVE_RIGHT, HIDE, INFO, UNINSTALL }

/** Uzun basma menüsü; favoriIndex -1 = favori değil. Sistem uygulamaları kaldırılamaz. */
fun menuActions(favoriteIndex: Int, favoriteCount: Int, removable: Boolean): List<MenuAction> = buildList {
    add(MenuAction.OPEN)
    if (favoriteIndex < 0) {
        add(MenuAction.FAV_ADD)
    } else {
        add(MenuAction.FAV_REMOVE)
        if (favoriteIndex > 0) add(MenuAction.MOVE_LEFT)
        if (favoriteIndex < favoriteCount - 1) add(MenuAction.MOVE_RIGHT)
    }
    add(MenuAction.HIDE)
    add(MenuAction.INFO)
    if (removable) add(MenuAction.UNINSTALL)
}

/** Görünen favoriler içinde taşır; görünmeyen (gizli/kaldırılmış) sabitlemeler sona eklenip korunur. */
fun moveVisibleFavorite(pinned: List<String>, visible: List<String>, pkg: String, delta: Int): List<String> =
    moveFavorite(visible, pkg, delta) + pinned.filter { it !in visible }
