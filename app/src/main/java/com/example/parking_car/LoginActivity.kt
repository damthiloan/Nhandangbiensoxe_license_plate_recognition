package com.example.parking_car

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)

        val edtUsername = findViewById<EditText>(R.id.edtUsername)
        val edtPassword = findViewById<EditText>(R.id.edtPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        // Tạo instance của Retrofit (Trong thực tế nên dùng Singleton hoặc Hilt/Koin)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://parking-car-k7mb.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val authService = retrofit.create(AuthApiService::class.java)

        btnLogin.setOnClickListener {
            val user = edtUsername.text.toString()
            val pass = edtPassword.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            } else {
                // Gửi request
                val request = LoginRequest(user, pass)
                authService.login(request).enqueue(object : retrofit2.Callback<LoginResponse> {
                    override fun onResponse(call: Call<LoginResponse>, response: retrofit2.Response<LoginResponse>) {
                        if (response.isSuccessful) {
                            val loginData = response.body()
                            val accessToken = loginData?.access_token

                            // LƯU Ý: Bạn nên lưu accessToken vào SharedPreferences để dùng cho các API sau
                            val sharedPref = getSharedPreferences("AUTH_PREF", MODE_PRIVATE)
                            sharedPref.edit().putString("TOKEN", accessToken).apply()

                            Toast.makeText(this@LoginActivity, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Sai tài khoản hoặc mật khẩu", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                        Toast.makeText(this@LoginActivity, "Lỗi kết nối: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

        tvRegister.setOnClickListener {
            // Chuyển sang màn hình đăng ký
        }
    }
}