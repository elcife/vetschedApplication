package com.example.vetsched.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vetsched.R
import com.example.vetsched.api.RetrofitClient
import com.example.vetsched.api.models.AuthResponse
import com.example.vetsched.data.CourseRepository
import com.example.vetsched.databinding.DialogChangePasswordBinding
import com.example.vetsched.databinding.FragmentProfileBinding
import com.example.vetsched.util.BottomNavigationInsets
import com.example.vetsched.util.InputValidation
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        BottomNavigationInsets.apply(binding.bottomNav)
        setupFaqs()

        val sharedPref = requireActivity().getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
        val userName = sharedPref.getString("userName", "User").orEmpty()
            .takeIf(String::isNotBlank) ?: "Student"
        val userEmail = sharedPref.getString("userEmail", "").orEmpty()
        val studentId = sharedPref.getString("studentId", "").orEmpty()
        val yearLevel = sharedPref.getInt("userYearLevel", 0)
        binding.tvUserNameProfile.text = userName
        binding.tvProfileEmail.text = userEmail.ifBlank { "No email on file" }
        binding.tvProfileEmailDetail.text = "Email address\n${userEmail.ifBlank { "Not available" }}"
        binding.tvProfileStudentId.text = "Student ID\n${studentId.ifBlank { "Not available" }}"
        binding.tvProfileYearLevel.text = if (yearLevel in 1..5) {
            "Year level (Tap to edit)\n${yearLevel.ordinalSuffix()} year"
        } else {
            "Year level (Tap to set)\nNot set"
        }
        binding.tvProfileYearLevel.setOnClickListener {
            showEditYearLevelDialog()
        }
        binding.tvProfileInitials.text = userName
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "VS" }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Log out?")
                .setMessage("Are you sure you want to log out of your VETSCHED account?")
                .setNegativeButton("Stay logged in", null)
                .setPositiveButton("Log out") { _, _ ->
                    CourseRepository.clear(requireContext())
                    sharedPref.edit().clear().apply()
                    if (findNavController().currentDestination?.id == R.id.profileFragment) {
                        findNavController().navigate(R.id.action_profileFragment_to_startFragment)
                    }
                }
                .show()
        }

        binding.btnSchedule.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.profileFragment) {
                findNavController().navigate(R.id.action_profileFragment_to_scheduleFragment)
            }
        }

        binding.btnCourses.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.profileFragment) {
                findNavController().navigate(R.id.action_profileFragment_to_coursesFragment)
            }
        }

        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnResetSchedule.setOnClickListener {
            showResetScheduleWarning()
        }

        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupFaqs() {
        val categories = listOf(
            "SCHEDULING" to listOf(
                "I submitted my schedule but need to change it. What should I do?" to
                    "A submitted schedule is locked while it is under administrator review. Visit the Veterinary Medicine Teachers' Lounge and ask a teacher to help you request that it be returned. Once it is returned, you can revise it in VETSCHED.",
                "How can I check my classes for today?" to
                    "Open Schedule from the bottom navigation and select today's day. Your scheduled classes, meeting times, and sections will be shown there.",
                "What should I review before submitting my schedule?" to
                    "Check that you selected the correct subjects and sections, and review each class day and time for overlaps. On the Schedule screen, tap Submit and review the confirmation before completing the submission.",
                "I selected the wrong section. Can I change it before submitting?" to
                    "Open Courses and select the section you want instead. VETSCHED will ask you to confirm replacing your current section selection. Review the new section's days and times before confirming.",
                "How do I remove a class from my schedule?" to
                    "Open Schedule, select the class, and choose the removal option in the confirmation dialog. Removing a class also removes the other class components in that same section bundle. Review the confirmation carefully before proceeding.",
                "The administrator returned my schedule. What should I do next?" to
                    "After the schedule is returned, VETSCHED unlocks it for changes. Review the administrator's instructions, make the necessary updates in Courses or Schedule, and submit it again when it is ready."
            ),
            "COURSES & ENROLLMENT" to listOf(
                "How do I add a subject to my schedule?" to
                    "Open Courses, find the subject, and expand it to view its sections. Select an open section with available seats, review its class days and times, and confirm the enrollment.",
                "The section I need is full or closed. What can I do?" to
                    "Full means no seats are currently available. VETSCHED does not automatically change a section from Full to Closed; Closed is a separate status indicating that enrollment is not open for that section. Review other sections for one marked open with seats available. If you must take that specific section, ask your teacher or the administrator whether another option is available.",
                "Why does VETSCHED show a schedule conflict?" to
                    "The selected section overlaps in time with a class already on your schedule. Compare the class days and times, then choose a section that does not overlap. If no alternative works, consult your teacher.",
                "Can I enroll in more classes after I submit my schedule?" to
                    "No. Enrollment changes are locked while your schedule is submitted to the administrator. Ask a teacher at the Veterinary Medicine Teachers' Lounge about having the schedule returned; you can make changes after it is returned."
            ),
            "ACCOUNT & ACCESS" to listOf(
                "How do I update my year level?" to
                    "Open Profile, tap your year level, select the correct year, and wait for the confirmation that the update succeeded.",
                "I forgot my password. How can I reset it?" to
                    "On the login screen, select Forgot password and enter your registered school email address. Request the verification code, then enter the code and your new password. If you cannot access that email, contact your school administrator.",
                "How do I change my current password?" to
                    "Open Profile and select Change password. Enter your current password, then enter and confirm a new password that meets the on-screen requirements.",
                "My name, student ID, or email is incorrect. How can I fix it?" to
                    "These account details cannot be edited in VETSCHED. Contact your school administrator to request a correction."
            ),
            "NOTIFICATIONS" to listOf(
                "How will I know if a subject, section, class time, or room changes?" to
                    "When VETSCHED detects an update for the year level you are viewing, an indicator appears beside the notification bell. Tap the bell to review added or removed subjects and sections, section availability changes, class schedule or room changes, and schedule conflicts. For schedule changes, the alert shows the previous and updated class details, including the room when that information is available. Check the bell regularly for updates."
            ),
            "APP & TROUBLESHOOTING" to listOf(
                "My selected classes are not appearing on Schedule. What should I check?" to
                    "Confirm that the enrollment was completed in Courses and that you are viewing the correct day in Schedule. If the class still does not appear, reopen Schedule to refresh its information. Contact your teacher or the administrator if the problem continues.",
                "What happens if I select Clear my schedule?" to
                    "This removes all classes from your schedule and cannot be undone in the app. If your schedule has already been submitted, it must first be returned by the administrator. Use this option only when you intend to clear the entire schedule."
            )
        )

        var expandedCategory: Pair<LinearLayout, LinearLayout>? = null
        var expandedQuestion: Pair<TextView, TextView>? = null

        categories.forEachIndexed { categoryIndex, (title, questions) ->
            val categoryView = layoutInflater.inflate(
                R.layout.item_faq_category,
                binding.faqContainer,
                false
            )
            val categoryHeader =
                categoryView.findViewById<LinearLayout>(R.id.faqCategoryHeader)
            val categoryTitle =
                categoryView.findViewById<TextView>(R.id.faqCategoryTitle)
            val categoryChevron =
                categoryView.findViewById<TextView>(R.id.faqCategoryChevron)
            val questionContainer =
                categoryView.findViewById<LinearLayout>(R.id.faqQuestions)

            categoryTitle.text = title
            categoryHeader.contentDescription = "Show $title"

            questions.forEach { (question, answer) ->
                val questionView = layoutInflater.inflate(
                    R.layout.item_faq,
                    questionContainer,
                    false
                )
                val questionHeader =
                    questionView.findViewById<LinearLayout>(R.id.faqHeader)
                val questionTitle =
                    questionView.findViewById<TextView>(R.id.faqQuestion)
                val answerView =
                    questionView.findViewById<TextView>(R.id.faqAnswer)
                val questionChevron =
                    questionView.findViewById<TextView>(R.id.faqChevron)
                questionTitle.text = question
                answerView.text = answer
                questionHeader.contentDescription = question
                questionHeader.setOnClickListener {
                    val shouldExpand = answerView.visibility != View.VISIBLE
                    expandedQuestion?.let { (previousAnswer, previousChevron) ->
                        if (previousAnswer != answerView) {
                            previousAnswer.visibility = View.GONE
                            previousChevron.rotation = 0f
                        }
                    }
                    answerView.visibility = if (shouldExpand) View.VISIBLE else View.GONE
                    questionChevron.animate()
                        .rotation(if (shouldExpand) 180f else 0f)
                        .setDuration(180)
                        .start()
                    expandedQuestion =
                        if (shouldExpand) answerView to questionChevron else null
                }
                questionContainer.addView(questionView)
            }

            val initiallyExpanded = categoryIndex == 0
            questionContainer.visibility =
                if (initiallyExpanded) View.VISIBLE else View.GONE
            categoryChevron.rotation = if (initiallyExpanded) 180f else 0f
            categoryHeader.contentDescription =
                if (initiallyExpanded) "Hide $title" else "Show $title"
            if (initiallyExpanded) expandedCategory = questionContainer to categoryHeader

            categoryHeader.setOnClickListener {
                val shouldExpand = questionContainer.visibility != View.VISIBLE
                expandedCategory?.let { (previousCategory, previousHeader) ->
                    if (previousCategory != questionContainer) {
                        previousCategory.visibility = View.GONE
                        previousHeader.findViewById<TextView>(R.id.faqCategoryChevron)
                            .rotation = 0f
                        val previousTitle =
                            previousHeader.findViewById<TextView>(R.id.faqCategoryTitle).text
                        previousHeader.contentDescription = "Show $previousTitle"
                        expandedQuestion?.let { (answerView, questionChevron) ->
                            answerView.visibility = View.GONE
                            questionChevron.rotation = 0f
                        }
                        expandedQuestion = null
                    }
                }
                if (!shouldExpand) {
                    expandedQuestion?.let { (answerView, questionChevron) ->
                        answerView.visibility = View.GONE
                        questionChevron.rotation = 0f
                    }
                    expandedQuestion = null
                }
                questionContainer.visibility =
                    if (shouldExpand) View.VISIBLE else View.GONE
                categoryChevron.animate()
                    .rotation(if (shouldExpand) 180f else 0f)
                    .setDuration(180)
                    .start()
                categoryHeader.contentDescription =
                    if (shouldExpand) "Hide $title" else "Show $title"
                expandedCategory =
                    if (shouldExpand) questionContainer to categoryHeader else null
            }
            binding.faqContainer.addView(categoryView)
        }
    }

    private fun Int.ordinalSuffix(): String = when (this) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        4 -> "4th"
        else -> "5th"
    }

    private fun showEditYearLevelDialog() {
        val sharedPref = requireActivity().getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
        val userEmail = sharedPref.getString("userEmail", null)
        if (userEmail.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Error: User email not found", Toast.LENGTH_SHORT).show()
            return
        }

        val yearLevels = arrayOf("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year")
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Year Level")
            .setItems(yearLevels) { _, which ->
                val newYearLevel = which + 1
                updateYearLevelOnServer(userEmail, newYearLevel)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateYearLevelOnServer(email: String, newYearLevel: Int) {
        val params = mapOf(
            "email" to email,
            "year_level" to newYearLevel.toString()
        )

        RetrofitClient.instance.updateYearLevel(params).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (!isAdded || _binding == null) return

                if (response.isSuccessful && response.body()?.success == true) {
                    val sharedPref = requireActivity().getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
                    sharedPref.edit().putInt("userYearLevel", newYearLevel).apply()
                    binding.tvProfileYearLevel.text = "Year level (Tap to edit)\n${newYearLevel.ordinalSuffix()} year"
                    Toast.makeText(requireContext(), "Year level updated to ${newYearLevel.ordinalSuffix()} year!", Toast.LENGTH_SHORT).show()
                } else {
                    val msg = response.body()?.message ?: "Failed to update year level"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                if (!isAdded || _binding == null) return
                Toast.makeText(requireContext(), "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showResetScheduleWarning() {
        CourseRepository.checkSubmissionStatus(requireContext()) { isSubmitted ->
            if (!isAdded) return@checkSubmissionStatus
            if (isSubmitted) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Schedule Locked")
                    .setMessage("Your schedule has been submitted to the admin and cannot be cleared unless returned by the admin.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Reset Schedule")
                    .setMessage("Are you sure you want to clear your entire schedule? This cannot be undone.")
                    .setPositiveButton("Clear All") { _, _ ->
                        CourseRepository.clear(requireContext()) { success ->
                            if (isAdded) {
                                if (success) {
                                    Toast.makeText(requireContext(), "Schedule cleared successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(requireContext(), "Failed to clear schedule", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showChangePasswordDialog() {
        val sharedPref = requireActivity().getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
        val userEmail = sharedPref.getString("userEmail", null)

        if (userEmail == null || !InputValidation.isValidEmail(userEmail)) {
            Toast.makeText(requireContext(), "Error: User email not found", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Change password")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Update", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val oldPass = dialogBinding.etOldPassword.text?.toString().orEmpty()
                val newPass = dialogBinding.etNewPassword.text?.toString().orEmpty()
                val confirmPass = dialogBinding.etConfirmNewPassword.text?.toString().orEmpty()

                when {
                    !InputValidation.isPasswordWithinLimit(oldPass) -> {
                        dialogBinding.etOldPassword.error = "Enter your current password"
                    }
                    !InputValidation.isValidPassword(newPass) -> {
                        dialogBinding.etNewPassword.error = "Use 10-128 characters"
                    }
                    newPass == oldPass -> {
                        dialogBinding.etNewPassword.error = "Choose a password different from your current one"
                    }
                    confirmPass != newPass -> {
                        dialogBinding.etConfirmNewPassword.error = "Passwords do not match"
                    }
                    else -> updatePassword(
                        userEmail,
                        oldPass,
                        newPass,
                        dialog
                    )
                }
            }
        }
        dialog.show()
    }

    private fun updatePassword(
        email: String,
        oldPassword: String,
        newPassword: String,
        dialog: AlertDialog
    ) {
        val updateButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        updateButton.isEnabled = false
        updateButton.text = "Updating…"
        val params = mapOf(
            "email" to email,
            "old_password" to oldPassword,
            "new_password" to newPassword
        )

        RetrofitClient.instance.changePassword(params).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (!isAdded || _binding == null) return
                val result = response.body()
                if (response.isSuccessful && result?.success == true) {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                } else {
                    updateButton.isEnabled = true
                    updateButton.text = "Update"
                    Toast.makeText(
                        context,
                        result?.message ?: "Could not update password",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<AuthResponse>, error: Throwable) {
                if (!isAdded || _binding == null) return
                updateButton.isEnabled = true
                updateButton.text = "Update"
                Toast.makeText(
                    context,
                    "Network error: ${error.localizedMessage ?: "Please try again"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
