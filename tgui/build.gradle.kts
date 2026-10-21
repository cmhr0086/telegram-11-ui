plugins {
    id("com.android.library")
    `maven-publish`
}

android {
    // Own namespace (not org.telegram.messenger) so hosts, including Telegram forks, do not clash.
    namespace = "org.telegram.tgui"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    // rlottie + lottie JNI (libtguilottie.so), ported from upstream TMessagesProj/jni.
    ndkVersion = "28.2.13676358"
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

    buildFeatures {
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation", "BlockedPrivateApi")
        abortOnError = false
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "100000", "-nowarn"))
}

dependencies {
    // RecyclerView is vendored (upstream fork, moved to org.telegram.tgui.recyclerview).
    api("androidx.core:core:1.13.1")
    api("androidx.annotation:annotation:1.8.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.customview:customview:1.1.0")
    implementation("androidx.collection:collection:1.4.0")
    implementation("androidx.interpolator:interpolator:1.0.0")
    // EmojiView pages (upstream uses androidx ViewPager)
    implementation("androidx.viewpager:viewpager:1.0.0")
}

// ./gradlew :tgui:publishToMavenLocal  ->  org.telegram.ui:tgui:11.4.2-ui4 (pulls androidx deps transitively)
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "org.telegram.ui"
            artifactId = "tgui"
            version = "11.4.2-ui4"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
