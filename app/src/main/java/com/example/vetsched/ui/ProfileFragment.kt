package com.example.vetsched.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
