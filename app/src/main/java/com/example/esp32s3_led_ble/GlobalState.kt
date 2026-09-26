package com.example.esp32s3_led_ble

import android.graphics.Color

object GlobalState {
    var currentColor: Int = Color.RED
    var brightness: Int = 100
    var wifiEnabled: Boolean = true
}