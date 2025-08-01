package com.example.scrolltrack.ui.limit

import com.example.scrolltrack.ui.main.SetLimitSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface LimitViewModelDelegate {
    val setLimitSheetState: StateFlow<SetLimitSheetState?>

    fun onQuickLimitIconClicked(scope: CoroutineScope, packageName: String, appName: String)
    fun onSetLimit(scope: CoroutineScope, packageName: String, limitMinutes: Int)
    fun onDeleteLimit(scope: CoroutineScope, packageName: String)
    fun dismissSetLimitSheet()
}