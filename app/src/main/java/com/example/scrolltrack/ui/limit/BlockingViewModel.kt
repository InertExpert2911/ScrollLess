package com.example.scrolltrack.ui.limit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.LimitsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockingViewModel @Inject constructor(
    private val limitsRepository: LimitsRepository
) : ViewModel() {
    private val _groupName = MutableStateFlow<String?>(null)
    val groupName = _groupName.asStateFlow()

    fun loadGroupName(groupId: Long) {
        if (groupId == -1L) return
        viewModelScope.launch {
            val group = limitsRepository.getGroupWithApps(groupId).firstOrNull()?.group
            // For invisible groups, we clean up the name to be more user-friendly.
            val displayName = group?.name?.replace("invisible_group_for_", "") ?: "this app"
            _groupName.value = displayName
        }
    }
}