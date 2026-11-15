package dev.anaekran

import android.content.Context

class Prefs(context: Context) : IntroState {
    private val sp = context.getSharedPreferences("anaekran", Context.MODE_PRIVATE)

    var pinned: List<String>
        get() = sp.getString(KEY_PINNED, "")!!.split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString(KEY_PINNED, v.joinToString("\n")).apply()

    var hidden: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_HIDDEN, v).apply()

    override var introPending: Boolean
        get() = sp.getBoolean(KEY_INTRO_PENDING, false)
        set(v) = sp.edit().putBoolean(KEY_INTRO_PENDING, v).apply()

    override var lastBootCount: Int
        get() = sp.getInt(KEY_LAST_BOOT, -1)
        set(v) = sp.edit().putInt(KEY_LAST_BOOT, v).apply()

    var favoritesInitialized: Boolean
        get() = sp.getBoolean(KEY_FAV_INIT, false)
        set(v) = sp.edit().putBoolean(KEY_FAV_INIT, v).apply()

    private companion object {
        const val KEY_FAV_INIT = "favorites_initialized"
        const val KEY_PINNED = "pinned"
        const val KEY_HIDDEN = "hidden"
        const val KEY_INTRO_PENDING = "intro_pending"
        const val KEY_LAST_BOOT = "last_boot_count"
    }
}
