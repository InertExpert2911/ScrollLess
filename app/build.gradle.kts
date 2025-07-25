import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.jacoco)
}

android {
    namespace = "com.example.scrolltrack"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.scrolltrack"
        minSdk = 28
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidxComposeCompiler.get()
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {

    implementation(libs.timber)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.expressive)

    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Essential libraries for XML-based UI with AppCompatActivity and Material Design
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // ConstraintLayout
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // For collectAsStateWithLifecycle

    // Room Database (using libs aliases - ensure these are defined in libs.versions.toml)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore)

    // Hilt for Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Hilt testing dependencies
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.work.compiler)
    testImplementation(libs.androidx.work.testing)

    implementation(libs.androidx.hilt.navigation.compose)

    // Coil for image loading
    implementation(libs.coil.compose)
    // Extended Material Icons
    implementation(libs.androidx.compose.material.iconsExtended)
    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.palette.ktx)

    // Coroutines and Flow testing
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.cash.turbine)

    // Mockito for mocking
    testImplementation(libs.mockito.core)

    // AndroidX Test
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)

    // Lottie for animated icons
    implementation(libs.lottie.compose)

    // Robolectric for providing Android framework classes in local tests
    testImplementation(libs.robolectric)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        // Android generated classes
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        // Hilt generated classes
        "**/*_HiltModules*.*",
        "**/*_Factory.*",
        "**/*_MembersInjector.*",
        "**/Dagger*Component.*",
        // Files to exclude
        "**/*Test*.*",
        "android/**/*.*",
        "**/*Application.*",
        "**/*Activity.*"
    )

    val mainSrc = "${project.projectDir}/src/main/java"
    val classFiles = fileTree(project.buildDir) {
        include("**/tmp/kotlin-classes/debug/**/*.class")
        exclude(fileFilter)
    }

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(classFiles)

    executionData.setFrom(
        fileTree(project.buildDir) {
            include("**/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        }
    )
}
