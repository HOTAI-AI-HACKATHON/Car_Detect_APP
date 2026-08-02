package com.example.car_detect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

// ===== API 回傳資料結構 =====
data class QualityMetrics(
    val blurScore: Double,
    val meanBrightness: Double
)

data class DamagePrediction(
    val label: String,
    val confidence: Double,
    val box: List<Double> // [xmin, ymin, xmax, ymax]
)

data class ApiPredictResult(
    val status: String,                 // "success" 或 "rejected"
    val message: String,                // 訊息說明
    val blurScore: Double? = null,
    val meanBrightness: Double? = null,
    val damageCount: Int = 0,
    val predictions: List<DamagePrediction> = emptyList(),
    val annotatedBitmap: ImageBitmap? = null // YOLO 畫好車損框的圖片 (Base64 解碼後)
)

object CarDamageApiService {
    // 預設連線位置：Android 模擬器存取本機電腦的 IP 為 10.0.2.2
    // 如果使用實體手機，請更換為電腦在區域網路的 IP (例如 http://192.168.1.100:8000)
    var serverBaseUrl = "http://10.0.2.2:8000"

    /**
     * 上傳照片至 FastAPI /predict 端點進行品質檢測與 YOLO 車損辨識
     */
    suspend fun uploadAndPredict(imageFile: File): ApiPredictResult = withContext(Dispatchers.IO) {
        val targetUrl = "${serverBaseUrl.trimEnd('/')}/predict"
        val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        var connection: HttpURLConnection? = null
        try {
            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doInput = true
                doOutput = true
                useCaches = false
                connectTimeout = 10000 // 10 秒連線逾時
                readTimeout = 30000    // 30 秒推論逾時 (YOLO 重度推論)
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(connection.outputStream).use { output ->
                // Multipart 檔案欄位 header
                output.writeBytes(twoHyphens + boundary + lineEnd)
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${imageFile.name}\"$lineEnd")
                output.writeBytes("Content-Type: image/jpeg$lineEnd$lineEnd")

                // 寫入照片 Byte 資料
                FileInputStream(imageFile).use { input ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
                output.writeBytes(lineEnd)
                output.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                output.flush()
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseText = inputStream.bufferedReader().use { it.readText() }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorJson = runCatching { JSONObject(responseText) }.getOrNull()
                val detail = errorJson?.optString("detail") ?: "伺服器回應錯誤 ($responseCode)"
                return@withContext ApiPredictResult(
                    status = "error",
                    message = detail
                )
            }

            // 解析 JSON 回應
            val json = JSONObject(responseText)
            val status = json.optString("status", "error")
            val message = json.optString("message", "")
            val damageCount = json.optInt("damage_count", 0)

            // 品質數據
            var blurScore: Double? = null
            var meanBrightness: Double? = null
            if (json.has("quality_metrics")) {
                val qm = json.getJSONObject("quality_metrics")
                blurScore = qm.optDouble("blur_score")
                meanBrightness = qm.optDouble("mean_brightness")
            }

            // 車損辨識框標籤列表
            val predictionsList = mutableListOf<DamagePrediction>()
            if (json.has("predictions")) {
                val arr = json.getJSONArray("predictions")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val label = obj.optString("label", "damage")
                    val conf = obj.optDouble("confidence", 0.0)
                    val boxArr = obj.getJSONArray("box")
                    val boxList = listOf(
                        boxArr.getDouble(0),
                        boxArr.getDouble(1),
                        boxArr.getDouble(2),
                        boxArr.getDouble(3)
                    )
                    predictionsList.add(DamagePrediction(label, conf, boxList))
                }
            }

            // Base64 解碼為標註完畢的照片 (YOLO plot annotated_image)
            var annotatedBitmap: ImageBitmap? = null
            if (json.has("annotated_image_base64")) {
                val base64Str = json.optString("annotated_image_base64")
                if (base64Str.isNotEmpty()) {
                    val pureBase64 = if (base64Str.contains(",")) {
                        base64Str.substringAfter(",")
                    } else {
                        base64Str
                    }
                    val imageBytes = Base64.decode(pureBase64, Base64.DEFAULT)
                    val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    annotatedBitmap = decoded?.asImageBitmap()
                }
            }

            ApiPredictResult(
                status = status,
                message = message,
                blurScore = blurScore,
                meanBrightness = meanBrightness,
                damageCount = damageCount,
                predictions = predictionsList,
                annotatedBitmap = annotatedBitmap
            )
        } catch (e: Exception) {
            ApiPredictResult(
                status = "error",
                message = "無法連線至本地 API 伺服器 (${e.localizedMessage ?: "請確認 Python FastAPI 已啟動"})"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
