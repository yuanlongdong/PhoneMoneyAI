package com.phonemoneyai.client.api

import com.phonemoneyai.client.BuildConfig
import com.phonemoneyai.client.model.DecisionResponse
import com.phonemoneyai.client.model.DecisionState
import com.phonemoneyai.client.model.NextStepResponse
import com.phonemoneyai.client.model.ScreenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PhoneMoneyApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun uploadScreen(payload: ScreenRequest) {
        post("/screen", json.encodeToString(ScreenRequest.serializer(), payload))
    }

    suspend fun getNextStep(taskId: String): NextStepResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/task/$taskId/next")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "next step failed: ${response.code}" }
            json.decodeFromString(NextStepResponse.serializer(), response.body!!.string())
        }
    }

    suspend fun decide(state: DecisionState): DecisionResponse = withContext(Dispatchers.IO) {
        val responseBody = post("/decide", json.encodeToString(DecisionState.serializer(), state))
        json.decodeFromString(DecisionResponse.serializer(), responseBody)
    }

    private suspend fun post(path: String, body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "request failed: ${response.code}" }
            response.body?.string().orEmpty()
        }
    }
}
