package com.yamone.games

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamone.games.sudoku.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

internal class AdAccessStore(context: Context) {
    private val prefs = context.getSharedPreferences("yamone_ad_access", Context.MODE_PRIVATE)

    fun adRemoved(): Boolean = prefs.getBoolean(KEY_AD_REMOVED, false)

    fun setAdRemoved(removed: Boolean) {
        prefs.edit().putBoolean(KEY_AD_REMOVED, removed).apply()
    }

    fun adFreeUntilMillis(): Long = prefs.getLong(KEY_AD_FREE_UNTIL, 0L)

    fun addMinutes(minutes: Int): Long {
        val now = System.currentTimeMillis()
        val base = maxOf(now, adFreeUntilMillis())
        val next = base + minutes * 60_000L
        prefs.edit().putLong(KEY_AD_FREE_UNTIL, next).apply()
        return next
    }

    fun hasUsedFirstFreeGame(): Boolean = prefs.getBoolean(KEY_FIRST_GAME_USED, false)

    fun markFirstFreeGameUsed() {
        prefs.edit().putBoolean(KEY_FIRST_GAME_USED, true).apply()
    }

    fun grantLoadFailureMinutes(): Long = addMinutes(10)

    companion object {
        private const val KEY_AD_REMOVED = "ad_removed"
        private const val KEY_AD_FREE_UNTIL = "ad_free_until"
        private const val KEY_FIRST_GAME_USED = "first_game_used"
    }
}

internal fun compactAdFreeRemaining(untilMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val totalSeconds = ((untilMillis - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
    if (totalSeconds <= 0L) return "00:00:00"

    val days = totalSeconds / 86_400L
    if (days >= 100L) return "99일+"

    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (days > 0L) {
        "%d일 %02d:%02d:%02d".format(days, hours, minutes, seconds)
    } else {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}

internal fun adFreeUntilText(untilMillis: Long): String {
    if (untilMillis <= System.currentTimeMillis()) return "현재 전면광고 없는 시간이 없어요"
    val formatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시 mm분")
    val dateTime = Instant.ofEpochMilli(untilMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "${dateTime.format(formatter)}까지 전면광고 없음"
}

internal fun promotionUntilText(untilMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시 mm분")
    return Instant.ofEpochMilli(untilMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
}

@Composable
internal fun AdFreeTimeCard(
    themeMode: YamoneThemeMode,
    adFreeUntilMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val promotionStore = remember { PromotionStore(context) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(adFreeUntilMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val promotion = promotionStore.current(nowMillis)
    val promotionActive = promotion.isActive(nowMillis)

    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = Color.White,
        border = BorderStroke(1.dp, yamonePrimaryLine(themeMode)),
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = yamonePrimarySoft(themeMode)) {
                Text(
                    "AD",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = yamonePrimaryDark(themeMode)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (promotionActive) "${promotion.label} 적용 중" else "전면광고 없는 시간",
                    fontSize = 11.sp,
                    color = YamoneMuted
                )
                Text(
                    if (promotionActive) "전면광고 없음" else compactAdFreeRemaining(adFreeUntilMillis, nowMillis),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = YamoneInk
                )
            }
            Text("자세히  ›", fontSize = 11.sp, color = yamonePrimaryDark(themeMode))
        }
    }
}

@Composable
internal fun DevelopmentBannerAd(themeMode: YamoneThemeMode) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEAF0EF))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = yamonePrimarySoft(themeMode)) {
                Text(
                    "TEST AD",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = yamonePrimaryDark(themeMode)
                )
            }
            Spacer(Modifier.width(9.dp))
            Text("개발 중 테스트 배너 광고 영역", fontSize = 11.sp, color = YamoneMuted)
        }
    }
}

@Composable
internal fun AdAccessDetailsDialog(
    themeMode: YamoneThemeMode,
    adFreeUntilMillis: Long,
    onDismiss: () -> Unit,
    onRewardedAd: () -> Unit,
    onPurchaseAdRemoval: () -> Unit,
    onRedeemPromo: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val promotionStore = remember { PromotionStore(context) }
    var promoCode by remember { mutableStateOf("") }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(adFreeUntilMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val promotion = promotionStore.current(nowMillis)
    val promotionActive = promotion.isActive(nowMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = YamoneCream,
        title = {
            Column {
                Text("전면광고 설정", fontWeight = FontWeight.Black, color = YamoneInk)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (promotionActive) "프로모션 적용 중" else compactAdFreeRemaining(adFreeUntilMillis, nowMillis),
                    fontSize = if (promotionActive) 21.sp else 27.sp,
                    fontWeight = FontWeight.Black,
                    color = yamonePrimaryDark(themeMode)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(shape = RoundedCornerShape(17.dp), color = Color.White) {
                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                        if (promotionActive) {
                            Text(
                                "${promotion.label} · 전면광고가 표시되지 않아요",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = YamoneInk
                            )
                            promotion.validUntilMillis?.let { until ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${promotionUntilText(until)}까지 적용",
                                    fontSize = 11.sp,
                                    color = YamoneMuted
                                )
                            }
                        } else {
                            Text(
                                adFreeUntilText(adFreeUntilMillis),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = YamoneInk
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("앱을 사용하지 않는 동안에도 시간은 계속 줄어들어요.", fontSize = 11.sp, color = YamoneMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "프로모션은 전면광고만 면제하며 배너 광고는 계속 표시돼요.",
                            fontSize = 11.sp,
                            color = YamoneMuted
                        )
                    }
                }

                if (!promotionActive) {
                    Button(
                        onClick = onRewardedAd,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = yamonePrimary(themeMode),
                            contentColor = Color.White
                        )
                    ) {
                        Text("광고 보고 +30분", fontWeight = FontWeight.Black)
                    }
                    Text(
                        "보상형 광고를 끝까지 보면 전면광고 없는 시간이 30분씩 계속 누적돼요.",
                        fontSize = 10.sp,
                        color = YamoneMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (PURCHASE_UI_ENABLED) {
                    OutlinedButton(
                        onClick = onPurchaseAdRemoval,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, yamonePrimary(themeMode))
                    ) {
                        Text("광고 완전히 제거", fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                    }
                }

                if (PROMOTION_REDEMPTION_ENABLED) {
                    HorizontalDivider(color = yamonePrimaryLine(themeMode))
                Text("프로모션 코드", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = YamoneInk)
                Text(
                    "유효한 코드를 적용하면 전면광고가 면제돼요. 배너 광고는 계속 표시됩니다.",
                    fontSize = 10.sp,
                    color = YamoneMuted
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = promoCode,
                        onValueChange = { promoCode = it.trimStart().take(40) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("코드 입력", fontSize = 12.sp) },
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = yamonePrimary(themeMode),
                            unfocusedBorderColor = yamonePrimaryLine(themeMode),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    Button(
                        onClick = { if (promoCode.isNotBlank()) onRedeemPromo(promoCode) },
                        enabled = promoCode.isNotBlank(),
                        shape = RoundedCornerShape(15.dp),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = yamonePrimaryDark(themeMode))
                    ) {
                        Text("확인", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = yamonePrimaryDark(themeMode), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun DevelopmentInterstitialAdDialog(
    themeMode: YamoneThemeMode,
    onComplete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = { Text("테스트 전면 광고", fontWeight = FontWeight.Black, color = YamoneInk) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(22.dp), color = yamonePrimarySoft(themeMode)) {
                    Box(
                        Modifier.fillMaxWidth().height(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TEST AD", fontSize = 25.sp, fontWeight = FontWeight.Black, color = yamonePrimaryDark(themeMode))
                            Spacer(Modifier.height(8.dp))
                            Text("출시 전에는 실제 광고 대신 테스트 광고만 사용해요.", fontSize = 11.sp, color = YamoneMuted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("광고가 정상 표시되면 이후 30분 동안 게임 시작 시 전면광고가 나오지 않아요.", fontSize = 11.sp, color = YamoneMuted)
            }
        },
        confirmButton = {
            Button(
                onClick = onComplete,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode))
            ) {
                Text("광고 닫기 · 30분 시작", fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
internal fun DevelopmentRewardedAdDialog(
    themeMode: YamoneThemeMode,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = { Text("테스트 보상형 광고", fontWeight = FontWeight.Black, color = YamoneInk) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = RoundedCornerShape(22.dp), color = yamoneSecondarySoft(themeMode)) {
                    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("REWARDED TEST AD", fontSize = 20.sp, fontWeight = FontWeight.Black, color = YamoneInk)
                            Spacer(Modifier.height(8.dp))
                            Text("보상 완료 이벤트가 오면 +30분", fontSize = 11.sp, color = YamoneMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onComplete,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode))
            ) {
                Text("시청 완료 · +30분", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("취소", color = YamoneMuted) }
        }
    )
}

@Composable
internal fun RankingNicknameDialog(
    themeMode: YamoneThemeMode,
    initialNickname: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialNickname.takeIf { it != "야모네 플레이어" }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = YamoneCream,
        title = { Text("랭킹 닉네임을 정해요", fontWeight = FontWeight.Black, color = YamoneInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("온라인 랭킹을 처음 공유할 때만 닉네임이 필요해요.", fontSize = 12.sp, color = YamoneMuted)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(AppPreferences.MAX_NICKNAME_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("닉네임 입력") },
                    shape = RoundedCornerShape(17.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = yamonePrimary(themeMode),
                        unfocusedBorderColor = yamonePrimaryLine(themeMode),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                Text("랭킹을 보기만 할 때는 닉네임이 없어도 돼요.", fontSize = 11.sp, color = YamoneMuted)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (value.trim().isNotBlank()) onConfirm(value.trim()) },
                enabled = value.trim().isNotBlank(),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = yamonePrimary(themeMode))
            ) {
                Text("설정하고 공유 ON", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = YamoneMuted) }
        }
    )
}
