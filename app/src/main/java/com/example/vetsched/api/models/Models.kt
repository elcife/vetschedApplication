package com.example.vetsched.api.models

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("student_id") val studentId: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val email: String,
    @SerializedName("year_level") val yearLevel: Int? = null
)

data class Subject(
    val id: Int,
    val code: String,
    val name: String,
    val yearLevel: Int,
    @Transient var sections: List<Section> = emptyList()
)

data class Section(
    val id: Int,
    val type: String,
    val isOpen: Boolean = true,
    val start: String,
    val end: String,
    val days: List<String>?,
    val instructor: String?,
    val room: String?,
    val maxCapacity: Int,
    val enrollmentCount: Int? = 0,
    val remainingSeats: Int? = null,
    val subjectId: Int?,
    val offeringIds: List<Int>? = null,
    val classes: List<ClassComponent>? = null
)

data class ClassComponent(
    val classType: String,
    val start: String,
    val end: String,
    val days: List<String>,
    val instructor: String?,
    val room: String?,
    val offeringIds: List<Int>
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("error_field") val errorField: String? = null,
    @SerializedName("resend_after_seconds") val resendAfterSeconds: Int? = null,
    val user: User? = null
)

data class EnrollmentRequest(
    @SerializedName("studentId") val studentId: String,
    @SerializedName("offeringIds") val offeringIds: List<Int>
)

data class ScheduleSubmissionRequest(
    @SerializedName("studentId") val studentId: String
)

data class SubmissionStatusResponse(
    @SerializedName("isSubmitted") val isSubmitted: Boolean,
    @SerializedName("submittedAt") val submittedAt: String? = null
)

data class EnrollmentResponse(
    val success: Boolean,
    val message: String? = null,
    val offeringIds: List<Int>? = null
)
