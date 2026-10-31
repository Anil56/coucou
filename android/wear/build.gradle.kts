plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The watch talks to the phone app through Google's Wearable API, which only connects apps with the same application id signed with the
// same key: release signing reads the same values as the phone's (app/build.gradle.kts), never a file in this repository.
fun signingValue(name: String): String? = (System.getenv(name) ?: providers.gradleProperty(name).orNull)?.takeIf { it.isNotBlank() }
val releaseKeystore = signingValue("COUCOU_KEYSTORE_FILE")
val releaseStorePassword = signingValue("COUCOU_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("COUCOU_KEY_ALIAS")
val releaseKeyPassword = signingValue("COUCOU_KEY_PASSWORD") ?: releaseStorePassword

// Mochi (drawn in code), his easing and the phone-watch format are the phone app's own files, built into the watch as they are: one
// source, no copy in git. The icon comes the same way.
val phoneSrc = "../app/src/main"
val sharedKotlin = layout.buildDirectory.dir("generated/phone/kotlin")
val sharedRes = layout.buildDirectory.dir("generated/phone/res")
val shareFromPhone by tasks.registering(Sync::class) {
    from("$phoneSrc/kotlin") {
        include("com/coucou/android/mochi/**", "com/coucou/android/core/Anim.kt", "com/coucou/android/core/WatchState.kt")
        exclude("com/coucou/android/mochi/MochiView.kt") // the phone's view follows its own motion setting; the watch has MochiFace
        into("kotlin")
    }
    from("$phoneSrc/res") {
        include("mipmap-*/ic_launcher*")
        into("res")
    }
    into(layout.buildDirectory.dir("generated/phone"))
}

android {
    namespace = "com.coucou.android.wear"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.coucou.android" // must be the phone app's (see above)
        minSdk = 30 // Wear OS 3; the Galaxy Watch 7 runs Wear OS 5
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("en", "fr")
    }
    signingConfigs {
        val keystore = releaseKeystore
        val storePass = releaseStorePassword
        val alias = releaseKeyAlias
        if (keystore != null && storePass != null && alias != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = storePass
                keyAlias = alias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") // null: unsigned
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].java.srcDirs("src/main/kotlin", sharedKotlin)
    sourceSets["main"].res.srcDirs("src/main/res", sharedRes)
    sourceSets["test"].java.srcDirs("src/test/kotlin")
}

tasks.named("preBuild") { dependsOn(shareFromPhone) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    val wearCompose = "1.4.0"
    implementation("androidx.wear.compose:compose-material:$wearCompose")
    implementation("androidx.wear.compose:compose-foundation:$wearCompose")
    implementation("androidx.wear.compose:compose-navigation:$wearCompose")
    // The Tile (swipe from the watch face): a few lines, redrawn when the phone sends something new.
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
