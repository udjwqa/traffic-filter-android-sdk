# App Analytics SDK v2

**Kotlin SDK для интеграции аналитики и проверки безопасности в Android APK**

---

## Быстрый старт

### 1. Добавь зависимости

В `build.gradle.kts` (app):

```kotlin
dependencies {
    implementation("com.google.android.play:integrity:1.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

### 2. Скопируй SDK-файлы

Скопируй папку `com/app/core/analytics/` в свой проект:

```
app/src/main/java/com/app/core/analytics/
├── AnalyticsConfig.kt        # Конфигурация
├── AppAnalytics.kt            # Главный класс (точка входа)
├── ConfigProvider.kt          # Запрос конфигурации
├── SecurityCheck.kt           # Проверка безопасности
├── ContentRenderer.kt         # Рендеринг контента в WebView
├── models/
│   └── ConfigResult.kt        # Модели данных
└── utils/
    ├── DeviceInfo.kt          # Информация об устройстве
    └── HttpClient.kt          # HTTP-обёртка
```

### 3. Добавь permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 4. Инициализируй SDK

В `Application.onCreate()`:

```kotlin
import com.app.core.analytics.AnalyticsConfig
import com.app.core.analytics.AppAnalytics

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        AppAnalytics.init(
            context = this,
            config = AnalyticsConfig(
                endpoint = "https://api.threeamigosteam.com/engine",
                appToken = "YOUR_TOKEN",
                fallbackUrl = "https://play.google.com/store",
                contentUrl = null,
                enableSecurityCheck = true,
                enableContentTracking = true,
                verbose = false,
            )
        )
    }
}
```

### 5. Используй в Activity

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)

        lifecycleScope.launch {
            val result = AppAnalytics.getInstance().initialize()

            if (result.status == Status.ACTIVE) {
                AppAnalytics.getInstance().renderContent(webView, result.contentUrl)
            } else {
                webView.loadUrl(result.contentUrl)
            }
        }
    }
}
```

---

## Конфигурация (AnalyticsConfig)

| Параметр | Тип | Описание |
|----------|-----|----------|
| `endpoint` | String | URL сервера (с `/engine`) |
| `appToken` | String | Токен приложения |
| `fallbackUrl` | String | URL по умолчанию |
| `contentUrl` | String? | URL контента. Если `null` — берётся с сервера |
| `connectTimeoutMs` | Long | Таймаут подключения (10 сек) |
| `readTimeoutMs` | Long | Таймаут чтения (15 сек) |
| `enableSecurityCheck` | Boolean | Проверка безопасности (true) |
| `enableContentTracking` | Boolean | Отслеживание контента (true) |
| `verbose` | Boolean | Логи в Logcat (false) |

---

## API Reference

### AppAnalytics

```kotlin
// Инициализация (один раз в Application)
AppAnalytics.init(context, config)

// Получить инстанс
val analytics = AppAnalytics.getInstance()
```

#### `initialize(): ConfigResult`

Полная проверка. Suspend-функция.

```kotlin
val result = analytics.initialize()

when (result.status) {
    Status.ACTIVE -> {
        // Контент доступен
        analytics.renderContent(webView, result.contentUrl)
    }
    Status.INACTIVE -> {
        // Fallback
        webView.loadUrl(result.contentUrl)
    }
}
```

#### `renderContent(webView, url, callback?)`

Загрузить контент в WebView с аналитикой.

```kotlin
analytics.renderContent(webView, url, object : ContentRenderer.Callback {
    override fun onReady(status: Status, score: Int) {
        Log.d("App", "Content ready: status=$status")
    }
    override fun onError(error: String) {
        Log.e("App", "Error: $error")
    }
})
```

#### `preload(): ConfigResult`

Быстрая проверка без security check.

### ConfigResult

| Поле | Тип | Описание |
|------|-----|----------|
| `status` | `Status` | `ACTIVE` = контент доступен, `INACTIVE` = fallback |
| `contentUrl` | `String` | URL контента |
| `score` | `Int` | Оценка (0 = чисто) |
| `reason` | `String?` | Причина (или null) |
| `configReady` | `Boolean` | Конфигурация загружена |
| `securityPassed` | `Boolean` | Проверка безопасности пройдена |

---

## Что отправляет SDK

### Запрос конфигурации (GET /analytics/config)

```
X-App-Token:       токен из конфига
X-App-Id:          package name (автоматически из AndroidManifest)
User-Agent:        Mozilla/5.0 (Linux; Android ...)
Accept-Language:   ru-RU,ru;q=0.9,en;q=0.8
X-Device-Info:     Samsung Galaxy S23
X-Device-Codename: dm1q
X-Build-Product:   dm1q
X-OS-Version:      Android 14
X-Graphics-Info:   Adreno (TM) 740
```

### Проверка безопасности (3 запроса)

```
1. GET  /api/security/token      → получить одноразовый токен
2. Google Play Integrity API     → получить security token
3. POST /api/security/validate   → валидация на сервере
   Headers: X-App-Id
   Body: { "integrityToken": "...", "nonce": "..." }
```

### Аналитика контента (автоматически)

```
POST /api/analytics/event
Body: { screen, battery, accelerometer, input, webgl, ... }
```

---

## ProGuard / R8

SDK поставляется с минимальными ProGuard-правилами. **НЕ добавляйте `-keep` для SDK-классов** — R8 должен их обфусцировать.

```proguard
# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Play Core
-keep class com.google.android.play.core.integrity.** { *; }

# WebView JS interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
```

**ВАЖНО:** Не добавляйте `-keep class com.app.core.analytics.*` — это отключит обфускацию SDK.

---

## WebView настройки

При использовании SDK в WebView, убедитесь:

```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowFileAccess = false                              // НЕ true
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER   // НЕ ALWAYS_ALLOW
    // НЕ делайте: userAgentString.replace("wv", "")
}
```

---

## Примеры

### Минимальная интеграция

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        val app = AppAnalytics.init(this, AnalyticsConfig(
            endpoint = "https://api.threeamigosteam.com/engine",
            appToken = "YOUR_TOKEN",
            fallbackUrl = "https://play.google.com/store",
        ))

        lifecycleScope.launch {
            val result = app.initialize()
            if (result.status == Status.ACTIVE) {
                app.renderContent(webView, result.contentUrl)
            } else {
                webView.loadUrl(result.contentUrl)
            }
        }
    }
}
```

### Со Splash-экраном

```kotlin
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val result = AppAnalytics.getInstance().initialize()
            val intent = Intent(this@SplashActivity, WebViewActivity::class.java).apply {
                putExtra("url", result.contentUrl)
                putExtra("is_active", result.status == Status.ACTIVE)
            }
            startActivity(intent)
            finish()
        }
    }
}
```

---

## Миграция с v1

| v1 | v2 |
|----|-----|
| `import com.filter.sdk.*` | `import com.app.core.analytics.*` |
| `FilterConfig(serverUrl=..., clientSecret=..., safeUrl=...)` | `AnalyticsConfig(endpoint=..., appToken=..., fallbackUrl=...)` |
| `TrafficFilter.init(...)` | `AppAnalytics.init(...)` |
| `checkTraffic()` | `initialize()` |
| `loadOffer(webView, url)` | `renderContent(webView, url)` |
| `quickCheck()` | `preload()` |
| `result.verdict == Verdict.GREY` | `result.status == Status.ACTIVE` |
| `result.targetUrl` | `result.contentUrl` |

---

## Требования

- Android SDK 24+ (Android 7.0)
- Kotlin 2.0+
- Google Play Services

---

*v2.0.0 | App Analytics SDK*
