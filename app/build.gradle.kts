import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.purewrite.writer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.purewrite.writer"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.7.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Material 3
    implementation("com.google.android.material:material:1.12.0")

    // ConstraintLayout & RecyclerView
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // FlexboxLayout（书架自适应网格）
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Vosk 离线语音识别
    implementation("com.alphacephei:vosk-android:0.3.75")

    // OkHttp 用于下载模型
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.4")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Splash
    implementation("androidx.core:core-splashscreen:1.0.1")
}

// ==================== 构建期下载 Vosk 语音模型到 assets ====================
// 模型直接打包进 APK，用户无需联网下载，首启自动解压到内部存储
val voskModelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
val voskModelFileName = "vosk-model-small-cn-0.22.zip"
val voskAssetsDir = file("$projectDir/src/main/assets")
val voskModelFile = file("$voskAssetsDir/$voskModelFileName")

tasks.register("downloadVoskModel") {
    group = "purewriter"
    description = "下载 Vosk 中文语音模型到 assets 目录（打包进 APK）"
    onlyIf { !voskModelFile.exists() || voskModelFile.length() < 1_000_000 }
    doLast {
        if (voskModelFile.exists() && voskModelFile.length() >= 1_000_000) {
            println("Vosk 模型已存在，跳过下载: ${voskModelFile.absolutePath}")
            return@doLast
        }
        voskAssetsDir.mkdirs()
        println("正在下载 Vosk 语音模型: $voskModelUrl")
        val proxyHost = System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy") ?: ""
        val connection = if (proxyHost.isNotEmpty()) {
            val proxyUrl = URI(proxyHost).toURL()
            val proxy = Proxy(Proxy.Type.HTTP,
                InetSocketAddress(proxyUrl.host, proxyUrl.port))
            URL(voskModelUrl).openConnection(proxy)
        } else {
            URL(voskModelUrl).openConnection()
        } as HttpURLConnection
        connection.connectTimeout = 60_000
        connection.readTimeout = 300_000
        connection.connect()
        if (connection.responseCode != 200) {
            throw GradleException("下载 Vosk 模型失败: HTTP ${connection.responseCode}")
        }
        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            FileOutputStream(voskModelFile).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                var downloaded = 0L
                var lastPercent = -1
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    val percent = ((downloaded * 100) / total).toInt()
                    if (percent != lastPercent && percent % 10 == 0) {
                        lastPercent = percent
                        println("  下载进度: $percent%")
                    }
                }
            }
        }
        connection.disconnect()
        println("Vosk 模型下载完成: ${voskModelFile.length() / 1024 / 1024} MB")
    }
}

// 构建前确保模型已下载到 assets
tasks.named("preBuild") {
    dependsOn("downloadVoskModel")
}
