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

    // 批量处理相关
    private var inputUris: List<Uri> = emptyList()
    private var currentIndex = -1
    private var outputUri: Uri? = null

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
            outputUri = null // 重新选输入文件夹后，清空输出
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
            points = emptyList()
            showCorrected = false
            status = "第 ${currentIndex + 1}/${inputUris.size} 张 | 按住拖动微调，松手选点"
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
                status = "校正完成，翻页时将自动保存"
            } catch (e: Exception) {
                status = "校正失败：${e.message}"
            }
        }
    }

    // 核心：翻页时自动保存
    private suspend fun autoSaveCurrent(): Boolean {
        val bmp = correctedBitmap ?: return true // 没有校正结果就不保存
        val dirUri = outputUri
        if (dirUri == null) {
            status = "未设置输出文件夹，无法自动保存"
            return false
        }
        return withContext(Dispatchers.IO) {
            BitmapUtils.saveToFolder(getApplication(), dirUri, bmp, "corrected_${currentIndex + 1}.jpg")
        }
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
            // 跳过时不自动保存
            correctedBitmap = null
            points = emptyList()
            showCorrected = false
            currentIndex++
            loadCurrentImage()
        }
    }

    fun saveCurrentManually() {
        val bmp = correctedBitmap ?: run { status = "没有可保存的结果"; return }
        viewModelScope.launch {
            val ok = autoSaveCurrent()
            status = if (ok) "手动保存成功" else "保存失败"
        }
    }
}
