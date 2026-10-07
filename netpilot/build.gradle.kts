plugins {
    `maven-publish`
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.mobile.netpilot"
    compileSdk {
        version = release(37)
    }
    resourcePrefix = "netpilot_"

    defaultConfig {
        minSdk = 24
        // AGP 9 would otherwise require host apps to use compileSdk 37 like this module.
        aarMetadata {
            minCompileSdk = 34
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(libs.okhttp)

    implementation(platform(libs.lib.compose.bom))
    implementation(libs.lib.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.lib.core.ktx)
    implementation(libs.lib.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    // Real org.json for HAR assertions; android.jar only ships stubs in unit tests.
    testImplementation(libs.org.json)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = providers.gradleProperty("netpilot.group").get()
            artifactId = "netpilot"
            version = providers.gradleProperty("netpilot.version").get()
            // The "release" software component only exists after the Android plugin configures it.
            afterEvaluate { from(components["release"]) }
        }
    }
}
