# Traffic Filter SDK for Android

**Kotlin SDK для интеграции системы фильтрации трафика в Android APK**

Этот SDK — клиентская часть системы клоаки. Встраиваешь в своё APK, и оно само определяет: перед нами реальный юзер или модератор/бот. Реальный юзер видит оффер, модер — заглушку.

---

## Что делает SDK

SDK выполняет 3 этапа проверки:

```
APK запускается
     |
     v
[1. Gateway Check]
     |  Отправляет заголовки устройства на сервер
     |  Сервер проверяет: UA, модель, GPU, ASN, IP, страну, ISP
     |
  ПРОВАЛ? --> Показать safeUrl (заглушка)
     |
     v
[2. Play Integrity]
     |  Запрашивает nonce у сервера
     |  Вызывает Google Play Integrity API
     |  Отправляет integrity token на сервер
     |  Сервер проверяет: устройство, подпись APK, лицензию
     |
  ПРОВАЛ? --> Показать safeUrl (заглушка)
     |
     v
[3. WebView + JS Tracker]
     |  Открывает оффер в WebView
     |  Трекер собирает: батарею, акселерометр, GPU, тач, скорость действий
     |  Отправляет на сервер для финального скоринга
     |
  ПРОВАЛ? --> (сервер может заблокировать на лету)
     |
     v
  Юзер видит оффер
```

---

## Быстрый старт

### 1. Добавь зависимости

В `build.gradle.kts` (app):

```kotlin
dependencies {
    // Play Integrity API
    implementation("com.google.android.play:integrity:1.4.0")

    // HTTP-клиент
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
```

### 2. Скопируй SDK-файлы

Скопируй папку `com/filter/sdk/` в свой проект:

```
app/src/main/java/com/filter/sdk/
├── FilterConfig.kt          # Конфигурация
├── TrafficFilter.kt          # Главный класс (точка входа)
├── GatewayClient.kt          # Проверка через gateway
├── IntegrityClient.kt        # Play Integrity проверка
├── WebViewTracker.kt          # WebView с JS-трекером
├── models/
│   └── FilterResult.kt       # Модели данных
└── utils/
    ├── DeviceInfo.kt          # Сбор инфы об устройстве
    └── HttpClient.kt          # HTTP-обёртка
```

### 3. Добавь permissions в AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 4. Инициализируй SDK

В `Application.onCreate()`:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        TrafficFilter.init(
            context = this,
            config = FilterConfig(
                serverUrl = "https://api.threeamigosteam.com/engine",
                clientSecret = "5ed315c474084ea436f0e57d1e54ef6f2f647c2f7757a02bdfb3f3339b0d2c0f",
                safeUrl = "https://play.google.com/store/apps/details?id=com.example.safe",
                targetUrl = null,  // null = брать из ответа сервера
                enablePlayIntegrity = true,
                enableJsTracker = true,
                debug = true,  // включает логи в Logcat (убери в продакшне)
            )
        )
    }
}
```

### 5. Проверяй трафик в главной Activity

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        // Запускаем проверку
        lifecycleScope.launch {
            filterTraffic()
        }
    }

    private suspend fun filterTraffic() {
        val filter = TrafficFilter.getInstance()

        // Полная проверка: Gateway → Play Integrity
        val result = filter.checkTraffic()

        if (result.verdict == Verdict.GREY) {
            // Реальный юзер — показываем оффер с трекером
            filter.loadOffer(webView, result.targetUrl, object : WebViewTracker.TrackerCallback {
                override fun onMetricsCollected(verdict: Verdict, score: Int) {
                    Log.d("Main", "Tracker: verdict=$verdict score=$score")
                }
                override fun onError(error: String) {
                    Log.e("Main", "Tracker error: $error")
                }
            })
        } else {
            // Бот/модер — показываем заглушку
            webView.loadUrl(result.targetUrl)  // safeUrl
        }
    }
}
```

---

## Конфигурация (FilterConfig)

| Параметр | Тип | Обязательный | Описание |
|----------|-----|-------------|----------|
| `serverUrl` | String | Да | URL сервера скоринга (без `/` в конце) |
| `clientSecret` | String | Да | Секретный ключ для X-Client-Secret заголовка |
| `safeUrl` | String | Да | URL заглушки (куда отправлять ботов) |
| `targetUrl` | String? | Нет | URL оффера. Если `null` — берётся из ответа сервера |
| `connectTimeoutMs` | Long | Нет | Таймаут подключения (дефолт: 10 сек) |
| `readTimeoutMs` | Long | Нет | Таймаут чтения (дефолт: 15 сек) |
| `enablePlayIntegrity` | Boolean | Нет | Включить проверку Play Integrity (дефолт: true) |
| `enableJsTracker` | Boolean | Нет | Включить JS-трекер в WebView (дефолт: true) |
| `debug` | Boolean | Нет | Логи в Logcat (дефолт: false, ОТКЛЮЧИ В ПРОДЕ) |

---

## API Reference

### TrafficFilter

Главный класс SDK. Singleton, инициализируется через `init()`.

```kotlin
// Инициализация (один раз в Application)
TrafficFilter.init(context, config)

// Получить инстанс
val filter = TrafficFilter.getInstance()
```

#### `checkTraffic(): FilterResult`

Полная проверка трафика. Выполняет Gateway → Play Integrity последовательно. Suspend-функция — вызывай из корутины.

```kotlin
lifecycleScope.launch {
    val result = filter.checkTraffic()

    when (result.verdict) {
        Verdict.GREY -> {
            // Юзер чистый, показывай оффер
            Log.d("Filter", "PASS: url=${result.targetUrl}")
        }
        Verdict.WHITE -> {
            // Бот/модер, показывай заглушку
            Log.d("Filter", "BLOCK: reason=${result.rejectionCode}")
        }
    }
}
```

#### `quickCheck(): FilterResult`

Быстрая проверка только через gateway redirect (без debug-скоринга и без Play Integrity). Используй если нужна скорость и не нужны подробности.

```kotlin
val result = filter.quickCheck()
// result.verdict — GREY или WHITE
// result.targetUrl — куда редиректить
```

#### `loadOffer(webView, url, callback?)`

Загрузить оффер-страницу в WebView с JS-трекером. Трекер автоматически собирает метрики устройства и отправляет на сервер.

```kotlin
filter.loadOffer(webView, "https://offer-page.com", object : WebViewTracker.TrackerCallback {
    override fun onMetricsCollected(verdict: Verdict, score: Int) {
        // Метрики собраны и отправлены на сервер
        // verdict — результат JS-скоринга
    }
    override fun onError(error: String) {
        // Ошибка трекера (не критично, оффер уже показан)
    }
})
```

### FilterResult

Результат проверки трафика.

| Поле | Тип | Описание |
|------|-----|----------|
| `verdict` | `Verdict` | `GREY` = пропустить, `WHITE` = заблокировать |
| `targetUrl` | `String` | URL куда направить юзера |
| `score` | `Int` | Скоринг-балл (0 = чистый, 100+ = подозрительный) |
| `rejectionCode` | `String?` | Код причины блокировки (или null если пропущен) |
| `gatewayPassed` | `Boolean` | Прошёл ли gateway-проверку |
| `integrityPassed` | `Boolean` | Прошёл ли Play Integrity |
| `details` | `List<ScoringDetail>` | Подробности скоринга (какие проверки сработали) |

### Verdict

```kotlin
enum class Verdict {
    GREY,   // Реальный юзер → показать оффер
    WHITE   // Бот/модер → показать заглушку
}
```

---

## Примеры интеграции

### Пример 1: Минимальная интеграция

Самый простой вариант — один экран, одна проверка:

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        val filter = TrafficFilter.init(this, FilterConfig(
            serverUrl = "https://api.threeamigosteam.com/engine",
            clientSecret = "YOUR_SECRET",
            safeUrl = "https://play.google.com/store",
        ))

        lifecycleScope.launch {
            val result = filter.checkTraffic()
            if (result.verdict == Verdict.GREY) {
                filter.loadOffer(webView, result.targetUrl)
            } else {
                webView.loadUrl(result.targetUrl)
            }
        }
    }
}
```

### Пример 2: Со splash-скрином

Показываем loading пока идёт проверка:

```kotlin
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)  // layout с ProgressBar

        lifecycleScope.launch {
            val filter = TrafficFilter.getInstance()
            val result = filter.checkTraffic()

            val intent = Intent(this@SplashActivity, WebViewActivity::class.java).apply {
                putExtra("url", result.targetUrl)
                putExtra("is_offer", result.verdict == Verdict.GREY)
            }
            startActivity(intent)
            finish()
        }
    }
}

class WebViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val webView = findViewById<WebView>(R.id.webView)
        val url = intent.getStringExtra("url") ?: return
        val isOffer = intent.getBooleanExtra("is_offer", false)

        if (isOffer) {
            TrafficFilter.getInstance().loadOffer(webView, url)
        } else {
            webView.loadUrl(url)
        }
    }
}
```

### Пример 3: Быстрая проверка без Play Integrity

Если не нужна проверка Play Integrity (например для тестов):

```kotlin
TrafficFilter.init(this, FilterConfig(
    serverUrl = "https://api.threeamigosteam.com/engine",
    clientSecret = "YOUR_SECRET",
    safeUrl = "https://play.google.com/store",
    enablePlayIntegrity = false,  // Отключить PI
    debug = true,
))

lifecycleScope.launch {
    val result = TrafficFilter.getInstance().quickCheck()
    // result.verdict — GREY или WHITE
}
```

### Пример 4: С кастомной обработкой ошибок

```kotlin
lifecycleScope.launch {
    try {
        val result = TrafficFilter.getInstance().checkTraffic()

        when {
            result.verdict == Verdict.GREY -> {
                // Оффер
                loadOffer(result.targetUrl)
            }
            result.rejectionCode == "device_compromised" -> {
                // Устройство не прошло Play Integrity
                showMessage("Устройство не поддерживается")
                loadSafe(result.targetUrl)
            }
            result.rejectionCode == "cert_mismatch" -> {
                // APK переподписан
                showMessage("Некорректная версия приложения")
                loadSafe(result.targetUrl)
            }
            else -> {
                // Любая другая причина блокировки
                loadSafe(result.targetUrl)
            }
        }
    } catch (e: Exception) {
        // Fail-safe: при ошибке SDK показываем заглушку
        loadSafe(TrafficFilter.getInstance().config.safeUrl)
    }
}
```

---

## Что отправляет SDK на сервер

### Gateway Check (GET /score-debug)

Заголовки которые SDK отправляет автоматически:

```
X-Client-Secret:    секретный ключ из конфига
User-Agent:         Mozilla/5.0 (Linux; Android 14; Samsung Galaxy S23 ...)
Accept-Language:    ru-RU,ru;q=0.9,en;q=0.8
X-Device-Model:     Samsung Galaxy S23    (Build.MODEL)
X-Device-Codename:  dm1q                  (Build.DEVICE)
X-Build-Product:    dm1q                  (Build.PRODUCT)
X-OS-Version:       Android 14            (Build.VERSION.RELEASE)
X-GPU-Renderer:     Adreno (TM) 740       (OpenGL ES)
```

Сервер анализирует эти заголовки и возвращает скоринг.

### Play Integrity (3 запроса)

```
1. GET  /api/integrity/nonce          → получить одноразовый токен
2. Google Play Integrity API          → получить integrity token (на стороне Google)
3. POST /api/integrity/verify         → отправить токен на верификацию
   Body: { "integrityToken": "...", "nonce": "..." }
```

### JS Tracker (автоматически из WebView)

Трекер встраивается в WebView и через 3 секунды отправляет:

```
POST /api/collect
Body: {
    "screen": { "width": 1080, "height": 2400, ... },
    "battery": { "level": 0.72, "charging": false, ... },
    "accelerometer": { "averageDeviation": 0.35, "samples": 50 },
    "input": { "touchEvents": 5, "maxActionsPerSec": 3, ... },
    "webgl": { "renderer": "Adreno (TM) 740", ... },
    "timezone": "Europe/Moscow",
    "language": "ru-RU",
    ...
}
```

---

## Коды блокировки (rejectionCode)

Если `verdict = WHITE`, поле `rejectionCode` содержит причину:

| Код | Описание | Где ловится |
|-----|----------|-------------|
| `no_client_secret` | Нет секретного заголовка | Gateway |
| `bot_user_agent` | User-Agent бота | Gateway |
| `device_blocked` | Модель устройства (Pixel) | Gateway |
| `emulator_detected` | Кодовое имя эмулятора | Gateway |
| `emulator_gpu` | GPU эмулятора | Gateway |
| `test_build_detected` | Тестовая сборка | Gateway |
| `country_blocked` | Страна заблокирована | Gateway |
| `asn_blocked` | ASN датацентра | Gateway |
| `ip_range_blocked` | IP из диапазона ботов | Gateway |
| `vpn_detected` | VPN обнаружен | Gateway |
| `suspicious_hosting` | IP хостинг-провайдера | Gateway |
| `integrity_invalid` | Nonce невалиден или replay | Play Integrity |
| `device_compromised` | Устройство не прошло проверку | Play Integrity |
| `app_tampered` | APK модифицирован | Play Integrity |
| `cert_mismatch` | APK переподписан | Play Integrity |
| `behavioral_score` | Совокупность JS-проверок | JS Tracker |

---

## Настройка Play Integrity

Чтобы Play Integrity работал, нужно:

### 1. Google Cloud Console

1. Зайди на https://console.cloud.google.com
2. Создай проект (или используй существующий)
3. Включи **Play Integrity API** в Library
4. Создай **Service Account** с ролью "Play Integrity User"
5. Скачай JSON-ключ → положи на сервер в `config/gcp-key.json`

### 2. Google Play Console

1. Зайди в приложение → **Integrity** (Целостность)
2. Скопируй **SHA-256 fingerprint** сертификата подписи
3. Пропиши на сервере: `.env` → `CERT_SHA256=...`

### 3. Package name

На сервере в `.env`:
```
PACKAGE_NAME=com.mazourbn.jaberbagh
```

Должен совпадать с `applicationId` в `build.gradle.kts` твоего APK.

### 4. В APK ничего дополнительно настраивать не надо

SDK сам вызывает Play Integrity API. Главное — чтобы в `build.gradle.kts` была зависимость:
```kotlin
implementation("com.google.android.play:integrity:1.4.0")
```

---

## Debug-режим

Включи `debug = true` в конфиге и смотри Logcat:

```
D/TrafficFilter: === Starting traffic filter ===
D/TrafficFilter: [1/2] Gateway check...
D/GatewayClient: Gateway request: score=0 verdict=grey
D/TrafficFilter: [1/2] Gateway: verdict=GREY score=0
D/TrafficFilter: [2/2] Play Integrity check...
D/IntegrityClient: Requesting nonce...
D/IntegrityClient: Got nonce: 7d7d3f62bd0e12d4... (TTL=300s)
D/IntegrityClient: Requesting integrity token from Google...
D/IntegrityClient: Got integrity token (1847 chars)
D/IntegrityClient: Sending token to server for verification...
D/IntegrityClient: Integrity result: verdict=GREY score=0
D/TrafficFilter: [2/2] Integrity: verdict=GREY score=0
D/TrafficFilter: PASSED! Redirecting to: https://offer-page.com
```

Фильтруй в Logcat по тегам: `TrafficFilter`, `GatewayClient`, `IntegrityClient`, `WebViewTracker`

**ВАЖНО:** Отключи `debug = false` перед публикацией в Google Play! Иначе в логах будут видны URL-ы серверов и секреты.

---

## ProGuard / R8

SDK уже содержит `proguard-rules.pro` с правилами для:
- OkHttp
- Play Integrity
- SDK моделей
- WebView JS Interface

Если используешь свой ProGuard, добавь эти правила:

```proguard
# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Play Integrity
-keep class com.google.android.play.core.integrity.** { *; }

# SDK
-keep class com.filter.sdk.models.** { *; }
-keep class com.filter.sdk.WebViewTracker$TrackerBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
```

---

## Структура файлов

```
android-sdk/
├── README.md                          # Этот файл
├── .gitignore
├── build.gradle.kts                   # Root gradle
├── settings.gradle.kts
└── app/
    ├── build.gradle.kts               # Зависимости
    ├── proguard-rules.pro             # ProGuard правила
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/filter/sdk/
            ├── FilterConfig.kt        # Конфигурация SDK
            ├── TrafficFilter.kt       # Главный класс (singleton)
            ├── GatewayClient.kt       # Gateway-проверка (заголовки + IP)
            ├── IntegrityClient.kt     # Play Integrity (nonce → token → verify)
            ├── WebViewTracker.kt      # WebView с JS-трекером
            ├── models/
            │   └── FilterResult.kt    # Модели: FilterResult, Verdict, etc.
            └── utils/
                ├── DeviceInfo.kt      # Сбор инфы об устройстве
                └── HttpClient.kt      # HTTP-обёртка (OkHttp)
```

---

## FAQ

### APK падает на старте с "TrafficFilter not initialized"
Ты забыл вызвать `TrafficFilter.init()` в `Application.onCreate()`. Убедись что в `AndroidManifest.xml` указан твой Application-класс:
```xml
<application android:name=".MyApp" ...>
```

### Play Integrity возвращает ошибку
- Проверь что APK подписан правильным ключом
- Проверь что package name в APK совпадает с `PACKAGE_NAME` на сервере
- Проверь что GCP ключ валиден и Play Integrity API включён
- На эмуляторе Play Integrity **не работает** — это нормально, он и должен его палить

### Gateway всегда возвращает WHITE
- Проверь `clientSecret` — должен совпадать с `CF_CLIENT_SECRET` на сервере
- Проверь `serverUrl` — должен быть правильный URL с `/engine` если через Nginx

### Трекер не отправляет метрики
- Проверь что WebView имеет доступ к интернету
- Проверь что `serverUrl` доступен из WebView (CORS настроен)
- Посмотри console.log в WebView (включи WebView debugging)

### Как тестировать на эмуляторе?
На эмуляторе скоринг всегда будет WHITE (codename=goldfish, GPU=SwiftShader). Это нормально — система работает правильно. Для тестирования SDK отключи Play Integrity и используй `quickCheck()`:

```kotlin
TrafficFilter.init(this, FilterConfig(
    serverUrl = "https://api.threeamigosteam.com/engine",
    clientSecret = "YOUR_SECRET",
    safeUrl = "https://play.google.com/store",
    enablePlayIntegrity = false,
    debug = true,
))
```

---

## Минимальные требования

- Android SDK 24+ (Android 7.0)
- Kotlin 2.0+
- Google Play Services (для Play Integrity)

---

*v1.0.0 | Traffic Filter SDK*
