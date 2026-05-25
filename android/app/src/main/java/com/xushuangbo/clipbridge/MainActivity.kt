package com.xushuangbo.clipbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xushuangbo.clipbridge.app.ClipBridgeApp
import com.xushuangbo.clipbridge.ui.theme.ClipBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipBridgeTheme {
                ClipBridgeApp()
            }
        }
    }
}
