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

        // ⬇️ BASE_URL'ü BuildConfig'e ekle
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000\"")
    }

    buildTypes {
        getByName("debug") {
            // İstersen debug için farklı URL:
            // buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000\"")
        }
        getByName("release") {
            // Prod URL istersen burada geçersiz kıl:
            // buildConfigField("String", "BASE_URL", "\"https://api.moneyapp.example\"")
            isMinifyEnabled = false
        }
    }

    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}


dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.20"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.webkit:webkit:1.14.0")

    // Room + KSP
    implementation("androidx.room:room-runtime:2.8.1")
    implementation("androidx.room:room-ktx:2.8.1")
    ksp("androidx.room:room-compiler:2.8.1")

    // Retrofit + Moshi + OkHttp 4.x (Kotlin 2.0 ile uyumlu)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
