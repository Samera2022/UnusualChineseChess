plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.samera2022.chinese_chess"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.samera2022.chinese_chess"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "../../ucc-common/src/main/java",
                "../../ucc-core/src/main/java",
                "../../ucc-api/src/main/java",
                "src/main/java"   // Android UI + adapted overlay
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.material)

    implementation(libs.gson)
}
