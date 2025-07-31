package com.example.scrolltrack.ui.limit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.scrolltrack.util.SnoozeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BlockingActivity : ComponentActivity() {

    @Inject
    lateinit var snoozeManager: SnoozeManager

    companion object {
        const val EXTRA_PACKAGE_NAME = "com.example.scrolltrack.EXTRA_PACKAGE_NAME"
        const val EXTRA_GROUP_ID = "com.example.scrolltrack.EXTRA_GROUP_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Time's up for this app!")
                        Button(onClick = {
                            if (groupId != -1L) {
                                snoozeManager.snoozeGroup(groupId)
                            }
                            finish()
                        }) {
                            Text("Snooze for 5 minutes")
                        }
                    }
                }
            }
        }
    }
}