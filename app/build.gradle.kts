import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

// Firebase Android client values are public project/app identifiers. Keep them
// separate from private server credentials and load them from a checked-in,
// four-field config so CI can build without manually copying GitHub secrets.
val firebaseProperties = Properties().apply {
    val propertiesFile = rootProject.file("firebase-config.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun configurationValue(name: String): String =
    providers.environmentVariable(name).orNull
        ?.takeIf(String::isNotBlank)
        ?: localProperties.getProperty(name).orEmpty().takeIf(String::isNotBlank)
        ?: firebaseProperties.getProperty(name).orEmpty()

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n") + "\""

val supabaseUrl = configurationValue("SUPABASE_URL")
val supabasePublishableKey = configurationValue("SUPABASE_PUBLISHABLE_KEY")
val firebaseApiKey = configurationValue("FIREBASE_API_KEY")
val firebaseApplicationId = configurationValue("FIREBASE_APPLICATION_ID")
val firebaseProjectId = configurationValue("FIREBASE_PROJECT_ID")
val firebaseSenderId = configurationValue("FIREBASE_SENDER_ID")
val firebaseConfigurationIsComplete = listOf(
    firebaseApiKey,
    firebaseApplicationId,
    firebaseProjectId,
    firebaseSenderId
).all(String::isNotBlank)

android {
    namespace = "com.unityhub.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.unityhub.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.12.0"

        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", supabasePublishableKey.asBuildConfigString())
        buildConfigField("String", "FIREBASE_API_KEY", firebaseApiKey.asBuildConfigString())
        buildConfigField("String", "FIREBASE_APPLICATION_ID", firebaseApplicationId.asBuildConfigString())
        buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseProjectId.asBuildConfigString())
        buildConfigField("String", "FIREBASE_SENDER_ID", firebaseSenderId.asBuildConfigString())

        // These resource names are read by FirebaseInitProvider before an Activity or
        // FirebaseMessagingService starts, so notification delivery also works from a
        // cold app process after Firebase has been configured.
        if (firebaseConfigurationIsComplete) {
            resValue("string", "google_app_id", firebaseApplicationId)
            resValue("string", "google_api_key", firebaseApiKey)
            resValue("string", "project_id", firebaseProjectId)
            resValue("string", "gcm_defaultSenderId", firebaseSenderId)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
