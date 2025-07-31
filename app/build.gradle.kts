plugins {

    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.grpc.grpc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.grpc.grpc"
        minSdk = 27
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Enable BuildConfig generation
        buildConfigField("boolean", "DEBUG", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Environment variables for release build
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${project.findProperty("FIREBASE_PROJECT_ID") ?: "grpc-app-12345"}\"")
            buildConfigField("String", "FIREBASE_API_KEY", "\"${project.findProperty("FIREBASE_API_KEY") ?: ""}\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"production\"")
            buildConfigField("String", "API_BASE_URL", "\"https://api.grpcstaff.com\"")
        }
        
        debug {
            // Environment variables for debug build
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${project.findProperty("FIREBASE_PROJECT_ID") ?: "grpc-app-debug"}\"")
            buildConfigField("String", "FIREBASE_API_KEY", "\"${project.findProperty("FIREBASE_API_KEY") ?: ""}\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"development\"")
            buildConfigField("String", "API_BASE_URL", "\"https://dev-api.grpcstaff.com\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
    
    buildFeatures {
        buildConfig = true
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ✅ iText7 Libraries for PDF Generation
    implementation("com.itextpdf:itext7-core:7.1.15")
    implementation("com.itextpdf:layout:7.1.15")
    implementation("com.itextpdf:io:7.1.15")
    implementation("com.itextpdf:kernel:7.1.15")

    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-database")
    


   



    // ✅ File Provider and File Access
    implementation("androidx.core:core-ktx:1.12.0")

    // recyclerview
    implementation("androidx.recyclerview:recyclerview:1.2.1")

    // ✅ WorkManager (used for in-app-only scheduled reminders)
    implementation("androidx.work:work-runtime:2.10.0")

    // ✅ Location (for last-known location sharing)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ✅ OkHttp for AI API calls
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

}