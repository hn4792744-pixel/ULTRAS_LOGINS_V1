plugins { `java-library` }

val hikari = "com.zaxxer:HikariCP:6.3.0"
val bouncy = "org.bouncycastle:bcprov-jdk18on:1.81"
val snake = "org.yaml:snakeyaml:2.4"

tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

// Heavy libraries are compileOnly here: the proxy module shades (and relocates) them.
// The bridge only needs the dependency-free "bridge" package of this module.
dependencies {
    compileOnly(hikari)
    compileOnly(bouncy)
    compileOnly(snake)
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation(hikari)
    testImplementation(bouncy)
    testImplementation(snake)
    testImplementation("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation("org.slf4j:slf4j-nop:2.0.17")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
