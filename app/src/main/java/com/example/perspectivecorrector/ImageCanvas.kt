package com.example.perspectivecorrector

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

@Composable
fun ImageCanvas(
    bitmap: Bitmap?,
    points: List<Offset>,           // 归一化坐标 0~1
    showPoints: Boolean,
    onTap: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()

        val imageRect: Rect? = remember(bitmap, cw, ch) {
            bitmap?.let {
                val s = min(cw / it.width, ch / it.height)
                val dw = it.width * s; val dh = it.height * s
                Rect((cw - dw) / 2f, (ch - dh) / 2f, (cw + dw) / 2f, (ch + dh) / 2f)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, imageRect) {
                    detectTapGestures { offset ->
                        val r = imageRect ?: return@detectTapGestures
                        if (bitmap == null) return@detectTapGestures
                        if (offset.x !in r.left..r.right || offset.y !in r.top..r.bottom)
                            return@detectTapGestures
                        val nx = ((offset.x - r.left) / r.width).coerceIn(0f, 1f)
                        val ny = ((offset.y - r.top) / r.height).coerceIn(0f, 1f)
                        onTap(Offset(nx, ny))
                    }
                }
        ) {
            if (bitmap == null || imageRect == null) return@Canvas

            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(imageRect.left.toInt(), imageRect.top.toInt()),
                dstSize = IntSize(imageRect.width.toInt(), imageRect.height.toInt())
            )

            if (showPoints && points.isNotEmpty()) {
                val screenPts = points.map {
                    Offset(
                        imageRect.left + it.x * imageRect.width,
                        imageRect.top + it.y * imageRect.height
                    )
                }
                if (screenPts.size == 4) {
                    val path = Path().apply {
                        moveTo(screenPts[0].x, screenPts[0].y)
                        for (i in 1..3) lineTo(screenPts[i].x, screenPts[i].y)
                        close()
                    }
                    drawPath(path, Color(0xFF00E676), style = Stroke(width = 5f))
                }
                // 手指优化：大热区 + 白圈内芯
                screenPts.forEachIndexed { i, p ->
                    drawCircle(Color(0x66FF0000), radius = 28f, center = p)
                    drawCircle(Color.Red, radius = 16f, center = p)
                    drawCircle(Color.White, radius = 8f, center = p)
                }
            }
        }
    }
}
