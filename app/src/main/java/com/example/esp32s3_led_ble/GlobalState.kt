package com.example.esp32s3_led_ble

import android.graphics.Color

object GlobalState {
    // 当前颜色，默认红色（避免纯白导致色相条失效）
    var currentColor: Int = Color.RED

    // 当前亮度，默认 100
    var brightness: Int = 100
}