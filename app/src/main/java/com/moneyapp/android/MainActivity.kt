package com.moneyapp.android

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.moneyapp.android.net.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // mevcut layout’un

        // Ping testi
        ApiClient.api.ping().enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, resp: Response<String>) {
                val body = resp.body() ?: "<null>"
                Toast.makeText(this@MainActivity, "Ping OK: $body", Toast.LENGTH_SHORT).show()
                android.util.Log.d("MoneyApp", "PING -> $body")
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Ping FAIL: ${t.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("MoneyApp", "PING error", t)
            }
        })
    }
}
