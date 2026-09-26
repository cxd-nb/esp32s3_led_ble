package com.example.esp32s3_led_ble

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

class ColorPickerActivity : AppCompatActivity() {

    private lateinit var colorPicker: ColorPickerView
    private lateinit var etColorHex: EditText
    private lateinit var viewColorPreview: View
    private lateinit var seekBrightnessPicker: SeekBar
    private lateinit var btnColorDone: Button
    private var currentColor = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_color_picker)

        colorPicker = findViewById(R.id.colorPicker)
        etColorHex = findViewById(R.id.etColorHex)
        viewColorPreview = findViewById(R.id.viewColorPreview)
        seekBrightnessPicker = findViewById(R.id.seekBrightnessPicker)
        btnColorDone = findViewById(R.id.btnColorDone)

        // 读取全局颜色和亮度，保证与主页一致
        currentColor = GlobalState.currentColor
        colorPicker.setColor(currentColor)
        etColorHex.setText(String.format("#%06X", 0xFFFFFF and currentColor))
        viewColorPreview.setBackgroundColor(currentColor)
        seekBrightnessPicker.progress = GlobalState.brightness

        colorPicker.onColorChanged = { color: Int ->
            currentColor = color
            GlobalState.currentColor = color
            viewColorPreview.setBackgroundColor(color)
            etColorHex.setText(String.format("#%06X", 0xFFFFFF and color))
            sendColor()
        }

        etColorHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hex = s.toString().trim()
                if (hex.length == 7 && hex.startsWith("#")) {
                    try {
                        val color = Color.parseColor(hex)
                        currentColor = color
                        GlobalState.currentColor = color
                        colorPicker.setColor(color)
                        viewColorPreview.setBackgroundColor(color)
                        sendColor()
                    } catch (_: Exception) { }
                }
            }
        })

        seekBrightnessPicker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 实时更新全局亮度，返回主页时保持一致
                GlobalState.brightness = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                BLEManager.sendCommand("BRIGHT:${seekBar?.progress}")
            }
        })

        btnColorDone.setOnClickListener { finish() }
    }

    private fun sendColor() {
        val r = Color.red(currentColor)
        val g = Color.green(currentColor)
        val b = Color.blue(currentColor)
        BLEManager.sendCommand("COLOR:$r,$g,$b")
    }
}