package com.example.scrolltrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UnlockBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rawAppEventDao: RawAppEventDao
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val TAG = "UnlockBroadcastReceiver"
    private val UNLOCK_PACKAGE_NAME = "android.system.unlock"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_USER_PRESENT) {
            Log.d(TAG, "ACTION_USER_PRESENT broadcast received.")

            receiverScope.launch {
                try {
                    val unlockEvent = RawAppEvent(
                        packageName = UNLOCK_PACKAGE_NAME,
                        className = null,
                        eventType = RawAppEvent.EVENT_TYPE_USER_PRESENT,
                        eventTimestamp = System.currentTimeMillis(),
                        eventDateString = DateUtil.getCurrentLocalDateString(),
                        source = RawAppEvent.SOURCE_SYSTEM_BROADCAST
                    )
                    rawAppEventDao.insertEvent(unlockEvent)
                    Log.i(TAG, "Logged user unlock event to database.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert unlock event into database.", e)
                }
            }
        }
    }
} 