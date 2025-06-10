package com.example.scrolltrack.util

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class ConversionUtilTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockResources: Resources

    @Mock
    private lateinit var mockDisplayMetrics: DisplayMetrics

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
    }

    private fun mockDpi(dpi: Int) {
        mockDisplayMetrics.ydpi = dpi.toFloat()
    }

    @Test
    fun `formatScrollDistance returns zero for zero scroll units`() {
        mockDpi(160) // Standard DPI
        val (metric, imperial) = ConversionUtil.formatScrollDistance(0L, mockContext)
        assertEquals("0 m", metric)
        assertEquals("0.00 miles", imperial)
    }

    @Test
    fun `formatScrollDistance handles less than 1 meter`() {
        // Assuming 1 inch = 2.54 cm, 160 DPI means 160 pixels per inch.
        // 1 meter = 39.3701 inches.
        // Pixels for 1 meter = 160 * 39.3701 = 6299.216
        // Let's test with 1000 scroll units.
        mockDpi(160)
        val (metric, imperial) = ConversionUtil.formatScrollDistance(1000L, mockContext)
        assertEquals("0.16 m", metric) // 1000 / 6299.216 = 0.1587...
        assertEquals("0.00 miles", imperial)
    }

    @Test
    fun `formatScrollDistance handles integer meters`() {
        // ~6300 pixels = 1 meter at 160 DPI
        mockDpi(160)
        val (metric, imperial) = ConversionUtil.formatScrollDistance(6300, mockContext)
        assertEquals("1.00 m", metric)
        assertEquals("0.00 miles", imperial)
    }

    @Test
    fun `formatScrollDistance handles kilometers`() {
        // ~6,300,000 pixels = 1 kilometer at 160 DPI
        mockDpi(160)
        val (metric, imperial) = ConversionUtil.formatScrollDistance(6_300_000, mockContext)
        assertEquals("1.00 km", metric)
        assertEquals("0.62 miles", imperial)
    }

    @Test
    fun `formatScrollDistance works correctly with high DPI`() {
        // ~12600 pixels = 1 meter at 320 DPI
        mockDpi(320)
        val (metric, imperial) = ConversionUtil.formatScrollDistance(12600, mockContext)
        assertEquals("1.00 m", metric)
        assertEquals("0.00 miles", imperial)
    }

    @Test
    fun `formatScrollDistance works correctly with low DPI`() {
        // ~3150 pixels = 1 meter at 80 DPI
        mockDpi(80)
        val (metric, imperial) = ConversionUtil.formatScrollDistance(3150, mockContext)
        assertEquals("1.00 m", metric)
        assertEquals("0.00 miles", imperial)
    }
}