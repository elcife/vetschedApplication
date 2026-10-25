package com.example.vetsched.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object BottomNavigationInsets {
    fun apply(navigation: View) {
        val baseHeight = navigation.layoutParams.height.takeIf { it > 0 }
            ?: (80f * navigation.resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(navigation) { view, insets ->
            val keyboardInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationInset = if (keyboardInset > 0) {
                0
            } else {
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }

            val params = view.layoutParams
            val targetHeight = baseHeight + navigationInset
            if (params.height != targetHeight) {
                params.height = targetHeight
                view.layoutParams = params
            }
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                navigationInset
            )
            insets
        }
        ViewCompat.requestApplyInsets(navigation)
    }
}
