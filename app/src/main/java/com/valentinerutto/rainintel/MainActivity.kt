package com.valentinerutto.rainintel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.valentinerutto.rainintel.navigation.AppNavGraph
import com.valentinerutto.rainintel.ui.theme.RainIntelTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RainIntelTheme {
                AppNavGraph()
            }
        }

    }
}
