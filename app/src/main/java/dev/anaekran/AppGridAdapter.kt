package dev.anaekran

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** 16:9 kart, köşeleri yuvarlatılmış. */
class CardFrame(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, 6 * resources.displayMetrics.density)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(w * 9 / 16, MeasureSpec.EXACTLY))
    }
}

class AppGridAdapter(private val loader: BannerLoader) : BaseAdapter() {
    var items: List<AppEntry> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = items[position].packageName.hashCode().toLong()
    override fun hasStableIds() = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        val e = items[position]
        val banner = v.findViewById<ImageView>(R.id.banner)
        val fallback = v.findViewById<View>(R.id.fallback)
        val icon = v.findViewById<ImageView>(R.id.icon)
        v.findViewById<TextView>(R.id.label).text = e.label
        fallback.setBackgroundColor(PALETTE[Math.floorMod(e.packageName.hashCode(), PALETTE.size)])
        fallback.visibility = View.INVISIBLE
        icon.setImageDrawable(null)
        loader.load(e, banner) { ic ->
            icon.setImageDrawable(ic)
            fallback.visibility = View.VISIBLE
        }
        return v
    }

    private companion object {
        val PALETTE = intArrayOf(
            0xffb20710.toInt(), 0xff0b2a8c.toInt(), 0xff5a189a.toInt(), 0xff00796b.toInt(),
            0xffff6a00.toInt(), 0xff1f6feb.toInt(), 0xff2b2d42.toInt(), 0xff8d6e00.toInt(),
        )
    }
}
