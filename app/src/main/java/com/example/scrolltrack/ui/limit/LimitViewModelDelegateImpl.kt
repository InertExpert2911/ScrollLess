package com.example.scrolltrack.ui.limit

import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.ui.main.SetLimitSheetState
import com.example.scrolltrack.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt


class LimitViewModelDelegateImpl @Inject constructor(
    private val scrollDataRepository: ScrollDataRepository,
    private val limitsRepository: LimitsRepository,
    private val dateUtil: DateUtil
) : LimitViewModelDelegate {

    private val _setLimitSheetState = MutableStateFlow<SetLimitSheetState?>(null)
    override val setLimitSheetState: StateFlow<SetLimitSheetState?> = _setLimitSheetState.asStateFlow()

    override fun onQuickLimitIconClicked(scope: CoroutineScope, packageName: String, appName: String) {
        scope.launch {
            val sevenDaysAgo = (0..6).map { dateUtil.getPastDateString(it) }
            val averageUsageFlow = scrollDataRepository.getAverageUsageForPackage(packageName, sevenDaysAgo)
            val existingLimitFlow = limitsRepository.getLimitedApp(packageName)

            combine(averageUsageFlow, existingLimitFlow) { averageUsage, existingLimit ->
                val existingLimitMinutes = if (existingLimit != null) {
                    limitsRepository.getGroupWithApps(existingLimit.group_id).firstOrNull()?.group?.time_limit_minutes
                } else {
                    null
                }

                val suggestedLimitMinutes = averageUsage?.let {
                    val suggestedMillis = it * 0.9
                    // Round to nearest 5 minutes
                    (suggestedMillis / (1000 * 60 * 5)).roundToInt() * 5
                }

                _setLimitSheetState.value = SetLimitSheetState(
                    packageName = packageName,
                    appName = appName,
                    existingLimitMinutes = existingLimitMinutes,
                    averageUsageMillis = averageUsage,
                    suggestedLimitMinutes = suggestedLimitMinutes
                )
            }.first()
        }
    }

    override fun onSetLimit(scope: CoroutineScope, packageName: String, limitMinutes: Int) {
        scope.launch {
            limitsRepository.setAppLimit(packageName, limitMinutes)
            dismissSetLimitSheet()
        }
    }

    override fun onDeleteLimit(scope: CoroutineScope, packageName: String) {
        scope.launch {
            limitsRepository.removeAppLimit(packageName)
            dismissSetLimitSheet()
        }
    }

    override fun dismissSetLimitSheet() {
        _setLimitSheetState.value = null
    }
}