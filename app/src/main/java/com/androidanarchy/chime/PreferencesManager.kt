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

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var primaryIntervalMinutes: Int
        get() = prefs.getInt(KEY_PRIMARY_INTERVAL, 60)
        set(value) = prefs.edit().putInt(KEY_PRIMARY_INTERVAL, value).apply()

    var primaryIntervalUseHours: Boolean
        get() = prefs.getBoolean(KEY_PRIMARY_INTERVAL_USE_HOURS, true)
        set(value) = prefs.edit().putBoolean(KEY_PRIMARY_INTERVAL_USE_HOURS, value).apply()

    var primaryToneIndex: Int
        get() = prefs.getInt(KEY_PRIMARY_TONE, 0)
        set(value) = prefs.edit().putInt(KEY_PRIMARY_TONE, value).apply()

    var primaryVolumePercent: Int
        get() = prefs.getInt(KEY_PRIMARY_VOLUME, 100)
        set(value) = prefs.edit().putInt(KEY_PRIMARY_VOLUME, value).apply()

    var secondaryIntervalMinutes: Int
        get() = prefs.getInt(KEY_SECONDARY_INTERVAL, 30)
        set(value) = prefs.edit().putInt(KEY_SECONDARY_INTERVAL, value).apply()

    var secondaryIntervalUseHours: Boolean
        get() = prefs.getBoolean(KEY_SECONDARY_INTERVAL_USE_HOURS, false)
        set(value) = prefs.edit().putBoolean(KEY_SECONDARY_INTERVAL_USE_HOURS, value).apply()

    var secondaryToneIndex: Int
        get() = prefs.getInt(KEY_SECONDARY_TONE, 1)
        set(value) = prefs.edit().putInt(KEY_SECONDARY_TONE, value).apply()

    var secondaryVolumePercent: Int
        get() = prefs.getInt(KEY_SECONDARY_VOLUME, 100)
        set(value) = prefs.edit().putInt(KEY_SECONDARY_VOLUME, value).apply()

    var startImmediately: Boolean
        get() = prefs.getBoolean(KEY_START_IMMEDIATELY, false)
        set(value) = prefs.edit().putBoolean(KEY_START_IMMEDIATELY, value).apply()

    var startAnchorMillis: Long
        get() = prefs.getLong(KEY_START_ANCHOR_MILLIS, -1L)
        set(value) = prefs.edit().putLong(KEY_START_ANCHOR_MILLIS, value).apply()

    var isIndigoBuntingMode: Boolean
        get() = prefs.getBoolean(KEY_INDIGO_BUNTING, false)
        set(value) = prefs.edit().putBoolean(KEY_INDIGO_BUNTING, value).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, false)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    var overrideDnd: Boolean
        get() = prefs.getBoolean(KEY_OVERRIDE_DND, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERRIDE_DND, value).apply()

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, value).apply()

    var quietStartHour: Int
        get() = prefs.getInt(KEY_QUIET_START_HOUR, 22)
        set(value) = prefs.edit().putInt(KEY_QUIET_START_HOUR, value).apply()

    var quietStartMinute: Int
        get() = prefs.getInt(KEY_QUIET_START_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_QUIET_START_MINUTE, value).apply()

    var quietEndHour: Int
        get() = prefs.getInt(KEY_QUIET_END_HOUR, 8)
        set(value) = prefs.edit().putInt(KEY_QUIET_END_HOUR, value).apply()

    var quietEndMinute: Int
        get() = prefs.getInt(KEY_QUIET_END_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_QUIET_END_MINUTE, value).apply()

    fun isQuietNow(): Boolean {
        if (!quietHoursEnabled) return false
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = quietStartHour * 60 + quietStartMinute
        val endMinutes = quietEndHour * 60 + quietEndMinute
        if (startMinutes == endMinutes) return false
        return if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    companion object {
        private const val PREFS_NAME = "chime_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRIMARY_INTERVAL = "primary_interval_minutes"
        private const val KEY_PRIMARY_INTERVAL_USE_HOURS = "primary_interval_use_hours"
        private const val KEY_PRIMARY_TONE = "primary_tone_index"
        private const val KEY_PRIMARY_VOLUME = "primary_volume_percent"
        private const val KEY_SECONDARY_INTERVAL = "secondary_interval_minutes"
        private const val KEY_SECONDARY_INTERVAL_USE_HOURS = "secondary_interval_use_hours"
        private const val KEY_SECONDARY_TONE = "secondary_tone_index"
        private const val KEY_SECONDARY_VOLUME = "secondary_volume_percent"
        private const val KEY_START_IMMEDIATELY = "start_immediately"
        private const val KEY_START_ANCHOR_MILLIS = "start_anchor_millis"
        private const val KEY_INDIGO_BUNTING = "indigo_bunting_mode"
        private const val KEY_VIBRATE = "vibrate_enabled"
        private const val KEY_OVERRIDE_DND = "override_dnd"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_START_HOUR = "quiet_start_hour"
        private const val KEY_QUIET_START_MINUTE = "quiet_start_minute"
        private const val KEY_QUIET_END_HOUR = "quiet_end_hour"
        private const val KEY_QUIET_END_MINUTE = "quiet_end_minute"
    }
}
