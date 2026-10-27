package com.agendabarber.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.agendabarber.app.ui.AgendaApp
import com.agendabarber.app.ui.theme.AgendaBarberTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgendaBarberTheme {
                AgendaApp()
            }
        }
    }
}
