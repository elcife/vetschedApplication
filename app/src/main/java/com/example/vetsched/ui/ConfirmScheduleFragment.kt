package com.example.vetsched.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.vetsched.R
import com.example.vetsched.data.CourseRepository
import com.example.vetsched.data.EnrolledCourse
import com.example.vetsched.databinding.FragmentConfirmScheduleBinding
import com.example.vetsched.databinding.ItemDayScheduleBinding
import com.example.vetsched.databinding.ItemScheduleCardBinding
import com.example.vetsched.util.DailyMotivation
import com.google.gson.Gson

class ConfirmScheduleFragment : Fragment() {

    private var _binding: FragmentConfirmScheduleBinding? = null
    private val binding get() = _binding!!

    private var previewCourses: List<EnrolledCourse> = emptyList()

    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmScheduleBinding.inflate(inflater, container, false)
        
        val json = arguments?.getString("preview_courses_json")
        if (json != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<EnrolledCourse>>() {}.type
            previewCourses = Gson().fromJson(json, type)
        }
        
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupTabs()

        if (previewCourses.isNotEmpty()) {
            val firstPreviewIndex = days.indexOfFirst { isPreviewDay(it) }
            if (firstPreviewIndex != -1) {
                binding.viewPager.post {
                    binding.viewPager.setCurrentItem(firstPreviewIndex, false)
                    updateTabsUI(firstPreviewIndex)
                }
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnYesSubmit.isEnabled = false
        binding.btnYesSubmit.text = "Checking status..."

        CourseRepository.checkSubmissionStatus(requireContext()) { isSubmitted ->
            if (!isAdded || _binding == null) return@checkSubmissionStatus

            if (previewCourses.isNotEmpty()) {
                // Mode A: Section Enrollment preview (from CoursesFragment)
                binding.btnExportPdf.visibility = View.GONE
                binding.btnYesSubmit.isEnabled = !isSubmitted
                binding.btnYesSubmit.setBackgroundColor(if (isSubmitted) Color.parseColor("#B0B0B0") else Color.parseColor("#77C647"))
                binding.btnYesSubmit.text = if (isSubmitted) "Submitted to Admin (Locked)" else "Confirm Enrollment"

                if (!isSubmitted) {
                    binding.btnYesSubmit.setOnClickListener {
                        enrollAllPreviews(0)
                    }
                } else {
                    binding.btnYesSubmit.setOnClickListener {
                        Toast.makeText(context, "Schedule is already submitted and locked by admin.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Mode B: Schedule Submission & Review (from ScheduleFragment)
                binding.btnExportPdf.visibility = View.GONE

                binding.btnYesSubmit.isEnabled = true
                binding.btnYesSubmit.setBackgroundColor(Color.parseColor("#5C6D4F"))

                if (isSubmitted) {
                    binding.btnYesSubmit.text = "Export Schedule to PDF"
                    binding.btnYesSubmit.setOnClickListener {
                        com.example.vetsched.util.SchedulePdfExporter.exportToPdf(requireContext())
                    }
                } else {
                    binding.btnYesSubmit.text = "Submit Schedule to Admin"
                    binding.btnYesSubmit.setOnClickListener {
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Submit Schedule")
                            .setMessage("Are you sure you wanna submit to admin? Once submitted you can no longer change your schedule.")
                            .setPositiveButton("Yes, Submit") { _, _ ->
                                binding.btnYesSubmit.isEnabled = false
                                CourseRepository.submitSchedule(requireContext()) { success ->
                                    if (!isAdded) return@submitSchedule
                                    binding.btnYesSubmit.isEnabled = true
                                    if (success) {
                                        Toast.makeText(requireContext(), "Schedule successfully submitted to Admin!", Toast.LENGTH_SHORT).show()
                                        findNavController().navigate(R.id.action_confirmScheduleFragment_to_scheduleFragment)
                                    } else {
                                        Toast.makeText(requireContext(), "Could not submit schedule. Try again.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun enrollAllPreviews(index: Int) {
        if (index >= previewCourses.size) {
            Toast.makeText(context, "Enrollment completed!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_confirmScheduleFragment_to_scheduleFragment)
            return
        }

        CourseRepository.enroll(previewCourses[index], requireContext()) { result ->
            if (!isAdded) return@enroll
            if (result.success) {
                Toast.makeText(context, "Enrolled in ${previewCourses[index].section}", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_confirmScheduleFragment_to_scheduleFragment)
            } else if (result.isFull) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Section Full")
                    .setMessage(result.message ?: "One or more selected sections are full. No enrollment changes were saved.")
                    .setCancelable(false)
                    .setPositiveButton("Choose Another Section") { _, _ ->
                        findNavController().popBackStack(R.id.coursesFragment, false) || findNavController().navigateUp()
                    }
                    .show()
            } else {
                Toast.makeText(context, result.message ?: "Enrollment failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.viewPager.adapter?.notifyDataSetChanged()
    }

    private fun setupViewPager() {
        val adapter = DailyScheduleAdapter(days)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.tvHeaderTitle.text = days[position]
                updateTabsUI(position)
            }
        })
    }

    private fun setupTabs() {
        val tabLayouts = listOf(
            binding.tabMon, binding.tabTue, binding.tabWed,
            binding.tabThu, binding.tabFri, binding.tabSat, binding.tabSun
        )

        tabLayouts.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                binding.viewPager.currentItem = index
            }
        }
    }

    private fun isPreviewDay(dayName: String): Boolean {
        if (previewCourses.isEmpty()) return false
        val shortDay = getShortDay(dayName)
        return previewCourses.any { pc ->
            pc.day.split(",").map { it.trim() }.any { it.equals(shortDay, ignoreCase = true) }
        }
    }

    private fun updateTabsUI(selectedPosition: Int) {
        val whiteColor = Color.WHITE
        val textPrimaryColor = ContextCompat.getColor(requireContext(), R.color.vetsched_text_primary)
        val primaryGreenColor = ContextCompat.getColor(requireContext(), R.color.vetsched_primary)

        val tabLayouts = listOf(
            binding.tabMon, binding.tabTue, binding.tabWed,
            binding.tabThu, binding.tabFri, binding.tabSat, binding.tabSun
        )
        val labelViews = listOf(
            binding.tvMonLabel, binding.tvTueLabel, binding.tvWedLabel,
            binding.tvThuLabel, binding.tvFriLabel, binding.tvSatLabel, binding.tvSunLabel
        )
        val dateViews = listOf(
            binding.tvMonDate, binding.tvTueDate, binding.tvWedDate,
            binding.tvThuDate, binding.tvFriDate, binding.tvSatDate, binding.tvSunDate
        )

        tabLayouts.forEachIndexed { index, layout ->
            if (index < tabLayouts.size) {
                val dayName = days[index]
                val hasPreview = isPreviewDay(dayName)

                if (index == selectedPosition) {
                    if (hasPreview) {
                        layout.setBackgroundResource(R.drawable.bg_date_preview_selected)
                    } else {
                        layout.setBackgroundResource(R.drawable.bg_date_selected)
                    }
                    labelViews[index].setTextColor(whiteColor)
                    dateViews[index].setTextColor(whiteColor)
                } else {
                    if (hasPreview) {
                        layout.setBackgroundResource(R.drawable.bg_date_preview_highlight)
                        labelViews[index].setTextColor(primaryGreenColor)
                        dateViews[index].setTextColor(primaryGreenColor)
                    } else {
                        layout.setBackgroundResource(R.drawable.bg_date_unselected)
                        labelViews[index].setTextColor(textPrimaryColor)
                        dateViews[index].setTextColor(textPrimaryColor)
                    }
                }
            }
        }

        if (selectedPosition < tabLayouts.size) {
            binding.dateSelector.post {
                val selectedTab = tabLayouts[selectedPosition]
                val scrollX = selectedTab.left - (binding.dateSelector.width - selectedTab.width) / 2
                binding.dateSelector.smoothScrollTo(scrollX, 0)
            }
        }
    }

    private fun showScheduleDetailsDialog(course: EnrolledCourse) {
        val dialogBinding = com.example.vetsched.databinding.DialogScheduleDetailsBinding.inflate(layoutInflater)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.tvDetailTime.text = ScheduleTimeRange.format(course.timeRange)
        dialogBinding.tvDetailSection.text = if (course.type != null) "${course.section} (${course.type})" else course.section
        dialogBinding.tvDetailCourseName.text = if (course.courseName.isNotBlank()) "${course.courseCode} - ${course.courseName}" else course.courseCode
        dialogBinding.tvDetailRoomInstructor.text = "${course.room}  •  ${course.instructor}"

        dialogBinding.btnViewInCourses.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getShortDay(fullDay: String): String {
        return when (fullDay) {
            "Monday" -> "Mon"
            "Tuesday" -> "Tue"
            "Wednesday" -> "Wed"
            "Thursday" -> "Thu"
            "Friday" -> "Fri"
            "Saturday" -> "Sat"
            "Sunday" -> "Sun"
            else -> fullDay.take(3)
        }
    }

    inner class DailyScheduleAdapter(private val days: List<String>) :
        RecyclerView.Adapter<DailyScheduleAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: ItemDayScheduleBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(dayTitle: String) {
                itemBinding.tvDayTitle.text = dayTitle
                itemBinding.tvDailyMotivation.text =
                     DailyMotivation.forDay(itemBinding.root.context, dayTitle)
                itemBinding.cardsContainer.removeAllViews()

                val enrolled = CourseRepository.getCoursesForDay(dayTitle, itemBinding.root.context).toMutableList()
                
                // Add preview courses if they belong to this day
                val shortDay = getShortDay(dayTitle)
                previewCourses.forEach { pc ->
                    val pcDays = pc.day.split(",").map { it.trim() }
                    if (pcDays.any { it.equals(shortDay, ignoreCase = true) }) {
                        enrolled.add(pc)
                    }
                }

                itemBinding.tvActiveClasses.text = if (enrolled.size == 1) "1 Class" else "${enrolled.size} Classes"
                
                itemBinding.cardsContainer.visibility = if (enrolled.isNotEmpty()) View.VISIBLE else View.GONE

                val nonConflicting = enrolled.filter { findConflictingCourse(it, enrolled) == null }
                val conflicting = enrolled.filter { findConflictingCourse(it, enrolled) != null }

                (nonConflicting + conflicting).forEach { course ->
                    if (course.courseCode.isNotBlank()) {
                        val isPreview = previewCourses.contains(course)
                        val conflict = findConflictingCourse(course, enrolled)
                        addCard(itemBinding, course, isPreview, conflict)
                    }
                }

                setupTimeline(itemBinding)
            }

            private fun findConflictingCourse(course: EnrolledCourse, dayList: List<EnrolledCourse>): EnrolledCourse? {
                val currentRange = ScheduleTimeRange.parse(course.timeRange) ?: return null
                for (other in dayList) {
                    if (other.courseCode != course.courseCode || other.section != course.section) {
                        val otherRange = ScheduleTimeRange.parse(other.timeRange) ?: continue
                        if (currentRange.startMinutes < otherRange.endMinutes && otherRange.startMinutes < currentRange.endMinutes) {
                            return other
                        }
                    }
                }
                return null
            }

            private fun addCard(
                itemBinding: ItemDayScheduleBinding,
                course: EnrolledCourse,
                isPreview: Boolean = false,
                conflictingCourse: EnrolledCourse? = null
            ) {
                val cardBinding = ItemScheduleCardBinding.inflate(
                    LayoutInflater.from(itemBinding.root.context),
                    itemBinding.cardsContainer,
                    false
                )

                val baseTitle = if (!course.type.isNullOrBlank()) "${course.courseName} (${course.type})" else course.courseName
                val courseTitle = if (baseTitle.isNotBlank()) baseTitle else course.courseCode
                cardBinding.tvCourseName.text = if (isPreview) "[PREVIEW] $courseTitle" else courseTitle
                cardBinding.tvTimeRange.text = ScheduleTimeRange.format(course.timeRange)
                cardBinding.tvSection.text = course.section
                cardBinding.tvLocation.text = course.room
                cardBinding.tvInstructor.text = course.instructor

                if (conflictingCourse != null) {
                    cardBinding.viewConflictOverlay.visibility = View.VISIBLE
                    cardBinding.tvConflictLabel.visibility = View.VISIBLE
                    cardBinding.tvConflictLabel.text = "CONFLICT WITH ${conflictingCourse.courseName}"
                } else {
                    cardBinding.viewConflictOverlay.visibility = View.GONE
                    cardBinding.tvConflictLabel.visibility = View.GONE
                }
                
                if (isPreview) {
                    cardBinding.root.setBackgroundResource(R.drawable.bg_schedule_card_preview)
                } else {
                    cardBinding.root.setBackgroundResource(R.drawable.bg_schedule_card)
                }

                cardBinding.btnRemove.visibility = View.GONE

                cardBinding.root.setOnClickListener {
                    showScheduleDetailsDialog(course)
                }

                val timeInfo = ScheduleTimeRange.parse(course.timeRange) ?: return
                val density = itemBinding.root.resources.displayMetrics.density
                val timelineStartMinutes = 7 * 60
                val timelineEndMinutes = 22 * 60
                val visibleStart = timeInfo.startMinutes.coerceAtLeast(timelineStartMinutes)
                val visibleEnd = timeInfo.endMinutes.coerceAtMost(timelineEndMinutes)
                if (visibleStart >= visibleEnd) return
                val hourHeight = itemBinding.root.resources.getDimension(R.dimen.schedule_timeline_hour_height)
                val labelWidth = itemBinding.root.resources.getDimension(R.dimen.schedule_timeline_label_width)
                val markerOffset = itemBinding.root.resources.getDimension(R.dimen.schedule_timeline_marker_offset)
                val lineOverlap = itemBinding.root.resources.getDimension(R.dimen.schedule_timeline_card_line_overlap)
                val topMargin = markerOffset - lineOverlap +
                    (visibleStart - timelineStartMinutes) * hourHeight / 60f
                val cardHeight = ((visibleEnd - visibleStart) * hourHeight / 60f + 2 * lineOverlap).coerceAtLeast(
                    itemBinding.root.resources.displayMetrics.density * 40f
                )

                val params = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                    cardHeight.toInt()
                )
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                params.marginStart = labelWidth.toInt()
                params.marginEnd = 0
                params.topMargin = topMargin.toInt()

                cardBinding.root.layoutParams = params
                cardBinding.root.elevation = 4 * density
                itemBinding.cardsContainer.addView(cardBinding.root)

                if (isPreview) {
                    val scrollY = (topMargin - 80 * density).coerceAtLeast(0f).toInt()
                    itemBinding.root.post {
                        itemBinding.root.scrollTo(0, scrollY)
                    }
                }
            }

        }

        private fun setupTimeline(itemBinding: ItemDayScheduleBinding) {
            ScheduleTimelineLabels.bind(
                listOf(
                    itemBinding.row7, itemBinding.row8, itemBinding.row9,
                    itemBinding.row10, itemBinding.row11, itemBinding.row12,
                    itemBinding.row1, itemBinding.row2, itemBinding.row3,
                    itemBinding.row4, itemBinding.row5, itemBinding.row6,
                    itemBinding.row7pm, itemBinding.row8pm, itemBinding.row9pm
                )
            )
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemDayScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(days[position])
        }

        override fun getItemCount(): Int = days.size
    }

}