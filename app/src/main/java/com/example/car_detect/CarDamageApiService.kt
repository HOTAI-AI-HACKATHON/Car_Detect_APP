package com.example.car_detect

import android.content.Context
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
    private const val PREFS_NAME = "CarDamageApiPrefs"
    private const val KEY_SERVER_URL = "server_base_url"

    // 預設支援：127.0.0.1 (ADB Reverse 模式) 或 10.0.2.2 (模擬器模式)
    private var inMemoryUrl: String? = null

    fun getServerUrl(context: Context? = null): String {
        if (inMemoryUrl != null) return inMemoryUrl!!
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUrl = prefs.getString(KEY_SERVER_URL, null)
            if (!savedUrl.isNullOrBlank()) {
                inMemoryUrl = savedUrl
                return savedUrl
            }
        }
        // 預設使用 127.0.0.1 (搭配 adb reverse 實體機/模擬器皆通)
        return "http://127.0.0.1:8000"
    }

    fun setServerUrl(context: Context, newUrl: String) {
        val formattedUrl = if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
            "http://$newUrl"
        } else {
            newUrl
        }.trimEnd('/')

        inMemoryUrl = formattedUrl
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SERVER_URL, formattedUrl).apply()
    }

    /**
     * 上傳照片至 FastAPI /predict 端點進行品質檢測與 YOLO 車損辨識
     */
    suspend fun uploadAndPredict(imageFile: File, context: Context? = null): ApiPredictResult = withContext(Dispatchers.IO) {
        val currentBaseUrl = getServerUrl(context)
        
        // 優先嘗試設定之 URL，若為 127.0.0.1 失敗則嘗試模擬器預設 10.0.2.2 進行備援
        val urlsToTry = mutableListOf("${currentBaseUrl.trimEnd('/')}/predict")
        if (currentBaseUrl.contains("127.0.0.1") || currentBaseUrl.contains("localhost")) {
            urlsToTry.add("http://10.0.2.2:8000/predict")
        } else if (currentBaseUrl.contains("10.0.2.2")) {
            urlsToTry.add("http://127.0.0.1:8000/predict")
        }

        var lastException: Exception? = null
        for (targetUrl in urlsToTry) {
            val result = trySendRequest(imageFile, targetUrl)
            if (result != null) {
                return@withContext result
            }
        }

        ApiPredictResult(
            status = "error",
            message = "無法連線至 API 伺服器 ($currentBaseUrl)。請確認後端已啟動或於 App 頂部設定正確電腦 IP"
        )
    }

    private fun trySendRequest(imageFile: File, targetUrl: String): ApiPredictResult? {
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
                connectTimeout = 5000  // 5 秒連線逾時
                readTimeout = 30000    // 30 秒推論逾時
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes(twoHyphens + boundary + lineEnd)
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${imageFile.name}\"$lineEnd")
                output.writeBytes("Content-Type: image/jpeg$lineEnd$lineEnd")

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
                return ApiPredictResult(
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

            // Base64 解碼為標註完畢的照片
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

            return ApiPredictResult(
                status = status,
                message = message,
                blurScore = blurScore,
                meanBrightness = meanBrightness,
                damageCount = damageCount,
                predictions = predictionsList,
                annotatedBitmap = annotatedBitmap
            )
        } catch (e: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
    }
}
