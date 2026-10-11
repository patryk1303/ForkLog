# AI Nutrition Calculation in Quick Add — Design

Date: 2026-10-10
Status: approved (brainstorming + builder check completed; questions resolved with the product owner)

## Intent

Quick add currently makes the user type name, protein, carbs, fat, and kcal by hand. The real
workflow today is: open an external AI chat app, paste a description or photo of the dish, get an
estimate, switch back, type the numbers in. This feature brings that loop into the app: the user
attaches photo(s) and/or a description directly in quick add, the app calls a user-configured LLM
provider, shows the structured result for review, and fills the form.

## Goals

- Quick add (create **and** update — both share `QuickAddForm`) gains an "AI calculation" section:
  - multiple photos (system photo picker, multi-select) plus camera capture;
  - a free-text description (for the dish and any additional ingredients);
  - at least one of photos/description is required to run the calculation.
- The AI returns structured JSON: short name, energy (kcal), proteins, carbohydrates, fats, and
  extended nutrients saturatedFats, sugars, dietaryFiber, salt — all absolute totals for the
  portion as described.
- The result is shown in a review dialog (editable name, displayed nutrients) before anything is
  applied; the normal explicit Save button is unchanged.
- Extended nutrients are persisted with the entry (`ManualDiaryEntryEntity` already stores all 43
  nutrients; no schema migration). The form keeps editing only its current 5 fields.
- Settings gains an "Artificial intelligence" screen: provider picker — OpenAI, Google Gemini,
  Anthropic, OpenRouter, Custom OpenAI-compatible — with BYO API key, model, and (custom only)
  base URL. Requests go device → provider directly; keys are stored on device only.
- Architecture keeps a clean seam for future on-device providers (LiteRT/Gemma, Gemini Nano):
  the domain interface has no HTTP types.

## Non-goals (v1)

- On-device models (LiteRT, Gemini Nano), Grok provider.
- AI in the food/product/recipe creation flows.
- Persisting photos; streaming responses; per-100g packaged-food analysis; cost tracking.
- iOS actual implementations (the repo has no iosMain code anywhere; Apple targets are ignored on
  this Windows host — Android-first follows the existing `expect`/android-`actual` pattern).

## Constraints

- Kotlin Multiplatform app, all app code in `app/src/commonMain`, package `com.maksimowiczm.foodyou`.
- Existing stack only, plus one new test dep: `ktor-client-mock` (commonTest). Ktor 3.4.3,
  kotlinx.serialization, Koin 4.2.2, DataStore Preferences, Compose Multiplatform 1.10.3,
  Material3. No image libraries: thumbnails are rendered from the image bytes the feature
  already prepares (decoded via a small `expect`/`actual` helper).
- New user-facing strings go only to
  `shared/resources/src/commonMain/composeResources/values/strings.xml` (English source; Crowdin
  translates the other locales).
- Formatting: ktfmt kotlinlang style (`just format`). There is no lint task.
- Verification: `gradlew.bat :app:testDebugUnitTest`, `gradlew.bat :app:assembleDebug`.
- No commits unless the product owner asks.

## Decisions (how it gets made)

### Data contract

AI response JSON (leniently parsed, unknown keys ignored):

```json
{
  "name": "Pierogi ruskie z masełkiem",
  "energy_kcal": 612,
  "proteins_g": 18.5,
  "carbohydrates_g": 71.0,
  "fats_g": 26.0,
  "saturated_fats_g": 8.0,
  "sugars_g": 4.0,
  "dietary_fiber_g": 5.0,
  "salt_g": 2.1
}
```

All nutrient fields nullable. Sanitization rules applied by the parser:

- Markdown fences / surrounding prose stripped (take substring from first `{` to last `}`).
- Negative values → null; blank/missing `name` → `InvalidResponse` exception.
- Missing `energy_kcal` → fallback `NutrientsHelper.calculateEnergy` (4/4/9); if any macro is null
  and energy is absent, energy stays null.
- Non-null → `NutrientValue.Complete`, null → `NutrientValue.Incomplete(null)`.

### Provider layer

- Domain: `NutritionCalculator` interface, `NutritionAiRequest(description?, images)`,
  `NutritionAiResult(name, nutritionFacts)`, `AiCalculationException` sealed
  (NotConfigured / Unauthorized / RateLimited / ServerError / Network / InvalidResponse).
- One implementation reads `AiPreferences`, dispatches to one of three internal protocol clients
  that return raw assistant text; the shared parser turns text into the result.
  - OpenAI-compatible `POST {base}/chat/completions` (OpenAI `https://api.openai.com/v1`,
    OpenRouter `https://openrouter.ai/api/v1`, custom = user base URL, cleartext `http://`
    allowed by decision below), Bearer auth, `response_format: json_object`.
  - Gemini `POST /v1beta/models/{model}:generateContent`, `x-goog-api-key` header,
    `generationConfig.responseMimeType: "application/json"`, inline_data images.
  - Anthropic `POST /v1/messages`, `x-api-key` + `anthropic-version: 2023-06-01`, prompt-enforced
    JSON, `max_tokens: 1024`, base64 image blocks.
- Default model constants (editable in settings): `gpt-4o-mini`, `gemini-2.5-flash`,
  `claude-sonnet-4-5`, `openai/gpt-4o-mini`; custom has no default (user must type one).
- Status mapping: 401/403 → Unauthorized, 429 → RateLimited, 5xx → ServerError, timeouts and IO
  failures → Network, everything else / unparseable body → InvalidResponse.
- Ktor `HttpClient` registered in Koin following the `OpenFoodFactsModule` pattern, `HttpTimeout`
  60 s, `Json { ignoreUnknownKeys = true }`.

### Prompt

System prompt: nutritionist role; respond only with a JSON object exactly matching the schema
above; units kcal and grams; values are absolute totals for the whole portion as described;
use `null` when genuinely unknown; short name in the user's app language tag (passed in).
User text: the description if present, otherwise a fixed "estimate from photo(s)" line; images
attached per protocol.

### Settings & storage

- `AiPreferences : UserPreferences` — selectedProvider + per-provider (apiKey, model) +
  custom baseUrl. Persisted via `AbstractDataStoreUserPreferencesRepository`, keys namespaced
  `ai:*`, registered with `userPreferencesRepositoryOf(::DataStoreAiPreferencesRepository)`
  (exact `FoodSearchPreferences` pattern). Keys stored plain — same precedent as the USDA key.
- `AiSettingsScreen` (Scaffold + LargeFlexibleTopAppBar + LazyColumn): provider radio rows,
  API key field (password visual), model field (placeholder = default), base URL field for
  custom provider with URL validation (must start `http://` or `https://`), privacy note card.
  Persistence through a small ViewModel calling `repo.update { }`.
- `SettingsScreen` gains an `AiSettingsListItem` + `onAiSettings` callback; nav host gains
  `@Serializable private object AiSettings` + `forwardBackwardComposable<AiSettings>`.
- Tapping Calculate with no configured provider → navigate to `AiSettings` (callback threaded
  through both quick-add screens).

### Quick add integration

- AI section state (description text, photos, status, applied result) is held in
  `CreateQuickAddViewModel` / `UpdateQuickAddViewModel` via a shared `QuickAddAiController`
  so it survives rotation and the round-trip to AI settings. Photos are prepared (downscaled
  JPEG + base64 + decoded thumbnail) immediately when picked and kept in controller state —
  a few hundred KB, no persistence, no re-reads.
- UI: `QuickAddAiSection` composited into `QuickAddScreen` above the form — description field,
  thumbnail row rendered from the prepared thumbnails (max 4 photos, remove button per
  thumbnail), add-photo menu (picker / take photo), Calculate button with loading state and
  error text.
- Review dialog `AiResultDialog`: editable name, core nutrients (display), extended summary
  (display), Apply / Cancel. Apply writes the 5 form fields
  (`textFieldState.setTextAndPlaceCursorAtEnd`, energy converted to user units) and tells the
  controller to keep the result as an overlay; `autoCalculateEnergy` is set to false only when
  |AI kcal − 4/4/9(AI macros)| > 1.
- Save path: pure function
  `mergeQuickAddNutritionFacts(base, aiOverlay?, energy, proteins, carbohydrates, fats)`:
  core 5 always taken from the form values; extended AI fields overwrite the base only when
  non-null (so an AI apply never wipes previously stored nutrients on update). Create passes
  `NutritionFacts()` as base (VM-held), update passes `entry.nutritionFacts`.
  `CreateQuickAddViewModel.addEntry` and `UpdateQuickAddViewModel.updateEntry` call the merge
  internally; screen-level signatures stay as they are.

### Images

- `ImagePreparation` interface (commonMain) + `AndroidImagePreparation(context)` (androidMain):
  read content URI → downsample so longest edge ≤ 1280 px → JPEG quality 80 → `PreparedImage`
  (base64 via a pure-Kotlin Base64 helper in commonMain). `MAX_AI_PHOTOS = 4`.
- Photo picker and camera capture are `@Composable expect` functions with androidMain actuals
  (`PickVisualMedia` multi-select, `TakePicture` with a `FileProvider` cache file,
  `RequestPermission` for `CAMERA`). New manifest entries: `CAMERA` permission, `FileProvider`,
  `android:networkSecurityConfig` pointing at a new `res/xml/network_security_config.xml`
  with `<base-config cleartextTrafficPermitted="true"/>` (app-wide cleartext accepted so local
  OpenAI-compatible endpoints work over `http://`).
- iOS: not compiled on this host; no iosMain actuals (matches existing repo pattern).

### Review-focus (accepted risks / QA hot spots)

1. AI apply on update must not erase stored nutrients not covered by the AI result.
2. Every provider failure must surface a localized, specific error — not a stack trace.
3. Hostile/malformed model output (prose, fences, negatives, missing fields) must parse or fail
   with `InvalidResponse`.
4. Energy unit round-trip: AI kcal → user units (kcal/kJ) in the field → converted back on save.
5. Configuration round-trip: leaving quick add for AI settings and returning must not lose
   description, photos, or applied results.

## What's left to the builder

Exact file layouts inside packages, prompt wording, dialog/layout styling details, string copy
within the established tone, and test naming. Wire formats (endpoints, headers, body shapes) and
data-contract rules above are fixed.
