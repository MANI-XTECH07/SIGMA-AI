package com.example

import com.example.voice.WakeWordManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordManagerTest {

    private val wakeWordManager = WakeWordManager()

    @Test
    fun testWakeWordDetection_heySigma() {
        val (triggered, command) = wakeWordManager.processSpokenText("Hey Sigma open YouTube")
        assertTrue(triggered)
        assertEquals("open youtube", command)
    }

    @Test
    fun testWakeWordDetection_sigmaAlone() {
        val (triggered, command) = wakeWordManager.processSpokenText("Sigma phone lock karo")
        assertTrue(triggered)
        assertEquals("phone lock karo", command)
    }

    @Test
    fun testDirectCommand_noWakeWord() {
        val (triggered, command) = wakeWordManager.processSpokenText("scroll down")
        assertEquals(false, triggered)
        assertEquals("scroll down", command)
    }
}
