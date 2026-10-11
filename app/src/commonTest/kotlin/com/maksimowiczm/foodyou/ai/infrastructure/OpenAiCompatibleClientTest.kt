package com.maksimowiczm.foodyou.ai.infrastructure

import com.maksimowiczm.foodyou.ai.domain.AiCalculationException
import com.maksimowiczm.foodyou.ai.domain.AiProviderConfig
import com.maksimowiczm.foodyou.ai.domain.PreparedImage
import com.maksimowiczm.foodyou.ai.infrastructure.openai.OpenAiCompatibleClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAiCompatibleClientTest {

    @Test
    fun `sends chat completions request`() = runBlocking {
        var captured: HttpRequestData? = null
        val client =
            openAiClientOf(MockEngine { request ->
                captured = request
                respond(content = ASSISTANT_RESPONSE, status = HttpStatusCode.OK)
            })

        client.complete(
            system = SYSTEM_PROMPT,
            user = "two slices of whole grain bread",
            images =
                listOf(
                    PreparedImage(jpeg = byteArrayOf(), base64 = "Zmlyc3Q="),
                    PreparedImage(jpeg = byteArrayOf(), base64 = "c2Vjb25k="),
                ),
            config = CONFIG,
        )

        val request = assertNotNull(captured)
        assertTrue(request.url.toString().endsWith("/v1/chat/completions"))
        assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
        val body = request.bodyText()
        assertTrue(body.contains("\"response_format\""))
        assertTrue(body.contains("two slices of whole grain bread"))
        assertEquals(2, body.split("data:image/jpeg;base64").size - 1)
    }

    @Test
    fun `returns assistant text`() = runBlocking {
        val client = openAiClientOf(MockEngine { respond(content = ASSISTANT_RESPONSE) })

        val text = client.complete(SYSTEM_PROMPT, "a bowl of oatmeal", emptyList(), CONFIG)

        assertEquals("Oatmeal", text)
    }

    @Test
    fun `401 maps to Unauthorized`() = runBlocking {
        assertStatusMapsTo(HttpStatusCode.Unauthorized, AiCalculationException.Unauthorized::class)
    }

    @Test
    fun `429 maps to RateLimited`() = runBlocking {
        assertStatusMapsTo(HttpStatusCode.TooManyRequests, AiCalculationException.RateLimited::class)
    }

    @Test
    fun `500 maps to ServerError`() = runBlocking {
        assertStatusMapsTo(HttpStatusCode.InternalServerError, AiCalculationException.ServerError::class)
    }

    @Test
    fun `400 maps to InvalidResponse`() = runBlocking {
        assertStatusMapsTo(HttpStatusCode.BadRequest, AiCalculationException.InvalidResponse::class)
    }

    @Test
    fun `thrown IOException maps to Network`() = runBlocking {
        val client = openAiClientOf(MockEngine { throw IOException("network down") })

        val exception =
            try {
                client.complete(SYSTEM_PROMPT, "a bowl of oatmeal", emptyList(), CONFIG)
                null
            } catch (exception: AiCalculationException) {
                exception
            }

        assertTrue(exception is AiCalculationException.Network)
    }

    private suspend fun assertStatusMapsTo(status: HttpStatusCode, expected: KClass<out AiCalculationException>) {
        val client = openAiClientOf(MockEngine { respond(content = "error", status = status) })

        val exception =
            try {
                client.complete(SYSTEM_PROMPT, "a bowl of oatmeal", emptyList(), CONFIG)
                null
            } catch (exception: AiCalculationException) {
                exception
            }

        assertNotNull(exception)
        assertEquals(expected, exception::class)
    }

    private fun openAiClientOf(engine: MockEngine): OpenAiCompatibleClient =
        OpenAiCompatibleClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private companion object {
        val CONFIG =
            AiProviderConfig(
                apiKey = "test-key",
                model = "test-model",
                baseUrl = "https://api.openai.com/v1",
            )
        const val SYSTEM_PROMPT = "system prompt"
        const val ASSISTANT_RESPONSE = """{"choices":[{"message":{"content":"Oatmeal"}}]}"""
    }
}