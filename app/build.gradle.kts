plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.grpc.grpc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.grpc.grpc"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
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

    // ✅ File Provider and File Access
    implementation("androidx.core:core-ktx:1.12.0")

    // recyclerview
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("com.github.barteksc:android-pdf-viewer:2.8.2")


}