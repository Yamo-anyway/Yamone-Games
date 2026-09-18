package com.yamone.games.arcadecore

import org.junit.Assert.*
import org.junit.Test

class IceHeightTest {
    @Test fun hundredthsAreNotTruncatedToWholeMeters() {
        val height=IceHeight();height.addScroll(.01234)
        assertEquals(1234,height.centimeters);assertEquals("12.34m",formatIceHeight(height.centimeters))
    }
    @Test fun subCentimetreIncrementsAccumulateWithoutFrameRounding() {
        val height=IceHeight(); repeat(1000) { height.addScroll(.0000001) }
        assertEquals(10,height.centimeters)
    }
    @Test fun sameDistanceAcrossDifferentFrameCountsIsIdentical() {
        val a=IceHeight();val b=IceHeight()
        repeat(60) {a.addScroll(.12345/60)};repeat(144) {b.addScroll(.12345/144)}
        assertEquals(a.centimeters,b.centimeters);assertEquals(12345,a.centimeters)
    }
    @Test fun badInputsAndResetCannotCorruptHeight() {
        val a=IceHeight(); a.addScroll(-1.0);a.addScroll(Double.NaN);a.addScroll(Double.POSITIVE_INFINITY)
        assertEquals(0,a.centimeters);a.addScroll(.1);a.reset();assertEquals(0,a.centimeters)
    }
    @Test fun oldMetresConvertWithoutInventingFractionsAndDoNotOverflow() {
        assertEquals(12300,legacyIceToCentimeters(123));assertEquals("123.00m",formatIceHeight(12300))
        assertEquals(2_000_000_000,legacyIceToCentimeters(Int.MAX_VALUE));assertEquals(0,legacyIceToCentimeters(-1))
    }
}
