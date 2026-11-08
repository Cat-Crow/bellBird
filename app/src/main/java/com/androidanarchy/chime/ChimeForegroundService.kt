// Copyright (C) 2026 Regina Nockers
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://gnu.org>.

package com.androidanarchy.chime

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ChimeForegroundService : Service() {

    private lateinit var prefs: PreferencesManager
    private lateinit var alarmManager: AlarmManager

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        val playPrimary = intent?.getBooleanExtra(EXTRA_PLAY_PRIMARY, false) ?: false
        val playSecondary = intent?.getBooleanExtra(EXTRA_PLAY_SECONDARY, false) ?: false
        val reschedulePrimary = intent?.getBooleanExtra(EXTRA_RESCHEDULE_PRIMARY, false) ?: false
        val rescheduleSecondary = intent?.getBooleanExtra(EXTRA_RESCHEDULE_SECONDARY, false) ?: false
        // If no specific reschedule flag is set (manual enable or START_STICKY restart), schedule both
        val scheduleAll = !reschedulePrimary && !rescheduleSecondary

        val quiet = prefs.isQuietNow()
        val overrideDnd = prefs.overrideDnd
        val blockedByDnd = !overrideDnd && isDndActive()

        if (playPrimary && !quiet && !blockedByDnd) {
            Thread {
                ChimePlayer.play(this, ToneDefinition.entries[prefs.primaryToneIndex], prefs.primaryVolumePercent)
            }.start()
            if (prefs.vibrateEnabled) vibrate(overrideDnd)
        }

        if (playSecondary) {
            // Skip secondary if its fire time coincides with a primary fire time
            val cal = Calendar.getInstance()
            val totalMinutesNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val primaryInterval = prefs.primaryIntervalMinutes
            val isCoincidence = primaryInterval > 0 && totalMinutesNow % primaryInterval == 0
            if (!isCoincidence && !quiet && !blockedByDnd) {
                Thread {
                    ChimePlayer.play(this, ToneDefinition.entries[prefs.secondaryToneIndex], prefs.secondaryVolumePercent)
                }.start()
                if (prefs.vibrateEnabled) vibrate(overrideDnd)
            }
        }

        if (scheduleAll || reschedulePrimary) scheduleAlarm(isPrimary = true)
        if (scheduleAll || rescheduleSecondary) scheduleAlarm(isPrimary = false)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAlarm(isPrimary = true)
        cancelAlarm(isPrimary = false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isDndActive(): Boolean {
        return try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.currentInterruptionFilter != INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            false
        }
    }

    private fun vibrate(overrideDnd: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1)
        val attrs = AudioAttributes.Builder()
            .setUsage(if (overrideDnd) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()
        vibrator.vibrate(effect, attrs)
    }

    private fun scheduleAlarm(isPrimary: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()) return

        val interval = if (isPrimary) prefs.primaryIntervalMinutes else prefs.secondaryIntervalMinutes
        if (interval <= 0) return
        val nextChimeTime: Long

        if (prefs.startImmediately && prefs.startAnchorMillis > 0) {
            val intervalMillis = interval * 60_000L
            val nowMillis = System.currentTimeMillis()
            val remainder = (nowMillis - prefs.startAnchorMillis) % intervalMillis
            nextChimeTime = nowMillis + (if (remainder == 0L) intervalMillis else intervalMillis - remainder)
        } else {
            val cal = Calendar.getInstance()
            val totalMinutesNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val minutesUntilNext = interval - (totalMinutesNow % interval)
            nextChimeTime = Calendar.getInstance().apply {
                add(Calendar.MINUTE, minutesUntilNext)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextChimeTime,
            getAlarmPendingIntent(isPrimary)
        )
    }

    private fun cancelAlarm(isPrimary: Boolean) =
        alarmManager.cancel(getAlarmPendingIntent(isPrimary))

    private fun getAlarmPendingIntent(isPrimary: Boolean): PendingIntent {
        val requestCode = if (isPrimary) PRIMARY_REQUEST_CODE else SECONDARY_REQUEST_CODE
        val intent = Intent(this, ChimeAlarmReceiver::class.java).apply {
            putExtra(ChimeAlarmReceiver.EXTRA_IS_PRIMARY, isPrimary)
        }
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(
            if (prefs.isQuietNow()) getString(R.string.notification_text_quiet)
            else getString(R.string.notification_text, prefs.primaryIntervalMinutes, prefs.secondaryIntervalMinutes)
        )
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)
        )
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Bell Anarchy", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while chimes are active"
            setSound(null, null)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "chime_service"
        const val NOTIFICATION_ID = 1
        const val PRIMARY_REQUEST_CODE = 100
        const val SECONDARY_REQUEST_CODE = 101
        const val EXTRA_PLAY_PRIMARY = "play_primary"
        const val EXTRA_PLAY_SECONDARY = "play_secondary"
        const val EXTRA_RESCHEDULE_PRIMARY = "reschedule_primary"
        const val EXTRA_RESCHEDULE_SECONDARY = "reschedule_secondary"

        fun start(
            context: Context,
            playPrimary: Boolean = false,
            playSecondary: Boolean = false,
            reschedulePrimary: Boolean = false,
            rescheduleSecondary: Boolean = false
        ) {
            val intent = Intent(context, ChimeForegroundService::class.java).apply {
                putExtra(EXTRA_PLAY_PRIMARY, playPrimary)
                putExtra(EXTRA_PLAY_SECONDARY, playSecondary)
                putExtra(EXTRA_RESCHEDULE_PRIMARY, reschedulePrimary)
                putExtra(EXTRA_RESCHEDULE_SECONDARY, rescheduleSecondary)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, ChimeForegroundService::class.java))
    }
}
