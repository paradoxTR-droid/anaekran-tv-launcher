package dev.anaekran

import android.content.Intent
import android.content.pm.PackageManager

/** Leanback (TV) uygulamalarını listeler. Ana thread dışında çağrılmalı. */
fun loadApps(pm: PackageManager, selfPackage: String): List<AppEntry> =
    pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        PackageManager.ResolveInfoFlags.of(0),
    )
        .map { it.activityInfo }
        .filter { it.packageName != selfPackage }
        .map { AppEntry(it.packageName, it.name, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
