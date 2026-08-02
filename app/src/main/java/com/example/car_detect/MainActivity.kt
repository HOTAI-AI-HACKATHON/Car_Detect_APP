package com.example.car_detect

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.car_detect.ui.theme.Car_DetectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Car_DetectTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}

// 定義 APP 畫面狀態
enum class Screen {
    PICKUP_HOME,         // 進入 APP 的「取車拍照」首頁
    CAMERA,              // 4 角度車身對齊拍照畫面
    SCRATCH_INSPECTION   // 額外傷痕/刮痕拍照畫面
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.PICKUP_HOME) }

    // 檢查與請求相機權限
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PermissionChecker.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            currentScreen = Screen.CAMERA
        } else {
            Toast.makeText(context, "需要相機權限才能進行車體拍照檢查", Toast.LENGTH_LONG).show()
        }
    }

    // 點擊「確認取車」按鈕後的動作
    val onStartPhotoFlow = {
        if (hasCameraPermission) {
            currentScreen = Screen.CAMERA
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when (currentScreen) {
        Screen.PICKUP_HOME -> {
            PickupHomeScreen(
                onConfirmPickupClick = onStartPhotoFlow
            )
        }

        Screen.CAMERA -> {
            CameraScreen(
                onBackClick = {
                    currentScreen = Screen.PICKUP_HOME
                },
                onNextStepClick = {
                    currentScreen = Screen.SCRATCH_INSPECTION
                }
            )
        }

        Screen.SCRATCH_INSPECTION -> {
            ScratchInspectionScreen(
                onBackClick = {
                    currentScreen = Screen.CAMERA
                },
                onCompleteClick = {
                    Toast.makeText(context, "🎉 車體取車拍照與傷痕檢驗全數完成！", Toast.LENGTH_LONG).show()
                    currentScreen = Screen.PICKUP_HOME
                }
            )
        }
    }
}

// ===== 依照用戶照片完全打造的 APP 首頁 =====
@Composable
fun PickupHomeScreen(
    onConfirmPickupClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F7))
    ) {
        // 1. 頂部 Action Bar (純白背景延伸涵蓋狀態欄 Edge-to-Edge)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        Toast.makeText(context, "點擊返回", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "返回",
                            tint = Color(0xFF222222),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = "取車拍照",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111)
                    )

                    IconButton(onClick = {
                        Toast.makeText(context, "聯繫客服", Toast.LENGTH_SHORT).show()
                    }) {
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

        // 2. 主內容區
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 上方標題與提示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "檢查車輛四周",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "請仔細檢查有無損傷、凹陷等，\n完善拍照可保障自身權益。",
                    fontSize = 12.sp,
                    color = Color(0xFF777777),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            // 中間車輛展示圖 (使用去背透明 PNG)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable { onConfirmPickupClick() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.white_car_transparent),
                    contentDescription = "白色車輛去背示意圖",
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(300.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // 下方車號資訊與確認按鈕區塊
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "車號：RFH-8052",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 紅色主按鈕：確認取車
                Button(
                    onClick = onConfirmPickupClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD30015)
                    )
                ) {
                    Text(
                        text = "確認取車",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部條款與略過說明
                Text(
                    text = "已確認車況正常，同意使用",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )

                Text(
                    text = "略過拍照",
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier
                        .clickable {
                            Toast.makeText(context, "已略過拍照流程", Toast.LENGTH_SHORT).show()
                        }
                        .padding(top = 4.dp, bottom = 8.dp)
                )
            }
        }
    }
}