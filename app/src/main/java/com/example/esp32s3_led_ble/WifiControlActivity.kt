package com.example.esp32s3_led_ble

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout

class WifiControlActivity : AppCompatActivity(), BLEManager.Listener {

    private lateinit var switchWifi: Switch
    private lateinit var btnScanWifi: Button
    private lateinit var listWifi: ListView
    private lateinit var etWifiPassword: EditText
    private lateinit var btnConnectWifi: Button

    private val wifiItems = mutableListOf<String>()
    private var selectedSsid: String? = null
    private lateinit var adapter: ArrayAdapter<String>

    private var isReady = false  // 防止初始化时触发开关事件

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_control)

        switchWifi = findViewById(R.id.switchWifi)
        btnScanWifi = findViewById(R.id.btnScanWifi)
        listWifi = findViewById(R.id.listWifi)
        etWifiPassword = findViewById(R.id.etWifiPassword)
        btnConnectWifi = findViewById(R.id.btnConnectWifi)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiItems)
        listWifi.adapter = adapter

        BLEManager.addListener(this)

        // 初始化开关状态
        switchWifi.isChecked = GlobalState.wifiEnabled

        switchWifi.setOnCheckedChangeListener { _, isChecked ->
            if (!isReady) return@setOnCheckedChangeListener
            GlobalState.wifiEnabled = isChecked
            if (isChecked) {
                BLEManager.sendCommand("WIFI_ON")
            } else {
                BLEManager.sendCommand("WIFI_OFF")
            }
        }

        btnScanWifi.setOnClickListener {
            wifiItems.clear()
            adapter.notifyDataSetChanged()
            selectedSsid = null
            BLEManager.sendCommand("WIFI_SCAN")
            Toast.makeText(this, "正在扫描...", Toast.LENGTH_SHORT).show()
        }

        listWifi.setOnItemClickListener { _, _, position, _ ->
            selectedSsid = wifiItems[position].substringBefore(",")
            Toast.makeText(this, "已选择: $selectedSsid", Toast.LENGTH_SHORT).show()
            // 在密码框的 hint 中显示已选 SSID
            etWifiPassword.hint = "请输入 $selectedSsid 的密码"
        }

        btnConnectWifi.setOnClickListener {
            val ssid = selectedSsid
            if (ssid.isNullOrEmpty()) {
                Toast.makeText(this, "请先从列表中选择一个 WiFi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pwd = etWifiPassword.text.toString()
            BLEManager.sendCommand("WIFI_CONNECT:$ssid,$pwd")
            Toast.makeText(this, "正在连接 $ssid ...", Toast.LENGTH_SHORT).show()
        }

        isReady = true
    }

    override fun onDestroy() {
        super.onDestroy()
        BLEManager.removeListener(this)
    }

    override fun onScanning() {}
    override fun onConnected() {}
    override fun onDisconnected() {}

    override fun onDataReceived(data: String) {
        // 处理扫描结果
        if (data.startsWith("SCAN:")) {
            val payload = data.substring(5)
            val items = payload.split(";")
            wifiItems.clear()
            for (item in items) {
                if (item.isNotEmpty()) {
                    wifiItems.add(item)
                }
            }
            runOnUiThread {
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "扫描到 ${wifiItems.size} 个 WiFi", Toast.LENGTH_SHORT).show()
            }
            return
        }
        // 同步 WiFi 开关状态
        if (data.startsWith("W:")) {
            val parts = data.split("|")
            val enabled = parts.getOrNull(3)?.substringAfter("E:") ?: "1"
            val isEnabled = enabled == "1"
            runOnUiThread {
                if (switchWifi.isChecked != isEnabled) {
                    val oldReady = isReady
                    isReady = false
                    switchWifi.isChecked = isEnabled
                    isReady = oldReady
                }
                GlobalState.wifiEnabled = isEnabled
            }
        }
    }

    override fun onError(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}