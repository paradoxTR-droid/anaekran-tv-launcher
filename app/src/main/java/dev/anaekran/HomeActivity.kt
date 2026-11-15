package dev.anaekran

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.Collator
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

class HomeActivity : Activity() {
    private class Tile(val key: String, val icon: Int, val label: Int, val intent: Intent)

    private val tiles = listOf(
        Tile("tile:wifi", R.drawable.ic_wifi, R.string.tile_wifi, Intent(Settings.ACTION_WIFI_SETTINGS)),
        // Paketsiz SOUND_SETTINGS bu cihazda iki uygulama arasında seçim ekranı açıyor.
        Tile("tile:sound", R.drawable.ic_sound, R.string.tile_sound, Intent(Settings.ACTION_SOUND_SETTINGS).setPackage(TV_SETTINGS)),
        // Google TV'de ayrı ekran sayfası yok; DISPLAY_SETTINGS çözülmüyor, görüntü ayarları bu sayfada.
        Tile("tile:display", R.drawable.ic_display, R.string.tile_display, Intent().setClassName(TV_SETTINGS, "$TV_SETTINGS.device.displaysound.DisplaySoundActivity")),
        // BLUETOOTH_SETTINGS çözülmüyor; Google TV'de bağlı cihazlar (kumanda, Bluetooth) sayfası.
        Tile("tile:bt", R.drawable.ic_bluetooth, R.string.tile_bluetooth, Intent("com.android.tv.settings.CONNECTED_DEVICES")),
        Tile(KEY_ALL_SETTINGS, R.drawable.ic_settings, R.string.tile_all_settings, Intent(Settings.ACTION_SETTINGS)),
    )

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Prefs
    private lateinit var gate: IntroGate
    private lateinit var intro: IntroAnimator
    private lateinit var loader: BannerLoader
    private lateinit var header: TextView
    private lateinit var clock: TextView
    private lateinit var empty: TextView
    private lateinit var rowsView: View
    private lateinit var favRow: LinearLayout
    private lateinit var allRow: LinearLayout
    private lateinit var tileRow: LinearLayout

    private var current: Rows? = null
    private var headerSlot: TimeSlot? = null
    private var initialFocusDone = false
    private var introQueued = false
    private var swallowKeyUp = false

    private val collator = Collator.getInstance(Locale.forLanguageTag("tr"))
    private val byLabel = Comparator<String> { a, b -> collator.compare(a, b) }
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateClock()
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = gate.onScreenOff()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = Prefs(this)
        gate = IntroGate(prefs)
        loader = BannerLoader(packageManager)
        header = findViewById(R.id.header)
        clock = findViewById(R.id.clock)
        empty = findViewById(R.id.empty)
        rowsView = findViewById(R.id.rows)
        favRow = findViewById(R.id.favRow)
        allRow = findViewById(R.id.allRow)
        tileRow = findViewById(R.id.tiles)
        intro = IntroAnimator(header, findViewById(R.id.overlayTitle))
        setGreeting(slotFor(LocalTime.now().hour))
        buildTiles()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
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

    override fun onResume() {
        super.onResume()
        val bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (gate.shouldPlay(bootCount)) {
            gate.markPlayed(bootCount)
            introQueued = true
            rowsView.alpha = 0f // satırlar liste yüklenince animasyonla gelir
            header.alpha = 0f
        }
        reloadApps()
    }

    /** Animasyon sırasında herhangi bir tuş animasyonu atlar; tuş başka işe yaramaz. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (intro.isRunning || introQueued)) {
            introQueued = false
            intro.skip()
            rowsView.alpha = 1f
            header.alpha = 1f
            swallowKeyUp = true
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && swallowKeyUp) {
            swallowKeyUp = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // --- Saat ve başlık ---

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

    // --- Satırlar ---

    fun reloadApps() {
        io.execute {
            val apps = loadApps(packageManager, packageName)
            if (!prefs.favoritesInitialized) {
                prefs.pinned = defaultFavorites(apps.map { it.packageName }.toSet())
                prefs.favoritesInitialized = true
            }
            val rows = splitRows(apps, prefs.pinned, prefs.hidden, byLabel)
            runOnUiThread { showRows(rows) }
        }
    }

    private fun showRows(rows: Rows) {
        // Satırlar yeniden kurulunca odak kaybolur; hangi satırda, hangi sırada olduğunu hatırla.
        val focused = currentFocus
        val focusedKey = focused?.tag as? String
        val focusedRow = listOf(favRow, allRow, tileRow).firstOrNull { focused != null && focused.parent === it }
        val focusedIndex = focusedRow?.indexOfChild(focused) ?: -1
        if (rows != current) {
            current = rows
            fillRow(favRow, rows.favorites, favorite = true, visible = 4)
            fillRow(allRow, rows.others, favorite = false, visible = 6)
        }
        val nothing = rows.favorites.isEmpty() && rows.others.isEmpty()
        empty.visibility = if (nothing) View.VISIBLE else View.GONE

        rowsView.post {
            if (introQueued) {
                introQueued = false
                rowsView.alpha = 1f
                setGreeting(slotFor(LocalTime.now().hour))
                val items = favRow.children() + allRow.children().take(7) + tileRow.children()
                intro.play(header.text.toString(), items) {}
            }
            val target = when {
                !initialFocusDone -> firstCard()
                focusedRow != null -> focusedRow.children().firstOrNull { it.tag == focusedKey }
                    ?: focusedRow.children().getOrNull(minOf(focusedIndex, focusedRow.childCount - 1))
                    ?: firstCard()
                else -> null
            }
            initialFocusDone = true
            target?.requestFocus()
        }
    }

    private fun firstCard(): View? =
        favRow.children().firstOrNull() ?: allRow.children().firstOrNull() ?: tileRow.children().first()

    private fun LinearLayout.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    private fun fillRow(row: LinearLayout, apps: List<AppEntry>, favorite: Boolean, visible: Int) {
        row.removeAllViews()
        val d = resources.displayMetrics.density
        val gap = (24 * d).toInt()
        val contentWidth = resources.displayMetrics.widthPixels - row.paddingStart - row.paddingEnd
        val width = (contentWidth - gap * (visible - 1)) / visible
        val inflater = LayoutInflater.from(this)
        apps.forEachIndexed { i, e ->
            val card = inflater.inflate(R.layout.item_app, row, false)
            card.layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { if (i > 0) marginStart = gap }
            card.tag = e.packageName
            bindCard(card, e)
            card.setOnClickListener { launch(e) }
            card.setOnLongClickListener { showAppMenu(e, favorite); true }
            card.setOnFocusChangeListener { v, has -> highlight(v, has) }
            row.addView(card)
        }
    }

    private fun bindCard(card: View, e: AppEntry) {
        val fallback = card.findViewById<View>(R.id.fallback)
        val icon = card.findViewById<ImageView>(R.id.icon)
        card.findViewById<TextView>(R.id.label).text = e.label
        fallback.setBackgroundColor(PALETTE[Math.floorMod(e.packageName.hashCode(), PALETTE.size)])
        fallback.visibility = View.INVISIBLE
        loader.load(e, card.findViewById(R.id.banner)) { ic ->
            icon.setImageDrawable(ic)
            fallback.visibility = View.VISIBLE
        }
    }

    private fun buildTiles() {
        val inflater = LayoutInflater.from(this)
        val gap = (24 * resources.displayMetrics.density).toInt()
        tiles.forEachIndexed { i, t ->
            val v = inflater.inflate(R.layout.item_tile, tileRow, false)
            (v.layoutParams as LinearLayout.LayoutParams).apply { if (i > 0) marginStart = gap }
            v.tag = t.key
            v.findViewById<ImageView>(R.id.tileIcon).setImageResource(t.icon)
            v.findViewById<TextView>(R.id.tileLabel).setText(t.label)
            v.setOnClickListener { openSettings(t.intent) }
            if (t.key == KEY_ALL_SETTINGS) v.setOnLongClickListener { showHiddenApps(); true }
            v.setOnFocusChangeListener { view, has -> highlight(view, has) }
            tileRow.addView(v)
        }
    }

    /** Odaklanan kart/kutucuk büyür ve beyaz çerçeve alır. */
    private fun highlight(v: View, has: Boolean) {
        val d = resources.displayMetrics.density
        v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f)
            .translationZ(if (has) 12 * d else 0f).setDuration(150).start()
        v.foreground = if (has) getDrawable(R.drawable.card_focus) else null
    }

    // --- Menüler ---

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun showAppMenu(e: AppEntry, favorite: Boolean) {
        val first = getString(if (favorite) R.string.fav_remove else R.string.fav_add)
        dialog().setTitle(e.label)
            .setItems(arrayOf(first, getString(R.string.hide))) { _, which ->
                if (which == 0) {
                    prefs.pinned = if (favorite) prefs.pinned - e.packageName else prefs.pinned - e.packageName + e.packageName
                } else {
                    prefs.hidden = prefs.hidden + e.packageName
                }
                reloadApps()
            }
            .show()
    }

    /** Gizlenenleri listeler; seçilen tekrar görünür. Artık kurulu olmayanlar atlanır. */
    private fun showHiddenApps() {
        io.execute {
            val pm = packageManager
            val hidden = prefs.hidden.mapNotNull { pkg ->
                try {
                    pkg to pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }.sortedWith(compareBy(byLabel) { it.second })
            runOnUiThread {
                val b = dialog().setTitle(R.string.hidden_apps)
                if (hidden.isEmpty()) b.setMessage(R.string.no_hidden)
                else b.setItems(hidden.map { it.second }.toTypedArray()) { _, which ->
                    prefs.hidden = prefs.hidden - hidden[which].first
                    reloadApps()
                }
                b.show()
            }
        }
    }

    // --- Açma ---

    fun openSettings(intent: Intent) {
        try {
            startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

    private companion object {
        const val TV_SETTINGS = "com.android.tv.settings"
        const val KEY_ALL_SETTINGS = "tile:all"
        val PALETTE = intArrayOf(
            0xffb20710.toInt(), 0xff0b2a8c.toInt(), 0xff5a189a.toInt(), 0xff00796b.toInt(),
            0xffff6a00.toInt(), 0xff1f6feb.toInt(), 0xff2b2d42.toInt(), 0xff8d6e00.toInt(),
        )
    }
}
