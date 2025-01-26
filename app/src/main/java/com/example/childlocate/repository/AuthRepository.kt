package com.example.childlocate.repository

/*
class AuthRepository {

    suspend fun sendOtpRequest(context: Context, token: String, email: String): Response<FcmResponse> {
        val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
        val credentials = GoogleCredentials.fromStream(serviceAccount)
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        val accessToken = withContext(Dispatchers.IO) { credentials.refreshAccessToken().tokenValue }

        val request = FcmRequest(
            Message(
                token = token,
                data = mapOf("request_type" to "otp_request", "email" to email)
            )
        )

        return RetrofitInstance.api.sendFcmMessage( request)
    }

    suspend fun resetPassword(otp: String, newPassword: String): Response<Any> {
        // Implement reset password logic
        // Verify OTP with server and reset password
        return Response.success(Any())
    }
}

*/

