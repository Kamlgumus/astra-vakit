package com.astravakit.app

import android.content.Context
import android.content.SharedPreferences

/** Tüm ayarlar telefonda saklanır; isim bir kez sorulur ve unutulmaz. */
object Store {
    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("astra", Context.MODE_PRIVATE)

    fun name(c: Context): String = p(c).getString("name", "") ?: ""
    fun setName(c: Context, v: String) = p(c).edit().putString("name", v.trim()).apply()

    fun onboarded(c: Context) = p(c).getBoolean("onboarded", false)
    fun setOnboarded(c: Context, v: Boolean) = p(c).edit().putBoolean("onboarded", v).apply()

    // Varsayılan konum İstanbul; GPS bulunca güncellenir
    fun lat(c: Context): Double = (p(c).getString("lat", "41.0082") ?: "41.0082").toDoubleOrNull() ?: 41.0082
    fun lon(c: Context): Double = (p(c).getString("lon", "28.9784") ?: "28.9784").toDoubleOrNull() ?: 28.9784
    fun place(c: Context): String = p(c).getString("place", "İstanbul (varsayılan)") ?: "İstanbul"
    fun hasLoc(c: Context) = p(c).getBoolean("hasLoc", false)
    fun setLoc(c: Context, lat: Double, lon: Double, place: String) =
        p(c).edit().putString("lat", lat.toString()).putString("lon", lon.toString())
            .putString("place", place).putBoolean("hasLoc", true).apply()
    fun setPlace(c: Context, place: String) = p(c).edit().putString("place", place).apply()

    fun method(c: Context) = p(c).getInt("method", 0)
    fun setMethod(c: Context, v: Int) = p(c).edit().putInt("method", v).apply()

    /** 1 = Şafii/Maliki/Hanbeli, 2 = Hanefi */
    fun madhab(c: Context) = p(c).getInt("madhab", 1)
    fun setMadhab(c: Context, v: Int) = p(c).edit().putInt("madhab", v).apply()

    fun notifOn(c: Context) = p(c).getBoolean("notifOn", true)
    fun setNotifOn(c: Context, v: Boolean) = p(c).edit().putBoolean("notifOn", v).apply()

    fun sound(c: Context) = p(c).getBoolean("sound", true)
    fun setSound(c: Context, v: Boolean) = p(c).edit().putBoolean("sound", v).apply()

    /** Vakitten kaç dakika önce hatırlat (0 = kapalı) */
    fun pre(c: Context) = p(c).getInt("pre", 0)
    fun setPre(c: Context, v: Int) = p(c).edit().putInt("pre", v).apply()

    fun prayerOn(c: Context, i: Int) = p(c).getBoolean("pn$i", i != 1)
    fun setPrayerOn(c: Context, i: Int, v: Boolean) = p(c).edit().putBoolean("pn$i", v).apply()

    // Tesbih
    fun tCount(c: Context) = p(c).getInt("tCount", 0)
    fun setTCount(c: Context, v: Int) = p(c).edit().putInt("tCount", v).apply()
    fun tTotal(c: Context) = p(c).getLong("tTotal", 0L)
    fun setTTotal(c: Context, v: Long) = p(c).edit().putLong("tTotal", v).apply()
    fun tTarget(c: Context) = p(c).getInt("tTarget", 33)
    fun setTTarget(c: Context, v: Int) = p(c).edit().putInt("tTarget", v).apply()
    fun tIdx(c: Context) = p(c).getInt("tIdx", 0)
    fun setTIdx(c: Context, v: Int) = p(c).edit().putInt("tIdx", v).apply()
}
