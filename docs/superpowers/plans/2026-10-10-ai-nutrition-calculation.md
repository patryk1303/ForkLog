# AI Nutrition Calculation in Quick Add — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let quick-add users estimate a meal's nutrition from photo(s) and/or a description via a user-configured LLM provider, review the JSON result, and save it through the existing quick-add flow.

**Architecture:** A `NutritionCalculator` domain interface is implemented by one class that reads `AiPreferences` from DataStore and dispatches to one of three internal protocol clients (OpenAI-compatible chat completions, Gemini generateContent, Anthropic messages) sharing a Ktor `HttpClient`. A shared lenient parser turns raw model text into a `NutritionFacts`. In quick add, a `QuickAddAiController` held by the screen ViewModels owns description/photos/status/applied-result state; a pure merge function combines the applied AI overlay with the 5 editable form fields at save time.

**Tech Stack:** Kotlin Multiplatform (Android-first; no iosMain), Compose Multiplatform 1.10.3, Ktor 3.4.3 (+ `ktor-client-mock` for tests), kotlinx.serialization 1.10.0, Koin 4.2.2, DataStore Preferences 1.2.1, Material3, accompanist-free permission handling via `ActivityResultContracts`.

**Spec:** `docs/superpowers/specs/2026-10-10-ai-nutrition-calculation-design.md` — the plan argues from the spec; read it first.

## Global Constraints

- Repo: D:\_Code\ForkLog, branch `ai-calculation`, package `com.maksimowiczm.foodyou`, app code in `app/src/{commonMain,androidMain}`.
- All new `expect` declarations get **androidMain actuals only** (existing repo pattern; iOS targets are ignored on this host — never create `iosMain` files).
- New user-facing strings go only to `shared/resources/src/commonMain/composeResources/values/strings.xml` (English). Before adding a key, grep for an existing one that fits.
- One new runtime dependency: none. One new test dependency: `io.ktor:ktor-client-mock` (commonTest).
- Do not commit anything; the product owner has not requested commits.
- Formatting: ktfmt kotlinlang style (`just format` if `$KTFMT_JAR` is available, otherwise match surrounding code style).
- Verification commands (Windows): `gradlew.bat :app:testDebugUnitTest`, `gradlew.bat :app:assembleDebug`.
- API keys and custom base URLs are stored plain in DataStore (USDA-key precedent). Keys must never be logged or placed in error messages.
- Provider defaults: OpenAI `https://api.openai.com/v1` model `gpt-4o-mini`; OpenRouter `https://openrouter.ai/api/v1` model `openai/gpt-4o-mini`; Gemini model `gemini-2.5-flash`; Anthropic model `claude-sonnet-4-5`; Custom no default base URL/model (user must type both). Base URLs and models are always user-editable.

## Review Focus

1. **AI apply on update must not erase stored nutrients the AI didn't return** (vitamins, cholesterol, and null AI fields). → `QuickAddNutritionMergeTest` in Task 6: "update with partial overlay preserves untouched nutrients" and "null overlay fields keep base values".
2. **Every provider failure surfaces a typed, localized error** (401/429/5xx/network) instead of a crash or raw body. → MockEngine status tests in Task 3 (`Unauthorized`, `RateLimited`, `ServerError` per client) + `Network` on thrown IO.
3. **Malformed/hostile model output** (markdown fences, prose around JSON, negatives, missing/blank `name`, missing energy) parses or fails with `InvalidResponse` — never crashes. → `AiResponseParserTest` cases in Task 2.
4. **Unconfigured provider**: Calculate with no key configured must navigate to AI settings (not show a stack-trace error). → `NutritionCalculatorImplTest` "no selected provider throws NotConfigured" (Task 3) + section guard in Task 7 (manual QA item 1).
5. **Energy unit round-trip**: AI kcal → user units (kcal or kJ) in the field → converted back with `toKcal` on save; auto-calc disabled only on >1 kcal mismatch. → `shouldDisableAutoCalculate` unit test (Task 6) + manual QA item 3.

---

### Task 1: AI domain types + preferences storage

**Files:**
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/domain/AiProvider.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/domain/AiPreferences.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/domain/NutritionCalculator.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/DataStoreAiPreferencesRepository.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/AiModule.kt`
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/di/InitKoin.kt` (add `aiModule` to the `modules(...)` list, import `com.maksimowiczm.foodyou.ai.infrastructure.aiModule`)
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/AiPreferencesMappingTest.kt`

**Interfaces:**
- Consumes: `com.maksimowiczm.foodyou.common.domain.userpreferences.{UserPreferences, UserPreferencesRepository}`, `com.maksimowiczm.foodyou.common.infrastructure.datastore.AbstractDataStoreUserPreferencesRepository`, `com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepositoryOf`, `com.maksimowiczm.foodyou.common.domain.food.NutritionFacts`.
- Produces (later tasks rely on these exact names):
  - `enum class AiProvider { OpenAi, Gemini, Anthropic, OpenRouter, Custom }` with `val defaultModel: String?` (`null` only for `Custom`) and `val defaultBaseUrl: String?` (`"https://api.openai.com/v1"` for OpenAi, `"https://openrouter.ai/api/v1"` for OpenRouter, `null` otherwise).
  - `data class AiProviderConfig(val apiKey: String?, val model: String?, val baseUrl: String?)`
  - `data class AiPreferences(val selectedProvider: AiProvider?, val openAi: AiProviderConfig, val gemini: AiProviderConfig, val anthropic: AiProviderConfig, val openRouter: AiProviderConfig, val custom: AiProviderConfig) : UserPreferences` with `fun configOf(provider: AiProvider): AiProviderConfig` and `val isConfigured: Boolean` (selected non-null AND its config has a non-blank `apiKey` AND non-blank `model` AND (provider != Custom OR non-blank `baseUrl`)).
  - Defaults: `AiPreferences(selectedProvider = null, ...)` where each config defaults to `AiProviderConfig(apiKey = null, model = provider.defaultModel, baseUrl = provider.defaultBaseUrl)`.
  - `data class PreparedImage(val jpeg: ByteArray, val base64: String)`
  - `data class NutritionAiRequest(val description: String?, val images: List<PreparedImage>)` (description blank → null at construction site)
  - `data class NutritionAiResult(val name: String, val nutritionFacts: NutritionFacts)`
  - `interface NutritionCalculator { suspend fun calculate(request: NutritionAiRequest): NutritionAiResult }`
  - `const val MAX_AI_PHOTOS = 4` (top-level in `NutritionCalculator.kt`)
  - `sealed class AiCalculationException(message: String, cause: Throwable? = null) : Exception(message, cause)` with `object NotConfigured : ...`, `object Unauthorized`, `object RateLimited`, `object ServerError`, `class Network(cause: Throwable)`, `object InvalidResponse`.
  - `DataStoreAiPreferencesRepository` exposing internal top-level `fun Preferences.toAiPreferences(): AiPreferences` and `fun MutablePreferences.applyAiPreferences(updated: AiPreferences)` (keeps mapping unit-testable without a DataStore instance); preference keys namespaced `"ai:*"` (`ai:selected_provider` stores `AiProvider.name`, `ai:openai_api_key`, `ai:openai_model`, …, `ai:custom_base_url`).
  - `fun Module.aiModule()` in `AiModule.kt` (Task 1 registers only `userPreferencesRepositoryOf(::DataStoreAiPreferencesRepository)`; Tasks 3–4 add more).

- [ ] **Step 1: Write the failing test**

`AiPreferencesMappingTest.kt` (package `com.maksimowiczm.foodyou.ai.infrastructure`):
- `defaults are unconfigured` — `preferencesOf().toAiPreferences()` equals `AiPreferences(selectedProvider = null, ...defaults)`; `isConfigured` false.
- `roundtrip preserves all fields` — build prefs with every provider configured (e.g. `selectedProvider = OpenRouter`, `openRouter = AiProviderConfig("key", "org/model", null)`, custom with `baseUrl = "http://10.0.0.2:11434/v1"`), apply to `preferencesOf().toMutablePreferences()`, read back with `toAiPreferences()`, assertEquals.
- `unknown selected provider string is ignored` — apply prefs with `selectedProvider = OpenAi`, then overwrite the raw key via `this[stringPreferencesKey("ai:selected_provider")] = "Bogus"`; read → `selectedProvider == null`.
- `missing model falls back to provider default` — apply prefs, remove `ai:gemini_model` key, read → `gemini.model == AiProvider.Gemini.defaultModel`.
- `isConfigured requires key, model and custom base url` — parametrized-ish assertions: missing key → false; blank model → false; Custom with key+model but null baseUrl → false; Custom with all three → true.

Use `androidx.datastore.preferences.core.preferencesOf` (already on classpath via `datastore-preferences-core`) and `preferences.toMutablePreferences()`.

- [ ] **Step 2: Run the test, verify it fails**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.maksimowiczm.foodyou.ai.infrastructure.AiPreferencesMappingTest"`
Expected: compilation FAIL (unresolved `AiPreferences` / `toAiPreferences`).

- [ ] **Step 3: Implement the domain + storage files**

As specified in the Interfaces block. `DataStoreAiPreferencesRepository` mirrors `food/search/infrastructure/repository/DataStoreFoodSearchPreferencesRepository.kt` exactly (extends `AbstractDataStoreUserPreferencesRepository<AiPreferences>`, private `object AiPreferencesKeys`). Null/absent keys read back as the defaults listed above; blank strings are stored as-is only if non-blank (mirror USDA: `takeIf { it.isNotBlank() }` happens at the UI layer — repository stores what it's given).

- [ ] **Step 4: Run the test, verify it passes**

Run: same command as Step 2. Expected: PASS.

- [ ] **Step 5: Register the module and compile**

Add `aiModule` to `InitKoin.kt`, then run `gradlew.bat :app:assembleDebug`. Expected: BUILD SUCCESSFUL.

---

### Task 2: Prompt builder + response parser

**Files:**
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/NutritionPrompt.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/AiResponseParser.kt`
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/AiResponseParserTest.kt`
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/NutritionPromptTest.kt`

**Interfaces:**
- Consumes: Task 1 types (`NutritionAiResult`, `AiCalculationException.InvalidResponse`), `common/domain/food/NutritionFacts`, `NutrientValue.toNutrientValue()`, `common/domain/food/NutrientsHelper` (`calculateEnergy(proteins, carbohydrates, fats): Double` — kcal).
- Produces:
  - `internal object NutritionPrompt { fun system(languageTag: String): String; fun user(description: String?): String }`
  - `internal object AiResponseParser { fun parse(raw: String): NutritionAiResult }`

**Fixed JSON schema taught by the prompt** (keys exactly): `name` (string), `energy_kcal`, `proteins_g`, `carbohydrates_g`, `fats_g`, `saturated_fats_g`, `sugars_g`, `dietary_fiber_g`, `salt_g` (all numbers or null).

- [ ] **Step 1: Write failing tests**

`AiResponseParserTest` cases (each is a `@Test fun`):
- `parses plain json` — `"""{"name":"Oatmeal","energy_kcal":350,"proteins_g":12,"carbohydrates_g":60,"fats_g":7,"saturated_fats_g":1.2,"sugars_g":3,"dietary_fiber_g":5,"salt_g":0.4}"""` → name `"Oatmeal"`; every nutrient `value` equals input; all are `NutrientValue.Complete`.
- `parses json wrapped in markdown fences` — input ```` ```json\n{...}\n``` ```` → same result.
- `parses json with surrounding prose` — `"Here is your estimate: {...} hope that helps"` → parses (substring from first `{` to last `}`).
- `missing nutrients become incomplete null` — `{"name":"X","proteins_g":10}` → `fats`, `sugars`, `salt` etc. are `Incomplete(null)`; energy computed.
- `missing energy falls back to atwater` — macros 10/20/5 → `energy.value == 4*10 + 4*20 + 9*5` (205.0) exactly.
- `missing energy with null macro stays null` — `{"name":"X","proteins_g":10,"fats_g":null}` → `energy.value == null`.
- `negative values become null` — `"carbohydrates_g": -3` → `Incomplete(null)`/value null, no exception, no crash.
- `blank name throws invalid response` — `{"name":"  "}` → `assertFailsWith<AiCalculationException.InvalidResponse>`.
- `garbage without json throws invalid response` — `"I cannot estimate that."` → `InvalidResponse`.
- `unknown keys are ignored` — extra `"notes":"..."` key → parses fine.

`NutritionPromptTest`:
- `system contains every schema key and the language tag` — for `"pl-PL"`, system text contains `pl-PL` and all 9 keys (`name`, `energy_kcal`, …, `salt_g`).
- `system states units and null rule` — contains `kcal` and `null`.
- `user with description returns description` / `user without description mentions photo` — non-blank description appears verbatim; null → text mentions "photo".

- [ ] **Step 2: Run tests, verify they fail**

Run: `gradlew.bat :app:testDebugUnitTest --tests "com.maksimowiczm.foodyou.ai.infrastructure.*"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement `NutritionPrompt` and `AiResponseParser`**

- Prompt: system prompt is a raw string describing the nutritionist role, "Respond with ONLY a JSON object, no prose, no markdown fences", the schema above with types, rules: absolute totals for the whole portion as described, `null` when unknown, non-negative, short dish name in the given language. `user(description)`: non-blank description verbatim, else fixed text like `"Estimate nutrition from the attached photo(s)."`.
- Parser: strip to first `{` … last `}` (no `{` → `InvalidResponse`); decode with `Json { ignoreUnknownKeys = true }` into an internal `@Serializable` DTO with all nutrient fields `Double?` and `name: String?`; blank/null name → `InvalidResponse`; map each nutrient with `value.takeIf { it >= 0 }?.toNutrientValue()` (i.e. negative → null → `Incomplete(null)`, non-null → `Complete`); energy fallback per rules above via `NutrientsHelper.calculateEnergy`. Wrap `SerializationException` → `InvalidResponse`.

- [ ] **Step 4: Run tests, verify they pass**

Run: same as Step 2. Expected: PASS (all parser + prompt tests).

---

### Task 3: Protocol clients, calculator implementation, Koin wiring

**Files:**
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/AiProtocolClient.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/openai/OpenAiCompatibleClient.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/gemini/GeminiClient.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/anthropic/AnthropicClient.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/NutritionCalculatorImpl.kt`
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/AiModule.kt` (HttpClient + clients + calculator)
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/OpenAiCompatibleClientTest.kt`
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/GeminiClientTest.kt`
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/AnthropicClientTest.kt`
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/NutritionCalculatorImplTest.kt`
- Modify: `gradle/libs.versions.toml` (add `ktor-client-mock` = "3.4.3", library `io.ktor:ktor-client-mock`) and `app/build.gradle.kts` (`commonTest.dependencies` add `implementation(libs.ktor.client.mock)`)

**Interfaces:**
- Consumes: Task 1–2 types; `common/system/SystemDetails` (`val languageTag: Flow<String>`); Koin `userPreferencesRepository<AiPreferences>()`.
- Produces:
  - `internal interface AiProtocolClient { suspend fun complete(system: String, user: String, images: List<PreparedImage>, config: AiProviderConfig): String }` — returns raw assistant text.
  - `internal class OpenAiCompatibleClient(private val client: HttpClient) : AiProtocolClient`
  - `internal class GeminiClient(private val client: HttpClient) : AiProtocolClient`
  - `internal class AnthropicClient(private val client: HttpClient) : AiProtocolClient`
  - `internal class NutritionCalculatorImpl(private val preferencesRepository: UserPreferencesRepository<AiPreferences>, private val systemDetails: SystemDetails, private val openAiClient: AiProtocolClient, private val geminiClient: AiProtocolClient, private val anthropicClient: AiProtocolClient) : NutritionCalculator`
  - Koin qualifiers: `named(HttpClient::class.qualifiedName!!)` style used by `OpenFoodFactsModule` — use `named(OpenAiCompatibleClient::class.qualifiedName!!)`, `named(GeminiClient::class.qualifiedName!!)`, `named(AnthropicClient::class.qualifiedName!!)` for the clients; a single shared `HttpClient` single named `NutritionCalculatorImpl::class.qualifiedName!!`.
  - `NutritionCalculatorImpl.calculate` flow: `val prefs = preferencesRepository.observe().first()`; `val provider = prefs.selectedProvider ?: throw AiCalculationException.NotConfigured`; `val config = prefs.configOf(provider)`; if `!prefs.isConfigured` → `throw NotConfigured`; `val languageTag = systemDetails.languageTag.first()`; pick client by provider (`OpenAi/OpenRouter/Custom → openAiClient`, `Gemini → geminiClient`, `Anthropic → anthropicClient`); `complete(system, user, images, config)` → `AiResponseParser.parse(text)`.

**Wire formats (fixed):**
- OpenAI-compatible: `POST "${config.baseUrl!!.trimEnd('/')}/chat/completions"`, header `Authorization: Bearer <apiKey>`. Body: `{"model", "messages":[{"role":"system","content":system},{"role":"user","content":[ {"type":"text","text":user}, ...images.map { {"type":"image_url","image_url":{"url":"data:image/jpeg;base64,<b64>"}} } ]}], "response_format":{"type":"json_object"}}`. Response: `choices[0].message.content` (null → `InvalidResponse`).
- Gemini: `POST "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent"`, header `x-goog-api-key: <apiKey>`. Body: `{"system_instruction":{"parts":[{"text":system}]}, "contents":[{"role":"user","parts":[ {"text":user}, ...images.map { {"inline_data":{"mime_type":"image/jpeg","data":<b64>}} } ]}], "generation_config":{"response_mime_type":"application/json"}}` (use kotlinx JsonObject/DTO field names exactly as shown — kotlinx uses whatever names you give the DTO properties; name DTO properties exactly like the JSON). Response: `candidates[0].content.parts[0].text` (null → `InvalidResponse`).
- Anthropic: `POST "https://api.anthropic.com/v1/messages"`, headers `x-api-key: <apiKey>`, `anthropic-version: 2023-06-01`. Body: `{"model", "max_tokens":1024, "system":system, "messages":[{"role":"user","content":[ ...images.map { {"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":<b64>}} }, {"type":"text","text":user} ]}]}` (images before text). Response: first `content` block with `type=="text"` → `text` (none → `InvalidResponse`).
- Status mapping (shared private helper in `AiProtocolClient.kt`): `HttpResponse` non-2xx → 401/403 `Unauthorized`, 429 `RateLimited`, 5xx `ServerError`, other 4xx `InvalidResponse`. Throw before reading body. Catch `CancellationException` → rethrow; catch `AiCalculationException` → rethrow; any other `Exception` → `Network(cause)`.

**Test details:**
- Each client test builds `HttpClient(MockEngine { request -> ...respond(content = fixtureJson, status = HttpStatusCode.OK) })` with `ContentNegotiation { json(Json { ignoreUnknownKeys = true }) }`.
- `OpenAiCompatibleClientTest`: `sends chat completions request` (assert `request.url.toString()` ends with `/v1/chat/completions`, `Authorization` header = `Bearer test-key`, body string contains `"response_format"`, the description text, and exactly 2 occurrences of `data:image/jpeg;base64` for 2 images) + `returns assistant text`; `401 maps to Unauthorized`, `429 maps to RateLimited`, `500 maps to ServerError`, `400 maps to InvalidResponse` (four small tests via a helper), `thrown IOException maps to Network`.
- `GeminiClientTest`: `sends generate content request` (URL contains `/v1beta/models/test-model:generateContent`, header `x-goog-api-key`, body contains `response_mime_type` and `inline_data`) + one status test (401 → Unauthorized).
- `AnthropicClientTest`: `sends messages request` (URL, both headers, body contains `"max_tokens":1024` and image block `"type":"base64"` appears before the text block index-wise) + one status test (429 → RateLimited).
- `NutritionCalculatorImplTest` (fake `UserPreferencesRepository` returning a `MutableStateFlow` of prefs, fake `SystemDetails` with `languageTag = flowOf("en-US")`, recording fake `AiProtocolClient`s returning a canned JSON):
  - `no selected provider throws NotConfigured`
  - `selected provider without key throws NotConfigured`
  - `dispatches to open ai client for OpenRouter and custom` (fake records call; assert system prompt got language `en-US`)
  - `dispatches to gemini for Gemini`, `dispatches to anthropic for Anthropic`
  - `parses client text into result`.

- [ ] **Step 1: Add the mock dependency, write failing client tests** (OpenAI full suite first).
- [ ] **Step 2: Run, verify FAIL** — `gradlew.bat :app:testDebugUnitTest --tests "com.maksimowiczm.foodyou.ai.infrastructure.*"`
- [ ] **Step 3: Implement `AiProtocolClient.kt` (interface + status/exception helper) + `OpenAiCompatibleClient`; wire HttpClient in `AiModule`.** Note: URL for OpenAI/OpenRouter comes from `config.baseUrl` (falls back to `provider.defaultBaseUrl` — if `config.baseUrl` is null, look it up: pass provider default into `AiProviderConfig` at read time in `configOf`, so `config.baseUrl` is never null for non-custom providers).
- [ ] **Step 4: Run OpenAI tests, verify PASS.**
- [ ] **Step 5: Implement Gemini + Anthropic clients and their tests; run, verify PASS.**
- [ ] **Step 6: Implement `NutritionCalculatorImpl` + tests; register everything in `AiModule`** (`single(named(...)) { HttpClient { install(HttpTimeout) { requestTimeoutMillis = 60_000 }; install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } } }.onClose { it?.close() }`, `factory(named(...)) { OpenAiCompatibleClient(get(named(...))) }` etc., `factory<NutritionCalculator> { NutritionCalculatorImpl(userPreferencesRepository(), get(), get(named(openAiQ)), get(named(geminiQ)), get(named(anthropicQ))) }`).
- [ ] **Step 7: Run all AI tests + `gradlew.bat :app:assembleDebug`, verify PASS/SUCCESS.**

---

### Task 4: Image preparation, Base64, platform manifest

**Files:**
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/Base64.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/ImagePreparation.kt` (interface + `expect fun decodeJpegToImageBitmap`)
- Create: `app/src/androidMain/kotlin/com/maksimowiczm/foodyou/ai/AndroidImagePreparation.kt`
- Create: `app/src/androidMain/kotlin/com/maksimowiczm/foodyou/ai/ImagePreparationModule.android.kt` (`actual fun Module.imagePreparationDefinition()`)
- Create (commonMain declaration): `expect fun Module.imagePreparationDefinition()` inside `ImagePreparation.kt` (commonMain) — pattern copied from `common/infrastructure/system`'s `expect fun Module.systemDetailsDefinition()`
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/infrastructure/AiModule.kt` (call `imagePreparationDefinition()`)
- Modify: `app/src/androidMain/AndroidManifest.xml` (CAMERA permission, `android:networkSecurityConfig`, FileProvider)
- Create: `app/src/androidMain/res/xml/network_security_config.xml`
- Create: `app/src/androidMain/res/xml/ai_file_paths.xml`
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/ai/Base64Test.kt`

**Interfaces:**
- Consumes: `PreparedImage`, `MAX_AI_PHOTOS` (Task 1); Koin `androidContext()` (pattern: `common/infrastructure/datastore/DataStoreModule.android.kt`).
- Produces:
  - `fun ByteArray.encodeToBase64(): String` (pure Kotlin, RFC 4648 standard alphabet with `=` padding).
  - `interface ImagePreparation { fun prepare(uri: String): PreparedImage? }` — returns null when the URI cannot be read/decoded.
  - `expect fun decodeJpegToImageBitmap(jpeg: ByteArray): ImageBitmap?`
  - `expect fun Module.imagePreparationDefinition()` / android `actual` binding `factory<ImagePreparation> { AndroidImagePreparation(androidContext()) }`.

**AndroidImagePreparation behavior:** `contentResolver.openInputStream(uri.toUri())` → decode with `BitmapFactory` + `inJustDecodeBounds` passes to compute `inSampleSize` so the longest edge ≤ 1280 → decode final bitmap → `compress(JPEG, 80)` into `ByteArrayOutputStream` → `PreparedImage(jpeg, jpeg.encodeToBase64())`. `decodeJpegToImageBitmap` actual: `BitmapFactory.decodeByteArray(...)?.asImageBitmap()`. Perform work off the main thread at the call site (Task 6 uses `Dispatchers.IO`).

**Manifest additions:**
- `<uses-permission android:name="android.permission.CAMERA" />`
- In `<application>`: `android:networkSecurityConfig="@xml/network_security_config"`
- FileProvider: `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.ai.fileprovider" android:exported="false" android:grantUriPermissions="true"><meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/ai_file_paths" /></provider>`
- `network_security_config.xml`: `<network-security-config><base-config cleartextTrafficPermitted="true" /></network-security-config>`
- `ai_file_paths.xml`: `<paths><cache-path name="ai_photos" path="ai_photos/" /></paths>`

- [ ] **Step 1: Write failing `Base64Test`** — vectors: `""` → `""`, `"f"` → `"Zg=="`, `"fo"` → `"Zm8="`, `"foo"` → `"Zm9v"`, `"foob"` → `"Zm9vYg=="`, `"fooba"` → `"Zm9vYmE="`, `"foobar"` → `"Zm9vYmFy"` (encode `"…".encodeToByteArray()`).
- [ ] **Step 2: Run, verify FAIL** — `gradlew.bat :app:testDebugUnitTest --tests "com.maksimowiczm.foodyou.ai.Base64Test"`
- [ ] **Step 3: Implement `Base64.kt`, run, verify PASS.**
- [ ] **Step 4: Implement `ImagePreparation` (common) + `AndroidImagePreparation` + module `expect`/`actual`, wire into `AiModule`, add manifest/xml files.**
- [ ] **Step 5: `gradlew.bat :app:assembleDebug` — BUILD SUCCESSFUL.** (No unit test for Android image decode — manual QA covers it; see QA checklist.)

---

### Task 5: AI settings screen + navigation

**Files:**
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/settings/ai/AiSettingsScreen.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/settings/ai/AiSettingsViewModel.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/settings/ai/AiSettingsModule.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/settings/ai/AiSettingsListItem.kt`
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/UiModule.kt` (add `aiSettings()`)
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/settings/SettingsScreen.kt` (new `onAiSettings: () -> Unit` param + list item after ExternalDatabases)
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/navigation/FoodYouAppNavHost.kt` (route + composable + pass `onAiSettings`)
- Modify: `shared/resources/src/commonMain/composeResources/values/strings.xml`
- Test: none (UI — verified by assemble + manual QA 4–5).

**Interfaces:**
- Consumes: `AiPreferences`, `AiProvider`, `AiProviderConfig`, `userPreferencesRepository<AiPreferences>()`, `SettingsListItem` (signature: `icon`, `label`, `supportingContent`, `onClick`, `shape`, `color`, `contentColor` — copy usage from `ExternalDatabasesSettingsListItem.kt`), nav patterns from `FoodYouAppNavHost.kt:87-98` and route style from line 448+.
- Produces:
  - `internal class AiSettingsViewModel(private val preferencesRepository: UserPreferencesRepository<AiPreferences>) : ViewModel()` with `val preferences: StateFlow<AiPreferences?>` (`observe().stateIn(...)`), `fun selectProvider(provider: AiProvider)` and `fun saveConfig(provider: AiProvider, apiKey: String?, model: String?, baseUrl: String?)` — both `viewModelScope.launch { preferencesRepository.update { ... } }` storing `apiKey/model` via `takeIf { it.isNotBlank() }`.
  - `internal fun Module.aiSettings()` registering `viewModelOf(::AiSettingsViewModel)`.
  - `fun AiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier)` — Scaffold + `LargeFlexibleTopAppBar` + LazyColumn (copy structure from `app/ui/database/externaldatabases/ExternalDatabasesScreen.kt:43`): provider `RadioButton` rows (5 options, labels from strings), then for the selected provider: API key `OutlinedTextField` with `PasswordVisualTransformation()`, model `OutlinedTextField` (placeholder = provider default), base-URL field only for `Custom` (validate `startsWith("http://") || startsWith("https://")`, show error text otherwise), privacy note `Text` (bodySmall, surface-variant Card), `FilledTonalButton(action_save)` at the end calling `saveConfig` with the field values.
  - Nav: `@Serializable private object AiSettings` + `forwardBackwardComposable<AiSettings> { AiSettingsScreen(onBack = { navController.popBackStackInclusive<AiSettings>() }) }` + `SettingsScreen(onAiSettings = { navController.navigateSingleTop(AiSettings) })`.
  - `AiSettingsListItem` mirrors `ExternalDatabasesSettingsListItem.kt` (icon `Icons.Outlined.AutoAwesome`).
- **New strings** (add; grep first for equivalents): `headline_ai_settings` "Artificial intelligence", `description_ai_settings` "Estimate nutrition with an AI provider", `headline_ai_provider` "Provider", `headline_base_url` "Base URL", `error_invalid_base_url` "Enter a valid URL starting with http:// or https://", `description_ai_privacy` "Photos and descriptions are sent directly to the selected provider. Your API key is stored on this device only.", `provider_openai` "OpenAI", `provider_google_gemini` "Google Gemini", `provider_anthropic` "Anthropic", `provider_openrouter` "OpenRouter", `provider_custom` "Custom (OpenAI-compatible)". Reuse: `headline_api_key`, `action_save`, `headline_settings`.

- [ ] **Step 1: Add strings to `strings.xml`** (grouped near existing settings/external-database strings).
- [ ] **Step 2: Implement ViewModel + module + `UiModule` registration; `gradlew.bat :app:assembleDebug` → SUCCESS.**
- [ ] **Step 3: Implement `AiSettingsListItem` + wire into `SettingsScreen` + nav route; `gradlew.bat :app:assembleDebug` → SUCCESS.**
- [ ] **Step 4: Implement `AiSettingsScreen`; `gradlew.bat :app:assembleDebug` → SUCCESS.**

---

### Task 6: AI calculation state, merge logic, save path

**Files:**
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/QuickAddAiController.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/QuickAddNutritionMerge.kt`
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/CreateQuickAddViewModel.kt`
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/UpdateQuickAddViewModel.kt`
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/FoodDiaryQuickAddModule.kt` (Create VM factory gains `nutritionCalculator = get()`, `imagePreparation = get()`; `UpdateQuickAddViewModel` gains the same ctor params via `viewModelOf`)
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/QuickAddFormState.kt` (add `applyAiResult`)
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/QuickAddNutritionMergeTest.kt`
- Test: `app/src/commonTest/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/AiAutoCalculateTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4 (`NutritionCalculator`, `ImagePreparation`, `AiCalculationException`, `MAX_AI_PHOTOS`, `decodeJpegToImageBitmap`, `PreparedImage`), `NutritionFacts` copy, `NutrientValue.toNutrientValue()`, `LocalEnergyFormatter`/`EnergyFormatter` (`fromKcal`, `toKcal`), `formatClipZeros`, `TextFieldState.setTextAndPlaceCursorAtEnd`.
- Produces:
  - `internal data class AiPhoto(val image: PreparedImage, val thumbnail: ImageBitmap?)`
  - `internal data class QuickAddAiState(val description: String = "", val photos: List<AiPhoto> = emptyList(), val status: Status = Status.Idle, val result: NutritionAiResult? = null, val appliedOverlay: NutritionFacts? = null) { sealed interface Status { data object Idle : Status; data object Loading : Status; data class Error(val exception: AiCalculationException) : Status }; val canCalculate: Boolean /* photos.isNotEmpty() || description.isNotBlank() */ }`
  - `internal class QuickAddAiController(private val calculator: NutritionCalculator, private val imagePreparation: ImagePreparation, private val scope: CoroutineScope)` with:
    - `val state: StateFlow<QuickAddAiState>` (backed by `MutableStateFlow`)
    - `fun setDescription(description: String)` (clears `result`, keeps `appliedOverlay`)
    - `fun addPhotos(uris: List<String>)` — truncate incoming list to `MAX_AI_PHOTOS - photos.size`; launch one coroutine in `scope` on `Dispatchers.IO` that processes the URIs sequentially: `imagePreparation.prepare(uri)` → on non-null append `AiPhoto(image, decodeJpegToImageBitmap(image.jpeg))` to state; skip nulls; never throw.
    - `fun removePhoto(photo: AiPhoto)`
    - `fun clearPhotos()`
    - `fun calculate()` — non-suspend, `scope.launch`: set `Status.Loading`; call `calculator.calculate(NutritionAiRequest(description.trim().takeIf { it.isNotBlank() }, photos.map { it.image }))`; success → `result = value, status = Idle`; failure → `status = Error(e as? AiCalculationException ?: AiCalculationException.Network(e))`.
    - `fun applyResult()` — `appliedOverlay = result?.nutritionFacts`, `result = null`
    - `fun dismissResult()` — `result = null`
  - `internal fun mergeQuickAddNutritionFacts(base: NutritionFacts, aiOverlay: NutritionFacts?, energyKcal: Double, proteins: Double, carbohydrates: Double, fats: Double): NutritionFacts` — starts from `base.copy(energy = energyKcal.toNutrientValue(), proteins = …, carbohydrates = …, fats = …)` then, only when `aiOverlay != null`, overlays `saturatedFats`, `sugars`, `dietaryFiber`, `salt` from the overlay **when `overlay.field.value != null`** (keep base otherwise). All other base fields untouched.
  - `internal fun shouldDisableAutoCalculate(aiKcal: Double?, proteins: Double?, carbohydrates: Double?, fats: Double?): Boolean` — true only when `aiKcal != null && all macros != null && abs(aiKcal - NutrientsHelper.calculateEnergy(proteins, carbohydrates, fats)) > 1.0`.
  - `QuickAddFormState.applyAiResult(result: NutritionAiResult, energyFormatter: EnergyFormatter)` (in `QuickAddFormState.kt`) — writes `name` (verbatim), and for each of proteins/carbohydrates/fats/energy: value non-null → `textFieldState.setTextAndPlaceCursorAtEnd(value.formatClipZeros())`, null → `setTextAndPlaceCursorAtEnd("")`. Energy: `energyFormatter.fromKcal(...)` when AI energy non-null; if AI energy is null and macros present, clear energy text and set `autoCalculateEnergy = true`. If `shouldDisableAutoCalculate(...)` → `autoCalculateEnergy = false`.
  - VM changes:
    - `CreateQuickAddViewModel` ctor gains `nutritionCalculator: NutritionCalculator`, `imagePreparation: ImagePreparation`; owns `val ai = QuickAddAiController(nutritionCalculator, imagePreparation, viewModelScope)`. `addEntry(...)` signature unchanged; internally: `val facts = mergeQuickAddNutritionFacts(base = NutritionFacts(), aiOverlay = ai.state.value.appliedOverlay, energy, proteins, carbohydrates, fats)`.
    - `UpdateQuickAddViewModel` gains the same ctor params; `updateEntry(...)` internally: `val facts = mergeQuickAddNutritionFacts(base = entry.nutritionFacts, aiOverlay = ai.state.value.appliedOverlay, energy, proteins, carbohydrates, fats)` then `entry.copy(name = name, nutritionFacts = facts, updatedAt = dateProvider.now())`.

**Test details:**
- `QuickAddNutritionMergeTest`:
  - `create without overlay fills core and leaves extended null`
  - `overlay overwrites extended ai nutrients` (base empty; overlay has saturatedFats=1 → result 1)
  - `null overlay field keeps base value` (base.saturatedFats = Complete(2.0), overlay.saturatedFats = Incomplete(null) → result 2.0)
  - `core fields always come from arguments not overlay` (overlay.proteins=99 but args proteins=10 → result 10)
  - `update preserves nutrients ai never returns` (base with `vitaminC = Complete(0.08)`, `cholesterol = Complete(0.03)`, overlay non-null for only `salt` → vitaminC/cholesterol unchanged)
  - `negative values never occur` — all args ≥ 0 produce `Complete`.
- `AiAutoCalculateTest`: matches tolerance boundaries — `within 1 kcal keeps auto calculate` (0.5 diff → false), `above 1 disables` (1.1 → true), `null ai energy → false`, `null macro → false`.

- [ ] **Step 1: Write failing merge + auto-calculate tests** (both files).
- [ ] **Step 2: Run, verify FAIL** — `gradlew.bat :app:testDebugUnitTest --tests "com.maksimowiczm.foodyou.app.ui.food.diary.quickadd.*"`
- [ ] **Step 3: Implement `QuickAddNutritionMerge.kt` (merge + shouldDisableAutoCalculate); run, verify PASS.**
- [ ] **Step 4: Implement `QuickAddAiController` + `QuickAddFormState.applyAiResult` + VM changes + `FoodDiaryQuickAddModule` changes; run all quickadd tests + `gradlew.bat :app:assembleDebug` → SUCCESS.**

---

### Task 7: Quick add UI — AI section, photo pickers, result dialog, wiring

**Files:**
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/common/compose/utility/ImagePickers.kt` (expect functions)
- Create: `app/src/androidMain/kotlin/com/maksimowiczm/foodyou/common/compose/utility/ImagePickers.android.kt` (actuals)
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/aicalc/QuickAddAiSection.kt`
- Create: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/aicalc/AiResultDialog.kt`
- Modify: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/ui/food/diary/quickadd/QuickAddScreen.kt` (AI section item above `QuickAddForm`; new params)
- Modify: `CreateQuickAddScreen.kt`, `UpdateQuickAddScreen.kt` (wire state/callbacks/apply; new `onOpenAiSettings` param)
- Modify: `FoodYouAppNavHost.kt` (pass `onOpenAiSettings = { navController.navigateSingleTop(AiSettings) }` at lines 168–186)
- Modify: `shared/resources/src/commonMain/composeResources/values/strings.xml`
- Test: none (Compose UI — verified by assemble + manual QA 1–3, 6–7).

**Interfaces:**
- Consumes: Task 5–6 exactly as named there; `AiPreferences` observation via `koinInject(named(AiPreferences::class.qualifiedName!!))` + `collectAsStateWithLifecycle` (pattern: `UpdateUsdaApiKeyDialog.kt:25-28`); accompanist NOT required — permissions via `ActivityResultContracts.RequestPermission`; `androidx.activity.compose.rememberLauncherForActivityResult` (androidMain only).
- Produces:
  - `@Composable expect fun rememberPhotoPicker(maxItems: Int, onImagesPicked: (List<String>) -> Unit): () -> Unit`
  - `@Composable expect fun rememberCameraCapture(onImageCaptured: (String) -> Unit, onPermissionDenied: () -> Unit): () -> Unit`
    - android actual photo: `ActivityResultContracts.PickMultipleVisualMedia(maxItems)` (guard: if `maxItems < 2` use single `PickVisualMedia`; our constant is 4) returning content URIs; if the contract falls back (picker unavailable) it degrades to single-select — acceptable.
    - android actual camera: create `File(context.cacheDir, "ai_photos").apply { mkdirs() }` + `photo_<timestamp>.jpg`; URI via `FileProvider.getUriForFile(context, "${context.packageName}.ai.fileprovider", file)`; first check `ContextCompat.checkSelfPermission(CAMERA)` → if granted launch `TakePicture`, else launch `RequestPermission(CAMERA)` and launch `TakePicture` only on `granted == true`, otherwise `onPermissionDenied()`.
  - `QuickAddAiSection(state: QuickAddAiState, isConfigured: Boolean, onDescriptionChange: (String) -> Unit, onAddPhotos: (List<String>) -> Unit, onRemovePhoto: (AiPhoto) -> Unit, onCalculate: () -> Unit, onOpenAiSettings: () -> Unit, modifier: Modifier = Modifier)` — OutlinedTextField (multiline, `KeyboardCapitalization.Sentences`, label from a new description string), horizontal `Row` of thumbnails (`Image(bitmap = photo.thumbnail!!, …)` with remove `IconButton` overlay; hide when `photos.size >= MAX_AI_PHOTOS`) + dashed/tonal "add" tile opening a `DropdownMenu` ("Choose photos" → `rememberPhotoPicker(MAX_AI_PHOTOS, onAddPhotos)()`, "Take photo" → `rememberCameraCapture(...)`, on denial set local `var permissionDenied` → show `error_ai_camera_permission` text), `FilledTonalButton` Calculate (enabled `state.canCalculate && status !is Loading`; onClick: `if (!isConfigured) onOpenAiSettings() else onCalculate()`; shows `CircularProgressIndicator` when Loading, otherwise `stringResource(action_ai_calculate)`), error text below in `MaterialTheme.colorScheme.error` mapping `AiCalculationException → error string` (`NotConfigured` → row with the error text + TextButton "Open settings" → `onOpenAiSettings()`).
  - `AiResultDialog(name: String, result: NutritionAiResult, onApply: (String) -> Unit, onDismiss: () -> Unit)` — `AlertDialog`; title `headline_ai_result`; editable name `OutlinedTextField` (local `rememberSaveable(result)` state seeded with `result.name`); nutrient rows for the 8 values using existing formatting (`formatClipZeros` + `Res.string.unit_gram_short` for grams; energy converted with `LocalEnergyFormatter.current.fromKcal(...)` and the formatter's suffix, so kJ users see kJ; null → em dash "—"); confirm `action_apply` → `onApply(editedName)`; dismiss `action_cancel`.
  - `QuickAddScreen` new params: `aiState: QuickAddAiState`, `aiResult: NutritionAiResult?` (or read `aiState.result`), `onAiDescriptionChange`, `onAiAddPhotos`, `onAiRemovePhoto`, `onAiCalculate`, `onAiApply: (String) -> Unit`, `onOpenAiSettings: () -> Unit`. Insert `item { QuickAddAiSection(...) }` between the header text and `QuickAddForm` (inside the existing LazyColumn, before the current single item — restructure to two items). Keep the existing focus-requester behavior unchanged.
  - Screen wiring (both Create/Update): `onAiApply = { editedName -> formState.applyAiResult(result.copy(name = editedName), energyFormatter); viewModel.ai.applyResult() }`; `onAiCalculate = { viewModel.ai.calculate() }`; `isConfigured` from injected `AiPreferences`.
- **New strings**: `headline_ai_calculation` "AI calculation", `description_ai_calculation` "Describe the dish or add photos and let AI estimate the nutrition.", `action_ai_calculate` "Calculate", `action_choose_photos` "Choose photos", `action_take_photo` "Take photo", `action_add_photo` "Add photo", `action_ai_open_settings` "Open settings", `headline_ai_result` "AI estimate", `action_apply` "Apply", `description_ai` "Description", `error_ai_max_photos` "You can add up to 4 photos", `error_ai_camera_permission` "Camera permission is required to take a photo", `error_ai_not_configured` "Set up an AI provider in settings first", `error_ai_unauthorized` "The AI provider rejected your API key", `error_ai_rate_limited` "Rate limit reached. Try again in a moment.", `error_ai_server` "The AI provider had a server error. Try again later.", `error_ai_network` "Network error. Check your connection and try again.", `error_ai_invalid_response` "Couldn't read the AI response. Try again." (grep for existing equivalents first — e.g. `action_cancel`, network-error strings, `headline_api_key`).

- [ ] **Step 1: Implement `ImagePickers.kt` expect + android actuals; `gradlew.bat :app:assembleDebug` → SUCCESS.**
- [ ] **Step 2: Add strings.**
- [ ] **Step 3: Implement `QuickAddAiSection` + `AiResultDialog`; `gradlew.bat :app:assembleDebug` → SUCCESS.**
- [ ] **Step 4: Wire `QuickAddScreen` + both screens + both nav hosts' call sites (only `FoodYouAppNavHost` uses these screens — verify with grep); `gradlew.bat :app:assembleDebug` → SUCCESS.**
- [ ] **Step 5: Manual smoke on device/emulator:** settings → configure provider → quick add → photo + description → Calculate → dialog → Apply → fields filled → Save → diary shows entry. (If no API key available, verify the NotConfigured path navigates to settings.)

---

### Task 8: Polish and full verification

**Files:**
- Modify: `shared/resources/src/commonMain/composeResources/values/strings.xml` (fill any gaps found)
- Modify (only if compile warnings surface): touched files

- [ ] **Step 1: Full unit suite** — `gradlew.bat :app:testDebugUnitTest` → all PASS (includes pre-existing tests).
- [ ] **Step 2: Full compile** — `gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 3: Format** — `just format` if `$KTFMT_JAR` is set (else match style manually).
- [ ] **Step 4: String sweep** — grep `Res.string.` usages added by this feature against `strings.xml`; confirm every key exists exactly once; run `dev/find-unused-strings.sh`-style check for keys added but unused (script is bash — only if environment supports it, otherwise grep manually).
- [ ] **Step 5: Manual QA checklist** (report results to the product owner):
  1. Unconfigured Calculate → navigates to AI settings; returning preserves description/photos.
  2. Rotation during/after calculation preserves everything (VM-held state).
  3. AI energy ≠ 4/4/9 → auto-calc toggle off; saved entry keeps AI kcal; energy shows in kJ when user uses kJ.
  4. Settings round-trip: provider/key/model/base URL persist; back button works.
  5. Update an old manual entry, run AI apply, save → previously stored extended nutrients not in the AI result are still there.
  6. Photo picker limits to 4; camera capture saves thumbnail; removing photos works; camera-permission-denied message shows once.
  7. Error paths: wrong API key → unauthorized message; airplane mode → network message.
