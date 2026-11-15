package dev.anaekran

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
    /** Her kutucuk sırayla dener: Google TV (Android 14) yolu, sonra Android 9 yolu; hiçbiri yoksa genel Ayarlar. */
    private class Tile(val key: String, val icon: Int, val label: Int, val intents: List<Intent>)

    private val tiles = listOf(
        Tile("tile:wifi", R.drawable.ic_wifi, R.string.tile_wifi, listOf(Intent(Settings.ACTION_WIFI_SETTINGS))),
        // Paketsiz SOUND_SETTINGS Google TV'de iki uygulama arasında seçim ekranı açıyor.
        Tile("tile:sound", R.drawable.ic_sound, R.string.tile_sound, listOf(Intent(Settings.ACTION_SOUND_SETTINGS).setPackage(TV_SETTINGS))),
        // Google TV'de DISPLAY_SETTINGS çözülmüyor, ekran ayarları "Ekran ve Ses" sayfasında; Android 9'da standart intent çalışıyor.
        Tile("tile:display", R.drawable.ic_display, R.string.tile_display, listOf(
            Intent().setClassName(TV_SETTINGS, "$TV_SETTINGS.device.displaysound.DisplaySoundActivity"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
        )),
        // BLUETOOTH_SETTINGS iki cihazda da çözülmüyor: Google TV'de bağlı cihazlar, Android 9'da aksesuar eşleştirme sayfası.
        Tile("tile:bt", R.drawable.ic_bluetooth, R.string.tile_bluetooth, listOf(
            Intent("com.android.tv.settings.CONNECTED_DEVICES"),
            Intent("com.google.android.intent.action.CONNECT_INPUT"),
        )),
        Tile(KEY_ALL_SETTINGS, R.drawable.ic_settings, R.string.tile_all_settings, listOf(Intent(Settings.ACTION_SETTINGS))),
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
    private lateinit var menuPanel: View
    private lateinit var menuScrim: View
    private var menuReturnFocus: View? = null
    private val menuOpen get() = menuPanel.visibility == View.VISIBLE

    private val collator = Collator.getInstance(Locale.forLanguageTag("tr"))
    private val byLabel = Comparator<String> { a, b -> collator.compare(a, b) }
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateClock()
    }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = gate.onScreenOff()
    }
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            loader.clear() // güncellenen uygulamanın banner'ı değişmiş olabilir
            reloadApps()
        }
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
        intro = IntroAnimator(header, findViewById(R.id.overlayTitle), listOf(findViewById(R.id.glowA), findViewById(R.id.glowB), findViewById(R.id.glowC)),
            listOf(findViewById(R.id.labelFav), findViewById(R.id.labelAll), findViewById(R.id.labelSettings)))
        menuPanel = findViewById(R.id.menuPanel)
        menuScrim = findViewById(R.id.menuScrim)
        menuScrim.setOnClickListener { closeMenu() }
        setGreeting(slotFor(LocalTime.now().hour))
        buildTiles()
        register(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), exported = false)
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        // NOT_EXPORTED ile bu cihazda PACKAGE_* yayınları gelmiyor; bunlar korumalı sistem yayınları, başka uygulama gönderemez.
        register(packages, pkgFilter, exported = true)
    }

    /** Home'a ana ekrandayken basılınca: animasyonsuz başa dön, ilk karta odaklan. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            closeMenu()
            findViewById<View>(R.id.favScroll).scrollTo(0, 0)
            findViewById<View>(R.id.allScroll).scrollTo(0, 0)
            firstCard()?.requestFocus()
        }
    }

    /** Android 13+ dışa açıklık bayrağı ister; daha eskilerde (Android 9 stick) bayrak yok. */
    private fun register(r: BroadcastReceiver, f: IntentFilter, exported: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, if (exported) RECEIVER_EXPORTED else RECEIVER_NOT_EXPORTED)
        else registerReceiver(r, f)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        unregisterReceiver(packages)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        register(tick, IntentFilter(Intent.ACTION_TIME_TICK), exported = false)
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
        // Geri: menü açıksa kapatır, değilse ana ekranda hiçbir şey yapmaz.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && menuOpen) closeMenu()
            return true
        }
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
            // Arka plan hatası ana ekran sürecini öldürmesin (ana ekran çökerse TV'de ekran kalmaz).
            val apps = try {
                loadApps(packageManager, packageName)
            } catch (e: RuntimeException) {
                android.util.Log.w("AnaEkran", "loadApps failed", e)
                return@execute
            }
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
            if (introQueued) rowsView.postDelayed(::startIntro, introDelayMs(SystemClock.elapsedRealtime()))
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

    /** Bekleme sırasında bir tuşa basıldıysa (introQueued=false) animasyon hiç başlamaz. */
    private fun startIntro() {
        if (!introQueued) return
        introQueued = false
        rowsView.alpha = 1f
        setGreeting(slotFor(LocalTime.now().hour))
        val items = favRow.children() + allRow.children().take(7) + tileRow.children()
        intro.play(header.text.toString(), items) {}
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
            card.outlineAmbientShadowColor = 0
            card.outlineSpotShadowColor = 0
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
            v.setOnClickListener { openSettings(*t.intents.toTypedArray()) }
            if (t.key == KEY_ALL_SETTINGS) v.setOnLongClickListener { showHiddenApps(); true }
            v.outlineAmbientShadowColor = 0
            v.outlineSpotShadowColor = 0
            v.setOnFocusChangeListener { view, has -> highlight(view, has) }
            tileRow.addView(v)
        }
    }

    /** Odaklanan kart/kutucuk büyür ve beyaz çerçeve alır. */
    private fun highlight(v: View, has: Boolean) {
        // Küçük yükseklik yalnızca büyüyen kart komşusunun üstünde çizilsin diye; gölge renkleri şeffaf (bkz. kart kurulumu).
        v.translationZ = if (has) 2f else 0f
        v.animate().scaleX(if (has) 1.1f else 1f).scaleY(if (has) 1.1f else 1f)
            .setInterpolator(if (has) OvershootInterpolator(2f) else DecelerateInterpolator())
            .setDuration(if (has) 220 else 150).start()
        v.foreground = if (has) getDrawable(R.drawable.card_focus) else null
    }

    // --- Menüler ---


    /** Sağdan kayan eylem paneli; seçenekler uygulamanın durumuna göre (bkz. menuActions). */
    private fun showAppMenu(e: AppEntry, favorite: Boolean) {
        val favs = current?.favorites.orEmpty()
        val actions = menuActions(if (favorite) favs.indexOf(e) else -1, favs.size, e.removable)
        findViewById<TextView>(R.id.menuTitle).text = e.label
        val items = findViewById<LinearLayout>(R.id.menuItems)
        items.removeAllViews()
        val inflater = LayoutInflater.from(this)
        actions.forEach { a ->
            val (icon, label) = when (a) {
                MenuAction.OPEN -> R.drawable.ic_play to R.string.menu_open
                MenuAction.FAV_ADD -> R.drawable.ic_star to R.string.fav_add
                MenuAction.FAV_REMOVE -> R.drawable.ic_star_off to R.string.fav_remove
                MenuAction.MOVE_LEFT -> R.drawable.ic_arrow_left to R.string.move_left
                MenuAction.MOVE_RIGHT -> R.drawable.ic_arrow_right to R.string.move_right
                MenuAction.HIDE -> R.drawable.ic_hide to R.string.hide
                MenuAction.INFO -> R.drawable.ic_info to R.string.app_info
                MenuAction.UNINSTALL -> R.drawable.ic_delete to R.string.uninstall
            }
            val row = inflater.inflate(R.layout.item_menu, items, false)
            row.findViewById<ImageView>(R.id.menuIcon).setImageResource(icon)
            row.findViewById<TextView>(R.id.menuLabel).setText(label)
            row.setOnClickListener { closeMenu(); runAction(a, e) }
            items.addView(row)
        }
        menuReturnFocus = currentFocus
        // Panel açıkken odak kartlara kaçmasın.
        (rowsView as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        menuScrim.alpha = 0f
        menuScrim.visibility = View.VISIBLE
        menuScrim.animate().alpha(1f).setDuration(220).start()
        menuPanel.visibility = View.VISIBLE
        menuPanel.post {
            menuPanel.translationX = menuPanel.width.toFloat()
            menuPanel.animate().translationX(0f).setInterpolator(DecelerateInterpolator(2f)).setDuration(220).start()
            items.getChildAt(0)?.requestFocus()
        }
    }

    private fun closeMenu() {
        if (!menuOpen) return
        menuScrim.animate().alpha(0f).setDuration(180).withEndAction { menuScrim.visibility = View.GONE }.start()
        menuPanel.animate().translationX(menuPanel.width.toFloat()).setInterpolator(DecelerateInterpolator())
            .setDuration(180).withEndAction { menuPanel.visibility = View.GONE }.start()
        (rowsView as ViewGroup).descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        menuReturnFocus?.requestFocus()
    }

    private fun visibleFavorites() = current?.favorites.orEmpty().map { it.packageName }

    private fun runAction(a: MenuAction, e: AppEntry) {
        val pkg = e.packageName
        when (a) {
            MenuAction.OPEN -> return launch(e)
            MenuAction.FAV_ADD -> prefs.pinned = prefs.pinned - pkg + pkg
            MenuAction.FAV_REMOVE -> prefs.pinned = prefs.pinned - pkg
            MenuAction.MOVE_LEFT -> prefs.pinned = moveVisibleFavorite(prefs.pinned, visibleFavorites(), pkg, -1)
            MenuAction.MOVE_RIGHT -> prefs.pinned = moveVisibleFavorite(prefs.pinned, visibleFavorites(), pkg, 1)
            MenuAction.HIDE -> prefs.hidden = prefs.hidden + pkg
            MenuAction.INFO -> return openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
            // Sistem onay penceresi açılır; kaldırılırsa PACKAGE_REMOVED listeyi yeniler.
            MenuAction.UNINSTALL -> return openSettings(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)))
        }
        reloadApps()
    }

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    /** Gizlenenleri listeler; seçilen tekrar görünür. Artık kurulu olmayanlar atlanır. */
    private fun showHiddenApps() {
        io.execute {
            val pm = packageManager
            val hidden = prefs.hidden.mapNotNull { pkg ->
                try {
                    pkg to pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
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

    /** Ana ekran asla çökmesin: hedef yoksa ya da dışa kapalıysa genel Ayarlar'a, o da olmazsa hiçbir şey. */
    fun openSettings(vararg intents: Intent) {
        for (i in intents.toList() + Intent(Settings.ACTION_SETTINGS)) {
            try {
                startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
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
