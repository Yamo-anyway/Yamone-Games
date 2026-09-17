package com.yamone.games.snowrush

import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class SnowRush304Test {
    private fun prepared(seed: Int = 304, millis: Int = 6100): SnowRushEngine {
        val e=SnowRushEngine(seed)
        repeat(millis/100) { e.hazards.clear(); e.advance(100_000_000L) }
        e.hazards.clear();e.advance((millis%100)*1_000_000L);e.hazards.clear()
        return e
    }
    @Test fun everyEmittedPairHasTwoDistinctSlowFallSpeeds() {
        repeat(60) { seed ->
            val e=prepared(seed)
            e.hazards.add(SnowRushEngine.Hazard(999,false,.5,.2,.05,0.0,.31))
            e.advance(1_000_000L)
            val children=e.hazards.filter {it.fragment}
            check(children.size==2)
            check(children.all {it.vy>0 && it.vy < .31*.71})
            check(abs(children[0].vy-children[1].vy) >= .31*.059)
            check(children.all {it.sourceBallSpeed==.31})
        }
    }
    @Test fun evenCombinedLateralAndVerticalSpeedStaysBelowSourceBall() {
        for(aspect in listOf(.4,.62,.95)) repeat(24) { seed ->
            val e=prepared(seed,73000);e.widthToHeight=aspect
            e.hazards.add(SnowRushEngine.Hazard(999,false,.5,.2,.05,0.0,.27))
            e.advance(1_000_000L)
            for(h in e.hazards.filter {it.fragment}) {
                check(hypot(h.vx*aspect,h.vy)<=h.sourceBallSpeed*.821)
            }
        }
    }
    @Test fun shardsNeverCatchUpByAcceleratingLater() {
        val e=prepared();e.moveTo(.91)
        e.hazards.add(SnowRushEngine.Hazard(999,false,.3,.2,.05,0.0,.30))
        e.advance(1_000_000L)
        val children=e.hazards.filter {it.fragment};val speeds=children.map {it.vy}
        repeat(140) {
            e.hazards.removeAll { !it.fragment }
            e.advance(100_000_000L)
            children.zip(speeds).forEach { (h,speed) ->
                check(h.vy==speed)
                check(hypot(h.vx*e.widthToHeight,h.vy)<=h.sourceBallSpeed*.821)
            }
        }
        check(!e.gameOver)
        check(e.hazards.isEmpty())
    }
    @Test fun fragmentsDriftDifferentDistancesBeforeFallingStraight() {
        val e=prepared()
        e.hazards.add(SnowRushEngine.Hazard(999,false,.5,.2,.05,0.0,.30));e.advance(1_000_000L)
        val shards=e.hazards.filter {it.fragment};val startX=shards.map{it.x};val startVx=shards.map{it.vx}
        repeat(10) {e.hazards.removeAll {!it.fragment};e.advance(100_000_000L)}
        check(shards[0].x < startX[0] && shards[1].x > startX[1])
        check(shards.zip(startVx).all{(h,v)->abs(h.vx)<abs(v)})
        check(abs(shards[0].x-startX[0]) != abs(shards[1].x-startX[1]))
    }
    @Test fun bothAvatarEdgesStayInsideVisibleBracketsAtAllTestWidths() {
        for(width in listOf(240f,280f,320f,360f,412f,600f,800f)) {
            val e=SnowRushEngine();e.configureViewport(width,570f,58f)
            e.moveBy(-1000f)
            check(abs((e.playerX-29.0/width)-SnowRushEngine.EDGE_INSET)<1e-6)
            e.moveBy(1000f)
            check(abs((e.playerX+29.0/width)-(1-SnowRushEngine.EDGE_INSET))<1e-6)
        }
    }
    @Test fun viewportChangeClampsPositionWithoutResettingTheScore() {
        val e=prepared();val before=e.elapsedMillis
        e.configureViewport(600f,500f,58f);e.moveTo(1.0)
        e.configureViewport(280f,600f,58f)
        check(e.playerX==e.maxPlayerX && e.elapsedMillis==before && !e.gameOver)
        check(abs(e.widthToHeight-280.0/600)<1e-6)
    }
    @Test fun invalidViewportAndInputCannotPoisonState() {
        val e=SnowRushEngine();e.configureViewport(360f,570f,58f)
        val min=e.minPlayerX;val aspect=e.widthToHeight
        e.configureViewport(0f,Float.NaN,58f);e.moveBy(Float.NaN);e.moveTo(Double.POSITIVE_INFINITY)
        check(e.playerX==.5 && e.minPlayerX==min && e.widthToHeight==aspect)
    }
    @Test fun decorationNeverAddsCollisionObjects() {
        val e=SnowRushEngine();e.configureViewport(360f,570f,58f)
        check(e.hazards.isEmpty());e.advance(899_000L);check(e.hazards.isEmpty())
        check(e.visuals().isEmpty())
    }
    @Test fun slowerShardsStillRespectGlobalHazardCap() {
        val e=SnowRushEngine(7304)
        repeat(8000) {
            e.hazards.removeAll { it.y>.66 }
            e.advance(30_000_000L)
            check(e.hazards.size<=35 && !e.gameOver)
            for(h in e.hazards.filter { it.fragment }) check(h.vy<h.sourceBallSpeed)
        }
    }
}
