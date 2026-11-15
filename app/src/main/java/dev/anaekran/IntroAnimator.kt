package dev.anaekran

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/**
 * Renkli ışık bulutları köşelerden açılır, cümle renk geçişli ve parıltılı belirir, başlığa kayar;
 * ardından kartlar satır satır yaylanarak oturur, bulutlar söner. Toplam ~2.2 sn.
 */
class IntroAnimator(
    private val header: TextView,
    private val overlay: TextView,
    private val glows: List<View>,
    private val labels: List<View>,
    /** Zayıf cihaz: parıltı yok, öğeler (satırlar) yaylanmadan kısa geçişle gelir. */
    private val lite: Boolean = false,
) {
    private var set: AnimatorSet? = null
    val isRunning: Boolean get() = set?.isRunning == true

    fun play(text: String, items: List<View>, onEnd: () -> Unit) {
        set?.cancel()
        val d = overlay.resources.displayMetrics.density
        val sw = overlay.resources.displayMetrics.widthPixels.toFloat()
        val sh = overlay.resources.displayMetrics.heightPixels.toFloat()
        header.text = text
        header.alpha = 0f
        overlay.text = text
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 1.4f; overlay.scaleY = 1.4f
        overlay.translationX = 0f; overlay.translationY = 0f
        items.forEach { it.alpha = 0f; it.translationY = 70 * d }
        labels.forEach { it.alpha = 0f }
        // Saydamlık animasyonu ekran dışı tampon açmasın (bu tamponlar animasyondan sonra ~40 MB GPU belleğinde kalıyordu).
        (items + glows + labels + header + overlay).forEach { it.forceHasOverlappingRendering(false) }
        // Bulutların başlangıç köşeleri (merkeze göre) ve ulaşacakları noktalar.
        val from = listOf(-0.55f to -0.5f, 0.55f to -0.45f, 0f to 0.6f)
        val to = listOf(-0.22f to -0.12f, 0.24f to -0.05f, 0.02f to 0.2f)
        glows.forEachIndexed { i, g ->
            g.visibility = View.VISIBLE
            g.alpha = 0f
            g.scaleX = 0.3f; g.scaleY = 0.3f
            g.translationX = from[i].first * sw; g.translationY = from[i].second * sh
        }

        overlay.post {
            // Yazıya renk geçişi; shimmer için yatayda kaydırılır.
            val w = overlay.width.toFloat().coerceAtLeast(1f)
            val shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(0xffb79cff.toInt(), 0xff7ad7ff.toInt(), 0xffffffff.toInt(), 0xffff7ab8.toInt(), 0xffb79cff.toInt()),
                null, Shader.TileMode.MIRROR,
            )
            if (!lite) overlay.paint.shader = shader
            val m = Matrix()
            val shimmer = ValueAnimator.ofFloat(-w, w).apply {
                duration = 1100
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { m.setTranslate(it.animatedValue as Float, 0f); shader.setLocalMatrix(m); overlay.invalidate() }
            }

            // Başlık 24sp, overlay 48sp; hedef = başlık metninin merkezi. Layout konumu ölçekten etkilenmez.
            val scale = header.textSize / overlay.textSize
            val h = IntArray(2).also { header.getLocationInWindow(it) }
            val dx = h[0] + header.paddingLeft + overlay.width * scale / 2f - (overlay.left + overlay.width / 2f)
            val dy = h[1] + header.height / 2f - (overlay.top + overlay.height / 2f)

            val appear = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f),
                    ObjectAnimator.ofFloat(overlay, View.SCALE_X, 1f),
                    ObjectAnimator.ofFloat(overlay, View.SCALE_Y, 1f),
                )
                duration = 420
                interpolator = OvershootInterpolator(1.4f)
            }
            val move = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(overlay, View.TRANSLATION_X, dx),
                    ObjectAnimator.ofFloat(overlay, View.TRANSLATION_Y, dy),
                    ObjectAnimator.ofFloat(overlay, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(overlay, View.SCALE_Y, scale),
                )
                startDelay = 750
                duration = 420
                interpolator = AccelerateDecelerateInterpolator()
            }
            val glowIn = glows.mapIndexed { i, g ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(g, View.ALPHA, 1f),
                        ObjectAnimator.ofFloat(g, View.SCALE_X, 1.25f),
                        ObjectAnimator.ofFloat(g, View.SCALE_Y, 1.25f),
                        ObjectAnimator.ofFloat(g, View.TRANSLATION_X, to[i].first * sw),
                        ObjectAnimator.ofFloat(g, View.TRANSLATION_Y, to[i].second * sh),
                    )
                    startDelay = i * 90L
                    duration = 900
                    interpolator = DecelerateInterpolator(1.5f)
                }
            }
            val glowOut = glows.map { g ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(g, View.ALPHA, 0f),
                        ObjectAnimator.ofFloat(g, View.SCALE_X, 1.8f),
                        ObjectAnimator.ofFloat(g, View.SCALE_Y, 1.8f),
                    )
                    startDelay = 1350
                    duration = 850
                    interpolator = AccelerateDecelerateInterpolator()
                }
            }
            val cardAnims = items.mapIndexed { i, c ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(c, View.ALPHA, 1f).apply { interpolator = DecelerateInterpolator() },
                        ObjectAnimator.ofFloat(c, View.TRANSLATION_Y, 0f)
                            .apply { interpolator = if (lite) DecelerateInterpolator() else OvershootInterpolator(1.6f) },
                    )
                    startDelay = if (lite) 950L + i * 110L else 1100L + minOf(i, 15) * 45L
                    duration = if (lite) 300 else 420
                }
            }
            // Renkli overlay başlığın üstündeyken beyaz başlığa yumuşakça geçiş.
            val handoff = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(header, View.ALPHA, 1f),
                    ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f),
                )
                startDelay = 1700
                duration = 450
            }
            // Satır başlıkları animasyonun sonunda belirir.
            val labelsIn = AnimatorSet().apply {
                playTogether(labels.map { ObjectAnimator.ofFloat(it, View.ALPHA, 1f) })
                startDelay = 1800
                duration = 450
            }
            if (lite) { handoff.startDelay = 1150; labelsIn.startDelay = 1250 }
            val all = AnimatorSet()
            set = all
            all.playTogether(listOfNotNull(appear, move, shimmer.takeUnless { lite }, handoff, labelsIn) + glowIn + glowOut + cardAnims)
            all.addListener(object : AnimatorListenerAdapter() {
                private var done = false
                override fun onAnimationEnd(a: Animator) {
                    if (done) return
                    done = true
                    finish(items)
                    if (set === all) set = null
                    onEnd()
                }
            })
            all.start()
        }
    }

    /** Animasyonu keser ve son hale atlar. */
    fun skip() {
        set?.cancel()
    }

    private fun finish(items: List<View>) {
        header.alpha = 1f
        overlay.visibility = View.GONE
        overlay.paint.shader = null
        items.forEach { it.alpha = 1f; it.translationY = 0f }
        labels.forEach { it.alpha = 1f }
        glows.forEach { it.visibility = View.GONE }
    }
}
