package com.yamone.games.arcadecore

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.*

enum class ScenicWorld { ICE, SNOW, OCEAN }

/** Decorative scenery is drawn in a separate layer and NEVER participates in collision. */
@Composable
fun ArcadeBackdrop(world: ScenicWorld, modifier: Modifier = Modifier) {
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(world) {
        var previous = 0L
        var accumulated = 0L
        while (isActive) withFrameNanos { now ->
            if (previous != 0L && GameFeedback.canAdvance && !GameFeedback.options.reduceMotion) {
                accumulated += (now - previous).coerceAtMost(100_000_000L)
                if (accumulated >= 32_000_000L) {
                    seconds = (seconds + accumulated / 1_000_000_000f) % 3600f
                    accumulated = 0L
                }
            }
            previous = now
        }
    }
    Canvas(modifier) { paintWorld(world, if (GameFeedback.options.reduceMotion) 0f else seconds) }
}

private fun DrawScope.polygon(points: List<Offset>, color: Color) {
    if (points.isEmpty()) return
    drawPath(Path().apply { moveTo(points[0].x,points[0].y); points.drop(1).forEach { lineTo(it.x,it.y) }; close() },color)
}

fun DrawScope.paintWorld(world: ScenicWorld, time: Float = 0f) {
    val w = size.width; val h = size.height
    if (world == ScenicWorld.OCEAN) { paintOcean(time); return }
    val snow = world == ScenicWorld.SNOW
    drawRect(Brush.verticalGradient(if(snow) listOf(Color(0xFF415F8F),Color(0xFF91BCD5),Color(0xFF80B3CD),Color(0xFF6999B4))
        else listOf(Color(0xFF7E97C7),Color(0xFFB4DFF1),Color(0xFFC8EAF6),Color(0xFF79BDD8))))
    // Aurora ribbons are confined to the sky, leaving the falling-object corridor clear.
    for (i in 0..2) {
        val ribbon = Path().apply {
            moveTo(-w*.1f,h*(.06f+i*.021f))
            cubicTo(w*.24f,h*(.20f+i*.022f+sin(time*.22f)*.008f),w*.63f,-h*.04f,w*1.1f,h*(.065f+i*.034f))
            lineTo(w*1.1f,h*(.092f+i*.035f))
            cubicTo(w*.67f,h*.035f,w*.21f,h*(.25f+i*.021f),-w*.1f,h*(.093f+i*.025f));close()
        }
        drawPath(ribbon,Brush.horizontalGradient(listOf(Color(0xFF7FF2DF).copy(alpha=.24f),Color(0xFFF2C0E9).copy(alpha=.38f),Color(0xFF90DDEF).copy(alpha=.06f))))
    }
    drawCircle(Color(0xFFFFFAE6).copy(alpha=.72f),w*.033f,Offset(w*.82f,h*.066f))
    repeat(12) { i -> drawCircle(Color.White.copy(alpha=.45f),1.1.dp.toPx(),Offset(w*((i*37+9)%96)/100f,h*(.013f+((i*7)%11)/100f))) }
    // Layered distant peaks, with broad smooth snow caps.
    repeat(5) { i ->
        val x=w*(-.1f+i*.27f); val peak=h*(.16f+(i%3)*.025f); val base=h*.37f
        polygon(listOf(Offset(x-w*.2f,base),Offset(x,peak),Offset(x+w*.23f,base)),if(i%2==0) Color(0xFF94B6D0) else Color(0xFFA9CBDD))
        polygon(listOf(Offset(x,peak),Offset(x-w*.085f,peak+h*.087f),Offset(x-w*.015f,peak+h*.071f),Offset(x+w*.032f,peak+h*.094f),Offset(x+w*.096f,peak+h*.087f)),Color(0xFFEAF8FD))
        polygon(listOf(Offset(x,peak),Offset(x+w*.03f,peak+h*.095f),Offset(x+w*.23f,base)),Color(0xFF6FA3C2).copy(alpha=.32f))
    }
    if (snow) {
        val slope=Path().apply { moveTo(w*.20f,h*.29f);cubicTo(w*.31f,h*.22f,w*.68f,h*.27f,w*.83f,h*.31f);lineTo(w*1.08f,h);lineTo(-w*.08f,h);close() }
        drawPath(slope,Brush.verticalGradient(listOf(Color(0xFFA6CCDE),Color(0xFF80AEC5),Color(0xFF6C9BB6)),startY=h*.28f,endY=h))
        for (side in listOf(0,1)) {
            repeat(7) { i ->
                val f=i/6f; val x=if(side==0) w*(.12f-.16f*f) else w*(.88f+.16f*f)
                val y=h*(.33f+.093f*i); val r=w*(.032f+.011f*i)
                pine(x,y,r,Color(0xFF2E788B).copy(alpha=.8f))
            }
        }
        repeat(4) { i ->
            val line=Path().apply { moveTo(w*(.35f+i*.1f),h*.33f);cubicTo(w*(.22f+i*.18f),h*.56f,w*(.3f+i*.19f),h*.79f,w*(.17f+i*.23f),h*1.02f) }
            drawPath(line,Color.White.copy(alpha=.08f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        }
    } else {
        repeat(6) { i ->
            val y=h*(.4f+i*.107f)
            drawOval(Color.White.copy(alpha=.15f),Offset(-w*.2f,y),Size(w*1.5f,h*.045f))
        }
        repeat(5) { i ->
            val x=if(i%2==0) -w*.09f else w*.88f; val y=h*(.39f+i*.13f)
            paintIcePlatform(x,y,w*.24f,h*.055f,i,alpha=.42f)
        }
    }
}

private fun DrawScope.pine(x:Float,y:Float,r:Float,color:Color) {
    drawLine(color,x.let { Offset(it,y) },Offset(x,y+r*.9f),r*.13f,StrokeCap.Round)
    repeat(3) { i ->
        val d=r*(.5f+i*.26f); val yy=y-r*(1.75f-i*.52f)
        polygon(listOf(Offset(x,yy-d),Offset(x-d,yy+d*.52f),Offset(x+d,yy+d*.52f)),color)
        polygon(listOf(Offset(x,yy-d),Offset(x-d*.7f,yy+d*.02f),Offset(x,yy-d*.1f),Offset(x+d*.64f,yy+d*.04f)),Color(0xFFD5EDF5))
    }
}

private fun DrawScope.paintOcean(time:Float) {
    val w=size.width; val h=size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF54CDCF),Color(0xFF2997B6),Color(0xFF1E709D),Color(0xFF173F70))))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFC4FFF2).copy(alpha=.47f),Color.Transparent),Offset(w*.32f,-h*.04f),w*.85f),w*.85f,Offset(w*.32f,-h*.04f))
    repeat(5) { i ->
        val x=w*(.06f+i*.20f); val sway=sin(time*.18f+i)*w*.035f
        val ray=Path().apply {moveTo(x,-h*.02f);lineTo(x+w*.065f,-h*.02f);lineTo(x+w*.43f+sway,h*.88f);lineTo(x+w*.20f+sway,h*.88f);close()}
        drawPath(ray,Brush.verticalGradient(listOf(Color(0xFFD9FFF5).copy(alpha=.17f),Color.Transparent)))
    }
    repeat(8) { i ->
        val phase=time*.35f+i*.9f
        val top=h*(.08f+i*.108f)
        val curve=Path().apply {moveTo(-w*.1f,top);cubicTo(w*.2f,top+sin(phase)*h*.014f,w*.6f,top-h*.028f,w*1.1f,top+h*.014f)}
        drawPath(curve,Color(0xFFC0FAEB).copy(alpha=.055f),style=Stroke(4.dp.toPx(),cap=StrokeCap.Round))
    }
    // Depth silhouettes stay behind the game; no fake collectible fish in the play corridor.
    for(side in listOf(0,1)) {
        val x=if(side==0) -w*.07f else w*.91f
        drawOval(Color(0xFF255B86),Offset(x,h*.76f),Size(w*.20f,h*.28f))
        repeat(5) { i ->
            val xx=if(side==0) w*(.018f+i*.025f) else w*(.982f-i*.025f)
            val height=h*(.13f+(i%3)*.047f)
            val seaweed=Path().apply {moveTo(xx,h*1.01f);cubicTo(xx-w*.04f,h-height*.3f,xx+w*.05f,h-height*.69f,xx+sin(time*.9f+i)*w*.02f,h-height)}
            drawPath(seaweed,if(i%2==0)Color(0xFF40AEAB)else Color(0xFF61C6B9),style=Stroke(w*.018f,cap=StrokeCap.Round))
        }
        coral(if(side==0) w*.09f else w*.9f,h*.985f,w*.19f,if(side==0)Color(0xFFF08EB6)else Color(0xFFF7B28D),side)
    }
    val sand=Path().apply {moveTo(0f,h*.975f);cubicTo(w*.28f,h*.934f,w*.55f,h*1.01f,w,h*.96f);lineTo(w,h);lineTo(0f,h);close()}
    drawPath(sand,Color(0xFF658DA5))
    repeat(12) { i ->
        val side=if(i%2==0) .055f else .91f
        val y=(1.08f-((time*.028f+i*.087f)%1.18f))*h
        val x=(side+sin(time*.5f+i)*.028f)*w
        val r=(.006f+(i%3)*.004f)*w
        drawCircle(Color(0xFFE2FFFF).copy(alpha=.34f),r,Offset(x,y),style=Stroke(1.1.dp.toPx()))
        drawCircle(Color.White.copy(alpha=.55f),r*.17f,Offset(x-r*.36f,y-r*.43f))
    }
}

private fun DrawScope.coral(x:Float,y:Float,r:Float,c:Color,side:Int) {
    drawLine(c,Offset(x,y),Offset(x,y-r*.7f),r*.10f,StrokeCap.Round)
    repeat(5) { i ->
        val sign=if((i+side)%2==0)1 else -1
        val start=Offset(x,y-r*i*.13f)
        val end=Offset(x+sign*r*(.21f+i*.062f),y-r*(.4f+i*.15f))
        val path=Path().apply {moveTo(start.x,start.y);quadraticTo(end.x,start.y,end.x,end.y)}
        drawPath(path,c,style=Stroke(r*.065f,cap=StrokeCap.Round))
        drawLine(c,Offset(end.x,end.y+r*.12f),Offset(end.x+sign*r*.13f,end.y-r*.035f),r*.045f,StrokeCap.Round)
    }
}

/** Snow-covered top aligned with the real landing line y; crystalline depth extends below it. */
fun DrawScope.paintIcePlatform(x:Float,y:Float,width:Float,depth:Float,variant:Int,alpha:Float=1f) {
    val d=depth; val w=width
    drawOval(Color(0xFF2C6287).copy(alpha=.15f*alpha),Offset(x+w*.05f,y+d*.78f),Size(w*.91f,d*.28f))
    val body=Path().apply {moveTo(x+w*.025f,y+d*.17f);lineTo(x+w*.975f,y+d*.17f);lineTo(x+w*.91f,y+d*.73f);lineTo(x+w*.73f,y+d*.79f);lineTo(x+w*.64f,y+d*.94f);lineTo(x+w*.46f,y+d*.75f);lineTo(x+w*.27f,y+d*.88f);lineTo(x+w*.07f,y+d*.65f);close()}
    drawPath(body,Brush.verticalGradient(listOf(Color(0xFFABF4FA).copy(alpha=alpha),Color(0xFF5DBFD9).copy(alpha=alpha),Color(0xFF4F90C4).copy(alpha=alpha)),y,y+d))
    polygon(listOf(Offset(x+w*.08f,y+d*.28f),Offset(x+w*.31f,y+d*.39f),Offset(x+w*.27f,y+d*.86f)),Color(0xFFC4FFFF).copy(alpha=.62f*alpha))
    polygon(listOf(Offset(x+w*.33f,y+d*.27f),Offset(x+w*.66f,y+d*.26f),Offset(x+w*.48f,y+d*.77f)),Color(0xFFEAFFFF).copy(alpha=.38f*alpha))
    polygon(listOf(Offset(x+w*.72f,y+d*.24f),Offset(x+w*.9f,y+d*.22f),Offset(x+w*.75f,y+d*.79f)),Color(0xFF3B8AB8).copy(alpha=.30f*alpha))
    val top=Path().apply {
        moveTo(x+w*.05f,y+d*.025f);quadraticTo(x+w*.20f,y-d*.04f,x+w*.35f,y+d*.015f)
        quadraticTo(x+w*.58f,y-d*.04f,x+w*.80f,y+d*.025f);quadraticTo(x+w*.99f,y-d*.01f,x+w*.99f,y+d*.16f)
        quadraticTo(x+w*.97f,y+d*.31f,x+w*.87f,y+d*.27f);quadraticTo(x+w*.78f,y+d*.39f,x+w*.70f,y+d*.26f)
        quadraticTo(x+w*.60f,y+d*.40f,x+w*.49f,y+d*.27f);quadraticTo(x+w*.35f,y+d*.43f,x+w*.25f,y+d*.27f)
        quadraticTo(x+w*.02f,y+d*.38f,x+w*.01f,y+d*.15f);quadraticTo(x,y+d*.035f,x+w*.05f,y+d*.025f);close()
    }
    drawPath(top,Color(0xFFF6FFFF).copy(alpha=alpha))
    drawLine(Color.White.copy(alpha=.82f*alpha),Offset(x+w*.18f,y+d*.12f),Offset(x+w*.49f,y+d*.1f),1.5.dp.toPx(),StrokeCap.Round)
    val crackX=x+w*(.38f+(variant%3)*.15f)
    val crack=Path().apply {moveTo(crackX,y+d*.36f);lineTo(crackX+w*.035f,y+d*.54f);lineTo(crackX+w*.009f,y+d*.7f)}
    drawPath(crack,Color(0xFFE5FFFF).copy(alpha=.72f*alpha),style=Stroke(1.2.dp.toPx(),cap=StrokeCap.Round))
}

/** A solid, shaded snow object. No decorative flakes; all objects using this glyph are hazards. */
fun DrawScope.paintSnowHazard(center:Offset,radius:Float,rotation:Float,id:Int,fragment:Boolean) {
    val r=radius
    drawOval(Color(0xFF234E78).copy(alpha=.23f),Offset(center.x-r*.83f,center.y+r*.52f),Size(r*1.82f,r*.55f))
    if(fragment) {
        rotate(rotation,center) {
            val points=(0..8).map { i ->
                val angle=i/9.0*PI*2; val rr=r*(.85f+((id+i*13)%5)*.032f)
                Offset(center.x+cos(angle).toFloat()*rr,center.y+sin(angle).toFloat()*rr)
            }
            val shape=Path().apply {moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)};close()}
            drawPath(shape,Brush.linearGradient(listOf(Color.White,Color(0xFFD6EFF9),Color(0xFF86BADA)),center-Offset(r,r),center+Offset(r,r)))
            drawPath(shape,Color(0xFF557FA4).copy(alpha=.85f),style=Stroke(1.1.dp.toPx()))
            drawLine(Color.White,center-Offset(r*.45f,r*.27f),center+Offset(r*.20f,-r*.37f),2.dp.toPx(),StrokeCap.Round)
            drawCircle(Color(0xFF91B9D4),r*.12f,center+Offset(r*.29f,r*.33f))
        }
    } else {
        drawCircle(Brush.radialGradient(listOf(Color.White,Color(0xFFF2FCFF),Color(0xFFC7E2F0),Color(0xFF73A7C8)),center-Offset(r*.33f,r*.36f),r*1.54f),r,center)
        drawCircle(Color(0xFF517DA0).copy(alpha=.55f),r,center,style=Stroke(1.0.dp.toPx()))
        rotate(rotation,center) {
            repeat(17) { i ->
                val angle=(i*137.5+id*17)*PI/180; val ring=.2f+(i%4)*.17f
                val p=center+Offset(cos(angle).toFloat()*r*ring,sin(angle).toFloat()*r*ring)
                val pit=r*(.035f+(i%3)*.017f)
                drawCircle(Color(0xFF83AFC8).copy(alpha=.42f),pit,p)
                drawCircle(Color.White.copy(alpha=.83f),pit*.77f,p-Offset(pit*.2f,pit*.43f))
            }
            val seam=Path().apply {moveTo(center.x-r*.71f,center.y-r*.3f);quadraticTo(center.x-r*.18f,center.y+r*.47f,center.x+r*.65f,center.y+r*.29f)}
            drawPath(seam,Color(0xFFA0C5DC).copy(alpha=.45f),style=Stroke(r*.045f,cap=StrokeCap.Round))
        }
        drawOval(Color.White.copy(alpha=.77f),Offset(center.x-r*.48f,center.y-r*.67f),Size(r*.59f,r*.32f))
    }
}
