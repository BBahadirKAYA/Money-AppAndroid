import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")

}

android {
    namespace = "com.moneyapp.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moneyapp.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 10307
        versionName = "1.3.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // gradle.properties → BASE_URL (yoksa 10.0.2.2)
        val backendUrl = (project.findProperty("BASE_URL") as String? ?: "http://10.0.2.2:8000")
        buildConfigField("String", "BASE_URL", "\"$backendUrl\"")
    }

    buildTypes {
        getByName("debug") {
            // debug'a özel ayarlar (gerekirse)
            // buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            // proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Versiyon sabitlemeleri (okunurluk için)
val roomVersion = "2.6.1"
val okhttpVersion = "4.12.0"
val retrofitVersion = "2.11.0"
val moshiVersion = "1.15.1"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.20"))

    // Retrofit + dönüştürücüler
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-scalars:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-moshi:$retrofitVersion")

    // Moshi (kullanacaksan)
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    implementation("com.squareup.moshi:moshi-kotlin:${moshiVersion}")
    // ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    // ⚠️ release derlemede de erişilsin diye implementation kullandık:
    debugImplementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Room + KSP (stabil sürüm)
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}




// ──────────────────────────────────────────────────────────────────────────────
// MoneyApp → Sheets: derleme sonrası versiyon bilgisini yaz
// ──────────────────────────────────────────────────────────────────────────────
// ——— Sheet’e POST eden görev (tam, sağlam) ———

val postVersionToSheet by tasks.registering {
    group = "release"
    description = "POST version row to Google Sheets webhook"

    doLast {
        // 1) Android defaultConfig bilgileri
        val appExt8 = project.extensions.findByType(com.android.build.api.dsl.ApplicationExtension::class.java)
        val appExt7 = project.extensions.findByType(com.android.build.gradle.internal.dsl.BaseAppModuleExtension::class.java)
        val (vCode, vName) = when {
            appExt8 != null -> (appExt8.defaultConfig.versionCode ?: 0) to (appExt8.defaultConfig.versionName ?: "0.0.0")
            appExt7 != null -> (appExt7.defaultConfig.versionCode ?: 0) to (appExt7.defaultConfig.versionName ?: "0.0.0")
            else -> throw GradleException("Android plugin not applied: 'android' extension yok.")
        }

        // 2) Property okuma — tüm yolları dene (projeden, root’tan, JVM sysprop’tan, env’den)
        fun prop(name: String): String? =
            (project.findProperty(name) as String?) ?:
            (rootProject.findProperty(name) as String?) ?:
            System.getProperty(name) ?:
            System.getenv(name)

        val apkUrl = prop("APK_PUBLISH_URL")
            ?: throw GradleException("APK_PUBLISH_URL tanımlı değil (gradle.properties, ~/.gradle/gradle.properties, -P veya env).")

        val webhook = prop("SHEET_WEBHOOK_URL")
            ?: throw GradleException("SHEET_WEBHOOK_URL tanımlı değil.")

        // 3) 302’leri başarı say + JSON
        val json = """
          {"version_code":$vCode,"versionCode":$vCode,
           "version_name":"$vName","versionName":"$vName",
           "apkUrl":"$apkUrl","asset_url":"$apkUrl"}
        """.trimIndent()

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)   // 3xx alırız → sorun değil
            .build()

        val req = HttpRequest.newBuilder(URI.create(webhook))
            .header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        val ok = res.statusCode() in 200..299 || res.statusCode() in 300..399
        if (!ok) {
            throw GradleException("Sheet'e yazılamadı → HTTP ${res.statusCode()} ${res.body()}")
        } else {
            println("✓ Sheet güncellendi: $vName ($vCode) -> $apkUrl [HTTP ${res.statusCode()}]")
        }
    }
}

// görevleri oluşturduktan sonra finalize et
gradle.projectsEvaluated {
    tasks.matching { it.name in setOf("assembleDebug","packageDebug","bundleDebug") }.configureEach {
        finalizedBy(postVersionToSheet)
    }
}
// mevcut debug görevini bulup onun üstünden çalıştıran yardımcı görev
tasks.register("buildDebugAndPost") {
    // hangisi varsa onu hedefle
    val target = listOf("assembleDebug","packageDebug","bundleDebug")
        .firstOrNull { tasks.findByName(it) != null }
        ?: throw GradleException("Debug için assemble/package/bundle görevi bulunamadı.")
    dependsOn(target)
    finalizedBy(postVersionToSheet)
}
