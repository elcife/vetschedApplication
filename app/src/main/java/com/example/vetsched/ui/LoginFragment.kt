package com.example.vetsched.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vetsched.R
import com.example.vetsched.api.RetrofitClient
import com.example.vetsched.api.models.AuthResponse
import com.example.vetsched.databinding.FragmentLoginBinding
import com.example.vetsched.util.InputValidation
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        listOf(binding.tvRegister, binding.tvForgotPassword).forEach { textView ->
            textView.paintFlags = textView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_passwordResetFragment)
        }

        binding.btnLogin.setOnClickListener {
            val email = InputValidation.normalizeEmail(binding.etEmail.text.toString())
            val password = binding.etPassword.text.toString()

            if (!InputValidation.isValidEmail(email)) {
                binding.etEmail.error = "Only @phinmaed.com email addresses are allowed"
                return@setOnClickListener
            }
            if (!InputValidation.isPasswordWithinLimit(password)) {
                Toast.makeText(requireContext(), "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val params = mapOf(
                "email" to email,
                "password" to password
            )

            RetrofitClient.instance.login(params).enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    if (!isAdded || _binding == null) return

                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                        
                        val user = response.body()?.user
                        val fullName = "${user?.firstName} ${user?.lastName}"
                        val emailSaved = user?.email ?: email
                        val yearLevel = user?.yearLevel ?: 0 // Use 0 to indicate missing year level
                        val semester = user?.semester ?: 1
                        
                        val sharedPref = requireActivity().getSharedPreferences("VETSCHED_PREFS", Context.MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putBoolean("isLoggedIn", true)
                            putString("userName", fullName)
                            putString("userEmail", emailSaved)
                            putString("studentId", user?.studentId)
                            putInt("userYearLevel", yearLevel)
                            putInt("userSemester", semester.coerceIn(1, 2))
                            apply()
                        }

                        val bundle = Bundle().apply {
                            putString("userName", fullName)
                        }
                        findNavController().navigate(R.id.action_loginFragment_to_scheduleFragment, bundle)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val authResponse = try {
                            if (errorBody != null) {
                                Gson().fromJson(errorBody, AuthResponse::class.java)
                            } else {
                                response.body()
                            }
                        } catch (e: Exception) {
                            null
                        }
                        
                        val errorMsg = authResponse?.message ?: "Login failed"
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    Toast.makeText(context, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
