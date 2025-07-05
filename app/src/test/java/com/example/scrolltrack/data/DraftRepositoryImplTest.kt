package com.example.scrolltrack.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DraftRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var repository: DraftRepositoryImpl
    private lateinit var sharedPreferences: SharedPreferences

    private companion object {
        const val PREFS_NAME = "ScrollTrackPrefs" // Must match the one in DraftRepositoryImpl
        const val KEY_DRAFT_PKG = "draft_package_name"
        const val KEY_DRAFT_ACTIVITY = "draft_activity_name"
        const val KEY_DRAFT_SCROLL = "draft_scroll_amount"
        const val KEY_DRAFT_START_TIME = "draft_start_time"
        const val KEY_DRAFT_LAST_UPDATE = "draft_last_update_time"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = DraftRepositoryImpl(context)
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Clear shared preferences before each test
        sharedPreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun `saveDraft - saves all fields correctly`() {
        val draft = SessionDraft("com.test.app", "MainActivity", 123L, 1000L, 2000L)
        repository.saveDraft(draft)

        assertThat(sharedPreferences.getString(KEY_DRAFT_PKG, null)).isEqualTo(draft.packageName)
        assertThat(sharedPreferences.getString(KEY_DRAFT_ACTIVITY, null)).isEqualTo(draft.activityName)
        assertThat(sharedPreferences.getLong(KEY_DRAFT_SCROLL, 0L)).isEqualTo(draft.scrollAmount)
        assertThat(sharedPreferences.getLong(KEY_DRAFT_START_TIME, 0L)).isEqualTo(draft.startTime)
        assertThat(sharedPreferences.getLong(KEY_DRAFT_LAST_UPDATE, 0L)).isEqualTo(draft.lastUpdateTime)
    }

    @Test
    fun `getDraft - retrieves saved draft correctly`() {
        val draftToSave = SessionDraft("com.test.app", "MainActivity", 123L, 1000L, 2000L)
        repository.saveDraft(draftToSave)

        val retrievedDraft = repository.getDraft()
        assertThat(retrievedDraft).isNotNull()
        assertThat(retrievedDraft).isEqualTo(draftToSave)
    }

    @Test
    fun `getDraft - no draft saved - returns null`() {
        val retrievedDraft = repository.getDraft()
        assertThat(retrievedDraft).isNull()
    }

    @Test
    fun `getDraft - package name missing in prefs - returns null`() {
        sharedPreferences.edit()
            // .putString(KEY_DRAFT_PKG, null) // Not putting it is equivalent
            .putLong(KEY_DRAFT_SCROLL, 100L)
            .putLong(KEY_DRAFT_START_TIME, 1000L)
            .commit()
        assertThat(repository.getDraft()).isNull()
    }

    @Test
    fun `getDraft - startTime is zero in prefs - returns null`() {
        sharedPreferences.edit()
            .putString(KEY_DRAFT_PKG, "com.test.app")
            .putLong(KEY_DRAFT_SCROLL, 100L)
            .putLong(KEY_DRAFT_START_TIME, 0L) // Invalid start time
            .commit()
        assertThat(repository.getDraft()).isNull()
    }

    @Test
    fun `getDraft - scrollAmount is zero in prefs - returns null`() {
        // Based on the current implementation: if (packageName != null && startTime != 0L && scrollAmount > 0L)
        sharedPreferences.edit()
            .putString(KEY_DRAFT_PKG, "com.test.app")
            .putLong(KEY_DRAFT_SCROLL, 0L) // Invalid scroll amount for retrieval
            .putLong(KEY_DRAFT_START_TIME, 1000L)
            .commit()
        assertThat(repository.getDraft()).isNull()
    }

    @Test
    fun `getDraft - scrollAmount is positive - returns draft`() {
        val draftToSave = SessionDraft("com.test.app", "MainActivity", 1L, 1000L, 2000L)
        repository.saveDraft(draftToSave)
        val retrievedDraft = repository.getDraft()
        assertThat(retrievedDraft).isEqualTo(draftToSave)
    }


    @Test
    fun `saveDraft - with null activityName - saves and retrieves correctly`() {
        val draftToSave = SessionDraft("com.test.app", null, 123L, 1000L, 2000L)
        repository.saveDraft(draftToSave)

        val retrievedDraft = repository.getDraft()
        assertThat(retrievedDraft).isNotNull()
        assertThat(retrievedDraft!!.packageName).isEqualTo(draftToSave.packageName)
        assertThat(retrievedDraft.activityName).isNull()
        assertThat(retrievedDraft.scrollAmount).isEqualTo(draftToSave.scrollAmount)
        assertThat(retrievedDraft.startTime).isEqualTo(draftToSave.startTime)
        assertThat(retrievedDraft.lastUpdateTime).isEqualTo(draftToSave.lastUpdateTime)
    }

    @Test
    fun `clearDraft - removes all draft keys`() {
        val draft = SessionDraft("com.test.app", "MainActivity", 123L, 1000L, 2000L)
        repository.saveDraft(draft)

        repository.clearDraft()

        assertThat(sharedPreferences.contains(KEY_DRAFT_PKG)).isFalse()
        assertThat(sharedPreferences.contains(KEY_DRAFT_ACTIVITY)).isFalse()
        assertThat(sharedPreferences.contains(KEY_DRAFT_SCROLL)).isFalse()
        assertThat(sharedPreferences.contains(KEY_DRAFT_START_TIME)).isFalse()
        assertThat(sharedPreferences.contains(KEY_DRAFT_LAST_UPDATE)).isFalse()
    }

    @Test
    fun `clearDraft - when no draft exists - does not crash`() {
        // No draft saved
        repository.clearDraft() // Should execute without error
        assertThat(sharedPreferences.getAll()).isEmpty()
    }

    @Test
    fun `saveDraft - overwrites existing draft`() {
        val draft1 = SessionDraft("com.app.old", "OldActivity", 10L, 100L, 200L)
        repository.saveDraft(draft1)

        val draft2 = SessionDraft("com.app.new", "NewActivity", 20L, 300L, 400L)
        repository.saveDraft(draft2)

        val retrievedDraft = repository.getDraft()
        assertThat(retrievedDraft).isNotNull()
        assertThat(retrievedDraft).isEqualTo(draft2)
    }
}
