plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.questionextractionmodule"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.questionextractionmodule"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndkVersion = "21.1.6352462"

        ndk {
            // On Apple silicon, you can omit x86_64.
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // 配置 CMake 构建参数
                arguments(
                    "-DANDROID_PLATFORM=android-24", // 修改为 minSdkVersion 的对应平台
                    "-DANDROID_STL=c++_shared",      // 使用 c++_shared STL
                    "-DANDROID_TOOLCHAIN="           // 可以省略或根据需要指定工具链
                )
                abiFilters.addAll(listOf("arm64-v8a"))
                cppFlags.addAll(listOf("-std=c++11"))
            }
        }

    }

    externalNativeBuild {
        cmake {
            path = File("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"  // 使用的 CMake 版本
        }
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.vanniktech:android-image-cropper:4.6.0")
    implementation("com.squareup.picasso:picasso:2.5.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

