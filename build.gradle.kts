// Ultras_login_v1 - root build.
// Build with JDK 25 (needed by the Paper 26.2 bridge). Velocity modules are compiled with --release 21.
allprojects {
    group = "me.uc_hussein"
    version = "1.0.0"
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.opencollab.dev/main/") { name = "opencollab" }
    }
}

subprojects {
    apply(plugin = "java")
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
