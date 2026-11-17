package com.jagadaun.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jagadaun.app.ui.theme.JagaDaunTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JagaDaunTheme {
                JagaDaunApp(rememberAppContainer())
            }
        }
    }
}
