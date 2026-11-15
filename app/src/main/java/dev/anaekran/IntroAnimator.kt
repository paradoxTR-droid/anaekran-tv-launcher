package dev.anaekran

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.GridView
import android.widget.TextView

/**
 * Ortada beliren cümle sol üstteki başlığa kayar, ardından kartlar alttan sırayla yerine oturur.
 * Toplam ~1.9 sn: 0–300 ms belirme, 550–900 ms başlığa kayma, 900 ms'den itibaren kartlar.
 */
class IntroAnimator(private val header: TextView, private val grid: GridView, private val overlay: TextView) {
    private var set: AnimatorSet? = null
    val isRunning: Boolean get() = set?.isRunning == true

    fun play(text: String, onEnd: () -> Unit) {
        val d = grid.resources.displayMetrics.density
        header.text = text
        header.alpha = 0f
        overlay.text = text
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.scaleX = 1.25f; overlay.scaleY = 1.25f
        overlay.translationX = 0f; overlay.translationY = 0f
        grid.alpha = 1f
        val cards = (0 until grid.childCount).map { grid.getChildAt(it) }
        cards.forEach { it.alpha = 0f; it.translationY = 60 * d }

        overlay.post {
            // Başlık 24sp, overlay 48sp: yarı ölçek; hedef = başlık metninin merkezi.
            val scale = header.textSize / overlay.textSize
            val o = IntArray(2).also { overlay.getLocationOnScreen(it) }
            val h = IntArray(2).also { header.getLocationOnScreen(it) }
            val targetCx = h[0] + header.paddingLeft + overlay.width * scale / 2f
            val targetCy = h[1] + header.height / 2f
            val dx = targetCx - (o[0] + overlay.width / 2f)
            val dy = targetCy - (o[1] + overlay.height / 2f)

            val appear = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f),
                    ObjectAnimator.ofFloat(overlay, View.SCALE_X, 1f),
                    ObjectAnimator.ofFloat(overlay, View.SCALE_Y, 1f),
                )
                duration = 300
            }
            val move = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(overlay, View.TRANSLATION_X, dx),
                    ObjectAnimator.ofFloat(overlay, View.TRANSLATION_Y, dy),
                    ObjectAnimator.ofFloat(overlay, View.SCALE_X, scale),
                    ObjectAnimator.ofFloat(overlay, View.SCALE_Y, scale),
                )
                startDelay = 550
                duration = 350
                interpolator = DecelerateInterpolator()
            }
            val cardAnims = cards.mapIndexed { i, c ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(c, View.ALPHA, 1f),
                        ObjectAnimator.ofFloat(c, View.TRANSLATION_Y, 0f),
                    )
                    startDelay = 900L + minOf(i, 14) * 50L
                    duration = 300
                    interpolator = DecelerateInterpolator()
                }
            }
            set = AnimatorSet().apply {
                playTogether(listOf(appear, move) + cardAnims)
                addListener(object : AnimatorListenerAdapter() {
                    private var done = false
                    override fun onAnimationEnd(a: Animator) {
                        if (done) return
                        done = true
                        finish(cards)
                        onEnd()
                    }
                })
                start()
            }
        }
    }

    /** Animasyonu keser ve son hale atlar (onEnd, cancel→end üzerinden çağrılır). */
    fun skip() {
        set?.cancel()
    }

    private fun finish(cards: List<View>) {
        header.alpha = 1f
        overlay.visibility = View.GONE
        cards.forEach { it.alpha = 1f; it.translationY = 0f }
        set = null
    }
}
