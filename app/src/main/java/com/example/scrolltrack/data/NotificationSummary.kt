package com.example.scrolltrack.data

/**
 * A data class representing the total count of notifications for a specific category.
 * This is used by the DAO to return aggregated query results.
 */
data class NotificationSummary(
    val category: String,
    val count: Int
) 