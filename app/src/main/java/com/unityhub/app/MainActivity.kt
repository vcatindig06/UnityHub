package com.unityhub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.unityhub.app.ui.UnityHubApp
import com.unityhub.app.ui.theme.UnityHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnityHubTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    UnityHubApp()
                }
            }
        }
    }
}
