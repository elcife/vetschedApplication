package com.example.vetsched.api

import com.example.vetsched.api.models.AuthResponse
import com.example.vetsched.api.models.EnrollmentRequest
import com.example.vetsched.api.models.Section
import com.example.vetsched.api.models.ScheduleSubmissionRequest
import com.example.vetsched.api.models.Subject
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @POST("login.php")
    fun login(@Body params: Map<String, String>): Call<AuthResponse>

    @POST("add_account.php")
    fun register(@Body params: Map<String, String>): Call<AuthResponse>

    @POST("request_registration_otp.php")
    fun requestRegistrationOtp(@Body params: Map<String, String>): Call<AuthResponse>

    @POST("verify_registration_otp.php")
    fun verifyRegistrationOtp(@Body params: Map<String, String>): Call<AuthResponse>

    @POST("change_password.php")
    fun changePassword(@Body params: Map<String, String>): Call<AuthResponse>

    @POST("request_password_reset.php")
    fun requestPasswordReset(@Body params: Map<String, String>): Call<AuthResponse>

    @POST("confirm_password_reset.php")
    fun confirmPasswordReset(@Body params: Map<String, String>): Call<AuthResponse>

    @POST("delete_account.php")
    fun deleteAccount(@Body params: Map<String, String>): Call<AuthResponse>

    @POST("update_year_level.php")
    fun updateYearLevel(@Body params: Map<String, String>): Call<AuthResponse>

    @GET("api.php?resource=subjects")
    fun getSubjects(): Call<List<Subject>>

    @GET("api.php?resource=sections")
    fun getSections(): Call<List<Section>>

    @GET("plotting.php?resource=enrolled_courses")
    fun getEnrolledCourses(@Query("studentId") studentId: String): Call<List<com.example.vetsched.data.EnrolledCourse>>

    @POST("plotting.php?resource=enroll")
    fun enroll(@Body request: EnrollmentRequest): Call<AuthResponse>

    @POST("plotting.php?resource=unenroll")
    fun unenroll(@Body request: EnrollmentRequest): Call<AuthResponse>

    @POST("plotting.php?resource=submit_schedule")
    fun submitSchedule(@Body request: ScheduleSubmissionRequest): Call<AuthResponse>

    @POST("plotting.php?resource=clear_schedule")
    fun clearSchedule(@Body request: ScheduleSubmissionRequest): Call<AuthResponse>

    @GET("plotting.php?resource=submission_status")
    fun getSubmissionStatus(@Query("studentId") studentId: String): Call<com.example.vetsched.api.models.SubmissionStatusResponse>
}
