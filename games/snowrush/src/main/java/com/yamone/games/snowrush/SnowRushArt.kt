package com.yamone.games.snowrush

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** One bundled painting, displayed directly. No simulated sky, forest or decorative flakes. */
@Composable
internal fun SnowPaintedBackdrop(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.snow_aurora_304), contentDescription = null,
        modifier = modifier, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
}

/** Only two lower brackets: visual edge == avatar edge at the engine's X clamp. */
internal fun DrawScope.paintDodgeBounds() {
    val inset = size.width * SnowRushEngine.EDGE_INSET.toFloat()
    val top = size.height * .735f; val bottom = size.height * .92f
    for (left in listOf(true, false)) {
        val x = if (left) inset else size.width-inset
        val inward = if (left) 1f else -1f
        val bracket = Path().apply {
            moveTo(x+inward*9.dp.toPx(),top)
            lineTo(x,top);lineTo(x,bottom);lineTo(x+inward*9.dp.toPx(),bottom)
        }
        drawPath(bracket, Color(0xFF183F68).copy(alpha=.8f), style=Stroke(5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
        drawPath(bracket, Color(0xFFB7FFF0), style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
    }
}

/** Ball: round packed snow. Shard: faceted violet-blue snow chunk, with a dark silhouette. */
internal fun DrawScope.paintSnow304Hazard(center:Offset,radius:Float,rotation:Float,id:Int,fragment:Boolean) {
    val r=radius
    if(r<=0f) return
    drawOval(Color(0xFF183C66).copy(alpha=.38f),Offset(center.x-r*.88f,center.y+r*.57f),Size(r*1.95f,r*.63f))
    if(fragment) {
        rotate(rotation,center) {
            val vertices=(0..6).map { i ->
                val angle=i*PI*2/7
                val length=r*(.86f+((id+i*17)%5)*.032f)
                center+Offset(cos(angle).toFloat()*length,sin(angle).toFloat()*length)
            }
            val shape=Path().apply {moveTo(vertices[0].x,vertices[0].y);vertices.drop(1).forEach{lineTo(it.x,it.y)};close()}
            drawPath(shape,Brush.linearGradient(listOf(Color(0xFFF5ECFF),Color(0xFFC9D3FC),Color(0xFF82AADF)),center-Offset(r,r),center+Offset(r,r)))
            drawPath(shape,Color(0xFF3F5187),style=Stroke(1.65.dp.toPx(),join=StrokeJoin.Round))
            val facet=Path().apply {moveTo(vertices[1].x,vertices[1].y);lineTo(center.x-r*.06f,center.y+r*.12f);lineTo(vertices[4].x,vertices[4].y);close()}
            drawPath(facet,Color(0xFF688FC3).copy(alpha=.45f))
            drawLine(Color.White,center-Offset(r*.51f,r*.22f),center+Offset(r*.08f,-r*.48f),1.7.dp.toPx(),StrokeCap.Round)
            drawCircle(Color(0xFFF6F1FF),r*.085f,center+Offset(r*.22f,r*.16f))
        }
    } else {
        drawCircle(Brush.radialGradient(listOf(Color.White,Color(0xFFF4FBFF),Color(0xFFBAD9EE),Color(0xFF6094BE)),center-Offset(r*.3f,r*.36f),r*1.52f),r,center)
        drawCircle(Color(0xFF2F648E),r,center,style=Stroke(1.45.dp.toPx()))
        rotate(rotation,center) {
            // Texture remains within the body and rotates with it; never spawn fake dots around it.
            repeat(18) { i ->
                val angle=(i*137.5+id*17)*PI/180
                val distance=.18f+(i%4)*.17f
                val p=center+Offset(cos(angle).toFloat()*r*distance,sin(angle).toFloat()*r*distance)
                val pit=r*(.042f+(i%3)*.018f)
                drawCircle(Color(0xFF77A1C0).copy(alpha=.48f),pit,p)
                drawCircle(Color.White.copy(alpha=.87f),pit*.73f,p-Offset(pit*.2f,pit*.4f))
            }
            val seam=Path().apply {moveTo(center.x-r*.72f,center.y-r*.28f);quadraticTo(center.x-r*.17f,center.y+r*.48f,center.x+r*.62f,center.y+r*.31f)}
            drawPath(seam,Color(0xFF86B0CF).copy(alpha=.7f),style=Stroke(r*.042f,cap=StrokeCap.Round))
        }
        drawOval(Color.White.copy(alpha=.68f),Offset(center.x-r*.49f,center.y-r*.67f),Size(r*.60f,r*.32f))
    }
}
