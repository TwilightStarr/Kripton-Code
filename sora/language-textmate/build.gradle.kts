import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.rosemoe.sora.langs.textmate"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        suppressWarnings.set(true)
    }
}

dependencies {
    compileOnly(project(":sora-editor"))
    compileOnly(project(":sora-oniguruma"))

    implementation(libs.sora.gson)
    implementation(libs.sora.jcodings)
    implementation(libs.sora.joni)
    implementation(libs.sora.snakeyaml)
    implementation(libs.sora.jdt.annotation)
}

// Üçüncü taraf kaynak: "deprecated/unchecked" notları derleme çıktısını kirletmesin (kodun davranışı etkilenmez).
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:-deprecation", "-Xlint:-unchecked", "-XDsuppressNotes"))
}
