package dev.anaekran

data class AppEntry(val packageName: String, val activityName: String, val label: String)

/** Sabitlenenler önce (sabitlenme sırasıyla), kalanlar etikete göre; gizlenenler çıkarılır, bilinmeyen anahtarlar yok sayılır. */
fun arrange(apps: List<AppEntry>, pinned: List<String>, hidden: Set<String>, compare: Comparator<String>): List<AppEntry> {
    val visible = apps.filter { it.packageName !in hidden }
    val byPkg = visible.associateBy { it.packageName }
    val head = pinned.mapNotNull { byPkg[it] }.distinct()
    val headSet = head.toSet()
    return head + visible.filter { it !in headSet }.sortedWith(compareBy(compare) { it.label })
}

fun pinFirst(pinned: List<String>, pkg: String): List<String> = listOf(pkg) + pinned.filter { it != pkg }
