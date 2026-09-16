package com.yamone.games.snowrush

import org.junit.Test
import kotlin.math.abs

class SnowRushEngineTest {
    private fun advanceSafely(e:SnowRushEngine, milliseconds:Int) {
        repeat(milliseconds / 100) { e.hazards.clear();e.advance(100_000_000L) }
        e.hazards.clear();e.advance((milliseconds%100)*1_000_000L);e.hazards.clear()
    }
    @Test fun retainsMillisecondPrecisionAndSubMillisecondRemainder() {
        val e=SnowRushEngine();e.advance(123_456_789L);check(e.elapsedMillis==123)
        e.advance(543_211L);check(e.elapsedMillis==124)
    }
    @Test fun frameRateDoesNotChangeStaticPlayerOutcome() {
        val a=SnowRushEngine();val b=SnowRushEngine();val c=SnowRushEngine()
        repeat(900) {a.advance(16_666_667L)}
        repeat(450) {b.advance(33_333_334L)}
        repeat(1800) {c.advance(8_333_333L)}
        check(a.gameOver==b.gameOver && b.gameOver==c.gameOver)
        check(a.elapsedMillis==b.elapsedMillis && abs(a.elapsedMillis-c.elapsedMillis)<=1)
        check(a.deathCause==b.deathCause && b.deathCause==c.deathCause)
    }
    @Test fun threeSecondProtectionPreventsInstantDeathEvenWithAnOverlappingObject() {
        val e=SnowRushEngine()
        e.hazards.add(SnowRushEngine.Hazard(999,false,.5,SnowRushEngine.PLAYER_Y,.05,0.0,0.0))
        repeat(11) {e.advance(250_000_000L)}
        e.advance(249_000_000L);check(e.elapsedMillis==2999);check(!e.gameOver)
        e.advance(1_000_000L);check(e.gameOver);check(e.elapsedMillis==3000)
    }
    @Test fun initialProtectionHoldsForManyRandomSeeds() {
        repeat(100) { seed ->
            val e=SnowRushEngine(seed)
            repeat(12) {e.advance(250_000_000L)}
            check(!e.gameOver) {"Premature death with seed $seed"}
        }
    }
    @Test fun verticalRadiusMatchesWidthBasedRendering() {
        val e=SnowRushEngine()
        val h=SnowRushEngine.Hazard(1,false,.5,.88,.05,0.0,0.0)
        check(!e.collides(h,.5,.82,.04,.02,.4))
        h.y=.847;check(e.collides(h,.5,.82,.04,.02,.4))
    }
    @Test fun circleCornerDoesNotUseAnOversizedSquareHitbox() {
        val e=SnowRushEngine()
        val h=SnowRushEngine.Hazard(1,true,.569,.8545,.04,0.0,0.0)
        check(!e.collides(h,.5,.82,.04,.02,.5))
        h.x=.54;h.y=.83;check(e.collides(h,.5,.82,.04,.02,.5))
    }
    @Test fun fragmentsAreRealVisibleHazards() {
        val e=SnowRushEngine();advanceSafely(e,3100)
        e.hazards.add(SnowRushEngine.Hazard(1,true,.5,.82,.025,0.0,0.0))
        e.advance(1_000_000L);check(e.gameOver);check(e.deathCause=="눈덩이 파편")
    }
    @Test fun fragmentsEmitToBothSidesAndThenFall() {
        val e=SnowRushEngine();advanceSafely(e,6100)
        e.hazards.add(SnowRushEngine.Hazard(1,false,.5,.2,.05,0.0,.3))
        e.advance(1_000_000L)
        val children=e.hazards.filter {it.fragment};check(children.size==2)
        check(children[0].vx<0 && children[1].vx>0);check(children.all {it.vy>0 && it.y<.53 && it.initialRadius>=.021})
        val before=children.map {it.y};e.advance(100_000_000L)
        check(children.zip(before).all {(a,y)->a.y>y})
        check(abs(children[0].vx)!=abs(children[1].vx))
    }
    @Test fun highDifficultyHasMoreEmissionsWithoutUnboundedHazardCounts() {
        val low=SnowRushEngine();val high=SnowRushEngine()
        advanceSafely(low,6100);advanceSafely(high,73_000)
        check(high.difficulty==7)
        for(e in listOf(low,high)) {
            e.hazards.add(SnowRushEngine.Hazard(999,false,.5,.49,.05,0.0,.3));e.advance(3_000_000L)
        }
        check(low.hazards.count {it.fragment}==2);check(high.hazards.count {it.fragment}==6)
        val e=SnowRushEngine()
        repeat(6000) {
            e.hazards.removeAll { it.y>.67 }
            e.advance(30_000_000L)
            check(e.hazards.size<=35);check(!e.gameOver)
        }
    }
    @Test fun ballsOnlyGrowAndNeverEmitNearThePlayer() {
        val e=SnowRushEngine();val h=SnowRushEngine.Hazard(1,false,.5,-.1,.05,0.0,.3)
        var previous=0.0
        repeat(130) {i->h.y=-.1+i*.01;val radius=e.radius(h);check(radius>=previous);previous=radius}
        advanceSafely(e,12_000)
        e.hazards.add(h.copy(y=.65));e.advance(1_000_000L);check(e.hazards.none {it.fragment})
    }
    @Test fun restartClearsHazardsClockAndDeath() {
        val e=SnowRushEngine();advanceSafely(e,12_000)
        e.hazards.add(SnowRushEngine.Hazard(1,true,.5,.82,.025,0.0,0.0));e.advance(1_000_000L)
        e.restart();check(e.elapsedMillis==0 && !e.gameOver && e.hazards.isEmpty() && e.dodged==0)
        e.advance(0L);check(e.elapsedMillis==0)
    }
    @Test fun frameStallsMustBePausedByTheCaller() {
        val e=SnowRushEngine()
        check(runCatching {e.advance(1_000_000_000L)}.isFailure)
        check(e.elapsedMillis==0)
    }
}
