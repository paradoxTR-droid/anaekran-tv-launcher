package dev.anaekran

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.GridView
import android.widget.TextView
import java.text.Collator
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

class HomeActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var header: TextView
    private lateinit var clock: TextView
    private var headerSlot: TimeSlot? = null
    private lateinit var loader: BannerLoader
    private lateinit var adapter: AppGridAdapter
    private var selected: View? = null
    private var initialFocusDone = false
    private val collator = Collator.getInstance(Locale.forLanguageTag("tr"))
    private val byLabel = Comparator<String> { a, b -> collator.compare(a, b) }
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateClock()
    }

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
        clock = findViewById(R.id.clock)
        setGreeting(slotFor(LocalTime.now().hour))

        bindIcon(R.id.btnWifi) { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        // Paketsiz SOUND_SETTINGS bu cihazda iki uygulama arasında seçim ekranı açıyor.
        bindIcon(R.id.btnSound) { openSettings(Settings.ACTION_SOUND_SETTINGS, "com.android.tv.settings") }
        bindIcon(R.id.btnSettings) { openSettings(Settings.ACTION_SETTINGS) }

        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ -> launch(adapter.getItem(pos)) }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // GridView odak yokken de seçim yapar; vurgu yalnızca ızgara odaktayken.
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = focus(if (grid.hasFocus()) v else null)
            override fun onNothingSelected(p: AdapterView<*>?) = focus(null)
        }
        grid.setOnFocusChangeListener { _, has -> focus(if (has) grid.selectedView else null) }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK), RECEIVER_NOT_EXPORTED)
        updateClock()
    }

    override fun onStop() {
        unregisterReceiver(tick)
        super.onStop()
    }

    private fun setGreeting(slot: TimeSlot) {
        headerSlot = slot
        header.text = pickGreeting(slot, Random.Default)
    }

    /** Saati yazar; saat dilimi değiştiyse başlığı animasyonsuz yeniler. */
    private fun updateClock() {
        val now = LocalTime.now()
        clock.text = now.format(clockFormat)
        val slot = slotFor(now.hour)
        if (slot != headerSlot) setGreeting(slot)
    }

    private fun bindIcon(id: Int, action: () -> Unit) {
        val v = findViewById<View>(id)
        v.setOnClickListener { action() }
        v.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && ev.action == KeyEvent.ACTION_DOWN && adapter.count > 0) {
                grid.setSelection(0)
                grid.requestFocus()
                true
            } else false
        }
    }

    fun openSettings(action: String, pkg: String? = null) {
        try {
            startActivity(Intent(action).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
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
                grid.post {
                    // Pencere ilk odağı üst bardaki simgeye verir; açılışta ilk karttan başla.
                    if (!initialFocusDone && list.isNotEmpty()) {
                        initialFocusDone = true
                        grid.setSelection(0)
                        grid.requestFocus()
                    }
                    if (grid.hasFocus()) focus(grid.selectedView)
                }
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
