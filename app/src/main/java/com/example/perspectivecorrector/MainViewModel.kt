package com.example.perspectivecorrector

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.documentfile.provider.DocumentFile
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
    var status by mutableStateOf("请选择输入文件夹"); private set

    private var inputUris: List<Uri> = emptyList()
    private var currentIndex = -1
    private var outputUri: Uri? = null

    // 保存每张图的标记点，key 为索引
    private val savedPoints = mutableMapOf<Int, List<Offset>>()
    // 记录已经保存过校正结果的图片索引，避免重复保存
    private val savedIndices = mutableSetOf<Int>()

    fun setInputFolder(uri: Uri) {
        viewModelScope.launch {
            status = "正在扫描图片…"
            val files = withContext(Dispatchers.IO) {
                val dir = DocumentFile.fromTreeUri(getApplication(), uri)
                dir?.listFiles()
                    ?.filter { it.type?.startsWith("image/") == true }
                    ?.map { it.uri }
                    ?.sortedBy { it.toString() }
            }
            if (files.isNullOrEmpty()) {
                status = "该文件夹内没有找到图片"
                return@launch
            }
            inputUris = files
            currentIndex = 0
            outputUri = null
            savedPoints.clear()
            savedIndices.clear()   // 换文件夹时清空保存记录
            loadCurrentImage()
        }
    }

    fun setOutputFolder(uri: Uri) {
        outputUri = uri
        status = "输出文件夹已设置，共 ${inputUris.size} 张图片"
    }

    private suspend fun loadCurrentImage() {
        if (currentIndex !in inputUris.indices) return
        val uri = inputUris[currentIndex]
        status = "正在读取第 ${currentIndex + 1} 张…"
        val bmp = withContext(Dispatchers.IO) {
            BitmapUtils.decodeBitmap(getApplication(), uri)
        }

        if (bmp != null) {
            originalBitmap = bmp
            correctedBitmap = null
            showCorrected = false

            // 恢复该张图片的历史选点
            val restoredPoints = savedPoints[currentIndex] ?: emptyList()
            points = restoredPoints

            if (restoredPoints.size == 4) {
                status = "第 ${currentIndex + 1}/${inputUris.size} 张 | 已恢复标记点，点击“预览”查看效果"
            } else {
                status = "第 ${currentIndex + 1}/${inputUris.size} 张 | 已选 ${restoredPoints.size}/4 个点"
            }
        } else {
            status = "第 ${currentIndex + 1}/${inputUris.size} 张 | 读取失败"
        }
    }

    fun addPoint(p: Offset) {
        if (showCorrected) return
        if (points.size >= 4) {
            status = "已选满 4 个点，可点“重置”重选"
            return
        }
        points = points + p
        // 选点变了，之前的预览和保存记录失效
        correctedBitmap = null
        showCorrected = false
        savedPoints[currentIndex] = points
        savedIndices.remove(currentIndex)   // 标记需要重新保存

        status = when (points.size) {
            4 -> "四个角点已选定，点击“预览”查看效果"
            else -> "已选 ${points.size}/4 个点"
        }
    }

    fun reset() {
        points = emptyList()
        correctedBitmap = null
        showCorrected = false
        savedPoints[currentIndex] = emptyList()
        savedIndices.remove(currentIndex)   // 选点清空，需要重新保存

        status = "已重置，请重新选点"
    }

    fun preview() {
        if (showCorrected) {
            showCorrected = false
            status = "已切回原图，可重新选点或再次预览"
            return
        }

        val src = originalBitmap ?: run { status = "请先选择图片"; return }
        if (points.size != 4) {
            status = "请先选满 4 个角点，再点击预览"
            return
        }

        if (correctedBitmap == null) {
            viewModelScope.launch {
                status = "正在生成预览…"
                try {
                    val out = withContext(Dispatchers.Default) {
                        PerspectiveCorrector.correct(src, points)
                    }
                    correctedBitmap = out
                    showCorrected = true
                    status = "预览校正结果，翻页时将自动保存"
                } catch (e: Exception) {
                    status = "生成预览失败：${e.message}"
                }
            }
        } else {
            showCorrected = true
            status = "正在显示校正结果，翻页时将自动保存"
        }
    }

    private suspend fun autoSaveCurrent(): Boolean {
        val bmp = correctedBitmap ?: return true
        val dirUri = outputUri ?: return false

        // 已经保存过且选点未修改，跳过保存
        if (currentIndex in savedIndices) {
            return true
        }

        val ok = withContext(Dispatchers.IO) {
            BitmapUtils.saveToFolder(getApplication(), dirUri, bmp, "corrected_${currentIndex + 1}.jpg")
        }
        if (ok) {
            savedIndices.add(currentIndex)   // 标记为已保存
        }
        return ok
    }

    fun nextImage() {
        if (currentIndex >= inputUris.size - 1) {
            status = "已经是最后一张了"
            return
        }
        viewModelScope.launch {
            autoSaveCurrent()
            currentIndex++
            loadCurrentImage()
        }
    }

    fun prevImage() {
        if (currentIndex <= 0) {
            status = "已经是第一张了"
            return
        }
        viewModelScope.launch {
            autoSaveCurrent()
            currentIndex--
            loadCurrentImage()
        }
    }

    fun skipImage() {
        if (currentIndex >= inputUris.size - 1) {
            status = "已经是最后一张了"
            return
        }
        viewModelScope.launch {
            currentIndex++
            loadCurrentImage()
        }
    }
}
