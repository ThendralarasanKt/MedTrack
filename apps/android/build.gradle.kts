import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // Makes app/google-services.json available to Firebase SDKs.
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { input -> load(input) }
    }
}
val localMcpEnabled =
    localProperties.getProperty("medtrack.localMcp.enabled", "false").toBoolean()
val seedSyntheticData =
    localProperties.getProperty("medtrack.seedSyntheticData", "true").toBoolean()

data class GoogleServicesConfig(val projectId: String, val webClientId: String)

fun googleServicesConfig(): GoogleServicesConfig {
    val file = file("google-services.json")
    if (!file.exists()) return GoogleServicesConfig("", "")
    val root = JsonSlurper().parse(file) as Map<*, *>
    val projectId = (root["project_info"] as? Map<*, *>)?.get("project_id")?.toString().orEmpty()
    val clients = (root["client"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    val matching = clients.filter { client ->
        val info = client["client_info"] as? Map<*, *>
        val android = info?.get("android_client_info") as? Map<*, *>
        android?.get("package_name") == "com.medtrack.app"
    }
    if (matching.isEmpty()) {
        throw GradleException("google-services.json has no Android client for com.medtrack.app")
    }
    if (matching.size > 1) {
        throw GradleException("google-services.json has multiple Android clients for com.medtrack.app")
    }
    val oauth = (matching.first()["oauth_client"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    val webClients = oauth.filter { (it["client_type"] as? Number)?.toInt() == 3 }
        .map { it["client_id"]?.toString().orEmpty() }
        .filter { it.isNotBlank() }
        .distinct()
    if (webClients.isEmpty()) {
        throw GradleException("google-services.json for com.medtrack.app is missing a web OAuth client (client_type 3)")
    }
    if (webClients.size > 1) {
        throw GradleException("google-services.json for com.medtrack.app has multiple web OAuth clients")
    }
    return GoogleServicesConfig(projectId, webClients.first())
}

val parsedGoogleServices = googleServicesConfig()
val googleWebClientId =
    localProperties.getProperty("medtrack.google.webClientId", "").trim()
        .ifBlank { parsedGoogleServices.webClientId }
val firebaseProjectId =
    localProperties.getProperty("medtrack.firebase.projectId", "").trim()
        .ifBlank { parsedGoogleServices.projectId }
val debugAuthBypass =
    localProperties.getProperty("medtrack.debugAuthBypass", "true").toBoolean()
val gatewayBaseUrl =
    localProperties.getProperty("medtrack.gateway.baseUrl", "").trim()
val useMockGateway =
    localProperties.getProperty(
        "medtrack.gateway.mock",
        if (gatewayBaseUrl.isBlank()) "true" else "false"
    ).toBoolean()
val ankitaAuthSubject =
    localProperties.getProperty("medtrack.ankita.authSubject", "").trim()
        .ifBlank { System.getenv("MEDTRACK_ANKITA_AUTH_SUBJECT").orEmpty().trim() }

android {
    namespace = "com.medtrack.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.medtrack.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${googleWebClientId.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            "\"${firebaseProjectId.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "GATEWAY_BASE_URL",
            "\"${gatewayBaseUrl.escapeForBuildConfig()}\""
        )
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_LOCAL_MCP_SERVER", localMcpEnabled.toString())
            buildConfigField("boolean", "SEED_SYNTHETIC_DATA", seedSyntheticData.toString())
            buildConfigField("boolean", "DEBUG_AUTH_BYPASS", debugAuthBypass.toString())
            buildConfigField("boolean", "USE_MOCK_GATEWAY", useMockGateway.toString())
            buildConfigField(
                "String",
                "ANKITA_AUTH_SUBJECT",
                "\"${ankitaAuthSubject.escapeForBuildConfig()}\""
            )
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "ENABLE_LOCAL_MCP_SERVER", "false")
            buildConfigField("boolean", "SEED_SYNTHETIC_DATA", "false")
            buildConfigField("boolean", "DEBUG_AUTH_BYPASS", "false")
            buildConfigField("boolean", "USE_MOCK_GATEWAY", "false")
            buildConfigField("String", "ANKITA_AUTH_SUBJECT", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room + SQLCipher
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Coil
    implementation(libs.coil.compose)

    // Firebase (BoM manages product versions)
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")

    // Google Sign-In
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    // Encryption
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
