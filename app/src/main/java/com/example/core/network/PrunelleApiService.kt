package com.example.core.network

import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OtpRequestDto(
    val countryCode: String,
    val phoneNumber: String
)

@JsonClass(generateAdapter = true)
data class OtpVerifyDto(
    val countryCode: String,
    val phoneNumber: String,
    val code: String
)

@JsonClass(generateAdapter = true)
data class ApiStatusDto(
    val ok: Boolean,
    val message: String = ""
)

@JsonClass(generateAdapter = true)
data class MessageSyncDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val type: String,
    val sentAt: Long,
    val mediaUri: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageSyncBatchDto(
    val messages: List<MessageSyncDto>
)

@JsonClass(generateAdapter = true)
data class MessageSyncResponseDto(
    val syncedIds: List<String>,
    val serverTimestamp: Long
)

@JsonClass(generateAdapter = true)
data class UserLookupDto(
    val id: String,
    val displayName: String,
    val username: String,
    val bio: String = "",
    val isOnline: Boolean = false
)

interface PrunelleApiService {
    @GET("v1/health")
    suspend fun checkHealth(): Response<ApiStatusDto>

    @POST("v1/auth/otp/send")
    suspend fun requestPhoneOtp(@Body body: OtpRequestDto): Response<ApiStatusDto>

    @POST("v1/auth/otp/verify")
    suspend fun verifyPhoneOtp(@Body body: OtpVerifyDto): Response<ApiStatusDto>

    @POST("v1/messages/sync")
    suspend fun syncMessages(@Body batch: MessageSyncBatchDto): Response<MessageSyncResponseDto>

    @GET("v1/users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<UserLookupDto>>

    @POST("v1/messages/{messageId}/ack")
    suspend fun acknowledgeMessage(
        @Path("messageId") messageId: String,
        @Body status: Map<String, String>
    ): Response<ApiStatusDto>
}

object ApiClient {
    fun create(baseUrl: String, authTokenProvider: () -> String? = { null }): PrunelleApiService {
        val safeUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val headerInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("X-Prunelle-Client", "Android/1.0")
            authTokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                builder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val moshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PrunelleApiService::class.java)
    }
}
