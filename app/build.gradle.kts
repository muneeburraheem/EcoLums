plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.ecolums"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ecolums"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Prevent Gradle from compressing the TFLite flatbuffer
    androidResources {
        noCompress += "tflite"
    }

    testOptions {
        unitTests.all { test ->
            test.jvmArgs("-Dnet.bytebuddy.experimental=true")
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.fragment:fragment:1.6.2")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")

    // Firebase BOM (keeps versions consistent)
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))

    // Firebase services
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage") // ✅ FIX ADDED

    // ZXing (QR code generation — US_03.03 invite system)
    implementation("com.google.zxing:core:3.5.3")

    // Gson (for JSON export)
    implementation("com.google.code.gson:gson:2.10.1")

    // TensorFlow Lite — on-device CO₂ estimation model
    // Models were generated with TF 2.19.0; runtime must be ≥ model version
    // TensorFlow Lite — on-device CO₂ estimation model
    // Models were generated with TF 2.19.0; 2.17.0 is the highest available Android runtime
    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    // MPAndroidChart (campus impact graphs)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

tasks.register<Javadoc>("generateJavadoc") {
    source = fileTree("src/main/java")
    classpath += files(android.bootClasspath)
    classpath += configurations.getByName("debugCompileClasspath").incoming.artifactView {
        attributes {
            attribute(Attribute.of("artifactType", String::class.java), "jar")
        }
    }.artifacts.artifactFiles
    setDestinationDir(file("${rootProject.projectDir}/docs"))
    (options as StandardJavadocDocletOptions).apply {
        windowTitle = "EcoLUMS API Documentation"
        docTitle = "EcoLUMS API Documentation"
        addStringOption("Xdoclint:none", "-quiet")
    }
    isFailOnError = false
}