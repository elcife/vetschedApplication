package com.example.vetsched.ui

import android.content.Context
import com.example.vetsched.R

object DailyMotivation {
    private val days = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    fun forDay(context: Context, day: String): String {
        val index = days.indexOf(day)
        return context.resources.getStringArray(R.array.daily_motivational_messages)
            .getOrElse(index) { context.getString(R.string.daily_motivation_default) }
    }
}
