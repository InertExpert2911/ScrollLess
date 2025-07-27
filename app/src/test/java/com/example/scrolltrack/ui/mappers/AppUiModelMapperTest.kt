package com.example.scrolltrack.ui.mappers

import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AppUiModelMapperTest {

    private lateinit var mapper: AppUiModelMapper
    private val appMetadataRepository: AppMetadataRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        coEvery { appMetadataRepository.getAppMetadata(any()) } answers {
            val packageName = firstArg<String>()
            AppMetadata(packageName, packageName.substringAfterLast('.').capitalize() + " App", isUserVisible = true, isSystemApp = false, userHidesOverride = null, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1L)
        }
        coEvery { appMetadataRepository.getIconFile(any()) } returns null
        mapper = AppUiModelMapper(appMetadataRepository)
    }

    @Test
    fun `mapToAppUsageUiItems aggregates, averages, and sorts correctly`() = runTest {
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.b", dateString = "2023-10-26", usageTimeMillis = 1000, appOpenCount = 1),
            DailyAppUsageRecord(packageName = "com.app.a", dateString = "2023-10-26", usageTimeMillis = 4000, appOpenCount = 1),
            DailyAppUsageRecord(packageName = "com.app.a", dateString = "2023-10-27", usageTimeMillis = 2000, appOpenCount = 1)
        )

        val result = mapper.mapToAppUsageUiItems(records, days = 2)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.packageName }).containsExactly("com.app.a", "com.app.b").inOrder()
        assertThat(result[0].usageTimeMillis).isEqualTo(3000) // (4000 + 2000) / 2
        assertThat(result[1].usageTimeMillis).isEqualTo(500)   // 1000 / 2
    }

    @Test
    fun `mapToAppUsageUiItem uses fallback when metadata is missing`() = runTest {
        val record = DailyAppUsageRecord(packageName = "com.example.app", dateString = "2023-10-26", usageTimeMillis = 1000L, appOpenCount = 1)
        coEvery { appMetadataRepository.getAppMetadata("com.example.app") } returns null

        val result = mapper.mapToAppUsageUiItem(record)

        assertThat(result.appName).isEqualTo("app")
    }
}