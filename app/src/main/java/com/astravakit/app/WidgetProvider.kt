package com.astravakit.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.widget.RemoteViews
import java.time.LocalDate

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        push(context, manager, ids)
    }

    override fun onEnabled(context: Context) {
        Scheduler.scheduleAll(context)
    }

    companion object {
        private val NAME_IDS = intArrayOf(R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4, R.id.w_n5)
        private val TIME_IDS = intArrayOf(R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4, R.id.w_t5)

        fun updateAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, WidgetProvider::class.java))
            if (ids.isNotEmpty()) push(c, m, ids)
        }

        private fun push(c: Context, m: AppWidgetManager, ids: IntArray) {
            val now = System.currentTimeMillis()
            val up = PrayerCalc.upcoming(c, now)
            val today = PrayerCalc.forDay(c, LocalDate.now())
            val rv = RemoteViews(c.packageName, R.layout.widget)

            rv.setTextViewText(R.id.w_next, PrayerCalc.NAMES[up.first])
            rv.setTextViewText(R.id.w_time, Fmt.hm(up.second))
            rv.setTextViewText(R.id.w_place, "📍 " + Store.place(c))
            rv.setChronometerCountDown(R.id.w_chrono, true)
            rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() + (up.second - now), null, true)

            val nextIsToday = up.second in today[0]..(today[5] + 60000L) && up.second == today[up.first]
            for (i in 0..5) {
                rv.setTextViewText(TIME_IDS[i], Fmt.hm(today[i]))
                val hl = nextIsToday && i == up.first
                rv.setTextColor(TIME_IDS[i], if (hl) Color.parseColor("#00D4FF") else if (today[i] < now) Color.parseColor("#7A7A99") else Color.WHITE)
                rv.setTextColor(NAME_IDS[i], if (hl) Color.parseColor("#00D4FF") else Color.parseColor("#9A9AB8"))
            }

            val open = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.w_root, open)
            m.updateAppWidget(ids, rv)
        }
    }
}
