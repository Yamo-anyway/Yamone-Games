package com.yamone.games.icejump

/**
 * Yamone Ice Jump – arcade game currently under development.
 *
 * Core V1 direction:
 * - character jumps automatically
 * - player controls horizontal movement only
 * - platforms scroll upward as the player climbs
 * - speed/difficulty increases gradually
 * - best height is stored locally; sharing can use the final result only
 */
object IceJumpGame {
    const val TITLE = "빙하 점프"
    const val STATUS = "개발중"
    const val DESCRIPTION = "자동으로 점프하며 얼음판을 계속 올라가요"

    const val START_SPEED = 1.0f
    const val MAX_SPEED = 2.2f
    const val SPEED_STEP = 0.05f
}
