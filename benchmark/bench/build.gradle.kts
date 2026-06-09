plugins {
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}

// Path to the ktoml-core jvm jar to compile against (swapped per version).
// Defaults to the upstream jar; override with -PktomlJar=/abs/path.
val ktomlJar: String = (findProperty("ktomlJar") as String?)
    ?: "${rootDir}/libs/ktoml-core-upstream.jar"

dependencies {
    // ktoml-core is provided explicitly at runtime (swapped per version), so compileOnly.
    compileOnly(files(ktomlJar))
    // ktoml-core's `api` deps — needed at runtime, same versions for both branches.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(8)
}

// Copy every runtime dependency (serialization, datetime, stdlib, transitives) into
// build/deps so we can assemble an explicit classpath and launch a clean JVM per version.
tasks.register<Copy>("copyDeps") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("deps"))
}
