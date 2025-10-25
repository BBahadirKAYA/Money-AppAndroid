// Kotlin DSL build script (app/)

// ── Imports (rename for clarity)
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime as JLocalDateTime
import java.time.ZoneId as JZoneId
import java.time.format.DateTimeFormatter as JDateTimeFormatter
// Gerekli importlar (Eğer hata verirse build.gradle.kts içindeki import'lardan kontrol edin)
import com.android.build.gradle.tasks.PackageApplication // Bu artık kullanılmayacak ama kalsın

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0" // Kotlin sürümüne göre aynı olmalı
}

android {
    namespace = "com.moneyapp.android"
    compileSdk = 36

    val hasSharedDebug = listOf(
        "SIGNING_STORE_FILE", "SIGNING_STORE_PASSWORD",
        "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD"
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
    }

    defaultConfig {
        applicationId = "com.moneyapp.android"
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val updateManifest = (project.findProperty("UPDATE_MANIFEST_URL") as String?)
            ?: "https://raw.githubusercontent.com/BBahadirKAYA/Money-AppAndroid/main/update-helper/update.json"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifest\"")

        val envVName = System.getenv("VNAME") ?: System.getenv("VERSION_NAME")
        val envVCode = System.getenv("VCODE") ?: System.getenv("VERSION_CODE")

        fun loadDotEnv(key: String): String? {
            val f = rootProject.file(".env")
            if (!f.exists()) return null
            val line = f.readLines()
                .firstOrNull {
                    val t = it.trim()
                    t.isNotEmpty() && !t.startsWith("#") && t.startsWith("$key=")
                } ?: return null
            return line.substringAfter("=").trim().trim('"', '\'')
        }
        val fileVName = loadDotEnv("VNAME") ?: loadDotEnv("VERSION_NAME")
        val fileVCode = loadDotEnv("VCODE") ?: loadDotEnv("VERSION_CODE")

        val propVName = project.findProperty("VERSION_NAME") as String?
        val propVCode = project.findProperty("VERSION_CODE") as String?

        val vName = (envVName ?: fileVName ?: propVName)
            ?: throw GradleException("VERSION_NAME/VNAME tanımlı değil (ENV/.env veya -P).")
        val vCode = (envVCode ?: fileVCode ?: propVCode)?.toIntOrNull()
            ?: throw GradleException("VERSION_CODE/VCODE tanımlı değil veya int değil (ENV/.env veya -P).")

        versionName = vName
        versionCode = vCode

        val backendUrl = (project.findProperty("BASE_URL") as String?) ?: "http://10.0.2.2:8000/"
        buildConfigField("String", "BASE_URL", "\"$backendUrl\"")
    }

    buildTypes {
        getByName("debug") {
            if (hasSharedDebug) {
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
        }
        getByName("release") {
            isMinifyEnabled = false // Gerekirse true yapıp ProGuard kurallarını ekleyin
            if (hasSharedDebug) {
                // Release build'ler genellikle ayrı bir release keystore kullanır.
                // Dikkatli olun, debug keystore ile release yapmak mağazaya yüklenemez.
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // Etkinleştirildi
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf("**/libc++_shared.so")
        }
        resources {
            excludes += listOf(
                "META-INF/LICENSE*", "META-INF/AL2.0", "META-INF/LGPL2.1"
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helper: Versiyon bilgisini yazdır
// ──────────────────────────────────────────────────────────────────────────────
tasks.register("printVersionInfo") {
    doLast {
        // AGP 8+ ile uyumlu defaultConfig erişimi
        val appExtension = project.extensions.findByType(com.android.build.api.dsl.ApplicationExtension::class.java)
        val vc = appExtension?.defaultConfig?.versionCode ?: 0
        val vn = appExtension?.defaultConfig?.versionName ?: ""
        println("VERSION_CODE=$vc")
        println("VERSION_NAME=$vn")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Google Sheets webhook’a (Apps Script) post eden task
// ──────────────────────────────────────────────────────────────────────────────
val postVersionToSheet by tasks.registering {
    group = "release"
    description = "POST version row to Google Sheets webhook"

    doLast {
        val appExtension = project.extensions.findByType(com.android.build.api.dsl.ApplicationExtension::class.java)
            ?: throw GradleException("Android application plugin not found.")

        val vCode = appExtension.defaultConfig.versionCode ?: 0
        val vName = appExtension.defaultConfig.versionName ?: "0.0.0"

        fun prop(name: String): String? =
            (project.findProperty(name) as String?)
                ?: (rootProject.findProperty(name) as String?)
                ?: System.getProperty(name)
                ?: System.getenv(name)

        val apkUrl = prop("APK_PUBLISH_URL")
            ?: throw GradleException("APK_PUBLISH_URL tanımlı değil (-P veya ENV).")
        val webhook = prop("SHEET_WEBHOOK_URL")
            ?: throw GradleException("SHEET_WEBHOOK_URL tanımlı değil (-P veya ENV).")

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
        val ok = res.statusCode() in 200..399
        if (!ok) error("Sheet'e yazılamadı → HTTP ${res.statusCode()} ${res.body()}")
        println("✓ Sheet güncellendi: $vName ($vCode) -> $apkUrl [HTTP ${res.statusCode()}]")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// APK’yı derleme sonrası yeniden adlandır (Basitleştirilmiş Yöntem)
// ──────────────────────────────────────────────────────────────────────────────
fun registerRenameApkTask(buildType: String) {
    val cap = buildType.replaceFirstChar { it.uppercase() }
    val assembleTaskName = "assemble$cap"
    val renameTaskName = "rename${cap}Apk"

    tasks.register<Task>(renameTaskName) {
        doLast {
            val appExtension = project.extensions.findByType(com.android.build.api.dsl.ApplicationExtension::class.java)
                ?: throw GradleException("Android application plugin not found.")
            val vn = appExtension.defaultConfig.versionName ?: "0.0.0"
            val vc = appExtension.defaultConfig.versionCode ?: 0
            val ts = JLocalDateTime.now(JZoneId.of("Europe/Istanbul"))
                .format(JDateTimeFormatter.ofPattern("yyMMddHH"))

            val outDir = layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
            if (!outDir.exists() || !outDir.isDirectory) {
                logger.warn("APK çıktı dizini bir klasör değil veya bulunamadı: $outDir")
                return@doLast
            }

            val srcApk = outDir.listFiles { file -> file.isFile && file.extension == "apk" }?.maxByOrNull { it.lastModified() }

            if (srcApk == null) {
                logger.warn("APK bulunamadı: $outDir altında .apk yok")
                return@doLast
            }

            val dest = outDir.resolve("moneyapp-$vn-$vc-$ts-$buildType.apk")
            Files.copy(srcApk.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("Saved → ${dest.absolutePath}")

            if (srcApk.name != dest.name) {
                srcApk.delete()
                println("Deleted original → ${srcApk.absolutePath}")
            }
        }
    }

    // assemble görevi bittikten sonra rename görevini çalıştır
    gradle.projectsEvaluated {
        try {
            tasks.named(assembleTaskName).configure {
                finalizedBy(renameTaskName)
            }
        } catch (e: org.gradle.api.UnknownTaskException) {
            logger.warn("Task $assembleTaskName not found for finalizing $renameTaskName")
        }
    }
}

// Fonksiyonu çağır
registerRenameApkTask("debug")
registerRenameApkTask("release")

// Publish görevlerini tanımla
val postTask = tasks.named("postVersionToSheet")

tasks.register("publishDebugApk") {
    dependsOn("renameDebugApk", postTask)
    // postTask'ın renameDebugApk'den sonra çalışmasını sağla
    postTask.configure { mustRunAfter("renameDebugApk") }
}

tasks.register("publishReleaseApk") {
    dependsOn("renameReleaseApk", postTask)
    // postTask'ın renameReleaseApk'den sonra çalışmasını sağla
    postTask.configure { mustRunAfter("renameReleaseApk") }
}

// Tek komutta uçtan uca (debug)
// Not: `findByName` yerine `tasks.register` kullanmak daha güvenli olabilir
// Eğer görev zaten varsa, `register` hata vermez.
tasks.register("buildDebugAndPost") {
    group = "custom" // Görevi gruplandır
    description = "Builds, renames debug APK, and posts version to sheet."
    dependsOn("publishDebugApk")
}


// ──────────────────────────────────────────────────────────────────────────────
// Dependencies
// ──────────────────────────────────────────────────────────────────────────────

val roomVersion = "2.6.1"
val okhttpVersion = "4.12.0"
val retrofitVersion = "2.11.0" // Tutarlı sürüm kullanıldı
val moshiVersion = "1.15.1"
val lifecycleVersion = "2.8.6" // Tutarlı sürüm kullanıldı

dependencies {
    implementation(project(":update-helper"))

    // Kotlin + Coroutines
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.20"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.emoji2:emoji2:1.4.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")

    // Material 3
    implementation("com.google.android.material:material:1.12.0")

    // Networking (OkHttp + Retrofit + Moshi)
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-moshi:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-scalars:$retrofitVersion") // Gerekliyse kalsın
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")

    // Room (KSP)
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Diğer Bağımlılıklar (Temizlenmiş)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // Core Library Desugaring (Sadece bir kez tanımlanmalı)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}