package com.safeshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.safeshield.navigation.SafeShieldNavGraph
import com.safeshield.ui.theme.SafeShieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SafeShieldTheme {
                SafeShieldNavGraph()
            }
        }
    }
}
