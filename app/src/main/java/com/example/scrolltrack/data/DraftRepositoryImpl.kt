package com.example.scrolltrack.data

import android.content.Context
import android.content.SharedPreferences

class DraftRepositoryImpl(context: Context) : DraftRepository {

    private val sharedPreferences: SharedPreferences

    private companion object {
        const val PREFS_NAME = "ScrollTrackPrefs"
        const val KEY_DRAFT_PKG = "draft_package_name"
        const val KEY_DRAFT_ACTIVITY = "draft_activity_name"
        const val KEY_DRAFT_SCROLL = "draft_scroll_amount"
        const val KEY_DRAFT_START_TIME = "draft_start_time"
        const val KEY_DRAFT_LAST_UPDATE = "draft_last_update_time"
    }

    init {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getDraft(): SessionDraft? {
        val packageName = sharedPreferences.getString(KEY_DRAFT_PKG, null)
        val activityName = sharedPreferences.getString(KEY_DRAFT_ACTIVITY, null)
        val scrollAmount = sharedPreferences.getLong(KEY_DRAFT_SCROLL, 0L)
        val startTime = sharedPreferences.getLong(KEY_DRAFT_START_TIME, 0L)
        val lastUpdateTime = sharedPreferences.getLong(KEY_DRAFT_LAST_UPDATE, 0L)

        return if (packageName != null && startTime != 0L && scrollAmount > 0L) {
            SessionDraft(packageName, activityName, scrollAmount, startTime, lastUpdateTime)
        } else {
            null
        }
    }

    override fun saveDraft(draft: SessionDraft) {
        with(sharedPreferences.edit()) {
            putString(KEY_DRAFT_PKG, draft.packageName)
            putString(KEY_DRAFT_ACTIVITY, draft.activityName)
            putLong(KEY_DRAFT_SCROLL, draft.scrollAmount)
            putLong(KEY_DRAFT_START_TIME, draft.startTime)
            putLong(KEY_DRAFT_LAST_UPDATE, draft.lastUpdateTime)
            apply()
        }
    }

    override fun clearDraft() {
        with(sharedPreferences.edit()) {
            remove(KEY_DRAFT_PKG)
            remove(KEY_DRAFT_ACTIVITY)
            remove(KEY_DRAFT_SCROLL)
            remove(KEY_DRAFT_START_TIME)
            remove(KEY_DRAFT_LAST_UPDATE)
            apply()
        }
    }
} 