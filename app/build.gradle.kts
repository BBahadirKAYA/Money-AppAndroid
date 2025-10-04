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
        versionCode = 1
        versionName = "1.0.0"
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
