package com.astravakit.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate

object Scheduler {
    private const val MAX = 16
    const val CH_SOUND = "vakit_ses"
    const val CH_QUIET = "vakit_sessiz"

    class Ev(val time: Long, val title: String, val text: String, val notify: Boolean)

    fun canExact(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun events(c: Context, now: Long): List<Ev> {
        val out = ArrayList<Ev>()
        val today = LocalDate.now()
        val name = Store.name(c)
        val place = Store.place(c)
        val pre = Store.pre(c)
        val master = Store.notifOn(c)
        for (d in 0..2) {
            val ts = PrayerCalc.forDay(c, today.plusDays(d.toLong()))
            for (i in 0..5) {
                val on = master && Store.prayerOn(c, i)
                val who = if (name.isBlank()) "" else "$name, "
                if (pre > 0) {
                    val tt = ts[i] - pre * 60000L
                    if (tt > now) {
                        out.add(Ev(tt, "${PrayerCalc.NAMES[i]} vaktine $pre dk kaldı",
                            "${who}hazırlanma vakti ✦ ${Fmt.hm(ts[i])} · $place", on))
                    }
                }
                if (ts[i] > now) {
                    out.add(Ev(ts[i], PrayerCalc.TITLES[i],
                        "${who}${PrayerCalc.NAMES[i]} · ${Fmt.hm(ts[i])} · $place", on))
                }
            }
        }
        out.sortBy { it.time }
        return out
    }

    private fun pi(c: Context, slot: Int, ev: Ev?): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
        if (ev != null) {
            i.putExtra("t", ev.title)
            i.putExtra("x", ev.text)
            i.putExtra("n", ev.notify)
            i.putExtra("w", ev.time)
        }
        return PendingIntent.getBroadcast(c, 1000 + slot, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleAll(c: Context) {
        val ctx = c.applicationContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val evs = events(ctx, System.currentTimeMillis()).take(MAX)
        val exact = canExact(ctx)
        for (slot in 0 until MAX) {
            if (slot < evs.size) {
                val ev = evs[slot]
                val p = pi(ctx, slot, ev)
                try {
                    if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.time, p)
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.time, p)
                } catch (e: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.time, p)
                }
            } else {
                am.cancel(pi(ctx, slot, null))
            }
        }
    }
}

object Notifier {
    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(Scheduler.CH_SOUND) == null) {
            val ch = NotificationChannel(Scheduler.CH_SOUND, "Vakit bildirimleri (sesli)", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Namaz vakti girdiğinde sesli bildirim"
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 300, 150, 300)
            nm.createNotificationChannel(ch)
        }
        if (nm.getNotificationChannel(Scheduler.CH_QUIET) == null) {
            val ch = NotificationChannel(Scheduler.CH_QUIET, "Vakit bildirimleri (sessiz)", NotificationManager.IMPORTANCE_DEFAULT)
            ch.setSound(null, null)
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }
    }

    fun show(c: Context, title: String, text: String) {
        try {
            ensureChannels(c)
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val channel = if (Store.sound(c)) Scheduler.CH_SOUND else Scheduler.CH_QUIET
            val n = android.app.Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(text))
                .setColor(0xFF7C5CFF.toInt())
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() / 1000L % 100000L).toInt(), n)
        } catch (e: Exception) {
            // bildirim izni yoksa sessizce geç
        }
    }
}
