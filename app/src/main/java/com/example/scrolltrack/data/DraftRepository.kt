package com.example.scrolltrack.data

data class SessionDraft(
    val packageName: String,
    val activityName: String?,
    val scrollAmount: Long,
    val startTime: Long,
    val lastUpdateTime: Long
)

interface DraftRepository {
    fun getDraft(): SessionDraft?
    fun saveDraft(draft: SessionDraft)
    fun clearDraft()
} 