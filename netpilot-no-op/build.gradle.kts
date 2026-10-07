plugins {
    `maven-publish`
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.mobile.netpilot"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        // AGP 9 would otherwise require host apps to use compileSdk 37 like this module.
        aarMetadata {
            minCompileSdk = 34
        }
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
}

dependencies {
    api(libs.okhttp)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = providers.gradleProperty("netpilot.group").get()
            artifactId = "netpilot-no-op"
            version = providers.gradleProperty("netpilot.version").get()
            // The "release" software component only exists after the Android plugin configures it.
            afterEvaluate { from(components["release"]) }
        }
    }
}
