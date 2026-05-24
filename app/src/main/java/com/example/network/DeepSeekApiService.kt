package com.example.network

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class Message(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class DeepSeekRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int
)

@JsonClass(generateAdapter = true)
data class ChoiceMessage(
    val role: String?,
    val content: String?
)

@JsonClass(generateAdapter = true)
data class Choice(
    val index: Int?,
    val message: ChoiceMessage?,
    val finish_reason: String?
)

@JsonClass(generateAdapter = true)
data class DeepSeekResponse(
    val id: String?,
    val choices: List<Choice>?
)

interface DeepSeekApiService {
    @POST("chat/completions")
    suspend fun getCompletions(
        @Header("Authorization") authHeader: String,
        @Body request: DeepSeekRequest
    ): Response<DeepSeekResponse>
}
