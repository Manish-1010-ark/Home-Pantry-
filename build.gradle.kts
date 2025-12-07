//build.gradle.kts file (in the project's root folder)

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Add these two lines to declare the Hilt and Kapt plugins
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    kotlin("kapt") version "1.9.22" apply false
//    id("com.google.devtools.ksp") version "1.9.22-1.0.14" apply false

    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.4.4" apply false

}