package com.moneyapp.android.ui
import com.moneyapp.android.data.net.ApiClient
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response



class TestDbActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ping çağrısı
        ApiClient.api.ping().enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, resp: Response<String>) {
                val body = resp.body()?.trim()
                Toast.makeText(this@TestDbActivity, "Ping: $body", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@TestDbActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}