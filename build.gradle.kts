// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.2" apply false;
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false;
    id("com.google.gms.google-services") version "4.4.1" apply false;
    id("androidx.navigation.safeargs.kotlin") version "2.5.3" apply false
    id ("com.android.library") version "7.3.0" apply false

}
buildscript {
    dependencies {
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
    }
    repositories {
        maven { url = uri("https://jitpack.io") } // Thêm dòng này
    }
}