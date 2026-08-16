plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5"
    java
}

group = providers.gradleProperty("mod_group").get()
version = "${providers.gradleProperty("mod_version").get()}+bta-${providers.gradleProperty("bta_version").get()}"
base.archivesName = providers.gradleProperty("mod_name")

loom {
    customMinecraftMetadata.set(
        "https://downloads.betterthanadventure.net/bta-client/release/v${providers.gradleProperty("bta_version").get()}/manifest.json"
    )
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    ivy("https://github.com/Turnip-Labs/fabric-loader/releases/download") {
        patternLayout {
            artifact("[revision]/[artifact]-[revision].[ext]")
        }
        metadataSources { artifact() }
        content { includeModule("net.fabricmc", "fabric-loader") }
    }
    maven("https://maven.thesignalumproject.net/infrastructure") {
        content {
            includeGroup("net.fabricmc")
            includeGroup("turniplabs")
        }
    }
    maven("https://maven.thesignalumproject.net/releases")
}

dependencies {
    minecraft("::${providers.gradleProperty("bta_version").get()}")
    implementation(files("libs/fabric-loader-${providers.gradleProperty("fabric_loader_version").get()}.jar"))
    compileOnly("turniplabs:halplibe:${providers.gradleProperty("halplibe_version").get()}+8.0")
    localRuntime("turniplabs:halplibe:${providers.gradleProperty("halplibe_version").get()}+8.0")

    implementation("com.code-disaster.steamworks4j:steamworks4j:1.10.0") { isTransitive = false }
    include("com.code-disaster.steamworks4j:steamworks4j:1.10.0") { isTransitive = false }
    implementation("net.java.dev.jna:jna:5.10.0")
    include("net.java.dev.jna:jna:5.10.0")

    compileOnly("org.lwjgl:lwjgl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-glfw:3.3.3")
    compileOnly("org.lwjgl:lwjgl-openal:3.3.3")
    compileOnly("org.lwjgl:lwjgl-opengl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-stb:3.3.3")
    compileOnly("org.joml:joml:1.10.8")
}

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
    exclude(group = "net.java.jutils")
    exclude(group = "net.java.jinput")
    exclude(group = "net.sf.jopt-simple")
    exclude(group = "net.minecraft", module = "launchwrapper")
}

val sharedSteamSources = fileTree("../common/src/main/java") {
    include("link/e4steam/HexCodec.java")
    include("link/e4steam/steam/**/*.java")
    exclude("link/e4steam/steam/SteamSession.java")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.named<JavaCompile>("compileJava") {
    source(sharedSteamSources)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("fabricloader", providers.gradleProperty("fabric_loader_version").get())
    inputs.property("halplibe", providers.gradleProperty("halplibe_version").get())
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "fabricloader" to providers.gradleProperty("fabric_loader_version").get(),
            "halplibe" to providers.gradleProperty("halplibe_version").get()
        )
    }
    from("../LICENSE") {
        into("META-INF")
    }
    from("../NOTICE") {
        into("META-INF")
    }
    from("../THIRD_PARTY_NOTICES.md") {
        into("META-INF")
    }
}
