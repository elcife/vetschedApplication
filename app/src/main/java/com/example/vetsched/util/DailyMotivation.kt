package com.example.vetsched.util

import android.content.Context

object DailyMotivation {
    fun forDay(context: Context, day: String): String {
        return "Keep going! You're doing great on $day."
    }
}
