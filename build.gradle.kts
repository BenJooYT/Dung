plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.lieyabull"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.11.0")
    testRuntimeOnly("com.google.code.gson:gson:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // WorldEdit is a REQUIRED plugin dependency. We compile against its public API only; the plugin
    // itself must be present on the server at runtime (see plugin.yml `depend`). Dung never ships or
    // parses its own schematic format — WorldEdit owns all clipboard load/transform/paste.
    // 7.3.x targets Java 21 (7.4.x requires JVM 25+, which the server's Java 21 toolchain can't use).
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.19")
    testCompileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.19")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
    }
    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "name" to "Dung"
            )
        }
    }
    runServer {
        minecraftVersion("1.21.11")
    }
}
