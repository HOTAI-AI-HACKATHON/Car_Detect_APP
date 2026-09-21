package com.example.car_detect

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// ===== 拍攝角度階段 =====
enum class InspectionAngle(val label: String, val imageResId: Int) {
    RIGHT_FRONT("右前方", R.drawable.car_angle_fr),
    LEFT_FRONT("左前方", R.drawable.car_angle_fl),
    RIGHT_REAR("右後方", R.drawable.car_angle_rr),
    LEFT_REAR("左後方", R.drawable.car_angle_rl)
}

// ===== 偵測對齊狀態 =====
enum class DetectionStatus {
    NO_CAR,
    NOT_ALIGNED,
    ALIGNED
}

@Composable
fun CameraScreen(
    onBackClick: (() -> Unit)? = null,
    onNextStepClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var activeTab by remember { mutableIntStateOf(0) } // 0: 拍照, 1: 錄影
    var selectedAngleIndex by remember { mutableIntStateOf(0) } // 預設: 右前方
    var detectionStatus by remember { mutableStateOf(DetectionStatus.NO_CAR) }
    var showAlignmentDialog by remember { mutableStateOf(false) }
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

    // 儲存已拍攝照片的 Bitmap map
    val capturedPhotos = remember { mutableStateMapOf<InspectionAngle, ImageBitmap>() }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    // 測試用：相簿選取照片 Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val currentAngle = InspectionAngle.entries[selectedAngleIndex]
            handleCameraPickedPhoto(
                context = context,
                uri = uri,
                angle = currentAngle,
                onPhotoCaptured = { angle, bitmap ->
                    capturedPhotos[angle] = bitmap
                    val uncapturedIndex = InspectionAngle.entries.indexOfFirst { !capturedPhotos.containsKey(it) }
                    if (uncapturedIndex != -1) {
                        selectedAngleIndex = uncapturedIndex
                    }
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
    }

    val objectDetector = remember {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val targetZone = remember { RectF(0.15f, 0.20f, 0.85f, 0.80f) }

    // 相機綁定
    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var alignedFramesCount = 0
            var lostFramesCount = 0
            val requiredAlignedFrames = 3
            val toleratedLostFrames = 12
            var currentStableStatus = DetectionStatus.NO_CAR

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                analyzeFrame(imageProxy, objectDetector, targetZone) { rawStatus ->
                    if (rawStatus == DetectionStatus.ALIGNED) {
                        lostFramesCount = 0
                        alignedFramesCount++
                        if (alignedFramesCount >= requiredAlignedFrames) {
                            currentStableStatus = DetectionStatus.ALIGNED
                        }
                    } else {
                        alignedFramesCount = 0
                        if (currentStableStatus == DetectionStatus.ALIGNED) {
                            lostFramesCount++
                            if (lostFramesCount > toleratedLostFrames) {
                                currentStableStatus = rawStatus
                            }
                        } else {
                            currentStableStatus = rawStatus
                        }
                    }
                    detectionStatus = currentStableStatus
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector,
                    preview, imageCapture, imageAnalysis
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

    DisposableEffect(Unit) {
        onDispose {
            objectDetector.close()
            analysisExecutor.shutdown()
        }
    }

    // 執行拍照的封裝函式 (拍照成功自動推進下一個尚未拍攝的角度)
    val doTakePhoto = {
        val currentAngle = InspectionAngle.entries[selectedAngleIndex]
        qualityWarningBannerText = null // 重新拍照時先清除之前的警示條
        takePhoto(
            context = context,
            imageCapture = imageCapture,
            angle = currentAngle,
            onPhotoCaptured = { angle, bitmap ->
                capturedPhotos[angle] = bitmap
                val uncapturedIndex = InspectionAngle.entries.indexOfFirst { !capturedPhotos.containsKey(it) }
                if (uncapturedIndex != -1) {
                    selectedAngleIndex = uncapturedIndex
                }
            },
            onQualityRejected = { errorMsg ->
                // 解析退回理由，並直接於相機畫面中央浮現 Figma 設計規範之紅色警示條（無需點擊任何按鈕）
                qualityWarningBannerText = when {
                    errorMsg.contains("模糊") -> "畫面過於模糊，請重新拍攝"
                    errorMsg.contains("暗") -> "畫面過暗，請重新拍攝"
                    errorMsg.contains("過曝") || errorMsg.contains("反光") -> "畫面過曝，請重新拍攝"
                    else -> "畫面不清晰，請重新拍攝"
                }
            }
        )
    }

    // 未對齊確認 Dialog
    if (showAlignmentDialog) {
        AlertDialog(
            onDismissRequest = { showAlignmentDialog = false },
            title = { Text("⚠️ 對齊提醒", fontWeight = FontWeight.Bold) },
            text = {
                Text("車牌與輪框尚未對準藍色輔助框，建議對齊後拍攝以利識別。\n\n是否仍要拍攝？", lineHeight = 22.sp, color = Color(0xFF1A1A1A))
            },
            confirmButton = {
                TextButton(onClick = {
                    showAlignmentDialog = false
                    doTakePhoto()
                }) {
                    Text("強制拍照", color = Color(0xFFD7000F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlignmentDialog = false }) {
                    Text("重新對齊", color = Color(0xFF727272))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F3F5))
    ) {
        // 1. 頂部列：拍照/錄影 切換 + 返回按鈕 (純白延伸至狀態欄)
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
                ) {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back),
                                contentDescription = "返回",
                                tint = Color(0xFF1A1A1A),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // 拍照 / 錄影 Tab
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { activeTab = 0 }
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "拍照",
                                fontSize = 14.sp,
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 0) Color(0xFF1A1A1A) else Color(0xFF757575)
                            )
                            if (activeTab == 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .width(28.dp)
                                        .height(3.dp)
                                        .background(Color(0xFFD7000F), RoundedCornerShape(2.dp))
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { activeTab = 1 }
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "錄影",
                                fontSize = 14.sp,
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == 1) Color(0xFF1A1A1A) else Color(0xFF757575)
                            )
                            if (activeTab == 1) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .width(28.dp)
                                        .height(3.dp)
                                        .background(Color(0xFFD7000F), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }

                    var showIpDialog by remember { mutableStateOf(false) }
                    var tempIpText by remember { mutableStateOf(CarDamageApiService.getServerUrl(context)) }

                    if (showIpDialog) {
                        AlertDialog(
                            onDismissRequest = { showIpDialog = false },
                            title = { Text("⚙️ 伺服器連線設定", fontWeight = FontWeight.Bold) },
                            text = {
                                Column {
                                    Text(
                                        text = "請輸入電腦本機 API 伺服器網址 (包含 Port 8000)：\n• USB / 模擬器：http://127.0.0.1:8000\n• 模擬器備援：http://10.0.2.2:8000\n• 同 Wi-Fi 模式：http://192.168.2.102:8000",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666),
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = tempIpText,
                                        onValueChange = { tempIpText = it },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("伺服器 URL") }
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        CarDamageApiService.setServerUrl(context, tempIpText)
                                        showIpDialog = false
                                        Toast.makeText(context, "已儲存伺服器連線網址！", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD7000F))
                                ) {
                                    Text("儲存", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showIpDialog = false }) {
                                    Text("取消", color = Color(0xFF888888))
                                }
                            }
                        )
                    }

                    IconButton(
                        onClick = {
                            tempIpText = CarDamageApiService.getServerUrl(context)
                            showIpDialog = true
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_headset),
                            contentDescription = "伺服器設定",
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E5E8), thickness = 1.dp)
            }
        }

        // 2. 主標題區塊 (適度縮小字級)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "車身拍照",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "請拍攝車輛四周 (含車牌)",
                fontSize = 12.sp,
                color = Color(0xFF727272)
            )
        }

        // 3. 相機視窗 + 車牌與輪框藍色對齊輔助框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(1.05f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            // 相機畫面
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // 車牌與輪框特徵對齊藍色虛線框
            PlateAndWheelAlignmentOverlay(
                detectionStatus = detectionStatus,
                angle = InspectionAngle.entries[selectedAngleIndex]
            )

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

            // 相機視窗下方提示條
            val currentAngle = InspectionAngle.entries[selectedAngleIndex]
            val hintText = when (currentAngle) {
                InspectionAngle.RIGHT_FRONT -> "請將右前輪與前車牌對齊藍色框"
                InspectionAngle.LEFT_FRONT -> "請將左前輪與前車牌對齊藍色框"
                InspectionAngle.RIGHT_REAR -> "請將右後輪與後車牌對齊藍色框"
                InspectionAngle.LEFT_REAR -> "請將左後輪與後車牌對齊藍色框"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = hintText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. 系統提示說明
        Text(
            text = "系統將自動識別車輛與拍攝並進入下一階段",
            fontSize = 11.sp,
            color = Color(0xFF727272),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 5. 角度階段選擇器 (右前方 -> 左前方 -> 右後方 -> 左後方)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 階段文字與單選圓點
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InspectionAngle.entries.forEachIndexed { index, angle ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .border(
                                        1.5.dp,
                                        if (selectedAngleIndex == index || capturedPhotos.containsKey(angle)) Color(0xFF1A1A1A) else Color(0xFF7B7B7F),
                                        CircleShape
                                    )
                                    .background(
                                        if (capturedPhotos.containsKey(angle)) Color(0xFF4CAF50)
                                        else if (selectedAngleIndex == index) Color(0xFF1A1A1A)
                                        else Color.White.copy(alpha = 0.8f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = angle.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (selectedAngleIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedAngleIndex == index) Color(0xFF1A1A1A) else Color(0xFF727272),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4 個角度的縮圖卡片
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectionAngle.entries.forEachIndexed { index, angle ->
                    val isSelected = (selectedAngleIndex == index)
                    val capturedBitmap = capturedPhotos[angle]

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2F2F30))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Color(0xFF1DA6D0) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedAngleIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedBitmap != null) {
                            Image(
                                bitmap = capturedBitmap,
                                contentDescription = angle.label,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Image(
                                painter = painterResource(id = angle.imageResId),
                                contentDescription = angle.label,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 6. 車號標籤
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "車號 : RFH-8052",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
                modifier = Modifier
                    .background(Color(0xFFE4C17F).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 7. 底部快門按鈕區 + 左側選取相片 + 略過/下一步膠囊按鈕
        val allCaptured = InspectionAngle.entries.all { capturedPhotos.containsKey(it) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            // 左下角「選取測試相片」灰色膠囊按鈕
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(80.dp))
                    .background(Color(0xFFE2E8F0))
                    .clickable { galleryLauncher.launch("image/*") }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "選測試圖",
                    color = Color(0xFF334155),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 大圓形快門按鈕 (居中)
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .border(3.5.dp, Color(0xFFE0E0E0), CircleShape)
                    .padding(3.5.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable {
                        if (detectionStatus != DetectionStatus.ALIGNED) {
                            showAlignmentDialog = true
                        } else {
                            doTakePhoto()
                        }
                    }
            )

            // 右下角「下一步 / 略過」紅色膠囊按鈕
            if (onNextStepClick != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(80.dp))
                        .background(if (allCaptured) Color(0xFFD7000F) else Color(0xFF888888))
                        .clickable { onNextStepClick() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (allCaptured) "下一步 ➔" else "略過",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ===== 車牌與輪框對齊藍色虛線框 (Keypoint Overlay - Figma 蔚藍 #1DA6D0) =====
@Composable
fun PlateAndWheelAlignmentOverlay(
    detectionStatus: DetectionStatus,
    angle: InspectionAngle
) {
    val guideColor = when (detectionStatus) {
        DetectionStatus.ALIGNED -> Color(0xFF4CAF50)    // 已對齊變綠色
        else -> Color(0xFF1DA6D0)                        // Figma iRent 蔚藍色對齊框
    }

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 3.dp.toPx(), pathEffect = dashEffect)

        // 根據 4 種不同拍攝角度設定專屬的「車牌」與「輪框」對齊座標
        val (plateLeft, plateTop, plateWidth, plateHeight) = when (angle) {
            InspectionAngle.RIGHT_FRONT -> listOf(w * 0.18f, h * 0.62f, w * 0.32f, h * 0.12f)
            InspectionAngle.LEFT_FRONT  -> listOf(w * 0.50f, h * 0.62f, w * 0.32f, h * 0.12f)
            InspectionAngle.RIGHT_REAR  -> listOf(w * 0.22f, h * 0.58f, w * 0.30f, h * 0.11f)
            InspectionAngle.LEFT_REAR   -> listOf(w * 0.48f, h * 0.58f, w * 0.30f, h * 0.11f)
        }

        val (tireLeft, tireTop, tireWidth, tireHeight) = when (angle) {
            InspectionAngle.RIGHT_FRONT -> listOf(w * 0.70f, h * 0.48f, w * 0.18f, h * 0.30f)
            InspectionAngle.LEFT_FRONT  -> listOf(w * 0.12f, h * 0.48f, w * 0.18f, h * 0.30f)
            InspectionAngle.RIGHT_REAR  -> listOf(w * 0.67f, h * 0.50f, w * 0.18f, h * 0.28f)
            InspectionAngle.LEFT_REAR   -> listOf(w * 0.15f, h * 0.50f, w * 0.18f, h * 0.28f)
        }

        // 1. 繪製對應角度的車牌對齊虛線框
        drawRoundRect(
            color = guideColor,
            topLeft = Offset(plateLeft, plateTop),
            size = Size(plateWidth, plateHeight),
            cornerRadius = CornerRadius(8f, 8f),
            style = stroke
        )

        // 2. 繪製對應角度的輪框/輪胎對齊虛線框
        drawOval(
            color = guideColor,
            topLeft = Offset(tireLeft, tireTop),
            size = Size(tireWidth, tireHeight),
            style = stroke
        )
    }
}

// ===== ML Kit 分析 =====
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeFrame(
    imageProxy: ImageProxy,
    objectDetector: ObjectDetector,
    targetZone: RectF,
    onResult: (DetectionStatus) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(
        mediaImage, imageProxy.imageInfo.rotationDegrees
    )

    val rotation = imageProxy.imageInfo.rotationDegrees
    val effectiveWidth = if (rotation == 90 || rotation == 270) imageProxy.height.toFloat() else imageProxy.width.toFloat()
    val effectiveHeight = if (rotation == 90 || rotation == 270) imageProxy.width.toFloat() else imageProxy.height.toFloat()

    objectDetector.process(inputImage)
        .addOnSuccessListener { detectedObjects ->
            if (detectedObjects.isEmpty()) {
                onResult(DetectionStatus.NO_CAR)
                return@addOnSuccessListener
            }

            val largest = detectedObjects.maxByOrNull {
                it.boundingBox.width() * it.boundingBox.height()
            } ?: run {
                onResult(DetectionStatus.NO_CAR)
                return@addOnSuccessListener
            }

            val box = largest.boundingBox
            val normalizedBox = RectF(
                box.left / effectiveWidth,
                box.top / effectiveHeight,
                box.right / effectiveWidth,
                box.bottom / effectiveHeight
            )

            val iou = calculateIoU(normalizedBox, targetZone)
            val objectArea = normalizedBox.width() * normalizedBox.height()

            if (iou >= 0.70f && objectArea >= 0.18f) {
                onResult(DetectionStatus.ALIGNED)
            } else if (objectArea >= 0.05f) {
                onResult(DetectionStatus.NOT_ALIGNED)
            } else {
                onResult(DetectionStatus.NO_CAR)
            }
        }
        .addOnFailureListener {
            onResult(DetectionStatus.NO_CAR)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
    val interLeft = max(boxA.left, boxB.left)
    val interTop = max(boxA.top, boxB.top)
    val interRight = min(boxA.right, boxB.right)
    val interBottom = min(boxA.bottom, boxB.bottom)

    if (interLeft >= interRight || interTop >= interBottom) return 0f

    val interArea = (interRight - interLeft) * (interBottom - interTop)
    val areaA = boxA.width() * boxA.height()
    val areaB = boxB.width() * boxB.height()
    val unionArea = areaA + areaB - interArea

    return if (unionArea > 0f) interArea / unionArea else 0f
}

// ===== 自動修正 EXIF 轉向的照片解碼 =====
private fun loadOrientedBitmap(filePath: String): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
    return try {
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
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

// ===== 拍照儲存、上傳 Python API 品質/YOLO 檢測與縮圖回傳 =====
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    angle: InspectionAngle,
    onPhotoCaptured: (InspectionAngle, ImageBitmap) -> Unit,
    onQualityRejected: (String) -> Unit
) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val photoFile = File(context.filesDir, "CAR_${angle.name}_${timestamp}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val originalBitmap = loadOrientedBitmap(photoFile.absolutePath) ?: return

                // 非同步上傳 Python FastAPI 進行 OpenCV 品質與 YOLO 檢測
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val result = CarDamageApiService.uploadAndPredict(photoFile, context)
                    when (result.status) {
                        "success" -> {
                            val finalBitmap = result.annotatedBitmap ?: originalBitmap.asImageBitmap()
                            onPhotoCaptured(angle, finalBitmap)
                            val msg = if (result.damageCount > 0) {
                                "✅ 辨識成功！檢測出 ${result.damageCount} 處車損"
                            } else {
                                "✅ 照片品質良好，未發現顯著車損"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                        "rejected" -> {
                            // 品質檢測未通過 (如畫面過於模糊、過暗、過曝)，攔截不儲存，並跳出 Figma 錯誤彈窗要求重新拍攝
                            onQualityRejected(result.message)
                        }
                        else -> {
                            // API 未啟動或連線失敗，優雅降級使用原圖
                            onPhotoCaptured(angle, originalBitmap.asImageBitmap())
                            Toast.makeText(context, "✅ 已拍攝！(本地 API 未連線)", Toast.LENGTH_SHORT).show()
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

// ===== 處理從相簿選取的測試照片 =====
private fun handleCameraPickedPhoto(
    context: Context,
    uri: Uri,
    angle: InspectionAngle,
    onPhotoCaptured: (InspectionAngle, ImageBitmap) -> Unit,
    onQualityRejected: (String) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val tempFile = File(context.cacheDir, "TEST_${angle.name}_${timestamp}.jpg")

        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }

        val originalBitmap = loadOrientedBitmap(tempFile.absolutePath) ?: return

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            Toast.makeText(context, "🔄 正在上傳 [${angle.label}] 測試照片進行 AI 辨識...", Toast.LENGTH_SHORT).show()
            val result = CarDamageApiService.uploadAndPredict(tempFile, context)
            when (result.status) {
                "success" -> {
                    val finalBitmap = result.annotatedBitmap ?: originalBitmap.asImageBitmap()
                    onPhotoCaptured(angle, finalBitmap)
                    val msg = if (result.damageCount > 0) {
                        "✅ [${angle.label}] 辨識成功！檢測出 ${result.damageCount} 處車損"
                    } else {
                        "✅ [${angle.label}] 辨識完成 (未發現顯著車損)"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                "rejected" -> {
                    onQualityRejected(result.message)
                }
                else -> {
                    onPhotoCaptured(angle, originalBitmap.asImageBitmap())
                    Toast.makeText(context, "✅ 已新增 [${angle.label}] 測試照片 (${result.message})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "選取照片失敗: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

