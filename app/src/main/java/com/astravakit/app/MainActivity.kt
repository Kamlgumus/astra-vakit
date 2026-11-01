package com.astravakit.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.YearMonth
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {

    private val BG = 0xFF050510.toInt()
    private val TEXT = 0xFFE8E8FF.toInt()
    private val MUTED = 0xFF9A9AB8.toInt()
    private val ACC = 0xFF7C5CFF.toInt()
    private val ACC2 = 0xFF00D4FF.toInt()
    private val GOLD = 0xFFFFC857.toInt()
    private val GREEN = 0xFF3DFF9A.toInt()
    private val GLASS = 0x22FFFFFF
    private val BORDER = 0x22FFFFFF
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val TR = Locale("tr", "TR")

    private lateinit var root: FrameLayout
    private lateinit var ui: FrameLayout
    private var content: FrameLayout? = null
    private val barItems = ArrayList<TextView>()
    private var tab = 0
    private var mainShown = false
    private val handler = Handler(Looper.getMainLooper())

    // Ana ekran canlı öğeleri
    private var cdTv: TextView? = null
    private var ring: RingView? = null
    private var sunArc: SunArcView? = null
    private var up: Triple<Int, Long, Long>? = null

    // Takvim
    private var calMonth: YearMonth = YearMonth.now()

    // Kıble
    private var compass: CompassView? = null
    private var qiblaStatus: TextView? = null
    private var sensorMgr: SensorManager? = null
    private var smoothHeading = 0f
    private var declination = 0f

    // Tesbih
    private var tCountTv: TextView? = null
    private var tTotalTv: TextView? = null

    private val DHIKR = listOf(
        Triple("Sübhanallah", "سُبْحَانَ اللّٰهِ", 33),
        Triple("Elhamdülillah", "اَلْحَمْدُ لِلّٰهِ", 33),
        Triple("Allahu Ekber", "اَللّٰهُ أَكْبَرُ", 33),
        Triple("Lâ ilâhe illallah", "لَا إِلٰهَ إِلَّا اللّٰهُ", 100),
        Triple("Estağfirullah", "أَسْتَغْفِرُ اللّٰهَ", 100),
        Triple("Salavat", "اَللّٰهُمَّ صَلِّ عَلَىٰ سَيِّدِنَا مُحَمَّدٍ", 100)
    )
    private val HIJRI = arrayOf("Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel",
        "Cemaziyelahir", "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce")

    // ------------------------------------------------------------------ yaşam döngüsü

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.addView(SpaceView(this), FrameLayout.LayoutParams(MATCH, MATCH))
        ui = FrameLayout(this)
        root.addView(ui, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        if (Store.name(this).isBlank() || !Store.onboarded(this)) showOnboarding() else showMain()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
        if (mainShown) {
            Scheduler.scheduleAll(this)
            WidgetProvider.updateAll(this)
            if (tab == 2) startSensors()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        stopSensors()
    }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L + 5L)
        }
    }

    private fun tick() {
        if (!mainShown || tab != 0 || cdTv == null) return
        val now = System.currentTimeMillis()
        val u = up ?: return
        if (now >= u.second) { showTab(0); return }
        cdTv?.text = Fmt.hms((u.second - now + 999L) / 1000L)
        val span = (u.second - u.third).coerceAtLeast(1L)
        ring?.progress = (now - u.third).toFloat() / span.toFloat()
        sunArc?.setNowMs(now)
    }

    // ------------------------------------------------------------------ yardımcılar

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(radius).toFloat()
        g.setStroke(dp(1), stroke)
        return g
    }

    private fun tv(text: String, size: Float, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun column(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(18), dp(16), dp(24))
        return l
    }

    private fun scroll(col: LinearLayout): ScrollView {
        val s = ScrollView(this)
        s.isVerticalScrollBarEnabled = false
        s.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        return s
    }

    private fun card(fill: Int = 0x40121228, stroke: Int = BORDER): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.background = rounded(fill, stroke, 20)
        l.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(12)
        l.layoutParams = lp
        return l
    }

    private fun topGap(v: View, px: Int): View {
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(px)
        v.layoutParams = lp
        return v
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun greet(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 5 -> "İyi geceler"
            h < 12 -> "Günaydın"
            h < 18 -> "İyi günler"
            else -> "İyi akşamlar"
        }
    }

    private fun hijriLine(d: LocalDate): String {
        return try {
            val h = HijrahDate.from(d)
            val day = h.get(ChronoField.DAY_OF_MONTH)
            val mo = h.get(ChronoField.MONTH_OF_YEAR)
            val yr = h.get(ChronoField.YEAR)
            var s = "$day ${HIJRI[mo - 1]} $yr"
            if (mo == 9) {
                s += "  ·  Ramazan'ın $day. günü"
            } else {
                var y = yr
                if (mo > 9) y++
                val r = LocalDate.from(HijrahDate.of(y, 9, 1))
                val days = ChronoUnit.DAYS.between(d, r)
                s += "  ·  Ramazan'a ~$days gün"
            }
            s
        } catch (e: Exception) { "" }
    }

    // ------------------------------------------------------------------ karşılama (isim + izinler)

    private fun showOnboarding() {
        mainShown = false
        ui.removeAllViews()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(dp(28), dp(28), dp(28), dp(28))

        col.addView(tv("✦", 54f, GOLD))
        col.addView(tv("Astra Vakit", 36f, TEXT, true))
        col.addView(tv("Yıldızların rehberliğinde vakitler", 14f, MUTED).apply { gravity = Gravity.CENTER })

        val q = tv("Seni nasıl çağıralım, yolcu?", 18f, TEXT)
        col.addView(topGap(q, 44))

        val et = EditText(this)
        et.hint = "Adın"
        et.setHintTextColor(MUTED)
        et.setTextColor(TEXT)
        et.textSize = 20f
        et.gravity = Gravity.CENTER
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        et.setSingleLine(true)
        et.background = rounded(GLASS, 0x557C5CFF, 16)
        et.setPadding(dp(16), dp(14), dp(16), dp(14))
        col.addView(topGap(et, 12))

        val btn = tv("Yola Çık 🚀", 17f, Color.WHITE, true)
        btn.gravity = Gravity.CENTER
        btn.background = rounded(ACC, 0x00000000, 16)
        btn.setPadding(dp(16), dp(15), dp(16), dp(15))
        col.addView(topGap(btn, 16))
        btn.setOnClickListener {
            val n = et.text.toString().trim()
            if (n.isEmpty()) { toast("Lütfen bir isim yaz"); return@setOnClickListener }
            Store.setName(this, n)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(et.windowToken, 0)
            askPermissions()
        }

        col.addView(topGap(tv("Konumun, vakitleri bulunduğun yere göre hesaplamak için; bildirim ise ezan vakti geldiğinde seni uyarmak için istenecek.", 12f, MUTED).apply { gravity = Gravity.CENTER }, 22))
        ui.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun askPermissions() {
        val list = ArrayList<String>()
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(list.toTypedArray(), 7)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 8) { fetchLocation(true); return }
        if (requestCode == 7) {
            Store.setOnboarded(this, true)
            showMain()
            if (!Scheduler.canExact(this)) askExactAlarm()
        }
    }

    private fun askExactAlarm() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Tam vaktinde bildirim")
            .setMessage("Ezan vakti geldiği dakikada bildirim alabilmen için \"Alarmlar ve hatırlatıcılar\" iznini açman gerekiyor.")
            .setPositiveButton("Ayarları aç") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                } catch (e: Exception) { toast("Ayarlar açılamadı") }
            }
            .setNegativeButton("Sonra", null)
            .show()
    }

    // ------------------------------------------------------------------ ana iskelet

    private fun showMain() {
        mainShown = true
        ui.removeAllViews()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val c = FrameLayout(this)
        content = c
        col.addView(c, LinearLayout.LayoutParams(MATCH, 0, 1f))
        col.addView(buildBar(), LinearLayout.LayoutParams(MATCH, WRAP))
        ui.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        showTab(0)
        Scheduler.scheduleAll(this)
        WidgetProvider.updateAll(this)
        fetchLocation(false)
    }

    private fun buildBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = rounded(0xE60A0A1E.toInt(), BORDER, 0)
        bar.setPadding(0, dp(6), 0, dp(6))
        barItems.clear()
        val items = arrayOf("🕌\nVakitler", "📅\nTakvim", "🧭\nKıble", "📿\nTesbih", "⚙️\nAyarlar")
        for (i in items.indices) {
            val t = tv(items[i], 11f, MUTED)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(6), 0, dp(6))
            t.setOnClickListener { showTab(i) }
            barItems.add(t)
            bar.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return bar
    }

    private fun showTab(i: Int) {
        tab = i
        stopSensors()
        cdTv = null; ring = null; sunArc = null; compass = null; qiblaStatus = null
        tCountTv = null; tTotalTv = null
        val c = content ?: return
        c.removeAllViews()
        val v = when (i) {
            0 -> buildHome()
            1 -> buildCalendar()
            2 -> buildQibla()
            3 -> buildTasbih()
            else -> buildSettings()
        }
        c.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        for (k in barItems.indices) barItems[k].setTextColor(if (k == i) ACC2 else MUTED)
        if (i == 2) startSensors()
    }

    private fun refreshAll() {
        Scheduler.scheduleAll(this)
        WidgetProvider.updateAll(this)
        up = null
        if (mainShown) showTab(tab)
    }

    // ------------------------------------------------------------------ SEKME 0: Vakitler

    private fun buildHome(): View {
        val col = column()
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val times = PrayerCalc.forDay(this, today)
        val u = PrayerCalc.upcoming(this, now)
        up = u

        col.addView(tv("${greet()}, ${Store.name(this)} ✦", 22f, TEXT, true))
        col.addView(tv(today.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", TR)), 13f, MUTED))
        col.addView(tv(hijriLine(today), 12f, GOLD))

        val chip = tv("📍 ${Store.place(this)}   ↻", 12f, TEXT)
        chip.background = rounded(GLASS, BORDER, 18)
        chip.setPadding(dp(14), dp(7), dp(14), dp(7))
        chip.setOnClickListener { fetchLocation(true) }
        val clp = LinearLayout.LayoutParams(WRAP, WRAP)
        clp.topMargin = dp(10)
        col.addView(chip, clp)

        // Sıradaki vakit kartı
        val fl = FrameLayout(this)
        fl.background = rounded(0x33301E78, 0x667C5CFF, 28)
        val rv = RingView(this)
        ring = rv
        fl.addView(rv, FrameLayout.LayoutParams(MATCH, dp(300)))
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.gravity = Gravity.CENTER
        inner.addView(tv("SIRADAKİ VAKİT", 11f, MUTED).apply { letterSpacing = 0.2f })
        inner.addView(tv(PrayerCalc.NAMES[u.first], 34f, Color.WHITE, true))
        val cd = tv(Fmt.hms((u.second - now + 999L) / 1000L), 40f, Color.WHITE)
        cd.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        cd.setShadowLayer(24f, 0f, 0f, ACC)
        cdTv = cd
        inner.addView(cd)
        inner.addView(tv("Vakit: ${Fmt.hm(u.second)}", 14f, ACC2))
        fl.addView(inner, FrameLayout.LayoutParams(MATCH, dp(300)))
        val flp = LinearLayout.LayoutParams(MATCH, WRAP)
        flp.topMargin = dp(14)
        col.addView(fl, flp)

        // Güneş/Ay yayı
        val sc = card()
        val arcV = SunArcView(this)
        val tomorrow = PrayerCalc.forDay(this, today.plusDays(1))
        val yesterday = PrayerCalc.forDay(this, today.minusDays(1))
        when {
            now in times[1]..times[4] -> {
                arcV.start = times[1]; arcV.end = times[4]; arcV.isDay = true
                arcV.title = "Gün batımına ${Fmt.dur(times[4] - now)} var"
                arcV.leftLabel = "☀ ${Fmt.hm(times[1])}"; arcV.rightLabel = "🌇 ${Fmt.hm(times[4])}"
            }
            now > times[4] -> {
                arcV.start = times[4]; arcV.end = tomorrow[1]; arcV.isDay = false
                arcV.title = "Gün doğumuna ${Fmt.dur(tomorrow[1] - now)} var"
                arcV.leftLabel = "🌇 ${Fmt.hm(times[4])}"; arcV.rightLabel = "☀ ${Fmt.hm(tomorrow[1])}"
            }
            else -> {
                arcV.start = yesterday[4]; arcV.end = times[1]; arcV.isDay = false
                arcV.title = "Gün doğumuna ${Fmt.dur(times[1] - now)} var"
                arcV.leftLabel = "🌇 ${Fmt.hm(yesterday[4])}"; arcV.rightLabel = "☀ ${Fmt.hm(times[1])}"
            }
        }
        arcV.now = now
        sunArc = arcV
        sc.addView(arcV, LinearLayout.LayoutParams(MATCH, dp(130)))
        col.addView(sc)

        // Vakit listesi
        val list = card()
        list.setPadding(dp(8), dp(8), dp(8), dp(8))
        val nextToday = u.second == times[u.first]
        for (i in 0..5) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(10), dp(11), dp(10), dp(11))
            val isNext = nextToday && i == u.first
            if (isNext) row.background = rounded(0x337C5CFF, 0x887C5CFF.toInt(), 14)
            val passed = times[i] < now
            val ic = tv(PrayerCalc.ICONS[i], 22f)
            ic.setPadding(0, 0, dp(12), 0)
            row.addView(ic)
            val nm = tv(PrayerCalc.NAMES[i], 17f, if (isNext) Color.WHITE else TEXT, isNext)
            row.addView(nm, LinearLayout.LayoutParams(0, WRAP, 1f))
            val tm = tv(Fmt.hm(times[i]), 19f, if (isNext) ACC2 else TEXT, true)
            row.addView(tm)
            val bell = tv(if (Store.prayerOn(this, i)) "🔔" else "🔕", 18f)
            bell.setPadding(dp(14), 0, dp(4), 0)
            bell.setOnClickListener {
                val nv = !Store.prayerOn(this, i)
                Store.setPrayerOn(this, i, nv)
                bell.text = if (nv) "🔔" else "🔕"
                Scheduler.scheduleAll(this)
                toast("${PrayerCalc.NAMES[i]} bildirimi ${if (nv) "açık" else "kapalı"}")
            }
            row.addView(bell)
            if (passed && !isNext) row.alpha = 0.5f
            list.addView(row)
        }
        col.addView(list)

        // Ek bilgiler
        val night = card()
        val nightLen = tomorrow[0] - times[4]
        val dayLen = times[4] - times[1]
        night.addView(tv("🌌 Gece & Gündüz", 15f, GOLD, true))
        night.addView(infoRow("Gün uzunluğu", Fmt.dur(dayLen)))
        night.addView(infoRow("Gece yarısı", Fmt.hm(times[4] + nightLen / 2)))
        night.addView(infoRow("Teheccüd (gecenin son üçte biri)", Fmt.hm(times[4] + nightLen * 2 / 3)))
        night.addView(infoRow("Kerahat (güneş doğuşu, yaklaşık)", "${Fmt.hm(times[1])} – ${Fmt.hm(times[1] + 45 * 60000L)}"))
        night.addView(infoRow("Kerahat (istiva, yaklaşık)", "${Fmt.hm(times[2] - 45 * 60000L)} – ${Fmt.hm(times[2])}"))
        night.addView(infoRow("Kerahat (gün batımı, yaklaşık)", "${Fmt.hm(times[4] - 45 * 60000L)} – ${Fmt.hm(times[4])}"))
        col.addView(night)

        val foot = tv("Vakitler konumuna göre cihazda hesaplanır (${PrayerCalc.METHODS[Store.method(this)].label}). Yerel ilanlardan 1-3 dk fark olabilir.", 11f, MUTED)
        foot.gravity = Gravity.CENTER
        col.addView(topGap(foot, 14))
        return scroll(col)
    }

    private fun infoRow(k: String, v: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.setPadding(0, dp(8), 0, 0)
        r.addView(tv(k, 13f, MUTED), LinearLayout.LayoutParams(0, WRAP, 1f))
        r.addView(tv(v, 13f, TEXT, true))
        return r
    }

    // ------------------------------------------------------------------ SEKME 1: Takvim

    private fun buildCalendar(): View {
        val col = column()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val prev = tv("‹", 30f, ACC2, true); prev.setPadding(dp(14), 0, dp(14), 0)
        val next = tv("›", 30f, ACC2, true); next.setPadding(dp(14), 0, dp(14), 0)
        val title = tv(calMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", TR)).replaceFirstChar { it.uppercase() }, 20f, TEXT, true)
        title.gravity = Gravity.CENTER
        prev.setOnClickListener { calMonth = calMonth.minusMonths(1); showTab(1) }
        next.setOnClickListener { calMonth = calMonth.plusMonths(1); showTab(1) }
        head.addView(prev)
        head.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(next)
        col.addView(head)
        col.addView(tv("📍 ${Store.place(this)}", 12f, MUTED).apply { gravity = Gravity.CENTER })

        val tbl = card()
        tbl.setPadding(dp(6), dp(10), dp(6), dp(10))
        tbl.addView(calRow(arrayOf("Gün", "İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı"), MUTED, true, false))
        val today = LocalDate.now()
        val dn = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
        for (d in 1..calMonth.lengthOfMonth()) {
            val date = calMonth.atDay(d)
            val t = PrayerCalc.forDay(this, date)
            val cells = arrayOf("$d ${dn[date.dayOfWeek.value - 1]}", Fmt.hm(t[0]), Fmt.hm(t[1]), Fmt.hm(t[2]), Fmt.hm(t[3]), Fmt.hm(t[4]), Fmt.hm(t[5]))
            val isToday = date == today
            val row = calRow(cells, if (isToday) Color.WHITE else TEXT, isToday, isToday)
            if (date.dayOfWeek.value == 5 && !isToday) row.setBackgroundColor(0x1A3DFF9A)
            tbl.addView(row)
        }
        col.addView(tbl)
        col.addView(topGap(tv("Yeşil satırlar Cuma günleridir.", 11f, MUTED).apply { gravity = Gravity.CENTER }, 10))
        return scroll(col)
    }

    private fun calRow(cells: Array<String>, color: Int, bold: Boolean, hl: Boolean): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.setPadding(dp(4), dp(7), dp(4), dp(7))
        if (hl) r.background = rounded(0x557C5CFF, 0x00000000, 10)
        for (i in cells.indices) {
            val t = tv(cells[i], 11.5f, color, bold)
            t.gravity = if (i == 0) Gravity.START else Gravity.CENTER
            t.maxLines = 1
            r.addView(t, LinearLayout.LayoutParams(0, WRAP, if (i == 0) 1.25f else 1f))
        }
        return r
    }

    // ------------------------------------------------------------------ SEKME 2: Kıble

    private fun buildQibla(): View {
        val col = column()
        val lat = Store.lat(this)
        val lon = Store.lon(this)
        val q = PrayerCalc.qibla(lat, lon)
        col.addView(tv("Kıble Pusulası", 22f, TEXT, true))
        col.addView(tv("📍 ${Store.place(this)}", 12f, MUTED))

        val cv = CompassView(this)
        cv.qibla = q.toFloat()
        compass = cv
        col.addView(topGap(cv, 10).also { it.layoutParams = LinearLayout.LayoutParams(MATCH, dp(340)).apply { topMargin = dp(10) } })

        val st = tv("Telefonu yere paralel tut", 18f, ACC2, true)
        st.gravity = Gravity.CENTER
        qiblaStatus = st
        col.addView(topGap(st, 4))

        val info = card()
        info.addView(infoRow("Kıble açısı", "${q.toInt()}° (${PrayerCalc.compassWord(q)})"))
        info.addView(infoRow("Kâbe'ye uzaklık", "${String.format(TR, "%,.0f", PrayerCalc.kaabaKm(lat, lon))} km"))
        info.addView(infoRow("Enlem / Boylam", String.format(Locale.US, "%.4f° / %.4f°", lat, lon)))
        col.addView(info)
        col.addView(topGap(tv("İpucu: Pusula doğru yönü göstermezse telefonu havada 8 çizerek kalibre et ve manyetik cisimlerden uzak dur. İbre yeşile dönünce Kâbe'ye bakıyorsun.", 12f, MUTED).apply { gravity = Gravity.CENTER }, 12))
        return scroll(col)
    }

    private fun startSensors() {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorMgr = sm
        val s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (s == null) {
            compass?.hasSensor = false
            qiblaStatus?.text = "Bu cihazda pusula sensörü yok"
            qiblaStatus?.setTextColor(MUTED)
            return
        }
        declination = GeomagneticField(Store.lat(this).toFloat(), Store.lon(this).toFloat(), 0f, System.currentTimeMillis()).declination
        sm.registerListener(sensorListener, s, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopSensors() {
        try { sensorMgr?.unregisterListener(sensorListener) } catch (e: Exception) { }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val cv = compass ?: return
            val rm = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rm, e.values)
            val o = FloatArray(3)
            SensorManager.getOrientation(rm, o)
            var az = (Math.toDegrees(o[0].toDouble()).toFloat() + declination + 360f) % 360f
            var d = az - smoothHeading
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            smoothHeading = (smoothHeading + d * 0.18f + 360f) % 360f
            cv.heading = smoothHeading
            val diff = abs(cv.diff())
            val st = qiblaStatus ?: return
            if (diff < 4f) {
                st.text = "✓ Kıbledesin"
                st.setTextColor(GREEN)
            } else {
                st.text = "Kıbleye ${diff.toInt()}° kaldı"
                st.setTextColor(ACC2)
            }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    // ------------------------------------------------------------------ SEKME 3: Tesbih

    private fun buildTasbih(): View {
        val col = column()
        col.gravity = Gravity.CENTER_HORIZONTAL
        val idx = Store.tIdx(this).coerceIn(0, DHIKR.size - 1)
        val dh = DHIKR[idx]
        val target = Store.tTarget(this)

        col.addView(tv("Dijital Tesbih", 22f, TEXT, true))
        val pick = tv("${dh.first}  ▾", 15f, ACC2, true)
        pick.background = rounded(GLASS, BORDER, 18)
        pick.setPadding(dp(16), dp(8), dp(16), dp(8))
        pick.setOnClickListener {
            val names = Array(DHIKR.size) { DHIKR[it].first }
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Zikir seç")
                .setItems(names) { _, w ->
                    Store.setTIdx(this, w); Store.setTTarget(this, DHIKR[w].third); Store.setTCount(this, 0); showTab(3)
                }.show()
        }
        col.addView(topGap(pick, 10).also { it.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(10) } })

        val ar = tv(dh.second, 28f, GOLD)
        ar.gravity = Gravity.CENTER
        col.addView(topGap(ar, 16))

        val btn = TextView(this)
        btn.gravity = Gravity.CENTER
        btn.textSize = 64f
        btn.setTextColor(Color.WHITE)
        btn.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7C5CFF.toInt(), 0xFF2A1A8A.toInt(), 0xFF0A1F5D.toInt()))
        g.shape = GradientDrawable.OVAL
        g.setStroke(dp(3), 0x8800D4FF.toInt())
        btn.background = g
        btn.text = "${Store.tCount(this)}"
        tCountTv = btn
        val blp = LinearLayout.LayoutParams(dp(230), dp(230))
        blp.topMargin = dp(18)
        col.addView(btn, blp)

        val total = tv("Toplam: ${Store.tTotal(this)}   ·   Hedef: $target", 14f, MUTED)
        tTotalTv = total
        col.addView(topGap(total, 18).also { it.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(18) } })

        btn.setOnClickListener {
            val n = Store.tCount(this) + 1
            Store.setTCount(this, n)
            Store.setTTotal(this, Store.tTotal(this) + 1L)
            btn.text = "$n"
            tTotalTv?.text = "Toplam: ${Store.tTotal(this)}   ·   Hedef: ${Store.tTarget(this)}"
            btn.animate().scaleX(0.94f).scaleY(0.94f).setDuration(50).withEndAction {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }.start()
            val done = n % Store.tTarget(this) == 0
            btn.performHapticFeedback(if (done) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
            if (done) toast("Maşallah, $n tamamlandı ✦")
        }

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        for (tg in intArrayOf(33, 99, 100, 500)) {
            val chip = tv("$tg", 14f, if (tg == target) Color.WHITE else MUTED, true)
            chip.background = rounded(if (tg == target) ACC else GLASS, BORDER, 16)
            chip.setPadding(dp(16), dp(8), dp(16), dp(8))
            chip.setOnClickListener { Store.setTTarget(this, tg); showTab(3) }
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.setMargins(dp(5), 0, dp(5), 0)
            row.addView(chip, lp)
        }
        col.addView(topGap(row, 18).also { it.layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(18) } })

        val reset = tv("Sıfırla", 14f, 0xFFFF5C7A.toInt(), true)
        reset.setPadding(dp(20), dp(14), dp(20), dp(14))
        reset.setOnClickListener { Store.setTCount(this, 0); showTab(3) }
        col.addView(reset)
        return scroll(col)
    }

    // ------------------------------------------------------------------ SEKME 4: Ayarlar

    private fun buildSettings(): View {
        val col = column()
        col.addView(tv("Ayarlar", 22f, TEXT, true))

        val prof = card()
        prof.addView(tv("👤 Profil & Konum", 15f, GOLD, true))
        prof.addView(settingRow("İsim", Store.name(this)) { editName() })
        prof.addView(settingRow("Konum", Store.place(this) + "  (yenile)") { fetchLocation(true) })
        col.addView(prof)

        val calc = card()
        calc.addView(tv("🧮 Hesaplama", 15f, GOLD, true))
        calc.addView(settingRow("Yöntem", PrayerCalc.METHODS[Store.method(this)].label) {
            val labels = Array(PrayerCalc.METHODS.size) { PrayerCalc.METHODS[it].label }
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hesaplama yöntemi")
                .setSingleChoiceItems(labels, Store.method(this)) { d, w -> Store.setMethod(this, w); d.dismiss(); refreshAll() }
                .show()
        })
        calc.addView(settingRow("İkindi (mezhep)", if (Store.madhab(this) == 2) "Hanefi" else "Şafii / Maliki / Hanbeli") {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("İkindi hesabı")
                .setSingleChoiceItems(arrayOf("Şafii / Maliki / Hanbeli", "Hanefi"), if (Store.madhab(this) == 2) 1 else 0) { d, w ->
                    Store.setMadhab(this, if (w == 1) 2 else 1); d.dismiss(); refreshAll() }
                .show()
        })
        col.addView(calc)

        val nt = card()
        nt.addView(tv("🔔 Bildirimler", 15f, GOLD, true))
        nt.addView(switchRow("Vakit bildirimleri", Store.notifOn(this)) { Store.setNotifOn(this, it); Scheduler.scheduleAll(this) })
        nt.addView(switchRow("Bildirim sesi", Store.sound(this)) { Store.setSound(this, it) })
        nt.addView(settingRow("Önceden hatırlat", if (Store.pre(this) == 0) "Kapalı" else "${Store.pre(this)} dk önce") {
            val opts = intArrayOf(0, 10, 15, 30, 45)
            val labels = Array(opts.size) { if (opts[it] == 0) "Kapalı" else "${opts[it]} dakika önce" }
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Önceden hatırlatma")
                .setSingleChoiceItems(labels, opts.indexOf(Store.pre(this)).coerceAtLeast(0)) { d, w ->
                    Store.setPre(this, opts[w]); d.dismiss(); refreshAll() }
                .show()
        })
        val exact = Scheduler.canExact(this)
        nt.addView(settingRow("Tam zamanlı alarm izni", if (exact) "Verildi ✓" else "Gerekli — dokun") { if (!exact) askExactAlarm() })
        nt.addView(settingRow("Test bildirimi gönder", "Dokun") {
            Notifier.show(this, "Astra Vakit ✦", "Bildirimler çalışıyor, ${Store.name(this)}!")
        })
        col.addView(nt)

        val wd = card()
        wd.addView(tv("🧩 Widget", 15f, GOLD, true))
        wd.addView(settingRow("Ana ekrana widget ekle", "Dokun") { pinWidget() })
        col.addView(wd)

        val ab = card()
        ab.addView(tv("ℹ️ Hakkında", 15f, GOLD, true))
        ab.addView(topGap(tv("Astra Vakit 1.0\nVakitler bulunduğun konuma göre cihazında hesaplanır; internet gerekmez. Bildirimlerin zamanında gelmesi için telefonun pil ayarlarında bu uygulamayı \"Kısıtlama yok\" yap.", 12f, MUTED), 8))
        col.addView(ab)
        return scroll(col)
    }

    private fun settingRow(title: String, value: String, onClick: () -> Unit): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(12), 0, dp(4))
        r.addView(tv(title, 15f, TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        val v = tv(value, 13f, ACC2)
        v.gravity = Gravity.END
        r.addView(v, LinearLayout.LayoutParams(0, WRAP, 1.1f))
        r.setOnClickListener { onClick() }
        return r
    }

    private fun switchRow(title: String, on: Boolean, change: (Boolean) -> Unit): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(10), 0, dp(2))
        r.addView(tv(title, 15f, TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        val s = Switch(this)
        s.isChecked = on
        s.setOnCheckedChangeListener { _, b -> change(b) }
        r.addView(s)
        return r
    }

    private fun editName() {
        val et = EditText(this)
        et.setText(Store.name(this))
        et.setTextColor(TEXT)
        et.setSingleLine(true)
        et.setPadding(dp(24), dp(16), dp(24), dp(16))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("İsmin")
            .setView(et)
            .setPositiveButton("Kaydet") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) { Store.setName(this, n); refreshAll() }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun pinWidget() {
        try {
            val awm = AppWidgetManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= 26 && awm.isRequestPinAppWidgetSupported) {
                awm.requestPinAppWidget(ComponentName(this, WidgetProvider::class.java), null, null)
            } else {
                toast("Ana ekrana uzun bas → Widget'lar → Astra Vakit")
            }
        } catch (e: Exception) {
            toast("Ana ekrana uzun bas → Widget'lar → Astra Vakit")
        }
    }

    // ------------------------------------------------------------------ KONUM

    private fun hasLocPerm(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun fetchLocation(verbose: Boolean) {
        if (!hasLocPerm()) {
            if (verbose) {
                toast("Konum izni gerekli")
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 8)
            }
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
            if (l != null && (best == null || l.time > best!!.time)) best = l
        }
        val b = best
        if (b != null && !Store.hasLoc(this)) applyLocation(b, false)
        val provs = ArrayList<String>()
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provs.add(LocationManager.NETWORK_PROVIDER)
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provs.add(LocationManager.GPS_PROVIDER)
        if (provs.isEmpty()) {
            if (verbose) toast("Telefonun konum özelliği kapalı")
            return
        }
        if (verbose) toast("Konum aranıyor…")
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) {
                try { lm.removeUpdates(this) } catch (e: Exception) { }
                applyLocation(l, verbose)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            for (p in provs) lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
            handler.postDelayed({ try { lm.removeUpdates(listener) } catch (e: Exception) { } }, 30000L)
        } catch (e: Exception) { }
    }

    private fun applyLocation(l: Location, verbose: Boolean) {
        val lat = l.latitude
        val lon = l.longitude
        if (Store.hasLoc(this)) {
            val res = FloatArray(1)
            Location.distanceBetween(Store.lat(this), Store.lon(this), lat, lon, res)
            if (res[0] < 2000f) {
                if (verbose) toast("Konum güncel: ${Store.place(this)}")
                return
            }
        }
        val fallback = String.format(Locale.US, "%.2f°, %.2f°", lat, lon)
        Store.setLoc(this, lat, lon, fallback)
        refreshIfMain()
        Thread {
            var name = fallback
            try {
                if (Geocoder.isPresent()) {
                    @Suppress("DEPRECATION")
                    val res = Geocoder(this, TR).getFromLocation(lat, lon, 1)
                    val a = res?.firstOrNull()
                    if (a != null) {
                        val parts = listOfNotNull(a.subAdminArea ?: a.locality, a.adminArea).distinct()
                        if (parts.isNotEmpty()) name = parts.joinToString(", ")
                    }
                }
            } catch (e: Exception) { }
            runOnUiThread {
                Store.setPlace(this, name)
                refreshIfMain()
                if (verbose) toast("Konum güncellendi: $name")
            }
        }.start()
    }

    private fun refreshIfMain() {
        if (!mainShown) return
        Scheduler.scheduleAll(this)
        WidgetProvider.updateAll(this)
        if (tab != 2 && tab != 3) { up = null; showTab(tab) }
    }
}
