import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    id("kotlin-kapt")
    id("androidx.navigation.safeargs")
}

android {
    namespace = "cs.colman.talento"
    compileSdk = 35

    defaultConfig {
        applicationId = "cs.colman.talento"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MapLibre configuration would usually go here,
        // For development we can leave it empty or mock it if secrets.properties is not available
        val maplibreApiKey = "EiunH9IaZgGKUSnhtElK" // Assuming the user provides it or we use a dummy for now
        buildConfigField("String", "MAPLIBRE_API_KEY", "\"$maplibreApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    viewBinding {
        enable = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.com.google.firebase.firebase.auth)
    implementation(libs.maplibre)
    implementation(libs.play.services.location)
    implementation(libs.glide)
    kapt(libs.compiler)
    implementation(libs.geofire.android.common)
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.gson)
    implementation(libs.cloudinary.android)
}