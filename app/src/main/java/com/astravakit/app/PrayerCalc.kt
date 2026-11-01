package com.astravakit.app

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Astronomik namaz vakti hesaplayıcı. İnternet gerektirmez. */
object PrayerCalc {

    val NAMES = arrayOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")
    val ICONS = arrayOf("🌠", "🌅", "☀️", "🌤️", "🌇", "🌙")
    val TITLES = arrayOf(
        "İmsak vakti girdi",
        "Güneş doğdu",
        "Öğle ezanı vakti",
        "İkindi ezanı vakti",
        "Akşam ezanı vakti",
        "Yatsı ezanı vakti"
    )

    /** adj: dakika düzeltmeleri (imsak, güneş, öğle, ikindi, akşam, yatsı) */
    class Method(val label: String, val fajr: Double, val isha: Double, val ishaMin: Int, val adj: IntArray)

    val METHODS = listOf(
        Method("Türkiye · Diyanet", 18.0, 17.0, 0, intArrayOf(0, -7, 5, 4, 7, 0)),
        Method("Muslim World League", 18.0, 17.0, 0, intArrayOf(0, 0, 0, 0, 0, 0)),
        Method("Umm al-Qura (Mekke)", 18.5, 0.0, 90, intArrayOf(0, 0, 0, 0, 0, 0)),
        Method("Mısır Genel Otoritesi", 19.5, 17.5, 0, intArrayOf(0, 0, 0, 0, 0, 0)),
        Method("Karaçi", 18.0, 18.0, 0, intArrayOf(0, 0, 0, 0, 0, 0)),
        Method("ISNA (Kuzey Amerika)", 15.0, 15.0, 0, intArrayOf(0, 0, 0, 0, 0, 0))
    )

    private const val D2R = Math.PI / 180.0
    private fun sinD(x: Double) = sin(x * D2R)
    private fun cosD(x: Double) = cos(x * D2R)
    private fun tanD(x: Double) = tan(x * D2R)
    private fun asinD(x: Double) = asin(x) / D2R
    private fun acosD(x: Double) = acos(x) / D2R
    private fun atan2D(y: Double, x: Double) = atan2(y, x) / D2R
    private fun fixAngle(a: Double): Double { val r = a % 360.0; return if (r < 0) r + 360.0 else r }
    private fun fixHour(a: Double): Double { val r = a % 24.0; return if (r < 0) r + 24.0 else r }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunDecl(jd: Double): Double {
        val d = jd - 2451545.0
        val q = fixAngle(280.459 + 0.98564736 * d)
        val g = fixAngle(357.529 + 0.98560028 * d)
        val l = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g))
        val e = 23.439 - 0.00000036 * d
        return asinD(sinD(e) * sinD(l))
    }

    private fun sunEqt(jd: Double): Double {
        val d = jd - 2451545.0
        val q = fixAngle(280.459 + 0.98564736 * d)
        val g = fixAngle(357.529 + 0.98560028 * d)
        val l = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(atan2D(cosD(e) * sinD(l), cosD(l)) / 15.0)
        return q / 15.0 - ra
    }

    private fun midDay(jd: Double, t: Double) = fixHour(12.0 - sunEqt(jd + t))

    private fun angleTime(jd: Double, lat: Double, angle: Double, t: Double, ccw: Boolean): Double {
        val decl = sunDecl(jd + t)
        val noon = midDay(jd, t)
        val x = (-sinD(angle) - sinD(decl) * sinD(lat)) / (cosD(decl) * cosD(lat))
        val h = acosD(x) / 15.0
        return if (ccw) noon - h else noon + h
    }

    private fun asrTime(jd: Double, lat: Double, factor: Double, t: Double): Double {
        val decl = sunDecl(jd + t)
        val angle = -(atan(1.0 / (factor + tanD(abs(lat - decl)))) / D2R)
        return angleTime(jd, lat, angle, t, false)
    }

    /** Bir gün için 6 vakti (epoch ms) döndürür: imsak, güneş, öğle, ikindi, akşam, yatsı */
    fun compute(date: LocalDate, lat: Double, lon: Double, m: Method, asrFactor: Int,
                zone: ZoneId = ZoneId.systemDefault()): LongArray {
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lon / 360.0
        var t = doubleArrayOf(5.0 / 24, 6.0 / 24, 12.0 / 24, 13.0 / 24, 18.0 / 24, 18.0 / 24)
        var r = DoubleArray(6)
        for (iter in 0 until 2) {
            val fajr = angleTime(jd, lat, m.fajr, t[0], true)
            val sunrise = angleTime(jd, lat, 0.833, t[1], true)
            val dhuhr = midDay(jd, t[2])
            val asr = asrTime(jd, lat, asrFactor.toDouble(), t[3])
            val maghrib = angleTime(jd, lat, 0.833, t[4], false)
            val isha = if (m.ishaMin > 0) maghrib + m.ishaMin / 60.0
                       else angleTime(jd, lat, m.isha, t[5], false)
            r = doubleArrayOf(fajr, sunrise, dhuhr, asr, maghrib, isha)
            t = DoubleArray(6) { r[it] / 24.0 }
        }
        // Kutup bölgelerinde NaN olursa 48° enleme sabitle
        if (r.any { it.isNaN() } && abs(lat) > 48.0) {
            return compute(date, if (lat > 0) 48.0 else -48.0, lon, m, asrFactor, zone)
        }
        val utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return LongArray(6) {
            val local = r[it] + tz - lon / 15.0 + m.adj[it] / 60.0
            utcMidnight + ((local - tz) * 3600000.0).toLong()
        }
    }

    fun forDay(c: Context, date: LocalDate): LongArray =
        compute(date, Store.lat(c), Store.lon(c), METHODS[Store.method(c).coerceIn(0, METHODS.size - 1)], Store.madhab(c))

    /** (sıradaki vakit indexi, zamanı, bir önceki vaktin zamanı) */
    fun upcoming(c: Context, now: Long): Triple<Int, Long, Long> {
        val today = LocalDate.now()
        val all = ArrayList<Long>(18)
        for (d in -1..1) for (v in forDay(c, today.plusDays(d.toLong()))) all.add(v)
        for (i in all.indices) {
            if (all[i] > now) {
                val prev = if (i > 0) all[i - 1] else all[i] - 6 * 3600000L
                return Triple(i % 6, all[i], prev)
            }
        }
        return Triple(0, now + 3600000L, now)
    }

    // ---- Kıble ----
    private const val KAABA_LAT = 21.4225
    private const val KAABA_LON = 39.8262

    fun qibla(lat: Double, lon: Double): Double {
        val dl = (KAABA_LON - lon) * D2R
        val p1 = lat * D2R
        val p2 = KAABA_LAT * D2R
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (atan2(y, x) / D2R + 360.0) % 360.0
    }

    fun kaabaKm(lat: Double, lon: Double): Double {
        val r = 6371.0
        val dLat = (KAABA_LAT - lat) * D2R
        val dLon = (KAABA_LON - lon) * D2R
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat * D2R) * cos(KAABA_LAT * D2R) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun compassWord(deg: Double): String {
        val w = arrayOf("K", "KKD", "KD", "DKD", "D", "DGD", "GD", "GGD", "G", "GGB", "GB", "BGB", "B", "BKB", "KB", "KKB")
        return w[(((deg + 11.25) % 360) / 22.5).toInt() % 16]
    }
}

object Fmt {
    fun hm(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun hms(totalSec: Long): String {
        val s = if (totalSec < 0) 0 else totalSec
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun dur(ms: Long): String {
        val m = ms / 60000
        return if (m >= 60) "${m / 60} sa ${m % 60} dk" else "$m dk"
    }
}
