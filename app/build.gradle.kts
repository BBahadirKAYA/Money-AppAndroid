import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime as JLocalDateTime
import java.time.ZoneId as JZoneId
import java.time.format.DateTimeFormatter as JDateTimeFormatter




// NOT: ApkVariantOutput import'u gerekmiyor (post-build rename kullanıyoruz)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.moneyapp.android"
    compileSdk = 36

    // --- DEBUG keystore'u opsiyonel yap ---
    val hasSharedDebug = listOf(
        "SIGNING_STORE_FILE", "SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD"
    ).all { !System.getenv(it).isNullOrBlank() }

    signingConfigs {
        if (hasSharedDebug) {
            create("sharedDebug") {
                storeFile = file(System.getenv("SIGNING_STORE_FILE"))
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
        // release için ayrı bir config istersen burada create("release") ile tanımlayabilirsin
    }

    defaultConfig {
        applicationId = "com.moneyapp.android"
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- VERSION_NAME / VERSION_CODE: -P ile gelirse onları kullan ---
        val vnProp = (project.findProperty("VERSION_NAME") as String?)
        val vcProp = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull()

        // Varsayılan (elle geliştirme sırasında) değer
        val vn = vnProp ?: "1.3.10"
        versionName = vn

        // Otomatik versionCode (timestamp) — ancak -PVERSION_CODE verilmişse onu kullan
        val base = vn.replace(".", "").toIntOrNull() ?: 0
        val stamp = JLocalDateTime.now(JZoneId.of("Europe/Istanbul"))
            .format(JDateTimeFormatter.ofPattern("yyMMddHH"))
            .toInt()
        versionCode = vcProp ?: (base * 100_000 + stamp)

        // ---- BuildConfig.BASE_URL ----
        val backendUrl: String =
            (project.findProperty("BASE_URL") as String?) ?: "http://10.0.2.2:8000/"
        buildConfigField("String", "BASE_URL", "\"$backendUrl\"")
    }


    // --- buildTypes: tek blok, şartlı imzalama ---
    buildTypes {
        getByName("debug") {
            if (hasSharedDebug) {
                signingConfig = signingConfigs.getByName("sharedDebug")
            } // else: Android'in default debug.keystore'u kullanılacak
        }
        getByName("release") {
            isMinifyEnabled = false
            // Geçici olarak aynı imzayı istiyorsan ve env doluysa:
            if (hasSharedDebug) {
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
            // Not: Mağaza dağıtımı hedefliyorsan release için ayrı bir keystore tanımla.
        }
    }

    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}


// ---- Version bilgisini yazdıran yardımcı task ----
tasks.register("printVersionInfo") {
    doLast {
        val vc = android.defaultConfig.versionCode ?: 0
        val vn = android.defaultConfig.versionName ?: ""
        println("VERSION_CODE=$vc")
        println("VERSION_NAME=$vn")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// MoneyApp → Sheets: derleme sonrası versiyon bilgisini yaz
// ──────────────────────────────────────────────────────────────────────────────
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

        // 2) Property okuma — projeden, root'tan, sysprop'tan, env'den
        fun prop(name: String): String? =
            (project.findProperty(name) as String?) ?:
            (rootProject.findProperty(name) as String?) ?:
            System.getProperty(name) ?:
            System.getenv(name)

        val apkUrl = prop("APK_PUBLISH_URL")
            ?: throw GradleException("APK_PUBLISH_URL tanımlı değil (gradle.properties, ~/.gradle/gradle.properties, -P veya env).")

        val webhook = prop("SHEET_WEBHOOK_URL")
            ?: throw GradleException("SHEET_WEBHOOK_URL tanımlı değil.")

        // 3) 3xx'leri başarı say + JSON
        val json = """
          {"version_code":$vCode,"versionCode":$vCode,
           "version_name":"$vName","versionName":"$vName",
           "apkUrl":"$apkUrl","asset_url":"$apkUrl"}
        """.trimIndent()

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        val req = HttpRequest.newBuilder(URI.create(webhook))
            .header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val res = client.send(req, HttpResponse.BodyHandlers.ofString())
        val ok = res.statusCode() in 200..299 || res.statusCode() in 300..399
        if (!ok) throw GradleException("Sheet'e yazılamadı → HTTP ${res.statusCode()} ${res.body()}")
        println("✓ Sheet güncellendi: $vName ($vCode) -> $apkUrl [HTTP ${res.statusCode()}]")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// APK dosyasını derleme SONRASI versiyon + tarih ile yeniden adlandır (copy)
// ve publish görevini oluştur
// ──────────────────────────────────────────────────────────────────────────────
fun registerRenameApkTask(buildType: String) {
    val cap = buildType.replaceFirstChar { it.uppercase() }
    val renameTaskName = "rename${cap}Apk"
    val publishTaskName = "publish${cap}Apk"

    // rename<Cap>Apk
    tasks.register(renameTaskName) {
        dependsOn("assemble$cap")
        doLast {
            val vn = android.defaultConfig.versionName ?: "0.0.0"
            val vc = android.defaultConfig.versionCode ?: 0
            val ts = JLocalDateTime.now(JZoneId.of("Europe/Istanbul"))
                .format(JDateTimeFormatter.ofPattern("yyMMddHH"))


            val outDir = layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
            require(outDir.exists()) { "APK dizini bulunamadı: $outDir" }

            val src = outDir.walkTopDown()
                .filter { it.isFile && it.extension == "apk" }
                .maxByOrNull { it.lastModified() }
                ?: error("APK bulunamadı: $outDir altında .apk yok")

            val dest = outDir.resolve("moneyapp-$vn-$vc-$ts-$buildType.apk")
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("Saved → ${dest.absolutePath}")
        }
    }

    // assemble<Cap> tamamlanınca finalize et (opsiyonel ama hoş)
    gradle.projectsEvaluated {
        tasks.matching { it.name == "assemble$cap" }.configureEach {
            finalizedBy(renameTaskName)
        }
    }

    // publish<Cap>Apk : rename → post
    tasks.register(publishTaskName) {
        dependsOn(renameTaskName, postVersionToSheet)
    }

    // postVersionToSheet mutlaka rename'den sonra koşsun
    postVersionToSheet.configure {
        mustRunAfter(renameTaskName)
    }
}

// Debug ve Release için kur
registerRenameApkTask("debug")
registerRenameApkTask("release")

// Tek komutta uçtan uca (debug) çalıştırmak için yardımcı task (tekil tanım)
if (tasks.findByName("buildDebugAndPost") == null) {
    tasks.register("buildDebugAndPost") {
        // zincir: assembleDebug → renameDebugApk → postVersionToSheet
        dependsOn("publishDebugApk")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// sürümler
// ──────────────────────────────────────────────────────────────────────────────
val roomVersion = "2.6.1"
val okhttpVersion = "4.12.0"
val retrofitVersion = "2.11.0"
val moshiVersion = "1.15.1"
val lifecycleVersion = "2.8.6"

dependencies {
    implementation(project(":update-helper"))
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6") // lifecycleScope için

    // Kotlin
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.20"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")

    // Material 3
    implementation("com.google.android.material:material:1.12.0")

    // Retrofit + dönüştürücüler
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-scalars:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-moshi:$retrofitVersion")

    // OkHttp (+ logging)
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")

    // Moshi
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")

    // Room + KSP
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
