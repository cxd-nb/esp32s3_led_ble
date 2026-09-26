package com.example.esp32s3_led_ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

@SuppressLint("MissingPermission")
object BLEManager {
    private const val TAG = "ESP32_BLE_DEBUG"
    private const val DEVICE_NAME = "ESP32_LED"

    private val UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var appContext: Context? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    var isConnected: Boolean = false
        private set

    private var isScanning = false

    interface Listener {
        fun onScanning()
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(data: String)
        fun onError(msg: String)
    }

    private val listeners = mutableListOf<Listener>()

    fun addListener(l: Listener) { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    fun init(context: Context) {
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    fun startScan() {
        if (isScanning) return
        if (bluetoothAdapter?.isEnabled == false) {
            notifyError("请先打开手机蓝牙")
            return
        }
        isScanning = true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        listeners.forEach { it.onScanning() }
        bluetoothLeScanner?.startScan(null, settings, scanCallback)
        Handler(Looper.getMainLooper()).postDelayed({
            stopScan()
        }, 10000)
    }

    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        isConnected = false
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bluetoothGatt = null
        rxCharacteristic = null
        txCharacteristic = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name ?: return@let
                Log.i(TAG, "扫描到: $name (${device.address})")
                if (name == DEVICE_NAME) {
                    stopScan()
                    disconnect()
                    Handler(Looper.getMainLooper()).postDelayed({
                        bluetoothGatt = device.connectGatt(
                            appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                        )
                    }, 300)
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "扫描失败: $errorCode")
            notifyError("扫描失败，错误码 $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt = gatt
                isConnected = true
                Log.d(TAG, "已连接，请求 MTU 247...")
                if (gatt?.requestMtu(247) == false) {
                    gatt?.discoverServices()
                }
                listeners.forEach { it.onConnected() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                try { gatt?.close() } catch (e: Exception) {}
                bluetoothGatt = null
                rxCharacteristic = null
                txCharacteristic = null
                listeners.forEach { it.onDisconnected() }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d(TAG, "MTU 已协商: $mtu, status: $status")
            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt?.getService(UART_UUID) ?: return
            rxCharacteristic = service.getCharacteristic(RX_UUID)
            txCharacteristic = service.getCharacteristic(TX_UUID)

            txCharacteristic?.let { tx ->
                gatt.setCharacteristicNotification(tx, true)
                val cccd = tx.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(cccd)
                    }
                    Log.d(TAG, "已开启 TX 通知")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) {
            if (characteristic.uuid == TX_UUID) handleRx(value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (characteristic.uuid == TX_UUID) {
                    characteristic.value?.let { handleRx(it) }
                }
            }
        }
    }

    private fun handleRx(value: ByteArray) {
        val data = value.toString(Charsets.UTF_8).trim()
        Log.d(TAG, "收到通知: $data")
        listeners.forEach { it.onDataReceived(data) }
    }

    fun sendCommand(cmd: String) {
        val gatt = bluetoothGatt ?: return
        val ch = rxCharacteristic ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(ch, cmd.toByteArray(Charsets.UTF_8),
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            ch.value = cmd.toByteArray(Charsets.UTF_8)
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        }
        Log.d(TAG, "发送: $cmd")
    }

    private fun notifyError(msg: String) {
        listeners.forEach { it.onError(msg) }
    }
}