package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import com.maksimowiczm.foodyou.ai.infrastructure.AiProtocolClient
import com.maksimowiczm.foodyou.ai.infrastructure.ensureSuccess
import com.maksimowiczm.foodyou.ai.infrastructure.executeAiRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class GeminiClient(private val client: HttpClient) : AiProtocolClient {

    override suspend fun complete(
        system: String,
        user: String,
        images: List<PreparedImage>,
        config: AiProviderConfig,
    ): String = executeAiRequest {
        val apiKey = config.apiKey ?: throw AiCalculationException.NotConfigured
        val model = config.model ?: throw AiCalculationException.NotConfigured

        val response =
            client.post(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            ) {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(requestBody(system = system, user = user, images = images))
            }
        response.ensureSuccess()

        val body =
            json.decodeFromString(GenerateContentResponse.serializer(), response.bodyAsText())
        body.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw AiCalculationException.InvalidResponse
    }

    private fun requestBody(system: String, user: String, images: List<PreparedImage>) =
        buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", user) }
                        images.forEach { image ->
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", image.base64)
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generation_config") { put("response_mime_type", "application/json") }
        }

    @Serializable
    private data class GenerateContentResponse(val candidates: List<Candidate> = emptyList()) {

        @Serializable
        data class Candidate(val content: Content = Content()) {

            @Serializable
            data class Content(val parts: List<Part> = emptyList()) {

                @Serializable
                data class Part(val text: String? = null)
            }
        }
    }

    private companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
