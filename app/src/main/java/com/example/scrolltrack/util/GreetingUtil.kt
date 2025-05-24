package com.example.scrolltrack.util

import java.util.Calendar
import kotlin.random.Random

object GreetingUtil {
    // A list of appropriate and friendly emojis for greetings
    private val greetingEmojis = listOf(
        "👋", // Waving hand
        "😊", // Smiling face with smiling eyes
        "✨", // Sparkles
        "👍", // Thumbs up
        "☀️", // Sun (for morning/day)
        "🖖",
        "✌️",
        "🤞",
        "🫰",
        "🤘",
        "🤙",
        "👍",
        "❤️",
        "🎉", // Party popper
        "🌟"  // Glowing star
    )

    /**
     * Generates a time-of-day appropriate greeting with a random emoji.
     * @return A greeting string like "Good Morning 👋".
     */
    fun getGreeting(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY) // 24-hour format

        val timeOfDayMessage = when (hour) {
            in 5..11 -> "Good Morning"  // 5 AM to 11:59 AM
            in 12..17 -> "Good Afternoon" // 12 PM to 5:59 PM
            in 18..21 -> "Good Evening" // 6 PM to 9:59 PM
            else -> "Good Night"        // 10 PM to 4:59 AM (or adjust as preferred)
        }
        val randomEmoji = greetingEmojis.random()
        return "$timeOfDayMessage $randomEmoji"
    }
}