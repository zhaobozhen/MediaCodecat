package com.absinthe.mediacodecat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.absinthe.mediacodecat.ui.theme.MediaCodecatTheme
import com.absinthe.mediacodecat.ui.view.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaCodecatTheme {
                MainScreen()
            }
        }
    }
}