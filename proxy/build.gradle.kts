plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.2.1"
}

tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

dependencies {
    implementation(project(":common"))
    // Shaded + relocated
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.yaml:snakeyaml:2.4")
    // Shaded, NOT relocated (JDBC drivers register themselves / use native code)
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")

    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.0.2")
}

tasks.shadowJar {
    archiveFileName.set("Ultras_login_v1-proxy.jar")
    val base = "me.uc_hussein.ultraslogin.libs"
    relocate("com.zaxxer.hikari", "$base.hikari")
    relocate("org.bouncycastle", "$base.bouncycastle")
    relocate("org.yaml.snakeyaml", "$base.snakeyaml")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class", "module-info.class")
    mergeServiceFiles()
}
tasks.build { dependsOn(tasks.shadowJar) }
