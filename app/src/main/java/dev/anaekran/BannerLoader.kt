package dev.anaekran

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Banner'ları arka planda yükler; yoksa ikonu fallback'e verir. */
class BannerLoader(private val pm: PackageManager) {
    private class Art(val banner: Drawable?, val icon: Drawable?)

    private val cache = LruCache<String, Art>(40)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun load(entry: AppEntry, into: ImageView, fallback: (Drawable?) -> Unit) {
        val key = entry.packageName
        into.tag = key
        into.setImageDrawable(null)
        cache.get(key)?.let { apply(it, into, fallback); return }
        executor.execute {
            val art = resolve(entry)
            cache.put(key, art)
            // Görünüm başka karta geri dönüştürüldüyse sonucu bırak.
            main.post { if (into.tag == key) apply(art, into, fallback) }
        }
    }

    fun clear() = cache.evictAll()

    /** Kuyruktaki tüm yüklemeler bitince ana thread'de çalışır (tek thread'li sıra: sona eklenen iş en son biter). */
    fun whenIdle(action: () -> Unit) = executor.execute { main.post(action) }

    private fun apply(art: Art, into: ImageView, fallback: (Drawable?) -> Unit) {
        if (art.banner != null) into.setImageDrawable(art.banner) else fallback(art.icon)
    }

    private fun resolve(e: AppEntry): Art {
        val cn = ComponentName(e.packageName, e.activityName)
        return try {
            val banner = pm.getActivityBanner(cn) ?: pm.getApplicationBanner(e.packageName)
            Art(banner, if (banner == null) pm.getActivityIcon(cn) else null)
        } catch (_: PackageManager.NameNotFoundException) {
            Art(null, null)
        }
    }
}
