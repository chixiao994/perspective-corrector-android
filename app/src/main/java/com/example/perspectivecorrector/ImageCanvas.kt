package com.example.perspectivecorrector

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun ImageCanvas(
    bitmap: Bitmap?,
    points: List<Offset>,
    showPoints: Boolean,
    onTap: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    // 记录当前手指在屏幕上的位置（用于放大镜和十字准星）
    var touchPos by remember { mutableStateOf<Offset?>(null) }

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
                // 1. 添加放大镜 Modifier
                .magnifier(
                    sourceCenter = { touchPos ?: Offset.Unspecified },
                    magnifierCenter = {
                        // 把放大镜放到手指上方 100dp 处，避免遮挡
                        touchPos?.let { Offset(it.x, it.y - 100.dp.toPx()) } ?: Offset.Unspecified
                    },
                    zoom = 2.5f,
                    size = androidx.compose.ui.unit.DpSize(140.dp, 140.dp)
                )
                .pointerInput(bitmap, imageRect) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (bitmap != null && imageRect?.contains(offset) == true) {
                                touchPos = offset
                            }
                        },
                        onDrag = { change, _ ->
                            touchPos = change.position
                        },
                        onDragEnd = {
                            touchPos?.let { pos ->
                                val r = imageRect
                                if (bitmap != null && r != null && r.contains(pos)) {
                                    val nx = ((pos.x - r.left) / r.width).coerceIn(0f, 1f)
                                    val ny = ((pos.y - r.top) / r.height).coerceIn(0f, 1f)
                                    onTap(Offset(nx, ny))
                                }
                            }
                            touchPos = null
                        },
                        onDragCancel = { touchPos = null }
                    )
                }
        ) {
            if (bitmap == null || imageRect == null) return@Canvas

            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(imageRect.left.toInt(), imageRect.top.toInt()),
                dstSize = IntSize(imageRect.width.toInt(), imageRect.height.toInt())
            )

            // 绘制已确定的点和连线
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
                screenPts.forEach { p ->
                    drawCircle(Color(0x66FF0000), radius = 28f, center = p)
                    drawCircle(Color.Red, radius = 16f, center = p)
                    drawCircle(Color.White, radius = 8f, center = p)
                }
            }

            // 2. 绘制十字准星
            touchPos?.let { pos ->
                val crosshairSize = 40f
                // 准星中心点
                drawCircle(Color.Red, radius = 6f, center = pos)
                drawCircle(Color.White, radius = 2f, center = pos)
                // 准星横竖线
                drawLine(Color.White, Offset(pos.x - crosshairSize, pos.y), Offset(pos.x + crosshairSize, pos.y), strokeWidth = 3f)
                drawLine(Color.White, Offset(pos.x, pos.y - crosshairSize), Offset(pos.x, pos.y + crosshairSize), strokeWidth = 3f)
                drawLine(Color.Black, Offset(pos.x - crosshairSize, pos.y), Offset(pos.x + crosshairSize, pos.y), strokeWidth = 1f)
                drawLine(Color.Black, Offset(pos.x, pos.y - crosshairSize), Offset(pos.x, pos.y + crosshairSize), strokeWidth = 1f)
            }
        }
    }
}
