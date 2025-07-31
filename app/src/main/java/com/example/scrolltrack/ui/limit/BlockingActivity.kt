package com.example.scrolltrack.ui.limit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scrolltrack.MainActivity
import com.example.scrolltrack.navigation.ScreenRoutes
import com.example.scrolltrack.util.SnoozeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BlockingActivity : ComponentActivity() {

    @Inject
    lateinit var snoozeManager: SnoozeManager
    private val viewModel: BlockingViewModel by viewModels()

    companion object {
        const val EXTRA_PACKAGE_NAME = "com.example.scrolltrack.EXTRA_PACKAGE_NAME"
        const val EXTRA_GROUP_ID = "com.example.scrolltrack.EXTRA_GROUP_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)

        viewModel.loadGroupName(groupId)

        setContent {
            val groupName by viewModel.groupName.collectAsStateWithLifecycle()

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlockingScreenContent(
                        groupName = groupName ?: "this app",
                        onSnoozeClick = {
                            if (groupId != -1L) {
                                snoozeManager.snoozeGroup(groupId)
                            }
                            finish()
                        },
                        onViewStatsClick = {
                            if (packageName != null) {
                                val intent = Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    // Pass the destination route as an extra
                                    putExtra("destination_route", ScreenRoutes.AppDetailRoute.createRoute(packageName))
                                }
                                startActivity(intent)
                            }
                            finish()
                        },
                        onOkClick = {
                            // This intent sends the user to their home screen
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BlockingScreenContent(
    groupName: String,
    onSnoozeClick: () -> Unit,
    onViewStatsClick: () -> Unit,
    onOkClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Time's up for $groupName!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onSnoozeClick, modifier = Modifier.fillMaxWidth()) {
            Text("Snooze for 5 minutes")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onViewStatsClick, modifier = Modifier.fillMaxWidth()) {
            Text("View Stats")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onOkClick, modifier = Modifier.fillMaxWidth()) {
            Text("OK")
        }
    }
}