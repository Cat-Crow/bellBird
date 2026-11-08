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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ChimeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!PreferencesManager(context).isEnabled) return
        val isPrimary = intent.getBooleanExtra(EXTRA_IS_PRIMARY, true)
        ChimeForegroundService.start(
            context,
            playPrimary = isPrimary,
            playSecondary = !isPrimary,
            reschedulePrimary = isPrimary,
            rescheduleSecondary = !isPrimary
        )
    }

    companion object {
        const val EXTRA_IS_PRIMARY = "is_primary"
    }
}
