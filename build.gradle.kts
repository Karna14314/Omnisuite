// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        maven {
            url = uri("file:///workspace/2c374d04-cb0c-4045-8c80-c1b0f1ec52d4/sessions/agent_0dd2f075-37e1-4263-861f-c7c4094ef7eb/local-plugins")
        }
    }
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.51.1")
    }
}

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false
}


