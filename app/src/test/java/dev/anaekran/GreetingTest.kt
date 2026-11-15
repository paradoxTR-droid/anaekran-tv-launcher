package dev.anaekran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GreetingTest {
    @Test fun slotBoundaries() {
        assertEquals(TimeSlot.GECE, slotFor(4)); assertEquals(TimeSlot.SABAH, slotFor(5))
        assertEquals(TimeSlot.SABAH, slotFor(10)); assertEquals(TimeSlot.GUNDUZ, slotFor(11))
        assertEquals(TimeSlot.GUNDUZ, slotFor(16)); assertEquals(TimeSlot.AKSAM, slotFor(17))
        assertEquals(TimeSlot.AKSAM, slotFor(21)); assertEquals(TimeSlot.GECE, slotFor(22))
        assertEquals(TimeSlot.GECE, slotFor(0))
    }

    @Test fun everySlotHasThreeToFivePhrases() =
        TimeSlot.entries.forEach { assertTrue(GREETINGS.getValue(it).size in 3..5) }

    @Test fun pickIsFromSlot() =
        repeat(50) { assertTrue(pickGreeting(TimeSlot.GECE, Random(it)) in GREETINGS.getValue(TimeSlot.GECE)) }
}
