package dev.anaekran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroGateTest {
    private class FakeState : IntroState {
        override var introPending = false
        override var lastBootCount = -1
    }

    private val gate = IntroGate(FakeState())

    @Test fun firstEverRunPlays() = assertTrue(gate.shouldPlay(461))

    @Test fun sameBootNoPending() {
        gate.markPlayed(461)
        assertFalse(gate.shouldPlay(461))
    }

    @Test fun newBootPlays() {
        gate.markPlayed(461)
        assertTrue(gate.shouldPlay(462))
    }

    @Test fun screenOffPlays() {
        gate.markPlayed(461)
        gate.onScreenOff()
        assertTrue(gate.shouldPlay(461))
        gate.markPlayed(461)
        assertFalse(gate.shouldPlay(461))
    }

    @Test fun unknownBootCountDoesNotLoop() {
        gate.markPlayed(-1)
        assertFalse(gate.shouldPlay(-1))
        gate.onScreenOff()
        assertTrue(gate.shouldPlay(-1))
    }

    @Test fun delayRightAfterBoot() = assertEquals(1500L, introDelayMs(uptimeMs = 40_000))

    @Test fun delayEndsAfterTwoMinutes() {
        assertEquals(1500L, introDelayMs(uptimeMs = 119_999))
        assertEquals(0L, introDelayMs(uptimeMs = 120_000))
    }
}
