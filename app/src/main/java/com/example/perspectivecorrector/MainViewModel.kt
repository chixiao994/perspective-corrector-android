package com.example.perspectivecorrector

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var originalBitmap by mutableStateOf<Bitmap?>(null); private set
    var correctedBitmap by mutableStateOf<Bitmap?>(null); private set
    var points by mutableStateOf<List<Offset>>(emptyList()); private set
    var showCorrected by mutableStateOf(false); private set
    var status by mutableStateOf("请选择图片"); private set

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            status = "正在读取图片…"
            val bmp = withContext(Dispatchers.IO) {
                BitmapUtils.decodeBitmap(getApplication(), uri)
            }
            if (bmp != null) {
                originalBitmap = bmp
                correctedBitmap = null
                points = emptyList()
                showCorrected = false
                // 修改了这里的状态提示文字
                status = "按住屏幕拖动微调，松手确认选点（依序点击四个角）"
            } else {
                status = "读取图片失败"
            }
        }
    }

    fun addPoint(p: Offset) {
        if (showCorrected) return
        if (points.size >= 4) {
            status = "已选满 4 个点，可点“重置”重选"
            return
        }
        points = points + p
        status = when (points.size) {
            4 -> "四个角点已选定，点击“校正”"
            else -> "已选 ${points.size}/4 个点"
        }
    }

    fun reset() {
        points = emptyList()
        correctedBitmap = null
        showCorrected = false
        status = "已重置，请重新选点"
    }

    fun toggleView() {
        if (correctedBitmap == null) return
        showCorrected = !showCorrected
        status = if (showCorrected) "正在显示校正结果" else "已切回原图"
    }

    fun correct() {
        val src = originalBitmap ?: run { status = "请先选择图片"; return }
        if (points.size != 4) { status = "请先选满 4 个角点"; return }
        viewModelScope.launch {
            status = "正在校正…"
            try {
                val out = withContext(Dispatchers.Default) {
                    PerspectiveCorrector.correct(src, points)
                }
                correctedBitmap = out
                showCorrected = true
                status = "校正完成，点击“保存”写入相册"
            } catch (e: Exception) {
                status = "校正失败：${e.message}"
            }
        }
    }

    fun save() {
        val bmp = correctedBitmap ?: run { status = "没有可保存的结果"; return }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BitmapUtils.saveToGallery(getApplication(), bmp)
            }
            status = if (ok) "已保存到相册 Pictures/PerspectiveCorrector" else "保存失败"
        }
    }
}
