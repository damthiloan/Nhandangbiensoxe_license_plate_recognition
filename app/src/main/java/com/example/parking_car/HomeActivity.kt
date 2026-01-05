package com.example.parking_car

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
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
    private val MAX_CAPACITY = 50 // Giả định bãi có 50 chỗ
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_activity)
        recyclerViewLatest = findViewById(R.id.recyclerViewLatest)
        recyclerViewLatest.layoutManager = LinearLayoutManager(this)
        val layoutManager = LinearLayoutManager(this)
        //đảo ngược danh sách
        layoutManager.reverseLayout = true
        layoutManager.stackFromEnd = true
        recyclerViewLatest.layoutManager = layoutManager
        recyclerViewLatest.isNestedScrollingEnabled = false
        historyAdapter = HistoryAdapter(mutableListOf())
        recyclerViewLatest.adapter = historyAdapter
        previewView = findViewById(R.id.previewView)
        tvLicensePlate = findViewById(R.id.tv_license_plate)
        btnCheck = findViewById(R.id.btn_check) // Ánh xạ nút bấm
        btnInput = findViewById(R.id.btn_input)
        btnOutput = findViewById(R.id.btn_output)
        layoutActions = btnInput.parent as android.widget.LinearLayout
        cameraExecutor = Executors.newSingleThreadExecutor()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_CODE)
        }

        //sự kiện tính tổng
        tvTotalAll = findViewById(R.id.tv_total_all)
        tvTotalIn = findViewById(R.id.tv_total_in)
        tvTotalOut = findViewById(R.id.tv_total_out)
        tvTotalAvailable = findViewById(R.id.tv_total_available)
        // THIẾT LẬP SỰ KIỆN CHO NÚT BẤM CHECK
        btnCheck.setOnClickListener {
            val currentPlate = tvLicensePlate.text.toString().trim().uppercase()
            if (currentPlate.isNotEmpty() && currentPlate.length > 3) {
                checkPlateInList(currentPlate) // Gọi hàm kiểm tra
            } else {
                Toast.makeText(this, "請拍攝車牌照片！", Toast.LENGTH_SHORT).show()
            }
        }

        btnInput.setOnClickListener {
            saveCarInfo(tvLicensePlate.text.toString().trim())
        }

        btnOutput.setOnClickListener {
            val id = currentCarHistoryId
            if (id != null) {
                handleCarExit(id)
            } else {
                Toast.makeText(this, "未找到車輛識別碼！", Toast.LENGTH_SHORT).show()
            }
        }
        fetchHistory()
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
                // -----------------------

            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    // Hàm kiểm tra quyền
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    // Xử lý kết quả cấp quyền
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
                    // Nếu lỗi vẫn muốn tiếp tục chụp
//                    scheduleNextCapture()
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
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "plate.jpg", imageData.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("https://parking-car-k7mb.onrender.com/api/v1/license-plate/upload")
            .post(requestBody)
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
    private fun checkPlateInList(plate: String) {
        val currentList = historyAdapter.getList()
        // Tìm xe có biển trùng và chưa có thời gian ra (exitTime == "null")
        val carInParking = currentList.find { it.licensePlate == plate && it.exitTime == "null" }
        runOnUiThread {
            // 1. Ẩn nút Kiểm tra (btnCheck)
            btnCheck.visibility = android.view.View.GONE

            // 2. Hiện layout chứa 2 nút Input/Output
            layoutActions.visibility = android.view.View.VISIBLE

            if (carInParking != null) {
                // TRƯỜNG HỢP: XE ĐANG Ở TRONG BÃI -> CHỈ CHO PHÉP RA
                currentCarHistoryId = carInParking.id

                // Nút Input: Disable
                btnInput.isEnabled = false
                btnInput.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_989B9F)

                // Nút Output: Enable và có màu xanh (Nếu drawable của bạn hỗ trợ)
                btnOutput.isEnabled = true
                btnOutput.backgroundTintList = ContextCompat.getColorStateList(this,R.color.color_4AAD52)
                btnOutput.alpha = 1.0f

                Toast.makeText(this, "車已停在停車場。請按“出口”鍵。", Toast.LENGTH_SHORT).show()
            } else {
                // TRƯỜNG HỢP: XE MỚI -> CHỈ CHO PHÉP VÀO
                currentCarHistoryId = null

                // Nút Input: Enable
                btnInput.isEnabled = true
                btnInput.alpha = 1.0f

                // Nút Output: Disable
                btnOutput.isEnabled = false
                btnOutput.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_989B9F)

                Toast.makeText(this, "新車。請按“確認”鍵。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCarInfo(plate: String) {
        val url = "https://parking-car-k7mb.onrender.com/api/v1/car-info"
        val jsonParams = JSONObject().apply {
            put("licensePlate", plate.uppercase())
            put("parkingLotId", "659e7592b3b56d0946b3c7b5")
            put("parkingSpotId", "759e7592b3b56d0946b3c7b9")
        }
        val requestBody = jsonParams.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Trong hàm saveCarInfo và handleCarExit sau khi response thành công:
                    runOnUiThread {
                        // Hiện lại nút kiểm tra cho lượt tiếp theo
                        btnCheck.visibility = android.view.View.VISIBLE
                        layoutActions.visibility = android.view.View.GONE

                        // Xóa text biển số cũ
                        tvLicensePlate.text = ""
                        fetchHistory()
                    }
                }
            }
        })
    }
    private fun handleCarExit(historyId: String) {
        val url = "https://parking-car-k7mb.onrender.com/api/v1/car-info/$historyId"
        val request = Request.Builder()
            .url(url)
            .delete()
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@HomeActivity, "連線錯誤", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Trong hàm saveCarInfo và handleCarExit sau khi response thành công:
                    runOnUiThread {
                        // Hiện lại nút kiểm tra cho lượt tiếp theo
                        btnCheck.visibility = android.view.View.VISIBLE
                        layoutActions.visibility = android.view.View.GONE

                        // Xóa text biển số cũ
                        tvLicensePlate.text = ""
                        fetchHistory()
                    }
                } else {
                    runOnUiThread { Toast.makeText(this@HomeActivity, "API錯誤: ${response.code}", Toast.LENGTH_SHORT).show() }
                }
            }
        })
    }
    private fun fetchHistory() {
        val request = Request.Builder()
            .url("https://parking-car-k7mb.onrender.com/api/v1/history")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FetchHistory", "Failed", e)
            }

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
                            newList.add(HistoryItem(
                                id = item.optString("id", i.toString()),
                                licensePlate = item.getString("licensePlate"),
                                entryTime = item.getString("createdAt"),
                                exitTime = item.optString("deletedAt", "null")
                            ))
                        }

                        runOnUiThread {
                            // Cập nhật lên giao diện
                            historyAdapter.updateData(newList)
                            // 2. Tính toán thống kê
                            val totalCount = newList.size
                            val inParkingCount = newList.count { it.exitTime == "null" }
                            val outParkingCount = totalCount - inParkingCount
                            val availableCount = MAX_CAPACITY - inParkingCount // Tính số chỗ trống

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
