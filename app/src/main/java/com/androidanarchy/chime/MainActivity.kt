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
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.androidanarchy.chime.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private var suppressIntervalUpdates = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (PreferencesManager(this).isIndigoBuntingMode) {
            setTheme(R.style.Theme_Chime_IndigoBunting)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        prefs = PreferencesManager(this)
        setupSpinners()
        loadPreferences()
        setupListeners()
        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateAlarmPermissionCard()
    }

    private fun setupSpinners() {
        listOf(binding.spinnerPrimaryTone, binding.spinnerSecondaryTone).forEach { spinner ->
            spinner.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_item, ToneDefinition.names
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
    }

    private fun loadPreferences() {
        suppressIntervalUpdates = true
        binding.switchMain.isChecked = prefs.isEnabled

        val primaryUseHours = prefs.primaryIntervalUseHours
        val primaryDisplay = if (primaryUseHours) prefs.primaryIntervalMinutes / 60 else prefs.primaryIntervalMinutes
        binding.etPrimaryInterval.setText(primaryDisplay.toString())
        binding.togglePrimaryUnit.check(if (primaryUseHours) R.id.btn_primary_hours else R.id.btn_primary_minutes)

        binding.sliderPrimaryVolume.value = prefs.primaryVolumePercent.toFloat()
        binding.spinnerPrimaryTone.setSelection(prefs.primaryToneIndex)

        val secondaryUseHours = prefs.secondaryIntervalUseHours
        val secondaryDisplay = if (secondaryUseHours) prefs.secondaryIntervalMinutes / 60 else prefs.secondaryIntervalMinutes
        binding.etSecondaryInterval.setText(secondaryDisplay.toString())
        binding.toggleSecondaryUnit.check(if (secondaryUseHours) R.id.btn_secondary_hours else R.id.btn_secondary_minutes)

        binding.sliderSecondaryVolume.value = prefs.secondaryVolumePercent.toFloat()
        binding.spinnerSecondaryTone.setSelection(prefs.secondaryToneIndex)

        suppressIntervalUpdates = false

        updatePrimaryVolumeLabel(prefs.primaryVolumePercent)
        updateSecondaryVolumeLabel(prefs.secondaryVolumePercent)
        binding.toggleTheme.check(
            if (prefs.isIndigoBuntingMode) R.id.btn_theme_indigo_bunting else R.id.btn_theme_flamingo
        )
        binding.switchStartImmediately.isChecked = prefs.startImmediately
        updateStartAnchorText()
        binding.switchVibrate.isChecked = prefs.vibrateEnabled
        binding.switchOverrideDnd.isChecked = prefs.overrideDnd
        binding.switchQuietHours.isChecked = prefs.quietHoursEnabled
        binding.btnQuietStart.isEnabled = prefs.quietHoursEnabled
        binding.btnQuietEnd.isEnabled = prefs.quietHoursEnabled
        updateQuietTimeButtons()
        updateStatusText()
    }

    private fun setupListeners() {
        binding.switchMain.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!canScheduleExactAlarms()) {
                    binding.switchMain.isChecked = false
                    prefs.isEnabled = false
                    promptAlarmPermission()
                    return@setOnCheckedChangeListener
                }
                prefs.isEnabled = true
                ChimeForegroundService.start(this)
            } else {
                prefs.isEnabled = false
                ChimeForegroundService.stop(this)
            }
            updateStatusText()
        }

        binding.etPrimaryInterval.doAfterTextChanged { text ->
            if (suppressIntervalUpdates) return@doAfterTextChanged
            val value = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
            if (value < 0) return@doAfterTextChanged
            prefs.primaryIntervalMinutes = if (prefs.primaryIntervalUseHours) value * 60 else value
            if (prefs.startImmediately) { prefs.startAnchorMillis = System.currentTimeMillis(); updateStartAnchorText() }
            updateStatusText()
            if (prefs.isEnabled) ChimeForegroundService.start(this)
        }

        binding.togglePrimaryUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressIntervalUpdates) return@addOnButtonCheckedListener
            val nowHours = checkedId == R.id.btn_primary_hours
            if (nowHours == prefs.primaryIntervalUseHours) return@addOnButtonCheckedListener
            val currentDisplay = binding.etPrimaryInterval.text?.toString()?.toIntOrNull() ?: 0
            prefs.primaryIntervalUseHours = nowHours
            val newDisplay = if (nowHours) currentDisplay / 60 else currentDisplay * 60
            suppressIntervalUpdates = true
            binding.etPrimaryInterval.setText(newDisplay.toString())
            suppressIntervalUpdates = false
            prefs.primaryIntervalMinutes = if (nowHours) newDisplay * 60 else newDisplay
            if (prefs.startImmediately) { prefs.startAnchorMillis = System.currentTimeMillis(); updateStartAnchorText() }
            updateStatusText()
            if (prefs.isEnabled) ChimeForegroundService.start(this)
        }

        binding.sliderPrimaryVolume.addOnChangeListener { _, value, _ ->
            prefs.primaryVolumePercent = value.toInt()
            updatePrimaryVolumeLabel(value.toInt())
        }

        binding.etSecondaryInterval.doAfterTextChanged { text ->
            if (suppressIntervalUpdates) return@doAfterTextChanged
            val value = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
            if (value < 0) return@doAfterTextChanged
            prefs.secondaryIntervalMinutes = if (prefs.secondaryIntervalUseHours) value * 60 else value
            if (prefs.startImmediately) { prefs.startAnchorMillis = System.currentTimeMillis(); updateStartAnchorText() }
            updateStatusText()
            if (prefs.isEnabled) ChimeForegroundService.start(this)
        }

        binding.toggleSecondaryUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressIntervalUpdates) return@addOnButtonCheckedListener
            val nowHours = checkedId == R.id.btn_secondary_hours
            if (nowHours == prefs.secondaryIntervalUseHours) return@addOnButtonCheckedListener
            val currentDisplay = binding.etSecondaryInterval.text?.toString()?.toIntOrNull() ?: 0
            prefs.secondaryIntervalUseHours = nowHours
            val newDisplay = if (nowHours) currentDisplay / 60 else currentDisplay * 60
            suppressIntervalUpdates = true
            binding.etSecondaryInterval.setText(newDisplay.toString())
            suppressIntervalUpdates = false
            prefs.secondaryIntervalMinutes = if (nowHours) newDisplay * 60 else newDisplay
            if (prefs.startImmediately) { prefs.startAnchorMillis = System.currentTimeMillis(); updateStartAnchorText() }
            updateStatusText()
            if (prefs.isEnabled) ChimeForegroundService.start(this)
        }

        binding.sliderSecondaryVolume.addOnChangeListener { _, value, _ ->
            prefs.secondaryVolumePercent = value.toInt()
            updateSecondaryVolumeLabel(value.toInt())
        }

        binding.spinnerPrimaryTone.onItemSelectedListener = toneListener { prefs.primaryToneIndex = it }
        binding.spinnerSecondaryTone.onItemSelectedListener = toneListener { prefs.secondaryToneIndex = it }

        binding.btnTestPrimary.setOnClickListener {
            Thread { ChimePlayer.play(this, ToneDefinition.entries[prefs.primaryToneIndex], prefs.primaryVolumePercent) }.start()
        }

        binding.btnTestSecondary.setOnClickListener {
            Thread { ChimePlayer.play(this, ToneDefinition.entries[prefs.secondaryToneIndex], prefs.secondaryVolumePercent) }.start()
        }

        binding.btnInfoPrimary.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.info_interval_scheduling))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        binding.btnInfoSecondary.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.info_interval_scheduling))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        binding.btnInfoStartImmediately.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.info_start_immediately))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        binding.switchStartImmediately.setOnCheckedChangeListener { _, isChecked ->
            prefs.startImmediately = isChecked
            prefs.startAnchorMillis = if (isChecked) System.currentTimeMillis() else -1L
            updateStartAnchorText()
            if (prefs.isEnabled) ChimeForegroundService.start(this)
        }

        binding.switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.vibrateEnabled = isChecked
        }

        binding.switchOverrideDnd.setOnCheckedChangeListener { _, isChecked ->
            prefs.overrideDnd = isChecked
        }

        binding.switchQuietHours.setOnCheckedChangeListener { _, isChecked ->
            prefs.quietHoursEnabled = isChecked
            binding.btnQuietStart.isEnabled = isChecked
            binding.btnQuietEnd.isEnabled = isChecked
            updateStatusText()
        }

        binding.btnQuietStart.setOnClickListener { showTimePicker(isStart = true) }
        binding.btnQuietEnd.setOnClickListener { showTimePicker(isStart = false) }

        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = checkedId == R.id.btn_theme_indigo_bunting
            if (newMode != prefs.isIndigoBuntingMode) {
                prefs.isIndigoBuntingMode = newMode
                recreate()
            }
        }
    }

    private fun toneListener(onSelected: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) =
            onSelected(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun updateStatusText() {
        if (!prefs.isEnabled) {
            binding.tvStatus.text = getString(R.string.status_off)
            return
        }
        if (prefs.isQuietNow()) {
            binding.tvStatus.text = getString(R.string.status_quiet,
                formatTime(prefs.quietEndHour, prefs.quietEndMinute))
            return
        }
        val pMin = minutesUntilNext(prefs.primaryIntervalMinutes)
        val sMin = minutesUntilNext(prefs.secondaryIntervalMinutes)
        binding.tvStatus.text = getString(R.string.status_on, pMin, sMin)
    }

    private fun minutesUntilNext(intervalMinutes: Int): Int {
        if (intervalMinutes <= 0) return 0
        val cal = Calendar.getInstance()
        val totalMinutesNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return intervalMinutes - (totalMinutesNow % intervalMinutes)
    }

    private fun updatePrimaryVolumeLabel(percent: Int) {
        binding.tvPrimaryVolumeLabel.text = getString(R.string.volume_label, percent)
    }

    private fun updateSecondaryVolumeLabel(percent: Int) {
        binding.tvSecondaryVolumeLabel.text = getString(R.string.volume_label, percent)
    }

    private fun updateStartAnchorText() {
        val isOn = prefs.startImmediately && prefs.startAnchorMillis > 0
        binding.tvStartAnchor.text = if (isOn) {
            val cal = Calendar.getInstance().apply { timeInMillis = prefs.startAnchorMillis }
            getString(R.string.start_anchor_on, formatTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
        } else {
            getString(R.string.start_anchor_disabled)
        }
        binding.tvStartAnchor.alpha = if (isOn) 1.0f else 0.5f
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true
    }

    private fun updateAlarmPermissionCard() {
        binding.cardAlarmPermission.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()
    }

    private fun promptAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        updateAlarmPermissionCard()
    }

    private fun showTimePicker(isStart: Boolean) {
        val hour = if (isStart) prefs.quietStartHour else prefs.quietEndHour
        val minute = if (isStart) prefs.quietStartMinute else prefs.quietEndMinute
        val title = getString(if (isStart) R.string.quiet_start_title else R.string.quiet_end_title)
        MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(title)
            .build()
            .also { picker ->
                picker.addOnPositiveButtonClickListener {
                    if (isStart) {
                        prefs.quietStartHour = picker.hour
                        prefs.quietStartMinute = picker.minute
                    } else {
                        prefs.quietEndHour = picker.hour
                        prefs.quietEndMinute = picker.minute
                    }
                    updateQuietTimeButtons()
                    updateStatusText()
                }
                picker.show(supportFragmentManager, if (isStart) "quiet_start" else "quiet_end")
            }
    }

    private fun updateQuietTimeButtons() {
        binding.btnQuietStart.text = formatTime(prefs.quietStartHour, prefs.quietStartMinute)
        binding.btnQuietEnd.text = formatTime(prefs.quietEndHour, prefs.quietEndMinute)
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "%d:%02d %s".format(h, minute, amPm)
    }
}
