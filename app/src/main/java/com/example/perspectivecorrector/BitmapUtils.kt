package com.example.perspectivecorrector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import kotlin.math.max

object BitmapUtils {

    fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width; val h = info.size.height
                    val maxDim = 2400
                    if (max(w, h) > maxDim) {
                        val s = maxDim.toFloat() / max(w, h)
                        decoder.setTargetSize((w * s).toInt(), (h * s).toInt())
                    }
                }.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                var sample = 1
                val maxDim = 2400
                while (max(opts.outWidth, opts.outHeight) / sample > maxDim) sample *= 2
                val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts2)
                }
            }
        } catch (e: Exception) { null }
    }

    // 新增：保存到指定的文件夹（SAF 方式）
    fun saveToFolder(context: Context, folderUri: Uri, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val dir = DocumentFile.fromTreeUri(context, folderUri) ?: return false
            // 如果同名文件已存在，先删除
            dir.findFile(fileName)?.delete()
            val file = dir.createFile("image/jpeg", fileName) ?: return false
            context.contentResolver.openOutputStream(file.uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
