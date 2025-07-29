package com.example.scrolltrack.ui.limit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.example.scrolltrack.ui.theme.ScrollTrackTheme

class BlockingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScrollTrackTheme {
                Text("Time's up for this app group!")
            }
        }
    }
}