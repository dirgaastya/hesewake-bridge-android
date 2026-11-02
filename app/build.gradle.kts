import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "id.hesewake.mediabridge"
    compileSdk = 36
    defaultConfig { applicationId = "id.hesewake.mediabridge"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.1.1" }
    // The release key is not in the repository: a release is signed only when HESEWAKE_KEYSTORE (the path to the .jks) is set.
    val releaseKeystore = System.getenv("HESEWAKE_KEYSTORE")
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("HESEWAKE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HESEWAKE_KEY_ALIAS") ?: "hesewake"
                keyPassword = System.getenv("HESEWAKE_KEY_PASSWORD") ?: System.getenv("HESEWAKE_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
