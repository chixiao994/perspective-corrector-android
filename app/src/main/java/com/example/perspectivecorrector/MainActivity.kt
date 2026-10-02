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
    val pickInputDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.setInputFolder(it) } }

    val pickOutputDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.setOutputFolder(it) } }

    Column(Modifier.fillMaxSize()) {
        // ========== 顶部：输入/输出、重置/预览 ==========
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { pickInputDir.launch(null) },
                modifier = Modifier.weight(1f)
            ) { Text("输入") }

            Button(
                onClick = { pickOutputDir.launch(null) },
                modifier = Modifier.weight(1f)
            ) { Text("输出") }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = vm::reset,
                modifier = Modifier.weight(1f)
            ) { Text("重置") }

            Button(
                onClick = vm::preview,
                modifier = Modifier.weight(1f)
            ) { Text("预览") }
        }

        // ========== 中间：图像画布 ==========
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

        // ========== 底部：状态栏与翻页 ==========
        Text(
            text = vm.status,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            fontWeight = FontWeight.Medium
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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
    }
}
