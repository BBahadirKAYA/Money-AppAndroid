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

        // gradle.properties → BASE_URL değerini oku (yoksa 10.0.2.2)
        val backendUrl = (project.findProperty("BASE_URL") as String? ?: "http://10.0.2.2:8000")
        // BuildConfig için mutlaka string literal ver
        buildConfigField("String", "BASE_URL", "\"$backendUrl\"")
    }

    buildTypes {
        getByName("debug") {
            // İstersen burada debug'a özel BASE_URL override edebilirsin.
            // buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            // Prod'da sabit domain kullanacaksın.
        }
    }

    // ⚠️ buildTypes dışına, android bloğunun içine
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

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.20"))
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
// (İsteğe bağlı) Kotlin codegen kullanacaksan:
// ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // Retrofit + OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0") // "pong" gibi düz text yanıtlar
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")    // JSON yanıtlar

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // lifecycleScope için:
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Room + KSP
    implementation("androidx.room:room-runtime:2.8.1")
    implementation("androidx.room:room-ktx:2.8.1")
    ksp("androidx.room:room-compiler:2.8.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
