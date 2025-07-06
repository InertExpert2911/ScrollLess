package com.example.scrolltrack.util

import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GreetingUtil @Inject constructor() {
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
    fun getGreeting(time: LocalTime = LocalTime.now()): String {
        return getGreetingForHour(time.hour)
    }

    internal fun getGreetingForHour(hour: Int): String {
        return when (hour) {
            in 5..11 -> "Good Morning! ☀️"
            in 12..17 -> "Good Afternoon! 🌤️"
            in 18..21 -> "Good Evening! 🌙"
            else -> "Good Night! 😴"
        }
    }
}