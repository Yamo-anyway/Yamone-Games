package com.yamone.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.arcadecore.GameFeedback
import com.yamone.games.sudoku.ui.theme.*

@Composable
internal fun AdFreeHeader(
    themeMode: YamoneThemeMode,
    permanentAdFree: Boolean,
    untilMillis: Long,
    nowMillis: Long,
    onAdDetails: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(V3Background)) {
        // Keep the complete brand and clock on one row even on compact phones / enlarged fonts.
        val scale = LocalDensity.current.fontScale
        val compact = maxWidth < 370.dp
        val fittedScale = scale.coerceAtMost(if (compact) 1f else 1.12f) / scale
        val brandSize = (if (compact) 20f else 23f) * fittedScale
        val clock = AdDisplayPolicy.clock(untilMillis, nowMillis)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = yamonePrimaryDark(themeMode))) { append("야모네 ") }
                    withStyle(SpanStyle(color = Color(0xFFDE6F91))) { append("게임") }
                },
                modifier = Modifier.weight(1f), fontSize = brandSize.sp,
                fontWeight = FontWeight.Black, maxLines = 1, softWrap = false
            )
            // Permanent owners see neither timer, purchase prompt nor an "ads removed" badge.
            if (!permanentAdFree) {
                Surface(
                    onClick = { GameFeedback.tap(); onAdDetails() },
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "광고 없음 $clock, 광고 안내 열기"
                    },
                    color = yamonePrimarySoft(themeMode), shape = RoundedCornerShape(17.dp)
                ) {
                    Row(
                        Modifier.heightIn(min = 46.dp).padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("광고 없음", fontSize = (10f * fittedScale).sp,
                            fontWeight = FontWeight.Bold, color = yamonePrimaryDark(themeMode), maxLines = 1)
                        Text(clock, fontSize = ((if (clock.length > 9) 10f else 12f) * fittedScale).sp,
                            fontWeight = FontWeight.ExtraBold, color = YamoneInk, maxLines = 1,
                            overflow = TextOverflow.Clip)
                        RewardPlayGlyph(Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

/** Resolution-independent rounded play jewel, not a platform-dependent emoji. */
@Composable
private fun RewardPlayGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Brush.linearGradient(listOf(Color(0xFFEA7899), Color(0xFFCA4F7E))), radius = size.minDimension / 2f)
        drawCircle(Color.White.copy(alpha = .22f), size.minDimension * .23f, Offset(size.width * .33f, size.height * .26f))
        val play = Path().apply {
            moveTo(size.width * .39f, size.height * .28f)
            quadraticTo(size.width * .34f, size.height * .25f, size.width * .34f, size.height * .32f)
            lineTo(size.width * .34f, size.height * .68f)
            quadraticTo(size.width * .34f, size.height * .75f, size.width * .40f, size.height * .71f)
            lineTo(size.width * .70f, size.height * .54f)
            quadraticTo(size.width * .77f, size.height * .50f, size.width * .70f, size.height * .46f)
            close()
        }
        drawPath(play, Color.White)
    }
}
