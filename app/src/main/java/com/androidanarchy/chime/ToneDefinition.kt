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

enum class ToneDefinition(
    val displayName: String,
    val frequency: Double,
    val secondFrequency: Double? = null,
    val durationMs: Int = 2500,
    val decayRate: Double = 3.0
) {
    SOFT_BELL("Soft Bell", 440.0, durationMs = 2500, decayRate = 2.5),
    HIGH_BELL("High Bell", 880.0, durationMs = 2000, decayRate = 3.5),
    DEEP_BELL("Deep Bell", 220.0, durationMs = 3000, decayRate = 2.0),
    DOUBLE_CHIME("Double Chime", 523.0, secondFrequency = 784.0, durationMs = 1500, decayRate = 3.5),
    BOWL("Bowl", 528.0, durationMs = 4000, decayRate = 1.5);

    companion object {
        val names: Array<String> get() = entries.map { it.displayName }.toTypedArray()
    }
}
