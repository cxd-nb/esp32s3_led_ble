package com.example.esp32s3_led_ble

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class WifiControlActivity : AppCompatActivity(), BLEManager.Listener {

    private lateinit var switchWifi: Switch
    private lateinit var btnScanWifi: Button
    private lateinit var listWifi: ListView
    private lateinit var etWifiPassword: EditText
    private lateinit var btnConnectWifi: Button

    private val wifiList = mutableListOf<String>()
    private var selectedSsid: String? = null
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_control)

        switchWifi = findViewById(R.id.switchWifi)
        btnScanWifi = findViewById(R.id.btnScanWifi)
        listWifi = findViewById(R.id.listWifi)
        etWifiPassword = findViewById(R.id.etWifiPassword)
        btnConnectWifi = findViewById(R.id.btnConnectWifi)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiList)
        listWifi.adapter = adapter

        BLEManager.addListener(this)

        // WiFi 开关
        switchWifi.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                BLEManager.sendCommand("WIFI_ON")
            } else {
                BLEManager.sendCommand("WIFI_OFF")
            }
        }

        // 扫描 WiFi
        btnScanWifi.setOnClickListener {
            wifiList.clear()
            adapter.notifyDataSetChanged()
            BLEManager.sendCommand("WIFI_SCAN")
            Toast.makeText(this, "正在扫描...", Toast.LENGTH_SHORT).show()
        }

        // 选择 WiFi
        listWifi.setOnItemClickListener { _, _, position, _ ->
            selectedSsid = wifiList[position].substringBefore(",")
            Toast.makeText(this, "已选择: $selectedSsid", Toast.LENGTH_SHORT).show()
        }

        // 连接 WiFi
        btnConnectWifi.setOnClickListener {
            val ssid = selectedSsid
            if (ssid == null) {
                Toast.makeText(this, "请先选择一个 WiFi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pwd = etWifiPassword.text.toString()
            BLEManager.sendCommand("WIFI_CONNECT:$ssid,$pwd")
            Toast.makeText(this, "正在连接 $ssid ...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BLEManager.removeListener(this)
    }

    override fun onScanning() {}
    override fun onConnected() {}
    override fun onDisconnected() {}

    override fun onDataReceived(data: String) {
        if (data.startsWith("SCAN:")) {
            val payload = data.substring(5)
            val items = payload.split(";")
            wifiList.clear()
            for (item in items) {
                if (item.isNotEmpty()) {
                    wifiList.add(item) // 格式: ssid,rssi
                }
            }
            runOnUiThread {
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "扫描到 ${wifiList.size} 个 WiFi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onError(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}