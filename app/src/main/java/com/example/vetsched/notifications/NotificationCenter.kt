package com.example.vetsched.notifications

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.vetsched.api.RetrofitClient
import com.example.vetsched.api.models.ClassComponent
import com.example.vetsched.api.models.Section
import com.example.vetsched.api.models.Subject
import com.example.vetsched.R
import com.example.vetsched.data.CourseRepository
import com.example.vetsched.data.EnrolledCourse
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class CourseUpdateNotification(
    val title: String,
    val message: String,
    val createdAt: Long,
    val emphasize: List<String>? = emptyList(),
    val relatedSectionId: Int? = null
) {
    fun emphasizedMessage(context: Context): CharSequence = emphasizedText(message, context)

    fun emphasizedText(text: String, context: Context): CharSequence {
        val styled = SpannableString(text)
        val highlightColor = ContextCompat.getColor(context, R.color.vetsched_primary)

        emphasize.orEmpty().filter(String::isNotBlank).forEach { phrase ->
            var index = text.indexOf(phrase, ignoreCase = true)
            while (index >= 0) {
                val end = index + phrase.length
                styled.setSpan(StyleSpan(Typeface.BOLD), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(highlightColor), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                index = text.indexOf(phrase, end, ignoreCase = true)
            }
        }
        return styled
    }

    fun displayDate(): String =
        SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(createdAt))

    fun accessibilityText(): String = "$title. $message. ${displayDate()}"

    companion object {
        fun create(title: String, message: String, timestamp: Long, vararg emphasis: String) =
            CourseUpdateNotification(title, message, timestamp, emphasis.toList())
    }
}

object NotificationCenter {
    private const val TAG = "NotificationCenter"
    private const val PREFS_NAME = "VETSCHED_PREFS"
    private const val MAX_NOTIFICATIONS = 50
    private val gson = Gson()

    private data class CurriculumSnapshot(
        val subjects: List<SubjectSnapshot>,
        val sections: List<SectionSnapshot>
    )

    private data class SubjectSnapshot(
        val id: Int,
        val code: String,
        val name: String,
        val yearLevel: Int
    ) {
        fun displayName() = "$code - $name"
    }

    private data class SectionSnapshot(
        val id: Int,
        val name: String,
        val subjectId: Int?,
        val isOpen: Boolean,
        val classes: List<String>
    )

    fun hasUnread(context: Context, yearLevel: Int): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(unreadKey(yearLevel), false)

    fun getHiddenSectionIds(context: Context, yearLevel: Int): Set<Int> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(hiddenSectionsKey(yearLevel), emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()

    fun pushCustomNotification(context: Context, yearLevel: Int, title: String, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyKey = "notification_history_year_$yearLevel"
        val unreadKey = unreadKey(yearLevel)

        val json = prefs.getString(historyKey, null)
        val type = object : TypeToken<List<CourseUpdateNotification>>() {}.type
        val list = try {
            gson.fromJson<List<CourseUpdateNotification>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }

        val mutableList = list.toMutableList()
        val notif = CourseUpdateNotification(title, message, System.currentTimeMillis(), listOf("returned", "admin"))
        mutableList.add(0, notif)

        if (mutableList.size > MAX_NOTIFICATIONS) {
            mutableList.removeAt(mutableList.size - 1)
        }

        prefs.edit()
            .putString(historyKey, gson.toJson(mutableList))
            .putBoolean(unreadKey, true)
            .apply()
    }

    fun initializeBaselineIfMissing(
        context: Context,
        yearLevel: Int,
        subjects: List<Subject>,
        sections: List<Section>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val snapshotKey = "notification_snapshot_year_$yearLevel"
        if (prefs.contains(snapshotKey)) return

        prefs.edit()
            .putString(snapshotKey, gson.toJson(createSnapshot(subjects, sections, yearLevel)))
            .apply()
    }

    fun initializeBaselineFromServer(context: Context, yearLevel: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains("notification_snapshot_year_$yearLevel")) return

        val completed = AtomicBoolean(false)
        var subjects: List<Subject>? = null
        var sections: List<Section>? = null

        fun logFailure(message: String) {
            if (completed.compareAndSet(false, true)) Log.w(TAG, message)
        }

        fun saveWhenReady() {
            val currentSubjects = subjects ?: return
            val currentSections = sections ?: return
            if (!completed.compareAndSet(false, true)) return
            initializeBaselineIfMissing(context, yearLevel, currentSubjects, currentSections)
        }

        RetrofitClient.instance.getSubjects().enqueue(object : Callback<List<Subject>> {
            override fun onResponse(call: Call<List<Subject>>, response: Response<List<Subject>>) {
                if (!response.isSuccessful) {
                    logFailure("Could not initialize update history (${response.code()})")
                    return
                }
                subjects = response.body() ?: emptyList()
                saveWhenReady()
            }

            override fun onFailure(call: Call<List<Subject>>, error: Throwable) {
                logFailure("Could not initialize update history: ${error.localizedMessage ?: "network error"}")
            }
        })

        RetrofitClient.instance.getSections().enqueue(object : Callback<List<Section>> {
            override fun onResponse(call: Call<List<Section>>, response: Response<List<Section>>) {
                if (!response.isSuccessful) {
                    logFailure("Could not initialize update history (${response.code()})")
                    return
                }
                sections = response.body() ?: emptyList()
                saveWhenReady()
            }

            override fun onFailure(call: Call<List<Section>>, error: Throwable) {
                logFailure("Could not initialize update history: ${error.localizedMessage ?: "network error"}")
            }
        })
    }

    fun loadUpdates(
        context: Context,
        yearLevel: Int,
        markAsRead: Boolean = false,
        callback: (List<CourseUpdateNotification>?, String?) -> Unit
    ) {
        val finished = AtomicBoolean(false)
        var subjects: List<Subject>? = null
        var sections: List<Section>? = null

        fun finishWithError(message: String) {
            if (finished.compareAndSet(false, true)) callback(null, message)
        }

        fun finishIfReady() {
            val currentSubjects = subjects ?: return
            val currentSections = sections ?: return
            if (!finished.compareAndSet(false, true)) return

            CourseRepository.fetchEnrolled(context) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val snapshotKey = "notification_snapshot_year_$yearLevel"
                val historyKey = "notification_history_year_$yearLevel"
                val currentSnapshot = createSnapshot(currentSubjects, currentSections, yearLevel)
                val previousJson = prefs.getString(snapshotKey, null)

                try {
                    val newlyConflictingSections = mutableSetOf<Int>()
                    val notifications = if (previousJson == null) {
                        emptyList()
                    } else {
                        val previousSnapshot = gson.fromJson(previousJson, CurriculumSnapshot::class.java)
                            ?: return@fetchEnrolled callback(null, "Saved notification data is invalid")
                        findChanges(
                            previousSnapshot,
                            currentSnapshot,
                            CourseRepository.getAllEnrolled(context),
                            newlyConflictingSections
                        )
                    }

                    val enrolledCourses = CourseRepository.getAllEnrolled(context)
                    val activeConflictingSections = currentSnapshot.sections
                        .filter { section ->
                            val subject = currentSnapshot.subjects.firstOrNull { it.id == section.subjectId }
                            findEnrolledScheduleConflicts(
                                section,
                                subject?.displayName() ?: "Course",
                                subject?.code.orEmpty(),
                                enrolledCourses,
                                System.currentTimeMillis()
                            ).isNotEmpty()
                        }
                        .mapTo(mutableSetOf()) { it.id }

                    val oldHistoryJson = prefs.getString(historyKey, null)
                    val oldHistory = if (oldHistoryJson.isNullOrBlank()) {
                        emptyList()
                    } else {
                        gson.fromJson(
                            oldHistoryJson,
                            Array<CourseUpdateNotification>::class.java
                        )?.toList() ?: emptyList()
                    }
                    val updatedHistory = (notifications + oldHistory)
                        .map(::upgradeLegacyScheduleNotification)
                        .take(MAX_NOTIFICATIONS)
                    val previousHiddenSections = prefs
                        .getStringSet(hiddenSectionsKey(yearLevel), emptySet())
                        .orEmpty()
                        .mapNotNull(String::toIntOrNull)
                        .toSet()
                    val hiddenSections = if (previousJson == null) {
                        emptySet()
                    } else {
                        (previousHiddenSections + newlyConflictingSections)
                            .intersect(activeConflictingSections)
                    }
                    val unread = if (markAsRead) {
                        false
                    } else {
                        notifications.isNotEmpty() ||
                            prefs.getBoolean(unreadKey(yearLevel), false)
                    }

                    prefs.edit()
                        .putString(snapshotKey, gson.toJson(currentSnapshot))
                        .putString(historyKey, gson.toJson(updatedHistory))
                        .putBoolean(unreadKey(yearLevel), unread)
                        .putStringSet(
                            hiddenSectionsKey(yearLevel),
                            hiddenSections.map(Int::toString).toSet()
                        )
                        .apply()
                    callback(updatedHistory, null)
                } catch (error: JsonParseException) {
                    callback(null, "Could not read saved notification data")
                }
            }
        }

        RetrofitClient.instance.getSubjects().enqueue(object : Callback<List<Subject>> {
            override fun onResponse(call: Call<List<Subject>>, response: Response<List<Subject>>) {
                if (!response.isSuccessful) {
                    finishWithError("Could not check course updates (${response.code()})")
                    return
                }
                subjects = response.body() ?: emptyList()
                finishIfReady()
            }

            override fun onFailure(call: Call<List<Subject>>, error: Throwable) {
                finishWithError("Could not check course updates: ${error.localizedMessage ?: "network error"}")
            }
        })

        RetrofitClient.instance.getSections().enqueue(object : Callback<List<Section>> {
            override fun onResponse(call: Call<List<Section>>, response: Response<List<Section>>) {
                if (!response.isSuccessful) {
                    finishWithError("Could not check section updates (${response.code()})")
                    return
                }
                sections = response.body() ?: emptyList()
                finishIfReady()
            }

            override fun onFailure(call: Call<List<Section>>, error: Throwable) {
                finishWithError("Could not check section updates: ${error.localizedMessage ?: "network error"}")
            }
        })
    }

    private fun unreadKey(yearLevel: Int) = "notification_unread_year_$yearLevel"
    private fun hiddenSectionsKey(yearLevel: Int) = "notification_hidden_sections_year_$yearLevel"

    private fun createSnapshot(
        subjects: List<Subject>,
        sections: List<Section>,
        yearLevel: Int
    ): CurriculumSnapshot {
        val relevantSubjects = subjects
            .filter { it.yearLevel == yearLevel }
            .map { SubjectSnapshot(it.id, it.code, it.name, it.yearLevel) }
            .sortedBy { it.id }
        val subjectIds = relevantSubjects.mapTo(mutableSetOf()) { it.id }
        val relevantSections = sections
            .filter { it.subjectId in subjectIds }
            .map { section ->
                SectionSnapshot(
                    id = section.id,
                    name = section.type,
                    subjectId = section.subjectId,
                    isOpen = section.isOpen,
                    classes = section.classes.orEmpty().map(::classSignature).sorted()
                )
            }
            .sortedBy { it.id }
        return CurriculumSnapshot(relevantSubjects, relevantSections)
    }

    private fun classSignature(component: ClassComponent): String =
        listOf(
            component.classType,
            component.days.sorted().joinToString(","),
            component.start,
            component.end,
            component.instructor.orEmpty(),
            component.room.orEmpty()
        ).joinToString("|")

    private fun findChanges(
        previous: CurriculumSnapshot,
        current: CurriculumSnapshot,
        enrolledCourses: List<EnrolledCourse>,
        conflictingSections: MutableSet<Int>
    ): List<CourseUpdateNotification> {
        val now = System.currentTimeMillis()
        val changes = mutableListOf<CourseUpdateNotification>()
        val previousSubjects = previous.subjects.associateBy { it.id }
        val currentSubjects = current.subjects.associateBy { it.id }
        val previousSections = previous.sections.associateBy { it.id }
        val currentSections = current.sections.associateBy { it.id }
        val previousSubjectNames = previousSubjects.mapValues { it.value.code }

        currentSubjects.values.forEach { subject ->
            val old = previousSubjects[subject.id]
            when {
                old == null -> changes += CourseUpdateNotification.create(
                    "New subject available",
                    "${subject.displayName()} was added for Year ${subject.yearLevel}.",
                    now,
                    subject.code,
                    subject.name
                )
                old != subject -> changes += CourseUpdateNotification.create(
                    "Subject updated",
                    "${subject.displayName()} details were changed.",
                    now,
                    subject.code,
                    subject.name
                )
            }
        }
        previousSubjects.values.filter { it.id !in currentSubjects }.forEach { subject ->
            changes += CourseUpdateNotification.create(
                "Subject removed",
                "${subject.displayName()} is no longer available.",
                now,
                subject.code,
                subject.name
            )
        }

        currentSections.values.forEach { section ->
            val old = previousSections[section.id]
            val subject = section.subjectId?.let { currentSubjects[it] }
            val courseName = subject?.displayName() ?: "Course"
            val conflicts = findEnrolledScheduleConflicts(
                section,
                courseName,
                subject?.code.orEmpty(),
                enrolledCourses,
                now
            )
            if ((old == null || old.classes != section.classes) && conflicts.isNotEmpty()) {
                conflictingSections += section.id
            }
            when {
                old == null -> changes += CourseUpdateNotification.create(
                    "New section available",
                    "$courseName Section ${section.name} was added.",
                    now,
                    subject?.code.orEmpty(),
                    subject?.name.orEmpty(),
                    section.name
                )
                old.isOpen != section.isOpen -> changes += CourseUpdateNotification.create(
                    if (section.isOpen) "Section now open" else "Section closed",
                    "$courseName Section ${section.name} is now ${if (section.isOpen) "open" else "closed"} for enrollment.",
                    now,
                    subject?.code.orEmpty(),
                    subject?.name.orEmpty(),
                    section.name
                )
                old.classes != section.classes -> changes += CourseUpdateNotification(
                    title = "Schedule updated",
                    message = scheduleChangeMessage(courseName, section.name, old.classes, section.classes),
                    createdAt = now,
                    emphasize = listOf(
                        subject?.code.orEmpty(),
                        subject?.name.orEmpty(),
                        section.name
                    ) + old.classes.flatMap(::scheduleEmphasis) +
                        section.classes.flatMap(::scheduleEmphasis)
                )
                old.name != section.name || old.subjectId != section.subjectId -> changes += CourseUpdateNotification.create(
                    "Section updated",
                    "$courseName Section ${section.name} details were changed.",
                    now,
                    subject?.code.orEmpty(),
                    subject?.name.orEmpty(),
                    section.name
                )
            }

            if (old == null || old.classes != section.classes) {
                changes += conflicts
            }
        }
        previousSections.values.filter { it.id !in currentSections }.forEach { section ->
            val courseCode = section.subjectId?.let { previousSubjectNames[it] } ?: "Course"
            changes += CourseUpdateNotification.create(
                "Section removed",
                "$courseCode Section ${section.name} is no longer available.",
                now,
                courseCode,
                section.name
            )
        }

        return changes
    }

    private fun findEnrolledScheduleConflicts(
        section: SectionSnapshot,
        courseName: String,
        subjectCode: String,
        enrolledCourses: List<EnrolledCourse>,
        now: Long
    ): List<CourseUpdateNotification> {
        val conflicts = mutableListOf<CourseUpdateNotification>()
        section.classes.forEach slotLoop@ { signature ->
            val changedSlot = parseClassSlot(signature) ?: return@slotLoop
            enrolledCourses.forEach enrolledLoop@ { enrolled ->
                if (enrolled.courseCode.equals(subjectCode, ignoreCase = true) &&
                    enrolled.section.equals(section.name, ignoreCase = true)
                ) return@enrolledLoop

                val enrolledSlot = parseEnrolledSlot(enrolled) ?: return@enrolledLoop
                if (changedSlot.days.none { it in enrolledSlot.days } ||
                    changedSlot.start >= enrolledSlot.end ||
                    enrolledSlot.start >= changedSlot.end
                ) return@enrolledLoop

                val overlapStart = maxOf(changedSlot.start, enrolledSlot.start)
                val overlapEnd = minOf(changedSlot.end, enrolledSlot.end)
                val days = changedSlot.days.intersect(enrolledSlot.days).joinToString(", ")
                val changedTime = "${formatMinutes(changedSlot.start)}-${formatMinutes(changedSlot.end)}"
                val enrolledTime = "${formatMinutes(enrolledSlot.start)}-${formatMinutes(enrolledSlot.end)}"
                val overlapTime = "${formatMinutes(overlapStart)}-${formatMinutes(overlapEnd)}"
                val detail = "$courseName Section ${section.name} ($days, $changedTime) overlaps your enrolled ${enrolled.courseCode} Section ${enrolled.section} (${enrolled.day}, $enrolledTime). The conflict is $overlapTime."
                conflicts += CourseUpdateNotification.create(
                    "Schedule conflict",
                    detail,
                    now,
                    subjectCode,
                    courseName.substringAfter(" - ", courseName),
                    section.name,
                    enrolled.courseCode,
                    enrolled.courseName,
                    enrolled.section,
                    days,
                    changedTime,
                    enrolledTime,
                    overlapTime
                ).copy(relatedSectionId = section.id)
            }
        }
        return conflicts.distinctBy { it.message }
    }

    private data class ParsedSlot(
        val days: Set<String>,
        val start: Int,
        val end: Int
    )

    private fun parseClassSlot(signature: String): ParsedSlot? {
        val fields = signature.split('|')
        if (fields.size < 4) return null
        val start = parseTime(fields[2]) ?: return null
        val end = parseTime(fields[3]) ?: return null
        val days = parseDays(fields[1])
        return ParsedSlot(days, start, end).takeIf { days.isNotEmpty() && start < end }
    }

    private fun parseEnrolledSlot(course: EnrolledCourse): ParsedSlot? {
        val parts = course.timeRange.split("-", "–", "—").map(String::trim)
        if (parts.size < 2) return null
        val start = parseTime(parts[0]) ?: return null
        val end = parseTime(parts[1]) ?: return null
        val days = parseDays(course.day)
        return ParsedSlot(days, start, end).takeIf { days.isNotEmpty() && start < end }
    }

    private fun parseDays(rawDays: String): Set<String> = rawDays
        .replace("{", "")
        .replace("}", "")
        .replace("[", "")
        .replace("]", "")
        .replace("\"", "")
        .split(",")
        .map { day -> day.trim().take(3).lowercase(Locale.ROOT) }
        .filter(String::isNotBlank)
        .toSet()

    private fun parseTime(rawTime: String): Int? {
        val normalized = rawTime.trim().uppercase(Locale.ROOT)
        val match = Regex("^(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*(AM|PM)?$").matchEntire(normalized)
            ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        when (match.groupValues[3]) {
            "AM" -> if (hour == 12) hour = 0
            "PM" -> if (hour < 12) hour += 12
        }
        return hour * 60 + minute
    }

    private fun formatMinutes(minutes: Int): String {
        val hour = minutes / 60
        val minute = minutes % 60
        val suffix = if (hour >= 12) "PM" else "AM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d %s".format(Locale.getDefault(), hour12, minute, suffix)
    }

    private fun scheduleChangeMessage(
        courseName: String,
        sectionName: String,
        oldClasses: List<String>,
        newClasses: List<String>
    ): String {
        val previous = oldClasses.map(::formatClassDetails).ifEmpty { listOf("No schedule") }
        val updated = newClasses.map(::formatClassDetails).ifEmpty { listOf("No schedule") }
        return "$courseName Section $sectionName schedule changed.\nBefore: ${previous.joinToString("; ")}\nNow: ${updated.joinToString("; ")}"
    }

    private fun upgradeLegacyScheduleNotification(
        notification: CourseUpdateNotification
    ): CourseUpdateNotification {
        val legacyPrefix = "The days, times, or class details for "
        if (!notification.message.startsWith(legacyPrefix) ||
            !notification.message.endsWith(" changed.")
        ) return notification

        val courseAndSection = notification.message
            .removePrefix(legacyPrefix)
            .removeSuffix(" changed.")
        val emphasis = courseAndSection.split(" Section ", limit = 2)
            .flatMap { it.split(" - ") }
        return notification.copy(
            title = "Schedule changed",
            message = "$courseAndSection schedule changed. This older alert does not contain the previous and updated times. Check Courses for the current schedule.",
            emphasize = emphasis
        )
    }

    private fun scheduleEmphasis(signature: String): List<String> {
        val fields = signature.split('|')
        if (fields.size < 4) return listOf(signature)
        return buildList {
            add(fields[1])
            add("${formatTime(fields[2])} - ${formatTime(fields[3])}")
            fields.getOrNull(4)?.takeIf(String::isNotBlank)?.let(::add)
            fields.getOrNull(5)?.takeIf(String::isNotBlank)?.let(::add)
        }
    }

    private fun formatClassDetails(signature: String): String {
        val fields = signature.split('|')
        if (fields.size < 4) return signature
        val details = mutableListOf(
            fields[0],
            fields[1],
            "${formatTime(fields[2])} - ${formatTime(fields[3])}"
        )
        fields.getOrNull(4)?.takeIf(String::isNotBlank)?.let { details += "Instructor: $it" }
        fields.getOrNull(5)?.takeIf(String::isNotBlank)?.let { details += "Room: $it" }
        return details.joinToString(", ")
    }

    private fun formatTime(value: String): String {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return value
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return value
        val suffix = if (hour >= 12) "PM" else "AM"
        val hour12 = when {
            hour % 12 == 0 -> 12
            else -> hour % 12
        }
        return "%d:%02d %s".format(Locale.getDefault(), hour12, minute, suffix)
    }
}
