package com.carbon.prolocker.core.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class ForegroundAppDetector(private val context: Context) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // هر بار که LockService نمایش داده می‌شود این timestamp آپدیت می‌شود.
    // رویدادهایی که قبل از این زمان رخ داده‌اند نادیده گرفته می‌شوند تا
    // تلگرام (یا هر اپ قفل‌شده) بعد از نمایش LockService دوباره تریگر نشود.
    @Volatile
    var lockServiceLaunchedAt: Long = 0L

    fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 1000 * 10, time)
        var foregroundApp: String? = null
        var lastEventTime: Long = 0
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            if (event.timeStamp <= lockServiceLaunchedAt) continue

            if (event.packageName == context.packageName) continue

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                if (event.timeStamp > lastEventTime) {
                    foregroundApp = event.packageName
                    lastEventTime = event.timeStamp
                }
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            ) {
                if (event.packageName == foregroundApp && event.timeStamp > lastEventTime) {
                    foregroundApp = null
                    lastEventTime = event.timeStamp
                }
            }
        }
        return foregroundApp
    }
}