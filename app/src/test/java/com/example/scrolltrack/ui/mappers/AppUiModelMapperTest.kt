package com.example.scrolltrack.ui.mappers

import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUiModelMapperTest {

    private lateinit var mapper: AppUiModelMapper
    private lateinit var appMetadataRepository: AppMetadataRepository

    @Before
    fun setUp() {
        appMetadataRepository = mockk(relaxed = true)
        mapper = AppUiModelMapper(appMetadataRepository)
    }

    @Test
    fun `test mapToAppUsageUiItem with missing metadata`() = runBlocking {
        val record = DailyAppUsageRecord(packageName = "com.example.app", dateString = "2023-10-26", usageTimeMillis = 1000L, appOpenCount = 1)
        coEvery { appMetadataRepository.getAppMetadata("com.example.app") } returns null

        val result = mapper.mapToAppUsageUiItem(record)

        assertThat(result.appName).isEqualTo("app")
        assertThat(result.icon).isNull()
    }

    @Test
    fun `test mapToAppScrollUiItem with missing metadata`() = runBlocking {
        val appData = AppScrollData("com.example.app", 100L, 50L, 50L, "type")
        coEvery { appMetadataRepository.getAppMetadata("com.example.app") } returns null

        val result = mapper.mapToAppScrollUiItem(appData)

        assertThat(result.appName).isEqualTo("app")
        assertThat(result.icon).isNull()
    }

    @Test
    fun `test mapToAppScrollUiItem unique id generation`() = runBlocking {
        val appData = AppScrollData("com.example.app", 100L, 50L, 50L, "type")
        coEvery { appMetadataRepository.getAppMetadata("com.example.app") } returns mockk<AppMetadata> {
            every { appName } returns "Test App"
        }

        val result = mapper.mapToAppScrollUiItem(appData)

        assertThat(result.id).isEqualTo("com.example.app-type")
    }
}