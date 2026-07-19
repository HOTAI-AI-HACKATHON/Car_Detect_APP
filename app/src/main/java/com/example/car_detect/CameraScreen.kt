package com.example.car_detect

import android.content.Context
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isAligned by remember { mutableStateOf(false) }
    var showAlignmentDialog by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    // 相機綁定
    DisposableEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(context, "無法啟動相機: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            val f = ProcessCameraProvider.getInstance(context)
            f.addListener({ f.get().unbindAll() }, ContextCompat.getMainExecutor(context))
        }
    }

    // 拍照對齊確認 Dialog
    if (showAlignmentDialog) {
        AlertDialog(
            onDismissRequest = { showAlignmentDialog = false },
            title = { Text("⚠️ 對齊提醒") },
            text = {
                Text(
                    "車體尚未對齊輔助框，建議重新對齊後再拍攝。\n\n是否仍要拍攝？",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAlignmentDialog = false
                    takePhoto(context, imageCapture)
                }) {
                    Text("強制拍照", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlignmentDialog = false }) {
                    Text("重新對齊")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ===== 相機預覽 + 對齊框 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 相機預覽
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 車體對齊框 (使用匯入的圖片 car_beside_guide)
            CarGuideOverlayImage(isAligned = isAligned)

            // 提示文字
            HintBanner(
                isAligned = isAligned,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 對齊狀態開關（測試用）
            AlignmentToggle(
                isAligned = isAligned,
                onToggle = { isAligned = !isAligned },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp)
            )
        }

        // ===== 底部控制列 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp))

            // 拍照按鈕
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
                        if (!isAligned) showAlignmentDialog = true
                        else takePhoto(context, imageCapture)
                    },
                contentAlignment = Alignment.Center
            ) {}

            // 切換鏡頭
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_switch_camera),
                    contentDescription = "切換鏡頭",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ===== 載入 PNG 對齊框圖片並加色彩遮罩 =====
@Composable
fun CarGuideOverlayImage(isAligned: Boolean) {
    val tintColor = if (isAligned) Color(0xFF4CAF50) else Color(0xFFFFC107)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.car_beside_guide),
            contentDescription = "車體對齊引導框",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tintColor)
        )
    }
}

// ===== 提示文字欄 =====
@Composable
fun HintBanner(isAligned: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (isAligned) Color(0xFF2E7D32).copy(alpha = 0.8f)
    else Color(0xFFE65100).copy(alpha = 0.8f)
    val text = if (isAligned) "✅ 已對齊，可以拍照"
    else "請將車體對準輔助框線"

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

// ===== 對齊開關（測試用）=====
@Composable
fun AlignmentToggle(isAligned: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (isAligned) "已對齊" else "未對齊"
    val color = if (isAligned) Color(0xFF4CAF50) else Color(0xFFFF9800)

    Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(color.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

// ===== 拍照 =====
private fun takePhoto(context: Context, imageCapture: ImageCapture) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val photoFile = File(context.filesDir, "CAR_${timestamp}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Toast.makeText(context, "✅ 照片已儲存: ${photoFile.name}", Toast.LENGTH_SHORT).show()
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "拍照失敗: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
