package com.example.esp32s3_led_ble

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), BLEManager.Listener {

    private lateinit var tvStatus: TextView
    private lateinit var tvWifiInfo: TextView
    private lateinit var tvIpInfo: TextView
    private lateinit var viewCurrentColor: View
    private lateinit var btnReset: ImageButton
    private lateinit var btnCopyIp: ImageButton
    private lateinit var btnScan: Button
    private lateinit var btnToggle: Button
    private lateinit var btnOpenColor: Button
    private lateinit var btnOpenWifi: Button
    private lateinit var seekBrightness: SeekBar
    private lateinit var seekStatusBright: SeekBar
    private lateinit var radioGroupMode: RadioGroup
    private lateinit var etBlinkInterval: EditText
    private lateinit var btnApplyBlink: Button

    private var isLightOn = false
    private var currentIp = "--"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            BLEManager.startScan()
        } else {
            Toast.makeText(this, "需要蓝牙和定位权限才能扫描设备", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        BLEManager.init(this)
        BLEManager.addListener(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvWifiInfo = findViewById(R.id.tvWifiInfo)
        tvIpInfo = findViewById(R.id.tvIpInfo)
        viewCurrentColor = findViewById(R.id.viewCurrentColor)
        btnReset = findViewById(R.id.btnReset)
        btnCopyIp = findViewById(R.id.btnCopyIp)
        btnScan = findViewById(R.id.btnScan)
        btnToggle = findViewById(R.id.btnToggle)
        btnOpenColor = findViewById(R.id.btnOpenColor)
        btnOpenWifi = findViewById(R.id.btnOpenWifi)
        seekBrightness = findViewById(R.id.seekBrightness)
        seekStatusBright = findViewById(R.id.seekStatusBright)
        radioGroupMode = findViewById(R.id.radioGroupMode)
        etBlinkInterval = findViewById(R.id.etBlinkInterval)
        btnApplyBlink = findViewById(R.id.btnApplyBlink)

        tvWifiInfo.text = "WiFi: -- | 信号: --"
        tvIpInfo.text = "IP: --"
        viewCurrentColor.setBackgroundColor(GlobalState.currentColor)
        seekBrightness.progress = GlobalState.brightness
        btnScan.visibility = View.VISIBLE

        btnReset.setOnClickListener {
            BLEManager.disconnect()
            btnScan.postDelayed({ BLEManager.startScan() }, 500)
        }

        btnCopyIp.setOnClickListener {
            if (currentIp == "--" || currentIp.isEmpty()) {
                Toast.makeText(this, "暂无可复制的 IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IP", currentIp))
            Toast.makeText(this, "IP 已复制: $currentIp", Toast.LENGTH_SHORT).show()
        }

        btnScan.setOnClickListener {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
                BLEManager.startScan()
            } else {
                permissionLauncher.launch(permissions)
            }
        }

        btnToggle.setOnClickListener {
            isLightOn = !isLightOn
            BLEManager.sendCommand(if (isLightOn) "ON" else "OFF")
            btnToggle.text = if (isLightOn) "关灯" else "开灯"
            btnToggle.backgroundTintList = ContextCompat.getColorStateList(
                this, if (isLightOn) android.R.color.holo_red_dark else android.R.color.holo_green_dark
            )
        }

        btnOpenColor.setOnClickListener {
            if (!BLEManager.isConnected) {
                Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, ColorPickerActivity::class.java))
        }

        btnOpenWifi.setOnClickListener {
            if (!BLEManager.isConnected) {
                Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, WifiControlActivity::class.java))
        }

        seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                GlobalState.brightness = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                BLEManager.sendCommand("BRIGHT:${seekBar?.progress}")
            }
        })

        seekStatusBright.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                BLEManager.sendCommand("STATUS_BRIGHT:${seekBar?.progress}")
            }
        })

        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioOn -> 0
                R.id.radioBreath -> 1
                R.id.radioRainbow -> 2
                R.id.radioBlink -> 3
                else -> 0
            }
            BLEManager.sendCommand("MODE:$mode")
        }

        btnApplyBlink.setOnClickListener {
            val interval = etBlinkInterval.text.toString().toIntOrNull()
            if (interval != null && interval in 50..5000) {
                BLEManager.sendCommand("BLINK_INTERVAL:$interval")
                Toast.makeText(this, "闪烁间隔已设为 $interval ms", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请输入 50-5000 之间的数值", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewCurrentColor.setBackgroundColor(GlobalState.currentColor)
        seekBrightness.progress = GlobalState.brightness
    }

    override fun onDestroy() {
        super.onDestroy()
        BLEManager.removeListener(this)
    }

    override fun onScanning() {
        runOnUiThread {
            tvStatus.text = "状态：正在扫描..."
            tvStatus.setTextColor(Color.parseColor("#FF9800"))
            btnScan.isEnabled = false
        }
    }

    override fun onConnected() {
        runOnUiThread {
            tvStatus.text = "状态：已连接"
            tvStatus.setTextColor(Color.parseColor("#00FF00"))
            btnScan.visibility = View.GONE
            btnScan.isEnabled = true
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            tvStatus.text = "状态：未连接"
            tvStatus.setTextColor(Color.parseColor("#FF4444"))
            tvWifiInfo.text = "WiFi: -- | 信号: --"
            tvIpInfo.text = "IP: --"
            currentIp = "--"
            btnScan.visibility = View.VISIBLE
            btnScan.isEnabled = true
        }
    }

    override fun onDataReceived(data: String) {
        if (!data.startsWith("W:")) return
        val parts = data.split("|")
        val ssid = parts.getOrNull(0)?.substringAfter("W:") ?: "--"
        val ip = parts.getOrNull(1)?.substringAfter("I:") ?: "--"
        val wifiRssi = parts.getOrNull(2)?.substringAfter("R:") ?: "--"
        val enabled = parts.getOrNull(3)?.substringAfter("E:") ?: "1"

        currentIp = ip
        GlobalState.wifiEnabled = enabled == "1"

        val line1 = "WiFi: $ssid | 信号: $wifiRssi dBm"
        val line2 = "IP: $ip"

        runOnUiThread {
            tvWifiInfo.text = line1
            tvIpInfo.text = line2
        }
    }

    override fun onError(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            btnScan.isEnabled = true
        }
    }
}