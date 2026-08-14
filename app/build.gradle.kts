plugins {
    id("com.android.application")
}

android {
    namespace = "com.artemjsdx.geminimine"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.artemjsdx.geminimine"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.0.2-foundation-signing"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++20")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    val devSigningPropsFile = rootProject.file("signing/development-signing.properties")
    val devSigningProps = java.util.Properties()
    if (devSigningPropsFile.exists()) {
        devSigningPropsFile.inputStream().use { devSigningProps.load(it) }
    }

    signingConfigs {
        create("development") {
            if (!devSigningPropsFile.exists()) {
                throw GradleException("Development signing properties file not found: " + devSigningPropsFile.absolutePath)
            }
            val storeFilePath = devSigningProps.getProperty("storeFile")
                ?: throw GradleException("Missing storeFile in " + devSigningPropsFile.absolutePath)
            val keystoreFile = rootProject.file(storeFilePath)
            if (!keystoreFile.exists()) {
                throw GradleException("Development keystore file not found: " + keystoreFile.absolutePath)
            }
            storeFile = keystoreFile
            storePassword = devSigningProps.getProperty("storePassword")
                ?: throw GradleException("Missing storePassword in " + devSigningPropsFile.absolutePath)
            keyAlias = devSigningProps.getProperty("keyAlias")
                ?: throw GradleException("Missing keyAlias in " + devSigningPropsFile.absolutePath)
            keyPassword = devSigningProps.getProperty("keyPassword")
                ?: throw GradleException("Missing keyPassword in " + devSigningPropsFile.absolutePath)
        }
    }

    ndkVersion = "29.0.14206865"

    buildFeatures {
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("development")
        }
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.games:games-activity:4.4.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.15.0")
}
