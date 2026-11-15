plugins { id("com.android.application") }
android {
    namespace = "com.example.hyundaiappproject"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.hyundaiappproject"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {
    implementation("com.airbnb.android:lottie:6.6.0")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
}
