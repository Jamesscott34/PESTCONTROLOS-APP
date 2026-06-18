// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {

    // Keep these aligned with a stable Android Studio + AGP combo.
    // If Android Studio sync shows only debug/release or errors on variants, mismatched AGP/Gradle is the usual cause.
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false

}