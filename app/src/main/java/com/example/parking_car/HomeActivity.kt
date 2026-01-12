package com.example.parking_car

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Callback
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HomeActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val CAMERA_PERMISSION_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private lateinit var tvLicensePlate: TextView
    private val client = OkHttpClient()
    private lateinit var btnCheck: android.view.View
    private lateinit var btnInput: android.view.View
    private lateinit var btnOutput: android.view.View
    private lateinit var layoutActions: android.widget.LinearLayout
    private lateinit var recyclerViewLatest: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private var currentCarHistoryId: String? = null
    private lateinit var tvTotalAll: TextView
    private lateinit var tvTotalIn: TextView
    private lateinit var tvTotalOut: TextView
    private lateinit var tvTotalAvailable: TextView
    private val MAX_CAPACITY = 30 // Giả định bãi có 30 chỗ
    private lateinit var btnKhuA: android.widget.Button
    private lateinit var btnKhuB: android.widget.Button
    private lateinit var btnKhuC: android.widget.Button
    private var selectedLotId: String? = null
    private var selectedAreaName: String = ""
    // Biến lưu ID thực tế từ API
    private var idKhuA: String = ""
    private var idKhuB: String = ""
    private var idKhuC: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)
        recyclerViewLatest = findViewById(R.id.recyclerViewLatest)
        recyclerViewLatest.layoutManager = LinearLayoutManager(this)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.reverseLayout = true
        layoutManager.stackFromEnd = true
        recyclerViewLatest.layoutManager = layoutManager
        recyclerViewLatest.isNestedScrollingEnabled = false
        historyAdapter = HistoryAdapter(mutableListOf())
        recyclerViewLatest.adapter = historyAdapter
        previewView = findViewById(R.id.previewView)
        tvLicensePlate = findViewById(R.id.tv_license_plate)
        btnCheck = findViewById(R.id.btn_check)
        btnInput = findViewById(R.id.btn_input)
        btnOutput = findViewById(R.id.btn_output)
        layoutActions = btnInput.parent as android.widget.LinearLayout
        cameraExecutor = Executors.newSingleThreadExecutor()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_CODE)
        }

        tvTotalAll = findViewById(R.id.tv_total_all)
        tvTotalIn = findViewById(R.id.tv_total_in)
        tvTotalOut = findViewById(R.id.tv_total_out)
        tvTotalAvailable = findViewById(R.id.tv_total_available)
        btnCheck.setOnClickListener {
            val currentPlate = tvLicensePlate.text.toString().trim().uppercase()
            if (currentPlate.isNotEmpty() && currentPlate.length > 3) {
                checkPlateInList(currentPlate) // Gọi hàm kiểm tra
            } else {
                Toast.makeText(this, "請拍攝車牌照片！", Toast.LENGTH_SHORT).show()
            }
        }

        btnOutput.setOnClickListener {
            val id = currentCarHistoryId
            if (id != null) {
                handleCarExit(id)
            } else {
                Toast.makeText(this, "未找到車輛識別碼！", Toast.LENGTH_SHORT).show()
            }
        }

        btnInput.setOnClickListener {
            val plate = tvLicensePlate.text.toString().trim()

            if (plate.isEmpty()) {
                Toast.makeText(this, "請先拍攝車牌", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedLotId == null) {
                Toast.makeText(this, "請先選擇區域 (A/B/C)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Nếu đã có biển số và đã chọn khu, gọi hàm lấy danh sách vị trí để hiện Dialog
            handleAreaClick(selectedLotId!!, selectedAreaName)
        }
        fetchHistory()
        btnKhuA = findViewById(R.id.khuA)
        btnKhuB = findViewById(R.id.khuB)
        btnKhuC = findViewById(R.id.khuC)

        // Lấy danh sách ID bãi đỗ từ API
        fetchParkingLots()

        // Thiết lập sự kiện click
        btnKhuA.setOnClickListener { selectArea(idKhuA, "A1", btnKhuA) }
        btnKhuB.setOnClickListener { selectArea(idKhuB, "A2", btnKhuB) }
        btnKhuC.setOnClickListener { selectArea(idKhuC, "A3", btnKhuC) }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                previewView.setOnClickListener {
                    tvLicensePlate.text = "正在分析中…"
                    tvLicensePlate.textSize = 20f
                    tvLicensePlate.setTextColor(Color.BLUE)
                    takePhotoAndUpload()
                }

            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    // Hàm kiểm tra quyền
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相機權限", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun getAccessToken(): String? {
        val sharedPref = getSharedPreferences("AUTH_PREF", MODE_PRIVATE)
        return sharedPref.getString("TOKEN", null)
    }
    private fun fetchParkingLots() {
        val token = getAccessToken()
        val request = Request.Builder()
            .url("https://parking-car-k7mb.onrender.com/api/v1/parking-lots")
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API_ERROR", "Không thể lấy danh sách bãi")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val items = json.getJSONObject("data").getJSONArray("item")

                    // Giả sử item 0 là A, 1 là B, 2 là C dựa trên thứ tự API trả về
                    if (items.length() >= 1) idKhuA = items.getJSONObject(0).getString("id")
                    if (items.length() >= 2) idKhuB = items.getJSONObject(1).getString("id")
                    if (items.length() >= 3) idKhuC = items.getJSONObject(2).getString("id")

                    Log.d("API_ID", "Khu A: $idKhuA, Khu B: $idKhuB, Khu C: $idKhuC")
                }
            }
        })
    }
    private fun selectArea(lotId: String, areaName: String, selectedBtn: Button) {
        selectedLotId = lotId
        selectedAreaName = areaName
        val buttons = listOf(btnKhuA, btnKhuB, btnKhuC)
        buttons.forEach { btn ->
            btn.setBackgroundResource(R.drawable.bg_area_blue)
            btn.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
        selectedBtn.setBackgroundResource(R.drawable.bg_area)
        selectedBtn.setTextColor(ContextCompat.getColor(this, R.color.color_25427A))

        Toast.makeText(this, "已選擇 $areaName 區", Toast.LENGTH_SHORT).show()
    }
    private fun handleAreaClick(parkingLotId: String, areaName: String) {
        val token = getAccessToken()

        // areaName lúc này sẽ là "A1", "A2" hoặc "A3"
        // URL sẽ là: .../parking-spots?keyword=a1 (hoặc a2, a3)
        val url = "https://parking-car-k7mb.onrender.com/api/v1/parking-spots?keyword=${areaName.lowercase()}"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@HomeActivity, "連線錯誤", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    runOnUiThread {
                        // Mở Dialog hiển thị danh sách đã lọc
                        showParkingSpotDialog(parkingLotId, areaName, body)
                    }
                }
            }
        })
    }

    private fun showParkingSpotDialog(lotId: String, areaName: String, jsonData: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_parking_spot, null)
        val gridLayout = dialogView.findViewById<android.widget.GridLayout>(R.id.gridParkingSpots)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("區域 $areaName - 選擇車位")
            .setView(dialogView)
            .setNegativeButton("取消", null)
            .create()

        try {
            val json = JSONObject(jsonData)
            val dataObj = json.getJSONObject("data")
            val itemsArray = dataObj.getJSONArray("item")

            gridLayout.removeAllViews()
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val spotId = item.getString("id")
                val spotName = item.optString("spotCode", item.optString("parkingSpotName", "位 ${i + 1}"))
                val isAvailable = item.optBoolean("isAvailable", true)
                val isOccupied = !isAvailable

                Log.d("DEBUG_PARKING", "Vị trí: $spotName | ID: $spotId | Trống: $isAvailable")
                val buttonHeightInPx = (60 * resources.displayMetrics.density).toInt()

                val button = Button(this).apply {
                    if (isOccupied) {
                        text = "$spotName\n(佔用中)"
                        setBackgroundColor(Color.parseColor("#D3D3D3"))
                        setTextColor(Color.parseColor("#808080"))
                        isEnabled = false
                        alpha = 0.7f
                    } else {
                        text = "$spotName "
                        setBackgroundColor(Color.parseColor("#4AAD52"))
                        setTextColor(Color.WHITE)
                        isEnabled = true
                        alpha = 1.0f
                    }
                    val params = GridLayout.LayoutParams().apply {
                        width = 0
                        height = buttonHeightInPx
                        rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER, 1f)
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(10, 10, 10, 10)
                    }
                    layoutParams = params

                    // 3. Đảm bảo chữ luôn nằm giữa nút
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 0) // Xóa padding cũ nếu cần

                    setOnClickListener {
                        saveCarInfoWithSpot(tvLicensePlate.text.toString(), lotId, spotId)
                        dialog.dismiss()
                    }
                }
                gridLayout.addView(button)
            }
        } catch (e: Exception) {
            Log.e("JSON_ERROR", "Lỗi phân tích JSON: ${e.message}")
            Toast.makeText(this, "無法解析車位數據", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun saveCarInfoWithSpot(plate: String, lotId: String, spotId: String) {
        val token = getAccessToken()
        val url = "https://parking-car-k7mb.onrender.com/api/v1/car-info"

        val jsonParams = JSONObject().apply {
            put("licensePlate", plate.uppercase())
            put("parkingLotId", lotId)
            put("parkingSpotId", spotId)
        }

        val requestBody = jsonParams.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@HomeActivity, "儲存失敗", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // --- BƯỚC QUAN TRỌNG: Cập nhật trạng thái ô đỗ sang FALSE ---
                    updateSpotStatus(spotId, false)

                    runOnUiThread {
                        tvLicensePlate.text = ""
                        layoutActions.visibility = android.view.View.GONE
                        btnCheck.visibility = android.view.View.VISIBLE

                        fetchHistory() // Cập nhật lại lịch sử đỗ xe
                        Toast.makeText(this@HomeActivity, "đã đậu xe thành công!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
    private fun updateSpotStatus(spotId: String, isAvailable: Boolean) {
        val token = getAccessToken()
        val url = "https://parking-car-k7mb.onrender.com/api/v1/parking-spots/$spotId"

        // Tạo body chứa trạng thái mới
        val jsonParams = JSONObject().apply {
            put("isAvailable", isAvailable)
        }

        val requestBody = jsonParams.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .patch(requestBody) // Sử dụng phương thức PATCH
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API_PATCH", "Cập nhật trạng thái ô đỗ thất bại: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("API_PATCH", "Đã cập nhật ô đỗ $spotId thành isAvailable = $isAvailable")
                } else {
                    Log.e("API_PATCH", "Lỗi server khi PATCH: ${response.code}")
                }
            }
        })
    }
    private fun takePhotoAndUpload() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(cacheDir, "license_plate.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bytes = compressImage(photoFile)
                    Log.d("LicensePlateAPI", "Image size = ${bytes.size / 1024} KB")
                    uploadImage(bytes)
                }
                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread { Toast.makeText(this@HomeActivity, "攝影錯誤", Toast.LENGTH_SHORT).show() }

                }
            }
        )
    }
    private fun compressImage(file: File): ByteArray {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            1280,
            (bitmap.height * 1280f / bitmap.width).toInt(),
            true
        )
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 65, stream)
        Log.d(
            "LicensePlateAPI",
            "Compressed image size = ${stream.size() / 1024} KB"
        )
        return stream.toByteArray()
    }
    private fun uploadImage(imageData: ByteArray) {
        val token = getAccessToken() // Lấy token
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "plate.jpg", imageData.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("https://parking-car-k7mb.onrender.com/api/v1/license-plate/upload")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token") // THÊM DÒNG NÀY
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                updateUI("Connection error", Color.RED, 14f)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread {
                    val plate = try {
                        val json = JSONObject(body ?: "{}")
                        json.optString("plate", "").uppercase()
                    } catch (e: Exception) { "分析結果中的錯誤" }

                    if (plate == "null" || plate.isEmpty()) {
                        updateUI("車牌號碼無法識別", Color.RED, 14f)
                    } else {
                        // Hiển thị biển số lên TextView nhưng CHƯA lưu vào history
                        updateUI(plate, Color.BLACK, 36f) // Size 36 theo thiết kế XML
                    }
                }
            }
        })
    }
    private var currentParkingSpotId: String? = null
    private fun checkPlateInList(plate: String) {
        val currentList = historyAdapter.getList()
        val carInParking = currentList.find { it.licensePlate == plate && it.exitTime == "在場" }

        runOnUiThread {
            btnCheck.visibility = android.view.View.GONE
            layoutActions.visibility = android.view.View.VISIBLE

            if (carInParking != null) {
                currentCarHistoryId = carInParking.id
                currentParkingSpotId = carInParking.parkingSpotId
                btnInput.isEnabled = false
                btnInput.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_989B9F)
                btnOutput.isEnabled = true
                btnOutput.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_4AAD52)
                btnOutput.alpha = 1.0f

                Toast.makeText(this, "車牌 $plate 已在場，請按出口鍵", Toast.LENGTH_SHORT).show()
            } else {
                currentCarHistoryId = null
                currentParkingSpotId = null
                btnInput.isEnabled = true
                btnInput.alpha = 1.0f

                btnOutput.isEnabled = false
                btnOutput.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_989B9F)

                Toast.makeText(this, "新車進入，請選擇區域 A1/A2/A3", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun handleCarExit(historyId: String) {
        val token = getAccessToken()
        val url = "https://parking-car-k7mb.onrender.com/api/v1/car-info/$historyId"
        val spotIdToRelease = currentParkingSpotId
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer $token") // THÊM DÒNG NÀY
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@HomeActivity, "連線錯誤", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    if (spotIdToRelease != null) {
                        updateSpotStatus(spotIdToRelease, true) // 改為 true 表示可用
                    }
                    runOnUiThread {
                        btnCheck.visibility = android.view.View.VISIBLE
                        layoutActions.visibility = android.view.View.GONE
                        tvLicensePlate.text = ""
                        currentParkingSpotId = null // 清空暫存
                        fetchHistory()
                        Toast.makeText(this@HomeActivity, "車輛已出場，車位已釋放", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread { Toast.makeText(this@HomeActivity, "API錯誤: ${response.code}", Toast.LENGTH_SHORT).show() }
                }
            }
        })
    }
    private fun fetchHistory() {
        val token = getAccessToken()
        val request = Request.Builder()
            .url("https://parking-car-k7mb.onrender.com/api/v1/history")
            .get()
            .addHeader("Authorization", "Bearer $token") // THÊM DÒNG NÀY
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FetchHistory", "Failed", e)
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val json = JSONObject(body)
                        val dataObj = json.getJSONObject("data")
                        val itemsArray = dataObj.getJSONArray("item")
                        val newList = mutableListOf<HistoryItem>()

                        for (i in 0 until itemsArray.length()) {
                            val item = itemsArray.getJSONObject(i)
                            val rawEntry = item.getString("createdAt")
                            val rawExit = item.optString("deletedAt", "null")

                            // 解析 parkingSpotId (請確認你 API 回傳的 Key 名稱是否為 parkingSpotId)
                            val spotId = item.optString("parkingSpotId", null)

                            newList.add(HistoryItem(
                                id = item.optString("id", i.toString()),
                                licensePlate = item.getString("licensePlate"),
                                entryTime = formatToTaiwanTime(rawEntry),
                                exitTime = if (rawExit == "null") "在場" else formatToTaiwanTime(rawExit),
                                parkingSpotId = spotId // 存入這裡
                            ))
                        }

                        runOnUiThread {
                            historyAdapter.updateData(newList)
                            val totalCount = newList.size
                            val inParkingCount = newList.count { it.exitTime == "在場" }
                            val outParkingCount = totalCount - inParkingCount
                            val availableCount = MAX_CAPACITY - inParkingCount
                            tvTotalAll.text = totalCount.toString()
                            tvTotalIn.text = inParkingCount.toString()
                            tvTotalOut.text = outParkingCount.toString()
                            tvTotalAvailable.text = if (availableCount >= 0) availableCount.toString() else "0"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatToTaiwanTime(isoString: String?): String {
        if (isoString == null || isoString == "null" || isoString.isEmpty()) return "---"
        return try {
            val instant = java.time.Instant.parse(isoString)
            val taiwanZone = java.time.ZoneId.of("Asia/Taipei")
            val localDateTime = instant.atZone(taiwanZone)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm")
            localDateTime.format(formatter)
        } catch (e: Exception) {
            isoString.take(16).replace("T", " ")
        }
    }
    private fun updateUI(message: String, color: Int, size: Float) {
        runOnUiThread {
            tvLicensePlate.text = message
            tvLicensePlate.setTextColor(color)
            tvLicensePlate.textSize = size
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
