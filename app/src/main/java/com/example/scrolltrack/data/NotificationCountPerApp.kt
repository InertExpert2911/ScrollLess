package com.example.scrolltrack.data

/**
 * A data class representing the total count of notifications for a specific application package.
 * This is used by the DAO to return aggregated query results grouped by package name.
 */
data class NotificationCountPerApp(
    val packageName: String,
    val count: Int
) 