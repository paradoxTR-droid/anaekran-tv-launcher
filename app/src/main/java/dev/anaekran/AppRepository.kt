package dev.anaekran

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Leanback (TV) uygulamalarını listeler. Ana thread dışında çağrılmalı. */
fun loadApps(pm: PackageManager, selfPackage: String): List<AppEntry> =
    pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        0, // int sürüm: ResolveInfoFlags Android 13+
    )
        .map { it.activityInfo }
        .filter { it.packageName != selfPackage }
        .map {
            val removable = it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
            AppEntry(it.packageName, it.name, it.loadLabel(pm).toString(), removable)
        }
        .distinctBy { it.packageName }
