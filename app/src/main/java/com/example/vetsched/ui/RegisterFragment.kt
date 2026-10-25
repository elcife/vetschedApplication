package com.example.vetsched.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Typeface
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vetsched.R
import com.example.vetsched.api.RetrofitClient
import com.example.vetsched.api.models.AuthResponse
import com.example.vetsched.databinding.FragmentRegisterBinding
import com.example.vetsched.util.InputValidation
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private var resendTimer: CountDownTimer? = null
    private var resendMillisRemaining = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupYearLevelDropdown()
        setupErrorClearing()

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.cbAcceptTerms.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) binding.tvTermsError.visibility = View.GONE
        }
        binding.tvReadTerms.setOnClickListener {
            showTermsOfService()
        }
        listOf(binding.tvReadTerms, binding.tvLogin, binding.btnEditRegistration)
            .forEach(::styleClickableText)

        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        binding.btnCreateAccount.setOnClickListener {
            val params = validatedRegistrationParams() ?: return@setOnClickListener
            requestRegistrationOtp(params)
        }

        binding.btnVerifyRegistrationOtp.setOnClickListener {
            verifyOtp()
        }

        binding.btnResendRegistrationOtp.setOnClickListener {
            val params = pendingRegistrationParams ?: validatedRegistrationParams() ?: return@setOnClickListener
            requestRegistrationOtp(params)
        }

        binding.btnEditRegistration.setOnClickListener {
            resendTimer?.cancel()
            resendTimer = null
            resendMillisRemaining = 0L
            binding.registrationOtpPanel.visibility = View.GONE
            binding.btnCreateAccount.visibility = View.VISIBLE
            setRegistrationFieldsEnabled(true)
            binding.etRegistrationOtp.text?.clear()
            renderResendCooldown()
        }
    }

    private fun validatedRegistrationParams(): Map<String, String>? {
        clearAllErrors()

        if (!requireTermsAcceptance()) return null

        val firstName = InputValidation.normalizeName(binding.etFirstName.text.toString())
        val lastName = InputValidation.normalizeName(binding.etLastName.text.toString())
        val emailInput = binding.etEmail.text.toString()
        val email = InputValidation.normalizeEmail(emailInput)
        val idNumber = binding.etIDNumber.text.toString().trim()
        val studentID = InputValidation.normalizeStudentId(idNumber)
        val yearLevelText = binding.etYearLevel.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (firstName.isEmpty() || lastName.isEmpty() || emailInput.isEmpty() ||
            idNumber.isEmpty() || yearLevelText.isEmpty() || password.isEmpty() ||
            confirmPassword.isEmpty()
        ) {
            Toast.makeText(requireContext(), "All fields are required!", Toast.LENGTH_SHORT).show()
            return null
        }

        if (!InputValidation.isValidName(firstName) || !InputValidation.isValidName(lastName)) {
            Toast.makeText(requireContext(), "Names must be 1-80 characters and contain no control characters", Toast.LENGTH_SHORT).show()
            return null
        }
        if (!InputValidation.isValidEmail(email)) {
            binding.etEmail.error = "Only @phinmaed.com email addresses are allowed"
            return null
        }

        val yearLevelInt = when (yearLevelText) {
            "1st Year" -> 1
            "2nd Year" -> 2
            "3rd Year" -> 3
            "4th Year" -> 4
            "5th Year" -> 5
            else -> 0
        }

        if (yearLevelInt == 0) {
            binding.tilYearLevel.error = "Please select a year level"
            return null
        }
        if (studentID == null) {
            binding.tilIDNumber.error = "Invalid ID format (9-12 digits required)"
            return null
        }
        if (!InputValidation.isValidPassword(password)) {
            binding.tilPassword.error = "Use 10-128 characters"
            return null
        }
        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            return null
        }

        return mapOf(
            "first_name" to firstName,
            "last_name" to lastName,
            "email" to email,
            "student_id" to studentID,
            "year_level" to yearLevelInt.toString(),
            "password" to password,
            "terms_accepted" to "true"
        )
    }

    private fun showTermsOfService() {
        val termsText = TextView(requireContext()).apply {
            setPadding(48, 24, 48, 24)
            text = getString(R.string.terms_of_service_body)
            textSize = 14f
            setTextColor(resources.getColor(R.color.vetsched_text_primary, null))
        }
        val scrollView = ScrollView(requireContext()).apply {
            addView(
                termsText,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.terms_of_service_title)
            .setView(scrollView)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun styleClickableText(textView: TextView) {
        textView.paintFlags = textView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private var pendingRegistrationParams: Map<String, String>? = null

    private fun requestRegistrationOtp(params: Map<String, String>) {
        pendingRegistrationParams = params
        setRequestLoading(true)
        RetrofitClient.instance.requestRegistrationOtp(params).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (!isAdded || _binding == null) return
                setRequestLoading(false)
                val result = response.body() ?: parseError(response)
                if (response.isSuccessful && result?.success == true) {
                    binding.registrationOtpPanel.visibility = View.VISIBLE
                    binding.btnCreateAccount.visibility = View.GONE
                    setRegistrationFieldsEnabled(false)
                    startResendCooldown(60)
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                } else {
                    val errorMsg = result?.message ?: "Could not send verification code"
                    when (result?.errorField) {
                        "student_id" -> _binding?.tilIDNumber?.error = errorMsg
                        "email" -> _binding?.tilEmail?.error = errorMsg
                        "year_level" -> _binding?.tilYearLevel?.error = errorMsg
                        else -> Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                if (!isAdded || _binding == null) return
                setRequestLoading(false)
                Toast.makeText(context, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun verifyOtp() {
        val otpCode = binding.etRegistrationOtp.text.toString().trim()
        val email = pendingRegistrationParams?.get("email") ?: binding.etEmail.text.toString().trim()

        if (otpCode.length != 6) {
            binding.etRegistrationOtp.error = "Enter a valid 6-digit verification code"
            return
        }

        val params = mapOf(
            "email" to email,
            "otp" to otpCode
        )

        setRequestLoading(true)
        RetrofitClient.instance.verifyRegistrationOtp(params).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (!isAdded || _binding == null) return
                setRequestLoading(false)
                val result = response.body() ?: parseError(response)
                if (response.isSuccessful && result?.success == true) {
                    Toast.makeText(context, "Account created successfully! You can now log in.", Toast.LENGTH_LONG).show()
                    findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
                } else {
                    val errorMsg = result?.message ?: "Invalid verification code"
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                if (!isAdded || _binding == null) return
                setRequestLoading(false)
                Toast.makeText(context, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun parseError(response: Response<AuthResponse>): AuthResponse? {
        val body = response.errorBody()?.string() ?: return null
        return try {
            Gson().fromJson(body, AuthResponse::class.java)
        } catch (_: com.google.gson.JsonSyntaxException) {
            null
        }
    }

    private fun requireTermsAcceptance(): Boolean {
        if (binding.cbAcceptTerms.isChecked) return true
        binding.tvTermsError.visibility = View.VISIBLE
        binding.cbAcceptTerms.requestFocus()
        Toast.makeText(requireContext(), R.string.terms_required, Toast.LENGTH_SHORT).show()
        return false
    }

    private fun setRequestLoading(loading: Boolean) {
        binding.btnCreateAccount.isEnabled = !loading
        binding.btnResendRegistrationOtp.isEnabled = !loading && resendMillisRemaining == 0L
        setRegistrationFieldsEnabled(
            !loading && binding.registrationOtpPanel.visibility != View.VISIBLE
        )
    }

    private fun setRegistrationFieldsEnabled(enabled: Boolean) {
        binding.etFirstName.isEnabled = enabled
        binding.etLastName.isEnabled = enabled
        binding.etEmail.isEnabled = enabled
        binding.etIDNumber.isEnabled = enabled
        binding.etYearLevel.isEnabled = enabled
        binding.etPassword.isEnabled = enabled
        binding.etConfirmPassword.isEnabled = enabled
    }

    private fun startResendCooldown(seconds: Int) {
        resendTimer?.cancel()
        resendMillisRemaining = seconds.coerceAtLeast(0) * 1_000L
        renderResendCooldown()
        if (resendMillisRemaining == 0L) return
        resendTimer = object : CountDownTimer(resendMillisRemaining, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                resendMillisRemaining = millisUntilFinished
                renderResendCooldown()
            }

            override fun onFinish() {
                resendMillisRemaining = 0L
                resendTimer = null
                renderResendCooldown()
            }
        }.start()
    }

    private fun renderResendCooldown() {
        if (_binding == null) return
        if (resendMillisRemaining > 0L) {
            val seconds = (resendMillisRemaining + 999L) / 1_000L
            binding.btnResendRegistrationOtp.text =
                getString(R.string.resend_otp_countdown, seconds)
            binding.btnResendRegistrationOtp.isEnabled = false
        } else {
            binding.btnResendRegistrationOtp.setText(R.string.resend_otp)
            binding.btnResendRegistrationOtp.isEnabled = true
        }
    }

    private fun setupYearLevelDropdown() {
        val yearLevels = arrayOf("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year")
        
        binding.etYearLevel.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Select Year Level")
                .setItems(yearLevels) { _, which ->
                    binding.etYearLevel.setText(yearLevels[which])
                    clearAllErrors()
                }
                .show()
        }
    }

    private fun clearAllErrors() {
        binding.tilEmail.error = null
        binding.tilIDNumber.error = null
        binding.tilYearLevel.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    private fun setupErrorClearing() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearAllErrors()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        
        binding.etEmail.addTextChangedListener(watcher)
        binding.etIDNumber.addTextChangedListener(watcher)
        binding.etYearLevel.addTextChangedListener(watcher)
        binding.etPassword.addTextChangedListener(watcher)
        binding.etConfirmPassword.addTextChangedListener(watcher)
    }

    override fun onDestroyView() {
        resendTimer?.cancel()
        resendTimer = null
        super.onDestroyView()
        _binding = null
    }
}
