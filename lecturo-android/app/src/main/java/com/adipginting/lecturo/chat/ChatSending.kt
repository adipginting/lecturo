package com.adipginting.lecturo.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How many replies are currently being fetched. A send can outlive its panel —
 * the user puts the chat away and goes back to reading — so the reader's chat
 * icon reads this to keep saying an answer is on its way.
 */
object ChatSending {

    private var inFlight by mutableStateOf(0)

    val any: Boolean get() = inFlight > 0

    fun begin() {
        inFlight++
    }

    fun end() {
        if (inFlight > 0) inFlight--
    }
}
