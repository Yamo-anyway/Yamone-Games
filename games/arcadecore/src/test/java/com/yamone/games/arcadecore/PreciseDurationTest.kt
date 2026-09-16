package com.yamone.games.arcadecore

import org.junit.Test

class PreciseDurationTest {
    @Test fun millisecondsAreNotLostOrRoundedAcrossBoundaries() {
        check(preciseDuration(1)=="00:00.001")
        check(preciseDuration(999)=="00:00.999")
        check(preciseDuration(1001)=="00:01.001")
        check(preciseDuration(59999)=="00:59.999")
        check(preciseDuration(60001)=="01:00.001")
        check(preciseDuration(3600001)=="1:00:00.001")
        check(preciseDuration(-1)=="00:00.000")
    }
}
