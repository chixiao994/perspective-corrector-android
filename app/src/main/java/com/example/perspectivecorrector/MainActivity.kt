package com.example.perspectivecorrector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(vm::loadImage) }

    Column(Modifier.fillMaxSize()) {
        // 顶部工具栏
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("打开") }

            Button(
                onClick = vm::correct,
                modifier = Modifier.weight(1f)
            ) { Text("校正") }

            Button(
                onClick = vm::save,
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
