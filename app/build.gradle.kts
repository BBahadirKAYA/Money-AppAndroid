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

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.moneyapp.android"
    compileSdk = 36

    // ENV ile ortak debug keystore (opsiyonel)
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
        // release için ayrıca create("release") tanımlayabilirsin (mağaza dağıtımı)
    }

    defaultConfig {
        applicationId = "com.moneyapp.android"
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // --- BuildConfig.UPDATE_MANIFEST_URL ---
        val updateManifest = (project.findProperty("UPDATE_MANIFEST_URL") as String?)
            ?: "https://raw.githubusercontent.com/BBahadirKAYA/Money-AppAndroid/main/update-helper/update.json"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifest\"")

        // --- Version name/code: ENV > .env > -P ---
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

        // --- BuildConfig.BASE_URL ---
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
            isMinifyEnabled = false
            if (hasSharedDebug) {
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
            // ProGuard/R8 kuralı eklemek istersen: proguardFiles(...)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            // Birden fazla libc++_shared.so varsa çakışmayı bastır
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
        val vc = android.defaultConfig.versionCode ?: 0
        val vn = android.defaultConfig.versionName ?: ""
        println("VERSION_CODE=$vc")
        println("VERSION_NAME=$vn")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Google Sheets webhook’a (Apps Script) post eden task
// Gerekli -P/ENV: SHEET_WEBHOOK_URL, APK_PUBLISH_URL
// ──────────────────────────────────────────────────────────────────────────────
val postVersionToSheet by tasks.registering {
    group = "release"
    description = "POST version row to Google Sheets webhook"

    doLast {
        // defaultConfig’i çek (AGP 8/9 uyumlu)
        val appExt8 = project.extensions.findByType(
            com.android.build.api.dsl.ApplicationExtension::class.java
        )
        val appExt7 = project.extensions.findByType(
            com.android.build.gradle.internal.dsl.BaseAppModuleExtension::class.java
        )
        val (vCode, vName) = when {
            appExt8 != null -> (appExt8.defaultConfig.versionCode ?: 0) to (appExt8.defaultConfig.versionName ?: "0.0.0")
            appExt7 != null -> (appExt7.defaultConfig.versionCode ?: 0) to (appExt7.defaultConfig.versionName ?: "0.0.0")
            else -> throw GradleException("Android plugin not applied.")
        }

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
// APK’yı derleme sonrası yeniden adlandır ve publish zincirini kur
// ──────────────────────────────────────────────────────────────────────────────
fun registerRenameApkTask(buildType: String) {
    val cap = buildType.replaceFirstChar { it.uppercase() }
    val renameTaskName = "rename${cap}Apk"
    val publishTaskName = "publish${cap}Apk"

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

    // assemble tamamlanınca rename'i finalize et
    gradle.projectsEvaluated {
        tasks.matching { it.name == "assemble$cap" }.configureEach {
            finalizedBy(renameTaskName)
        }
    }

    // publish<Cap>Apk : rename → post
    tasks.register(publishTaskName) {
        dependsOn(renameTaskName, postVersionToSheet)
    }

    // postVersionToSheet, rename'den sonra koşsun
    postVersionToSheet.configure { mustRunAfter(renameTaskName) }
}

registerRenameApkTask("debug")
registerRenameApkTask("release")

// Tek komutta uçtan uca (debug): assemble → rename → sheet POST
if (tasks.findByName("buildDebugAndPost") == null) {
    tasks.register("buildDebugAndPost") {
        dependsOn("publishDebugApk")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Dependencies
// ──────────────────────────────────────────────────────────────────────────────

val roomVersion = "2.6.1"
val okhttpVersion = "4.12.0"
val retrofitVersion = "2.11.0"
val moshiVersion = "1.15.1"
val lifecycleVersion = "2.8.6"

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
    implementation("com.squareup.retrofit2:converter-scalars:$retrofitVersion")
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

}
