package com.yamone.spiritshift.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.yamone.spiritshift.R
import com.yamone.spiritshift.game.ElementKind

data class ElementVisual(
    @DrawableRes val drawable: Int,
    val accent: Color,
    val label: String,
)

fun ElementKind.visual(): ElementVisual = when (this) {
    ElementKind.FIRE -> ElementVisual(R.drawable.element_fire, Color(0xFFFF453A), "불")
    ElementKind.WATER -> ElementVisual(R.drawable.element_water, Color(0xFF246BFD), "물")
    ElementKind.NATURE -> ElementVisual(R.drawable.element_nature, Color(0xFF32C759), "자연")
    ElementKind.LIGHTNING -> ElementVisual(R.drawable.element_lightning, Color(0xFFFFD60A), "번개")
    ElementKind.MOON -> ElementVisual(R.drawable.element_moon, Color(0xFFA35CFF), "달")
    ElementKind.LIGHT -> ElementVisual(R.drawable.element_light, Color(0xFFFF9F0A), "빛")
    ElementKind.ICE -> ElementVisual(R.drawable.element_ice, Color(0xFF55DDE0), "얼음")
}
