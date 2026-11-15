package dev.anaekran

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.GridView
import android.widget.TextView
import java.text.Collator
import java.time.LocalTime
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

class HomeActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var header: TextView
    private lateinit var loader: BannerLoader
    private lateinit var adapter: AppGridAdapter
    private var selected: View? = null
    private val collator = Collator.getInstance(Locale.forLanguageTag("tr"))
    private val byLabel = Comparator<String> { a, b -> collator.compare(a, b) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = Prefs(this)
        grid = findViewById(R.id.grid)
        empty = findViewById(R.id.empty)
        header = findViewById(R.id.header)
        loader = BannerLoader(packageManager)
        adapter = AppGridAdapter(loader)
        grid.adapter = adapter
        header.text = pickGreeting(slotFor(LocalTime.now().hour), Random.Default)

        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ -> launch(adapter.getItem(pos)) }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = focus(v)
            override fun onNothingSelected(p: AdapterView<*>?) = focus(null)
        }
        grid.setOnFocusChangeListener { _, has -> focus(if (has) grid.selectedView else null) }
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
    }

    fun reloadApps() {
        io.execute {
            val list = arrange(loadApps(packageManager, packageName), prefs.pinned, prefs.hidden, byLabel)
            runOnUiThread {
                adapter.items = list
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                grid.post { if (grid.hasFocus()) focus(grid.selectedView) }
            }
        }
    }

    /** Odaklanan kart büyür ve çerçeve alır; önceki eski haline döner. */
    private fun focus(v: View?) {
        if (v === selected) return
        selected?.let { old ->
            old.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(150).start()
            old.foreground = null
        }
        selected = v
        v?.let {
            it.animate().scaleX(1.08f).scaleY(1.08f).translationZ(12 * resources.displayMetrics.density).setDuration(150).start()
            it.foreground = getDrawable(R.drawable.card_focus)
        }
    }

    private fun launch(e: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setClassName(e.packageName, e.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            reloadApps()
        } catch (_: SecurityException) {
            reloadApps()
        }
    }
}
