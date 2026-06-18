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
        versionCode = 4
        versionName = "2.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY") as String? ?: ""
        
        // Enable BuildConfig generation (flavor buildConfigField overrides these per variant)
        buildConfigField("boolean", "DEBUG", "true")
        buildConfigField("boolean", "IS_OFFLINE", "false")
        buildConfigField("boolean", "IS_DEMO", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Environment variables for release build
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${project.findProperty("FIREBASE_PROJECT_ID") ?: "grpc-app-12345"}\"")
            buildConfigField("String", "FIREBASE_API_KEY", "\"${project.findProperty("FIREBASE_API_KEY") ?: ""}\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"production\"")
            buildConfigField("String", "API_BASE_URL", "\"https://api.grpcstaff.com\"")
            signingConfig = signingConfigs.getByName("debug")
        }
        
        debug {
            // Environment variables for debug build
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${project.findProperty("FIREBASE_PROJECT_ID") ?: "grpc-app-debug"}\"")
            buildConfigField("String", "FIREBASE_API_KEY", "\"${project.findProperty("FIREBASE_API_KEY") ?: ""}\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"development\"")
            buildConfigField("String", "API_BASE_URL", "\"https://dev-api.grpcstaff.com\"")
        }
    }

    // Build-time tenant selection (no runtime switching).
    // GRPC flavor keeps the existing production applicationId unchanged.
    flavorDimensions += "tenant"
    productFlavors {
        create("grpc") {
            dimension = "tenant"
            buildConfigField("String", "FLAVOR", "\"grpc\"")
            buildConfigField("boolean", "IS_OFFLINE", "false")
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("int", "OFFLINE_TRIAL_DAYS", "0")
            buildConfigField("int", "DEMO_FIREBASE_EXPIRY_DAYS", "0")
            buildConfigField("int", "MAX_SAVED_TEMPLATES", "0")  // 0 = no limit
            // Intentionally no applicationIdSuffix/versionNameSuffix to preserve production identity.
        }
        create("demo") {
            dimension = "tenant"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("String", "FLAVOR", "\"demo\"")
            buildConfigField("boolean", "IS_OFFLINE", "false")
            buildConfigField("boolean", "IS_DEMO", "true")
            buildConfigField("int", "OFFLINE_TRIAL_DAYS", "30")   // Trial redirect affects demo + offline, not grpc
            buildConfigField("int", "DEMO_FIREBASE_EXPIRY_DAYS", "30")  // After N days Firebase closed to admin/tech; super_admin only
            buildConfigField("int", "MAX_SAVED_TEMPLATES", "3")  // Demo/offline template limit
        }
        create("offline") {
            dimension = "tenant"
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
            buildConfigField("String", "FLAVOR", "\"offline\"")
            buildConfigField("boolean", "IS_OFFLINE", "true")
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("int", "OFFLINE_TRIAL_DAYS", "30")
            buildConfigField("int", "DEMO_FIREBASE_EXPIRY_DAYS", "0")
            buildConfigField("int", "MAX_SAVED_TEMPLATES", "3")
        }
        // Additional production tenants (same online model as grpc; replace google-services.json per flavor).
        create("viking") {
            dimension = "tenant"
            applicationIdSuffix = ".viking"
            versionNameSuffix = "-viking"
            buildConfigField("String", "FLAVOR", "\"viking\"")
            buildConfigField("boolean", "IS_OFFLINE", "false")
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("int", "OFFLINE_TRIAL_DAYS", "0")
            buildConfigField("int", "DEMO_FIREBASE_EXPIRY_DAYS", "0")
            buildConfigField("int", "MAX_SAVED_TEMPLATES", "0")
        }
        create("primal") {
            dimension = "tenant"
            applicationIdSuffix = ".primal"
            versionNameSuffix = "-primal"
            buildConfigField("String", "FLAVOR", "\"primal\"")
            buildConfigField("boolean", "IS_OFFLINE", "false")
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("int", "OFFLINE_TRIAL_DAYS", "0")
            buildConfigField("int", "DEMO_FIREBASE_EXPIRY_DAYS", "0")
            buildConfigField("int", "MAX_SAVED_TEMPLATES", "0")
        }
        create("discreet") {
            dimension = "tenant"
            applicationIdSuffix = ".discreet"
            versionNameSuffix = "-discreet"
            buildConfigField("String", "FLAVOR", "\"discreet\"")
            buildConfigField("boolean", "IS_OFFLINE", "false")
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("int", "OFFLINE_TRIAL_DAYS", "0")
            buildConfigField("int", "DEMO_FIREBASE_EXPIRY_DAYS", "0")
            buildConfigField("int", "MAX_SAVED_TEMPLATES", "0")
        }
        create("counterpest") {
            dimension = "tenant"
            applicationIdSuffix = ".counterpest"
            versionNameSuffix = "-counterpest"
            buildConfigField("String", "FLAVOR", "\"counterpest\"")
            buildConfigField("boolean", "IS_OFFLINE", "false")
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("int", "OFFLINE_TRIAL_DAYS", "0")
            buildConfigField("int", "DEMO_FIREBASE_EXPIRY_DAYS", "0")
            buildConfigField("int", "MAX_SAVED_TEMPLATES", "0")
        }
        create("safesecure") {
            dimension = "tenant"
            applicationIdSuffix = ".safesecure"
            versionNameSuffix = "-safesecure"
            buildConfigField("String", "FLAVOR", "\"safesecure\"")
            buildConfigField("boolean", "IS_OFFLINE", "false")
            buildConfigField("boolean", "IS_DEMO", "false")
            buildConfigField("int", "OFFLINE_TRIAL_DAYS", "0")
            buildConfigField("int", "DEMO_FIREBASE_EXPIRY_DAYS", "0")
            buildConfigField("int", "MAX_SAVED_TEMPLATES", "0")
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

    lint {
        abortOnError = false          // never fail the build on lint errors
        checkReleaseBuilds = false    // skip lint on release variants (CI runs debug)
        disable += "MissingTranslation"   // multi-tenant app has intentional per-flavour strings
        disable += "ExtraTranslation"
        disable += "Instantiatable"   // suppress false positives on WorkManager workers
        htmlReport = true
        htmlOutput = file("build/reports/lint/lint-report.html")
        xmlReport = false
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
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
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")


   



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

    // PDF → Word (.docx): page images in OOXML
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // PDF text extraction: OCR for scanned pages (Latin script model bundled)
    implementation("com.google.mlkit:text-recognition:16.0.1")

}

// Hide deprecation warnings so build doesn't show "Some input files use deprecated API".
// To see them again and fix, use: options.compilerArgs.add("-Xlint:deprecation")
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-deprecation")
}