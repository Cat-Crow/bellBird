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
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object ChimePlayer {

    private const val SAMPLE_RATE = 44100
    private const val ATTACK_SAMPLES = SAMPLE_RATE * 20 / 1000  // 20 ms linear fade-in

    fun play(context: Context, tone: ToneDefinition, volumePercent: Int) {
        // Base amplitude is 50% of PCM max so that 200% = full scale with no clipping
        val amplitude = Short.MAX_VALUE * 0.5 * (volumePercent.coerceIn(0, 200) / 100.0)
        if (tone.secondFrequency != null) {
            playNote(context, tone.frequency, tone.durationMs, tone.decayRate, amplitude)
            Thread.sleep(80)
            playNote(context, tone.secondFrequency, tone.durationMs, tone.decayRate, amplitude)
        } else {
            playNote(context, tone.frequency, tone.durationMs, tone.decayRate, amplitude)
        }
    }

    private fun playNote(context: Context, frequency: Double, durationMs: Int, decayRate: Double, amplitude: Double) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Always route through the alarm stream so volume tracks alarm level, not media/ringer
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Without audio focus, Android silently drops audio from background services
        val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttrs)
                .build()
            if (audioManager.requestAudioFocus(req) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            req
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            null
        }

        val numSamples = SAMPLE_RATE * durationMs / 1000
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = if (i < ATTACK_SAMPLES) i.toDouble() / ATTACK_SAMPLES else 1.0
            val decay = exp(-t * decayRate)
            // Fundamental + 2nd and 3rd harmonics, normalized to [-1, 1]
            val wave = (
                sin(2 * PI * frequency * t) +
                0.4 * sin(2 * PI * frequency * 2 * t) +
                0.15 * sin(2 * PI * frequency * 3 * t)
            ) / 1.55
            samples[i] = (attack * decay * wave * amplitude)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val streamBuf = maxOf(minBuf * 2, 4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(streamBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            track.play()
            var written = 0
            while (written < samples.size) {
                val chunk = minOf(streamBuf / 2, samples.size - written)
                val result = track.write(samples, written, chunk)
                if (result < 0) break
                written += result
            }
            // Wait for the final buffer-worth of audio to drain from hardware
            Thread.sleep(streamBuf.toLong() * 1000L / (SAMPLE_RATE * 2) + 50)
        } finally {
            if (track.state == AudioTrack.STATE_INITIALIZED) track.stop()
            track.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        }
    }
}
