package com.example.esp32s3_led_ble

import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class WifiControlActivity : AppCompatActivity(), BLEManager.Listener {

    private lateinit var switchWifi: Switch
    private lateinit var btnScanWifi: Button
    private lateinit var listWifi: ListView
    private lateinit var lstSavedWifi: ListView
    private lateinit var etWifiPassword: EditText
    private lateinit var btnConnectWifi: Button

    private val nearbyItems = mutableListOf<String>()
    private val savedItems = mutableListOf<String>()
    private var selectedSsid: String? = null
    private lateinit var nearbyAdapter: ArrayAdapter<String>
    private lateinit var savedAdapter: ArrayAdapter<String>

    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_control)

        switchWifi = findViewById(R.id.switchWifi)
        btnScanWifi = findViewById(R.id.btnScanWifi)
        listWifi = findViewById(R.id.listWifi)
        lstSavedWifi = findViewById(R.id.lstSavedWifi)
        etWifiPassword = findViewById(R.id.etWifiPassword)
        btnConnectWifi = findViewById(R.id.btnConnectWifi)

        nearbyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, nearbyItems)
        listWifi.adapter = nearbyAdapter

        savedAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, savedItems)
        lstSavedWifi.adapter = savedAdapter

        BLEManager.addListener(this)

        // 初始化开关状态
        switchWifi.isChecked = GlobalState.wifiEnabled

        // 请求已保存列表
        BLEManager.sendCommand("WIFI_LIST")

        switchWifi.setOnCheckedChangeListener { _, isChecked ->
            if (!isReady) return@setOnCheckedChangeListener
            GlobalState.wifiEnabled = isChecked
            BLEManager.sendCommand(if (isChecked) "WIFI_ON" else "WIFI_OFF")
        }

        btnScanWifi.setOnClickListener {
            nearbyItems.clear()
            nearbyAdapter.notifyDataSetChanged()
            selectedSsid = null
            BLEManager.sendCommand("WIFI_SCAN")
            Toast.makeText(this, "正在扫描...", Toast.LENGTH_SHORT).show()
        }

        // 点击附近列表：选中，等待用户输入密码
        listWifi.setOnItemClickListener { _, _, position, _ ->
            selectedSsid = nearbyItems[position].substringBefore(",")
            etWifiPassword.hint = "请输入 $selectedSsid 的密码"
            etWifiPassword.requestFocus()
            Toast.makeText(this, "已选择: $selectedSsid", Toast.LENGTH_SHORT).show()
        }

        // 点击已保存列表：直接连接（ESP32 用已保存的密码）
        lstSavedWifi.setOnItemClickListener { _, _, position, _ ->
            val ssid = savedItems[position]
            BLEManager.sendCommand("WIFI_CONNECT_SAVED:$ssid")
            Toast.makeText(this, "正在连接 $ssid ...", Toast.LENGTH_SHORT).show()
        }

        // 长按已保存列表：忘记
        lstSavedWifi.setOnItemLongClickListener { _, _, position, _ ->
            val ssid = savedItems[position]
            AlertDialog.Builder(this)
                .setTitle("忘记 WiFi")
                .setMessage("确定要忘记 \"$ssid\" 吗？")
                .setPositiveButton("忘记") { _, _ ->
                    BLEManager.sendCommand("WIFI_FORGET:$ssid")
                    Toast.makeText(this, "已请求忘记 $ssid", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        // 连接按钮：使用附近列表选中的 SSID + 输入的密码
        btnConnectWifi.setOnClickListener {
            val ssid = selectedSsid
            if (ssid.isNullOrEmpty()) {
                Toast.makeText(this, "请先从附近列表中选择一个 WiFi", Toast.LENGTH_SHORT).show()
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
        when {
            data.startsWith("SCAN:") -> {
                val payload = data.substring(5)
                nearbyItems.clear()
                if (payload.isNotEmpty()) {
                    for (item in payload.split(";")) {
                        if (item.isNotEmpty()) nearbyItems.add(item)
                    }
                }
                runOnUiThread {
                    nearbyAdapter.notifyDataSetChanged()
                    Toast.makeText(this, "扫描到 ${nearbyItems.size} 个 WiFi", Toast.LENGTH_SHORT).show()
                }
            }
            data.startsWith("WLIST:") -> {
                val payload = data.substring(6)
                savedItems.clear()
                if (payload.isNotEmpty()) {
                    for (item in payload.split(";")) {
                        if (item.isNotEmpty()) savedItems.add(item)
                    }
                }
                runOnUiThread {
                    savedAdapter.notifyDataSetChanged()
                }
            }
            data.startsWith("W:") -> {
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
    }

    override fun onError(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}