package com.adipginting.lecturo.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether formulas are typeset. Mirrors the Settings switch, which lives in
 * DataStore; the renderer reads it from here so every message observes one
 * value without threading it through every host.
 */
object MathSetting {
    var enabled by mutableStateOf(true)
}
