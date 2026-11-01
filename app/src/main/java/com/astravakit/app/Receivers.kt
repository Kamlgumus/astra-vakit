package com.astravakit.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val title = i.getStringExtra("t") ?: ""
        val text = i.getStringExtra("x") ?: ""
        val notify = i.getBooleanExtra("n", false)
        if (notify && title.isNotEmpty()) Notifier.show(c, title, text)
        Scheduler.scheduleAll(c)
        WidgetProvider.updateAll(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.scheduleAll(c)
        WidgetProvider.updateAll(c)
    }
}
