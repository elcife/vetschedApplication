package com.example.vetsched.ui

import android.content.Context
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
import com.example.vetsched.databinding.FragmentScheduleBinding
import com.example.vetsched.databinding.ItemDayScheduleBinding
import com.example.vetsched.databinding.ItemScheduleCardBinding
import com.example.vetsched.notifications.NotificationCenter
import com.example.vetsched.notifications.NotificationInbox
import com.example.vetsched.util.BottomNavigationInsets
import com.example.vetsched.util.DailyMotivation

class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        BottomNavigationInsets.apply(binding.bottomNav)

        val sharedPref = requireActivity().getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
        val studentId = sharedPref.getString("studentId", null)
        
        if (studentId == null) {
            Toast.makeText(requireContext(), "Session expired, please login again", Toast.LENGTH_LONG).show()
            findNavController().navigate(R.id.action_scheduleFragment_to_startFragment)
            return
        }

        binding.tvUserName.text = sharedPref.getString("userName", "Student")

        setupTabs()
        checkYearLevel()
        sharedPref.getInt("userYearLevel", 0)
            .takeIf { it in 1..4 }
            ?.let { NotificationCenter.initializeBaselineFromServer(requireContext(), it) }

        CourseRepository.checkSubmissionStatus(requireContext()) { isSubmitted ->
            updateSubmitButtonState(isSubmitted)
        }

        binding.btnProfile.setOnClickListener {
            findNavController().navigate(R.id.action_scheduleFragment_to_profileFragment)
        }

        binding.btnCourses.setOnClickListener {
            findNavController().navigate(R.id.action_scheduleFragment_to_coursesFragment)
        }

        updateSubmitButtonState(CourseRepository.isSubmitted)

        binding.ivNotification.setOnClickListener {
            val yearLevel = sharedPref.getInt("userYearLevel", 1).coerceIn(1, 5)
            NotificationCenter.loadUpdates(
                requireContext(),
                yearLevel,
                markAsRead = true
            ) { updates, error ->
                if (!isAdded || _binding == null) return@loadUpdates
                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                } else {
                    binding.viewNotificationIndicator.visibility = View.GONE
                    NotificationInbox.show(requireContext(), updates.orEmpty())
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadEnrolledData()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Only fetch if memory is empty or we just came back from enrollment
        loadEnrolledData()
        val yearLevel = requireContext()
            .getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
            .getInt("userYearLevel", 0)
        if (yearLevel in 1..5 && _binding != null) {
            refreshNotificationIndicator(yearLevel)
        }
    }

    private fun refreshNotificationIndicator(yearLevel: Int) {
        binding.viewNotificationIndicator.visibility =
            if (NotificationCenter.hasUnread(requireContext(), yearLevel)) View.VISIBLE else View.GONE
        NotificationCenter.loadUpdates(requireContext(), yearLevel) { _, error ->
            if (!isAdded || _binding == null) return@loadUpdates
            if (error == null) {
                binding.viewNotificationIndicator.visibility =
                    if (NotificationCenter.hasUnread(requireContext(), yearLevel)) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateSubmitButtonState(isSubmitted: Boolean) {
        if (!isAdded || _binding == null) return
        if (isSubmitted) {
            binding.btnSubmit.text = "Export Schedule to PDF"
            binding.btnSubmit.isEnabled = true
            binding.btnSubmit.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.vetsched_primary)
            binding.btnSubmit.setOnClickListener {
                com.example.vetsched.util.SchedulePdfExporter.exportToPdf(requireContext())
            }
        } else {
            binding.btnSubmit.text = "Submit"
            binding.btnSubmit.isEnabled = true
            binding.btnSubmit.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.vetsched_primary)
            binding.btnSubmit.setOnClickListener {
                val enrolledList = CourseRepository.getAllEnrolled(requireContext())
                if (enrolledList.isEmpty()) {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("No Subjects Added")
                        .setMessage("You must add at least one subject to your schedule before submitting.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnClickListener
                }

                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Submit Schedule")
                    .setMessage("Are you sure you want to submit your schedule?")
                    .setPositiveButton("Yes, Review Schedule") { _, _ ->
                        if (findNavController().currentDestination?.id == R.id.scheduleFragment) {
                            findNavController().navigate(R.id.action_scheduleFragment_to_confirmScheduleFragment)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun loadEnrolledData() {
        CourseRepository.fetchEnrolled(requireContext()) {
            if (isAdded && _binding != null) {
                setupViewPager()
                updateDayIndicators()
                updateSubmitButtonState(CourseRepository.isSubmitted)
            }
        }
    }

    private fun updateDayIndicators() {
        val tabLayouts = listOf(
            binding.tabMon, binding.tabTue, binding.tabWed, 
            binding.tabThu, binding.tabFri, binding.tabSat, binding.tabSun
        )
        
        days.forEachIndexed { index, day ->
            val hasCourses = CourseRepository.getCoursesForDay(day, requireContext()).isNotEmpty()
            if (binding.viewPager.currentItem != index) {
                if (hasCourses) {
                    tabLayouts[index].setBackgroundResource(R.drawable.bg_date_has_courses)
                } else {
                    tabLayouts[index].setBackgroundResource(R.drawable.bg_date_unselected)
                }
            }
        }
    }

    private fun checkYearLevel() {
        val sharedPref = requireActivity().getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
        val yearLevel = sharedPref.getInt("userYearLevel", 0)
        
        if (yearLevel == 0) {
            showYearLevelSelectionDialog()
        }
    }

    private fun showYearLevelSelectionDialog() {
        val yearLevels = arrayOf("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Your Year Level")
            .setMessage("We need to know your year level to show you the correct courses.")
            .setCancelable(false)
            .setItems(yearLevels) { _, which ->
                val yearLevelInt = which + 1
                updateYearLevelOnServer(yearLevelInt.toString())
            }
            .show()
    }

    private fun updateYearLevelOnServer(yearLevel: String) {
        val sharedPref = requireActivity().getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
        val userEmail = sharedPref.getString("userEmail", null)

        if (userEmail == null) {
            Toast.makeText(requireContext(), "Error: User email not found", Toast.LENGTH_SHORT).show()
            return
        }

        val params = mapOf(
            "email" to userEmail,
            "year_level" to yearLevel
        )

        com.example.vetsched.api.RetrofitClient.instance.updateYearLevel(params).enqueue(object : retrofit2.Callback<com.example.vetsched.api.models.AuthResponse> {
            override fun onResponse(
                call: retrofit2.Call<com.example.vetsched.api.models.AuthResponse>,
                response: retrofit2.Response<com.example.vetsched.api.models.AuthResponse>
            ) {
                if (!isAdded || _binding == null) return

                if (response.isSuccessful && response.body()?.success == true) {
                    sharedPref.edit().putInt("userYearLevel", yearLevel.toInt()).apply()
                    NotificationCenter.initializeBaselineFromServer(
                        requireContext(),
                        yearLevel.toInt()
                    )
                    refreshNotificationIndicator(yearLevel.toInt())
                    Toast.makeText(context, "Year level updated!", Toast.LENGTH_SHORT).show()
                } else {
                    val msg = response.body()?.message ?: "Update failed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    // If it failed, we might need to ask again
                    showYearLevelSelectionDialog()
                }
            }

            override fun onFailure(call: retrofit2.Call<com.example.vetsched.api.models.AuthResponse>, t: Throwable) {
                if (!isAdded || _binding == null) return
                Toast.makeText(context, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                showYearLevelSelectionDialog()
            }
        })
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
            if (findNavController().currentDestination?.id == R.id.scheduleFragment) {
                val bundle = Bundle().apply {
                    putString("targetCourseCode", course.courseCode)
                }
                findNavController().navigate(R.id.action_scheduleFragment_to_coursesFragment, bundle)
            }
        }

        dialog.show()
    }

    private fun setupViewPager() {
        val adapter = DailyScheduleAdapter(days)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
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

    private fun updateTabsUI(selectedPosition: Int) {
        val whiteColor = Color.WHITE
        val textPrimaryColor = ContextCompat.getColor(requireContext(), R.color.vetsched_text_primary)

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
            if (index == selectedPosition) {
                layout.setBackgroundResource(R.drawable.bg_date_selected)
                labelViews[index].setTextColor(whiteColor)
                dateViews[index].setTextColor(whiteColor)
            } else {
                layout.setBackgroundResource(R.drawable.bg_date_unselected)
                labelViews[index].setTextColor(textPrimaryColor)
                dateViews[index].setTextColor(textPrimaryColor)
            }
        }

        binding.dateSelector.post {
            val selectedTab = tabLayouts[selectedPosition]
            val scrollX = selectedTab.left - (binding.dateSelector.width - selectedTab.width) / 2
            binding.dateSelector.smoothScrollTo(scrollX, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                
                val enrolled = CourseRepository.getCoursesForDay(dayTitle, itemBinding.root.context)
                itemBinding.tvActiveClasses.text = if (enrolled.size == 1) "1 Active Class" else "${enrolled.size} Active Classes"
                
                itemBinding.cardsContainer.visibility = if (enrolled.isNotEmpty()) View.VISIBLE else View.GONE

                // Add non-conflicting courses first, then conflicting courses so conflicting ones render at bottom
                val nonConflicting = enrolled.filter { findConflictingCourse(it, enrolled) == null }
                val conflicting = enrolled.filter { findConflictingCourse(it, enrolled) != null }

                (nonConflicting + conflicting).forEach { course ->
                    if (course.courseCode.isNotBlank()) {
                        val conflict = findConflictingCourse(course, enrolled)
                        addCard(itemBinding, course, conflict)
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

            private fun addCard(itemBinding: ItemDayScheduleBinding, course: EnrolledCourse, conflictingCourse: EnrolledCourse? = null) {
                val cardBinding = ItemScheduleCardBinding.inflate(
                    LayoutInflater.from(itemBinding.root.context),
                    itemBinding.cardsContainer,
                    false
                )
                
                val courseTitle = if (!course.type.isNullOrBlank()) "${course.courseName} (${course.type})" else course.courseName
                cardBinding.tvCourseName.text = if (courseTitle.isNotBlank()) courseTitle else course.courseCode
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
                
                cardBinding.btnRemove.setOnClickListener {
                    val context = it.context
                    androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle("Remove Class")
                        .setMessage("Are you sure you want to remove this class? Removing it will also remove all other class components in the ${course.section} section bundle.")
                        .setPositiveButton("Yes, Remove All") { _, _ ->
                            val allEnrolled = CourseRepository.getAllEnrolled(context)
                            val sectionBundleCourses = allEnrolled.filter { enrolled ->
                                enrolled.courseCode == course.courseCode && enrolled.section == course.section
                            }

                            val combinedOfferingIds = sectionBundleCourses
                                .flatMap { enrolled -> enrolled.offeringIds.orEmpty() }
                                .distinct()

                            val bundleToRemove = if (combinedOfferingIds.isNotEmpty()) {
                                course.copy(offeringIds = combinedOfferingIds)
                            } else {
                                course
                            }

                            CourseRepository.remove(bundleToRemove, context) { success ->
                                if (success) {
                                    Toast.makeText(context, "Removed ${course.courseCode} (${course.section}) section bundle", Toast.LENGTH_SHORT).show()
                                    loadEnrolledData()
                                } else {
                                    Toast.makeText(context, "Failed to unenroll", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }

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
                cardBinding.root.setBackgroundResource(R.drawable.bg_schedule_card)
                cardBinding.root.elevation = 4 * density
                itemBinding.cardsContainer.addView(cardBinding.root)
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
