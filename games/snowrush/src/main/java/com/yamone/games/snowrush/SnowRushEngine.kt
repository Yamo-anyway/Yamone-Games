package com.yamone.games.snowrush

import kotlin.math.*
import kotlin.random.Random

/** Millisecond fixed-step simulation. No Android/Compose dependencies or wall-clock changes. */
internal class SnowRushEngine(private val seed: Int = 20260916) {
    internal data class Hazard(
        val id: Int, val fragment: Boolean, var x: Double, var y: Double,
        val initialRadius: Double, var vx: Double, var vy: Double,
        var rotation: Double = 0.0, var emissions: Int = 0,
        val sourceBallSpeed: Double = 0.0, val lateralDrag: Double = .9992
    )
    data class Visual(val id: Int, val fragment: Boolean, val x: Float, val y: Float,
        val radius: Float, val rotation: Float)
    var elapsedMillis = 0; private set
    var dodged = 0; private set
    var gameOver = false; private set
    var playerX = .5; private set
    var deathCause = ""; private set
    var minPlayerX = .09; private set
    var maxPlayerX = .91; private set
    var widthToHeight = .62
    var playerHalfWidth = .045
    var playerHalfHeight = .028
    val difficulty: Int get() = (elapsedMillis / 12_000 + 1).coerceAtMost(7)
    val protected: Boolean get() = elapsedMillis < PROTECTION_MS
    internal val hazards = mutableListOf<Hazard>()
    private var remainderNanos = 0L
    private var nextSpawnMillis = 900
    private var serial = 0
    private var random = Random(seed)
    private var lastSpawnX = .5

    fun restart() {
        elapsedMillis = 0; dodged = 0; gameOver = false; playerX = .5
        deathCause = ""; remainderNanos = 0L; nextSpawnMillis = 900
        serial = 0; random = Random(seed); lastSpawnX = .5; hazards.clear()
    }
    /** Visual bounds and collision dimensions follow the same actual avatar, even after a banner relayout. */
    fun configureViewport(widthDp: Float, heightDp: Float, avatarDp: Float) {
        if (!widthDp.isFinite() || !heightDp.isFinite() || !avatarDp.isFinite() ||
            widthDp <= 0f || heightDp <= 0f || avatarDp <= 0f) return
        widthToHeight = (widthDp / heightDp).toDouble()
        playerHalfWidth = avatarDp / widthDp * .29
        playerHalfHeight = avatarDp / heightDp * .28
        minPlayerX = (EDGE_INSET + avatarDp / widthDp * .5).coerceAtMost(.45)
        maxPlayerX = 1.0 - minPlayerX
        playerX = playerX.coerceIn(minPlayerX, maxPlayerX)
    }
    fun moveBy(delta: Float) {
        if (!gameOver && delta.isFinite()) playerX = (playerX + delta * 1.1).coerceIn(minPlayerX, maxPlayerX)
    }
    fun moveTo(x: Double) { if (!gameOver && x.isFinite()) playerX = x.coerceIn(minPlayerX, maxPlayerX) }
    fun radius(h: Hazard): Double = if (h.fragment) h.initialRadius else
        h.initialRadius * (.78 + ((h.y + .1) / 1.2).coerceIn(0.0, 1.0) * .68)
    fun visuals(): List<Visual> = hazards.map { Visual(it.id,it.fragment,it.x.toFloat(),it.y.toFloat(),radius(it).toFloat(),it.rotation.toFloat()) }

    /** Caller pauses on long stalls. Pauses/background time are never submitted here. */
    fun advance(deltaNanos: Long) {
        if (gameOver || deltaNanos <= 0L) return
        require(deltaNanos <= 250_000_000L) { "A frame stall must pause, not fast-forward the game" }
        remainderNanos += deltaNanos
        while (remainderNanos >= 1_000_000L && !gameOver) {
            remainderNanos -= 1_000_000L
            step()
        }
    }
    private fun step() {
        elapsedMillis++
        if (elapsedMillis >= MAX_DURATION_MS) { gameOver = true; deathCause = "완주"; return }
        if (elapsedMillis >= nextSpawnMillis) {
            spawn()
            nextSpawnMillis += (1_650 - (difficulty - 1) * 130 + random.nextInt(-90, 91)).coerceAtLeast(780)
        }
        val born = mutableListOf<Hazard>()
        val iterator = hazards.iterator()
        while (iterator.hasNext()) {
            val h = iterator.next()
            h.x += h.vx * .001; h.y += h.vy * .001
            h.rotation = (h.rotation + (if (h.fragment) 90.0 else 75.0) * .001) % 360.0
            if (h.fragment) {
                // A shard never accelerates into a faster obstacle than its source snowball.
                // Different terminal fall speeds + independent lateral drag create delayed paths.
                h.vx *= h.lateralDrag
            } else {
                // All emission happens well above the player. Never spawn a shard on the avatar.
                val emissionCount = if (difficulty >= 5) 3 else if (difficulty >= 3) 2 else 1
                val gate = .19 + h.emissions * .15
                if (elapsedMillis >= 6_000 && h.emissions < emissionCount && h.y >= gate && h.y <= .52) {
                    val capacity = (8 + difficulty * 3).coerceAtMost(28)
                    val existing = hazards.count { it.fragment } + born.size
                    if (existing + 2 <= capacity) emit(h, born)
                    h.emissions++
                }
            }
            if (!protected && collides(h, playerX, PLAYER_Y, playerHalfWidth, playerHalfHeight, widthToHeight)) {
                gameOver = true; deathCause = if (h.fragment) "눈덩이 파편" else "눈덩이"
                return
            }
            if (h.y > 1.13 || h.x < -.18 || h.x > 1.18) {
                if (!h.fragment) dodged++
                iterator.remove()
            }
        }
        hazards.addAll(born)
    }
    private fun spawn() {
        if (hazards.count { !it.fragment } >= 7) return
        var x = .12 + random.nextDouble() * .76
        // An open middle lane on first approach; avoid tightly stacked spawn positions thereafter.
        if (serial == 0) x = if (random.nextBoolean()) .20 else .80
        if (abs(x - lastSpawnX) < .18) x = (1.0 - x).coerceIn(.12, .88)
        lastSpawnX = x
        hazards.add(Hazard(++serial, false, x, -.1, .045 + random.nextDouble() * .012,
            0.0, (.25 + (difficulty - 1) * .025) * (.94 + random.nextDouble() * .12)))
    }
    private fun emit(ball: Hazard, born: MutableList<Hazard>) {
        val slowOnLeft = random.nextBoolean()
        for (sign in listOf(-1.0, 1.0)) {
            // Each pair includes a lingering shard and a medium-speed shard.
            // Fall speed is fixed at birth; later difficulty increases don't accelerate old shards.
            val slow = (sign < 0) == slowOnLeft
            val fraction = if (slow) .32 + random.nextDouble() * .14 else .52 + random.nextDouble() * .18
            val fallSpeed = ball.vy * fraction
            val spread = .055 + random.nextDouble() * (.065 + difficulty * .020)
            // X is in widths/second and Y in heights/second. Bound the real 2D speed too.
            val maxLateral = sqrt((ball.vy * .82).pow(2) - fallSpeed.pow(2)) / widthToHeight.coerceAtLeast(.1)
            born.add(Hazard(++serial, true,
                ball.x + sign * (radius(ball) + .016), ball.y + radius(ball) * widthToHeight * .12,
                .021 + random.nextDouble() * .007, sign * min(spread, maxLateral), fallSpeed,
                random.nextDouble() * 360, sourceBallSpeed = ball.vy,
                lateralDrag = .9989 + random.nextDouble() * .00065))
        }
    }
    internal fun collides(h: Hazard, px: Double, py: Double, hw: Double, hh: Double, aspect: Double): Boolean {
        // Radius is normalized to WIDTH; convert vertical distance to the same unit.
        // Circle/rectangle collision, not the old oversized square around each object.
        val dx = (abs(h.x - px) - hw).coerceAtLeast(0.0)
        val dy = (abs(h.y - py) - hh).coerceAtLeast(0.0) / aspect.coerceAtLeast(.1)
        val r = radius(h) * if (h.fragment) .80 else .86
        return dx * dx + dy * dy <= r * r
    }
    companion object {
        const val EDGE_INSET = .025
        const val PLAYER_Y = .82
        const val PROTECTION_MS = 3_000
        const val MAX_DURATION_MS = 86_400_000
    }
}
