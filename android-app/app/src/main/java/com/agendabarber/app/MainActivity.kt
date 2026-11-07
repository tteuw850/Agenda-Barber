package com.agendabarber.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.agendabarber.app.ui.AgendaApp
import com.agendabarber.app.ui.theme.AgendaBarberTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            AgendaBarberTheme {
                AgendaApp()
            }
        }
    }
}
