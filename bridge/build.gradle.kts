plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.2.1"
}

tasks.withType<JavaCompile>().configureEach { options.release.set(25) }

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("org.jetbrains:annotations:26.0.2")
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveFileName.set("Ultras_login_v1-bridge.jar")
    // Only the protocol package of :common is needed on the backend.
    dependencies { include(project(":common")) }
    exclude("db/**", "META-INF/*.SF")
}
tasks.build { dependsOn(tasks.shadowJar) }
