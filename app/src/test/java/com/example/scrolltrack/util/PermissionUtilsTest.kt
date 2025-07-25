package com.example.scrolltrack.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import com.example.scrolltrack.ScrollTrackService
import com.example.scrolltrack.services.NotificationListener
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
 
 @RunWith(RobolectricTestRunner::class)
 class PermissionUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockAppOpsManager: AppOpsManager
    private lateinit var mockAccessibilityManager: AccessibilityManager

    private val packageName = "com.example.scrolltrack"

    @Before
    fun setUp() {
        // Use a mocked context that can still provide system services
        mockContext = mockk(relaxed = true)
        mockAppOpsManager = mockk(relaxed = true)
        mockAccessibilityManager = mockk(relaxed = true)

        // Standard mocking for context behavior
        every { mockContext.packageName } returns packageName
        every { mockContext.getSystemService(Context.APP_OPS_SERVICE) } returns mockAppOpsManager
        every { mockContext.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns mockAccessibilityManager
        // Provide a real ContentResolver for Settings.Secure.putString to work with Robolectric
        every { mockContext.contentResolver } returns ApplicationProvider.getApplicationContext<Context>().contentResolver
    }

    // --- hasUsageStatsPermission Tests ---

    @Test
    fun hasUsageStatsPermission_whenPermissionIsAllowed_returnsTrue() {
        every {
            mockAppOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } returns AppOpsManager.MODE_ALLOWED

        val result = PermissionUtils.hasUsageStatsPermission(mockContext)
        assertThat(result).isTrue()
    }

    @Test
    fun hasUsageStatsPermission_whenPermissionIsDenied_returnsFalse() {
        every {
            mockAppOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } returns AppOpsManager.MODE_ERRORED // Or MODE_DEFAULT, MODE_IGNORED

        val result = PermissionUtils.hasUsageStatsPermission(mockContext)
        assertThat(result).isFalse()
    }

    @Test
    fun hasUsageStatsPermission_whenAppOpsManagerIsNull_returnsFalse() {
        every { mockContext.getSystemService(Context.APP_OPS_SERVICE) } returns null
        val result = PermissionUtils.hasUsageStatsPermission(mockContext)
        assertThat(result).isFalse()
    }


    // --- isNotificationListenerEnabled Tests ---

    @Test
    fun isNotificationListenerEnabled_whenListenerIsPresentInSettings_returnsTrue() {
        val componentName = ComponentName(packageName, NotificationListener::class.java.name).flattenToString()
        val settingString = "some.other.listener:$componentName:another.one"
        // FIX: Use the standard Android SDK method, not the Robolectric shadow method.
        // This resolves the "Val cannot be reassigned" error.
        Settings.Secure.putString(mockContext.contentResolver, "enabled_notification_listeners", settingString)

        val result = PermissionUtils.isNotificationListenerEnabled(mockContext, NotificationListener::class.java)
        assertThat(result).isTrue()
    }

    @Test
    fun isNotificationListenerEnabled_whenListenerIsTheOnlyOne_returnsTrue() {
        val componentName = ComponentName(packageName, NotificationListener::class.java.name).flattenToString()
        // FIX: Use the standard Android SDK method.
        Settings.Secure.putString(mockContext.contentResolver, "enabled_notification_listeners", componentName)

        val result = PermissionUtils.isNotificationListenerEnabled(mockContext, NotificationListener::class.java)
        assertThat(result).isTrue()
    }

    @Test
    fun isNotificationListenerEnabled_whenSettingStringDoesNotContainListener_returnsFalse() {
        val settingString = "some.other.listener:another.one"
        // FIX: Use the standard Android SDK method.
        Settings.Secure.putString(mockContext.contentResolver, "enabled_notification_listeners", settingString)

        val result = PermissionUtils.isNotificationListenerEnabled(mockContext, NotificationListener::class.java)
        assertThat(result).isFalse()
    }

    @Test
    fun isNotificationListenerEnabled_whenSettingStringIsEmpty_returnsFalse() {
        // FIX: Use the standard Android SDK method.
        Settings.Secure.putString(mockContext.contentResolver, "enabled_notification_listeners", "")

        val result = PermissionUtils.isNotificationListenerEnabled(mockContext, NotificationListener::class.java)
        assertThat(result).isFalse()
    }

    @Test
    fun isNotificationListenerEnabled_whenSettingStringIsNull_returnsFalse() {
        // FIX: Use the standard Android SDK method.
        Settings.Secure.putString(mockContext.contentResolver, "enabled_notification_listeners", null)

        val result = PermissionUtils.isNotificationListenerEnabled(mockContext, NotificationListener::class.java)
        assertThat(result).isFalse()
    }


    // --- isAccessibilityServiceEnabled Tests ---

    @Test
    fun isAccessibilityServiceEnabled_whenServiceIsRunningAndEnabled_returnsTrue() {
        // Mock AccessibilityServiceInfo
        val mockServiceInfo = mockk<AccessibilityServiceInfo>()
        val expectedId = ComponentName(packageName, ScrollTrackService::class.java.name).flattenToString()

        // Stub the 'id' property of the mocked AccessibilityServiceInfo
        every { mockServiceInfo.id } returns expectedId
        // If your code under test also accesses other properties of AccessibilityServiceInfo,
        // you might need to stub them as well, e.g.:
        // every { mockServiceInfo.resolveInfo } returns mockk() // or some specific ResolveInfo

        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns listOf(mockServiceInfo)

        val result = PermissionUtils.isAccessibilityServiceEnabled(mockContext, ScrollTrackService::class.java)
        assertThat(result).isTrue()
    }

    @Test
    fun isAccessibilityServiceEnabled_whenOtherServicesAreRunning_returnsTrue() {
        val otherMockServiceInfo1 = mockk<AccessibilityServiceInfo>()
        every { otherMockServiceInfo1.id } returns "com.other/com.other.Service1"

        val ourMockServiceInfo = mockk<AccessibilityServiceInfo>()
        val expectedId = ComponentName(packageName, ScrollTrackService::class.java.name).flattenToString()
        every { ourMockServiceInfo.id } returns expectedId

        val otherMockServiceInfo2 = mockk<AccessibilityServiceInfo>()
        every { otherMockServiceInfo2.id } returns "com.other/com.other.Service2"


        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns listOf(
            otherMockServiceInfo1, ourMockServiceInfo, otherMockServiceInfo2
        )

        val result = PermissionUtils.isAccessibilityServiceEnabled(mockContext, ScrollTrackService::class.java)
        assertThat(result).isTrue()
    }

    @Test
    fun isAccessibilityServiceEnabled_whenServiceIsNotInEnabledList_returnsFalse() {
        val otherMockServiceInfo1 = mockk<AccessibilityServiceInfo>()
        every { otherMockServiceInfo1.id } returns "com.other/com.other.Service1"

        val otherMockServiceInfo2 = mockk<AccessibilityServiceInfo>()
        every { otherMockServiceInfo2.id } returns "com.other/com.other.Service2"

        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns listOf(otherMockServiceInfo1, otherMockServiceInfo2)

        val result = PermissionUtils.isAccessibilityServiceEnabled(mockContext, ScrollTrackService::class.java)
        assertThat(result).isFalse()
    }

    @Test
    fun isAccessibilityServiceEnabled_whenEnabledListIsEmpty_returnsFalse() {
        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns emptyList()

        val result = PermissionUtils.isAccessibilityServiceEnabled(mockContext, ScrollTrackService::class.java)
        assertThat(result).isFalse()
    }

    @Test
    fun isAccessibilityServiceEnabled_whenEnabledListIsNull_returnsFalse() {
        // AccessibilityManager can return null if the system service is not ready
        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns null

        val result = PermissionUtils.isAccessibilityServiceEnabled(mockContext, ScrollTrackService::class.java)
        assertThat(result).isFalse()
    }

    // --- Flow-based Tests ---

    @Test
    fun `isUsageStatsPermissionGrantedFlow emits true when permission is granted`() = runTest {
        every {
            mockAppOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } returns AppOpsManager.MODE_ALLOWED

        val hasPermission = PermissionUtils.isUsageStatsPermissionGrantedFlow(mockContext).first()
        assertThat(hasPermission).isTrue()
    }

    @Test
    fun `isUsageStatsPermissionGrantedFlow emits false when permission is denied`() = runTest {
        every {
            mockAppOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } returns AppOpsManager.MODE_ERRORED

        val hasPermission = PermissionUtils.isUsageStatsPermissionGrantedFlow(mockContext).first()
        assertThat(hasPermission).isFalse()
    }

    @Test
    fun `isAccessibilityServiceEnabledFlow emits true when service is enabled`() = runTest {
        val mockServiceInfo = mockk<AccessibilityServiceInfo>()
        val expectedId = ComponentName(packageName, ScrollTrackService::class.java.name).flattenToString()
        every { mockServiceInfo.id } returns expectedId
        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns listOf(mockServiceInfo)

        val isEnabled = PermissionUtils.isAccessibilityServiceEnabledFlow(mockContext).first()
        assertThat(isEnabled).isTrue()
    }

    @Test
    fun `isAccessibilityServiceEnabledFlow emits false when service is disabled`() = runTest {
        every { mockAccessibilityManager.getEnabledAccessibilityServiceList(any()) } returns emptyList()

        val isEnabled = PermissionUtils.isAccessibilityServiceEnabledFlow(mockContext).first()
        assertThat(isEnabled).isFalse()
    }

    @Test
    fun `isNotificationListenerEnabledFlow emits true when listener is enabled`() = runTest {
        val componentName = ComponentName(packageName, NotificationListener::class.java.name).flattenToString()
        Settings.Secure.putString(mockContext.contentResolver, "enabled_notification_listeners", componentName)

        val isEnabled = PermissionUtils.isNotificationListenerEnabledFlow(mockContext).first()
        assertThat(isEnabled).isTrue()
    }

    @Test
    fun `isNotificationListenerEnabledFlow emits false when listener is disabled`() = runTest {
        Settings.Secure.putString(mockContext.contentResolver, "enabled_notification_listeners", "")

        val isEnabled = PermissionUtils.isNotificationListenerEnabledFlow(mockContext).first()
        assertThat(isEnabled).isFalse()
    }
}
