package com.example.car_detect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScratchInspectionScreen(
    onBackClick: () -> Unit,
    onCompleteClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 已拍攝的傷痕照片列表
    val scratchPhotos = remember { mutableStateListOf<ImageBitmap>() }
    var qualityWarningBannerText by remember { mutableStateOf<String?>(null) }
    var displayedBannerText by remember { mutableStateOf("") }

    // 當品質警示條跳出後，記錄欲顯示文字並於 2 秒後自動觸發淡出漸隱消失
    LaunchedEffect(qualityWarningBannerText) {
        if (qualityWarningBannerText != null) {
            displayedBannerText = qualityWarningBannerText!!
            delay(2000)
            qualityWarningBannerText = null
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    // 相機綁定
    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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

    // 拍攝傷痕照片
    val takeScratchPhoto = {
        qualityWarningBannerText = null
        takeScratchPhotoFile(
            context = context,
            imageCapture = imageCapture,
            onPhotoCaptured = { bitmap ->
                scratchPhotos.add(bitmap)
            },
            onQualityRejected = { errorMsg ->
                qualityWarningBannerText = when {
                    errorMsg.contains("模糊") -> "畫面過於模糊，請重新拍攝"
                    errorMsg.contains("暗") -> "畫面過暗，請重新拍攝"
                    errorMsg.contains("過曝") || errorMsg.contains("反光") -> "畫面過曝，請重新拍攝"
                    else -> "畫面不清晰，請重新拍攝"
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F7))
    ) {
        // 1. 頂部列 (純白背景延伸涵蓋狀態欄)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "返回",
                            tint = Color(0xFF222222),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = "額外傷痕拍照",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    IconButton(
                        onClick = {
                            Toast.makeText(context, "聯繫客服", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_headset),
                            contentDescription = "客服",
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E5E8), thickness = 1.dp)
            }
        }

        // 2. 主標題區塊
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "車身傷痕與舊傷特寫",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "如車身有顯著刮痕、凹陷或掉漆，請近距離特寫拍攝",
                fontSize = 12.sp,
                color = Color(0xFF777777)
            )
        }

        // 3. 相機視窗 + 特寫對焦準星框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(1.05f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 特寫準星對焦輔助線
            ScratchFocusReticleOverlay()

            // Figma 畫面過於模糊 / 過暗 / 過曝 紅色膠囊警示條 (浮動於相機視窗正中央，跳出後逐漸淡出消失)
            androidx.compose.animation.AnimatedVisibility(
                visible = (qualityWarningBannerText != null),
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(700)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(80.dp))
                        .background(Color(0xFFD7000F))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = displayedBannerText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 視窗下方提示條
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = "請將鏡頭對準車身刮痕或凹陷特寫拍攝",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. 已拍攝傷痕照片橫向列表
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已拍攝傷痕照片",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF222222)
            )
            Text(
                text = "${scratchPhotos.size} 張照片",
                fontSize = 12.sp,
                color = Color(0xFF888888)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (scratchPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEAEAEE)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "尚未拍攝傷痕特寫 (若車況良好無舊傷，可直接完成)",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(scratchPhotos) { index, photoBitmap ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        Image(
                            bitmap = photoBitmap,
                            contentDescription = "傷痕照片 $index",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // 右上角刪除按鈕 ✕
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable {
                                    scratchPhotos.removeAt(index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 5. 底部快門按鈕區 + 右側完成膠囊按鈕 (完全同步 CameraScreen 美編)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            // 大圓形快門按鈕 (居中，同步 CameraScreen 極簡白圈)
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .border(3.5.dp, Color(0xFFE0E0E0), CircleShape)
                    .padding(3.5.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { takeScratchPhoto() }
            )

            // 右下角「完成拍攝」紅色膠囊按鈕 (Figma: #D7000F)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(80.dp))
                    .background(Color(0xFFD7000F))
                    .clickable { onCompleteClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (scratchPhotos.isEmpty()) "完成 ➔" else "完成 (${scratchPhotos.size}張) ➔",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ===== 特寫傷痕對焦準星圖層 =====
@Composable
fun ScratchFocusReticleOverlay() {
    val reticleColor = Color(0xFFFFC107) // 黃色對焦框
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2f.dp.toPx(), pathEffect = dashEffect)

        // 中央方形特寫對焦框
        val boxSize = w * 0.40f
        val boxLeft = (w - boxSize) / 2f
        val boxTop = (h - boxSize) / 2f

        drawRoundRect(
            color = reticleColor,
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(8f, 8f),
            style = stroke
        )

        // 中央十字符號
        val cx = w / 2f
        val cy = h / 2f
        val arm = 14.dp.toPx()
        drawLine(reticleColor, Offset(cx - arm, cy), Offset(cx + arm, cy), 2f.dp.toPx())
        drawLine(reticleColor, Offset(cx, cy - arm), Offset(cx, cy + arm), 2f.dp.toPx())
    }
}

// ===== 拍照與 EXIF 解碼 =====
private fun takeScratchPhotoFile(
    context: Context,
    imageCapture: ImageCapture,
    onPhotoCaptured: (ImageBitmap) -> Unit,
    onQualityRejected: (String) -> Unit
) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val photoFile = File(context.filesDir, "SCRATCH_${timestamp}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val originalBitmap = loadScratchOrientedBitmap(photoFile.absolutePath) ?: return

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val result = CarDamageApiService.uploadAndPredict(photoFile)
                    when (result.status) {
                        "success" -> {
                            val finalBitmap = result.annotatedBitmap ?: originalBitmap.asImageBitmap()
                            onPhotoCaptured(finalBitmap)
                            val msg = if (result.damageCount > 0) {
                                "✅ 傷痕辨識成功！發現 ${result.damageCount} 處車損"
                            } else {
                                "✅ 已新增傷痕特寫照片"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                        "rejected" -> {
                            // 品質檢測退回 (畫面過於模糊、過暗、過曝)，攔截不加入相簿，觸發 Figma 彈窗要求重新拍攝
                            onQualityRejected(result.message)
                        }
                        else -> {
                            onPhotoCaptured(originalBitmap.asImageBitmap())
                            Toast.makeText(context, "✅ 已新增傷痕照片 (API 未連線)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "拍照失敗: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

private fun loadScratchOrientedBitmap(filePath: String): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
    return try {
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        var degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f && bitmap.width > bitmap.height) {
            degrees = 90f
        }
        if (degrees != 0f) {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        bitmap
    }
}
