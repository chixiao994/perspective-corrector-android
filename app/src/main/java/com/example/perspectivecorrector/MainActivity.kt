package com.example.perspectivecorrector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { PerspectiveScreen() }
            }
        }
    }
}

@Composable
fun PerspectiveScreen(vm: MainViewModel = viewModel()) {
    // 选择输入文件夹
    val pickInputDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 申请持久化权限
            vm.setInputFolder(it)
        }
    }

    // 选择输出文件夹
    val pickOutputDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { vm.setOutputFolder(it) }
    }

    Column(Modifier.fillMaxSize()) {
        // 第一行：文件夹选择
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { pickInputDir.launch(null) },
                modifier = Modifier.weight(1f)
            ) { Text("输入文件夹") }

            Button(
                onClick = { pickOutputDir.launch(null) },
                modifier = Modifier.weight(1f)
            ) { Text("输出文件夹") }
        }

        // 第二行：图片操作
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = vm::correct,
                modifier = Modifier.weight(1f)
            ) { Text("校正") }

            Button(
                onClick = vm::saveCurrentManually,
                modifier = Modifier.weight(1f)
            ) { Text("保存") }

            OutlinedButton(
                onClick = vm::reset,
                modifier = Modifier.weight(1f)
            ) { Text("重置") }

            OutlinedButton(
                onClick = vm::toggleView,
                modifier = Modifier.weight(1f)
            ) { Text("切换") }
        }

        // 第三行：翻页与跳过
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = vm::prevImage,
                modifier = Modifier.weight(1f)
            ) { Text("◀ 上一张") }

            OutlinedButton(
                onClick = vm::skipImage,
                modifier = Modifier.weight(1f)
            ) { Text("跳过 ▶") }

            Button(
                onClick = vm::nextImage,
                modifier = Modifier.weight(1f)
            ) { Text("下一张 ▶") }
        }

        // 图像区域
        ImageCanvas(
            bitmap = if (vm.showCorrected) vm.correctedBitmap else vm.originalBitmap,
            points = vm.points,
            showPoints = !vm.showCorrected,
            onTap = vm::addPoint,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF2B2B2B))
        )

        // 状态栏
        Text(
            text = vm.status,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            fontWeight = FontWeight.Medium
        )
    }
}
