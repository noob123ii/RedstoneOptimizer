plugins {
    java
}

group = "me.cpatt"
version = "1.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-transport:4.2.7.Final")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.jar {
    archiveFileName = "RedstoneOptimizer-${project.version}.jar"
}
