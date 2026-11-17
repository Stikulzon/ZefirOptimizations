plugins {
    `java-library`
    id("dev.kikugie.loom-back-compat")
}

val minecraftVersion = sc.current.version
val javaVersion = when {
    sc.current.parsed >= "26.1" -> 25
    else -> 21
}

version = "${sc.properties["mod.version"]}+$minecraftVersion"
group = sc.properties["mod.group"]

base {
    archivesName = "${sc.properties["mod.id"]}-mc$minecraftVersion"
}

repositories {
    maven("https://jitpack.io")
    maven("https://maven.bawnorton.com/releases")
    maven("https://api.modrinth.com/maven")
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    withSourcesJar()
}

stonecutter {
    files.disable(current.parsed >= "26.1", "ua/zefir/zephyrium/process/mixin/ServerLevelInvoker.java")
}

val compatMods: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${sc.properties["deps.fabric_loader"]}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"]}")

    val mixinSquared = "com.github.bawnorton.mixinsquared:mixinsquared-fabric:${sc.properties["deps.mixinsquared"]}"
    include(mixinSquared)
    implementation(mixinSquared)
    annotationProcessor(mixinSquared)

    val admiral = "maven.modrinth:admiral:${sc.properties["deps.admiral"]}+$minecraftVersion+fabric"
    include(admiral)
    modImplementation(admiral)

    if (findProperty("zoCompatMods")?.toString() != "false") {
        compatMods("maven.modrinth:lithium:${sc.properties["deps.lithium"]}")
        compatMods("maven.modrinth:c2me-fabric:${sc.properties["deps.c2me"]}")
    }

    testImplementation(platform("org.junit:junit-bom:${sc.properties["deps.junit"]}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    accessWidenerPath = sc.process(
        rootProject.file("src/main/resources/zephyrium.accesswidener"),
        "build/stonecutter/zephyrium.accesswidener",
    )

    mods {
        create("zephyrium") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        create("botClient") {
            client()
            name = "Bot Client"
            runDir = "run-client"
            programArgs(
                "--quickPlayMultiplayer", findProperty("zoServer")?.toString() ?: "127.0.0.1:25565",
                "--username", findProperty("zoBotName")?.toString() ?: "ZoBot",
            )
        }
    }

    val cores = findProperty("zoCores")?.toString()
        ?: maxOf(1, Runtime.getRuntime().availableProcessors() / 2).toString()
    fun arguments(name: String): List<String> =
        findProperty(name)?.toString()?.trim()?.split(Regex("""\s+"""))?.filter { it.isNotEmpty() } ?: emptyList()
    runs.configureEach {
        vmArg("-XX:ActiveProcessorCount=$cores")
        arguments("zoJvmArgs").forEach { vmArg(it) }
        arguments("zoArgs").forEach { programArg(it) }
    }
}

tasks.withType<JavaExec>().configureEach {
    val mods = compatMods
    inputs.files(mods)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        if (mods.isEmpty) emptyList() else listOf("-Dfabric.addMods=${mods.asPath}")
    })
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val mixinPackagePattern = Regex("\"package\"\\s*:\\s*\"([^\"]+)\"")

tasks.named<ProcessResources>("processResources") {
    val javaSources = sourceSets.main.map { it.java }
    val metadataProperties = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to minecraftVersion,
        "loader_version" to sc.properties["deps.fabric_loader"],
        "java_version" to javaVersion.toString(),
    )
    inputs.properties(metadataProperties)
    inputs.files(javaSources)
    filesMatching("fabric.mod.json") {
        expand(metadataProperties)
    }
    filesMatching("*.mixins.json") {
        val mixinPackage = mixinPackagePattern.find(file.readText())!!.groupValues[1]
        val mixinClassPattern = Regex(".*/${Regex.escape(mixinPackage.replace('.', '/'))}/[^/]+\\.java")
        val mixins = javaSources.get().files
            .map { it.toURI().path }
            .filter { mixinClassPattern.matches(it) }
            .map { "\"${it.substringAfterLast('/').removeSuffix(".java")}\"" }
            .sorted()
        expand(mapOf("java_version" to javaVersion.toString(), "mixins" to mixins.joinToString(", ")))
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<Jar>("jar") {
    val archivesName = base.archivesName
    from(rootProject.file("LICENSE")) {
        rename { "${it}_${archivesName.get()}" }
    }
}
