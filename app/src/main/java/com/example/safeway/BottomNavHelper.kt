package com.example.safeway

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

enum class NavTab { HOME, LOG, CIRCLE, RECORDS }

object BottomNavHelper {

    private val activeColor = R.color.highlight_accent
    private val inactiveColor = R.color.neutral_muted

    fun setup(activity: Activity, activeTab: NavTab) {
        val navHome = activity.findViewById<LinearLayout>(R.id.nav_home) ?: return
        val navLog = activity.findViewById<LinearLayout>(R.id.nav_log)
        val navCircle = activity.findViewById<LinearLayout>(R.id.nav_circle)
        val navRecords = activity.findViewById<LinearLayout>(R.id.nav_records)

        val tabs = listOf(
            Triple(navHome, R.id.iv_nav_home, R.id.indicator_home),
            Triple(navLog, R.id.iv_nav_log, R.id.indicator_log),
            Triple(navCircle, R.id.iv_nav_circle, R.id.indicator_circle),
            Triple(navRecords, R.id.iv_nav_records, R.id.indicator_records)
        )

        tabs.forEachIndexed { index, (container, iconId, indicatorId) ->
            val isActive = index == activeTab.ordinal
            val icon = container.findViewById<ImageView>(iconId)
            val indicator = container.findViewById<View>(indicatorId)
            val label = container.findViewById<TextView>(when (index) {
                0 -> R.id.label_home
                1 -> R.id.label_log
                2 -> R.id.label_circle
                3 -> R.id.label_records
                else -> throw IndexOutOfBoundsException()
            })

            val color = ContextCompat.getColor(activity, if (isActive) activeColor else inactiveColor)
            icon.setColorFilter(color)
            indicator.layoutParams?.width = if (isActive) 20.dpToPx(activity) else 0
            indicator.requestLayout()
            label.setTextColor(color)
            label.setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

        // Set up navigation — only for tabs that are NOT the current one
        val navActions = listOf(
            navHome to HomeActivity::class.java,
            navLog to LogIncidentActivity::class.java,
            navCircle to SupportCircleActivity::class.java,
            navRecords to RecordsActivity::class.java
        )

        navActions.forEach { (navView, targetClass) ->
            val targetTab = when (targetClass) {
                HomeActivity::class.java -> NavTab.HOME
                LogIncidentActivity::class.java -> NavTab.LOG
                SupportCircleActivity::class.java -> NavTab.CIRCLE
                RecordsActivity::class.java -> NavTab.RECORDS
                else -> return@forEach
            }
            if (targetTab != activeTab) {
                navView.setOnClickListener {
                    activity.startActivity(Intent(activity, targetClass).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    activity.overridePendingTransition(R.anim.fade_in, 0)
                }
            } else {
                navView.setOnClickListener(null)
            }
        }
    }

    private fun Int.dpToPx(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}
