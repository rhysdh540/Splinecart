plugins {
    id("net.neoforged.moddev") version("2.0.78")
}

base {
    version = "mod_version"()
    group = "maven_group"()
    archivesName = "archives_base_name"()
}

ext["minecraft_version"] = "1.${"neoforge_version"().substringBeforeLast(".")}"

sourceSets.client {
    compileClasspath += sourceSets.main.compileClasspath + sourceSets.main.output
    runtimeClasspath += sourceSets.main.runtimeClasspath + sourceSets.main.output
}

java {
    withSourcesJar()

    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

neoForge {
    version = "neoforge_version"()

    runs {
        register("client") {
            sourceSet = sourceSets.client
            client()
        }

        register("server") {
            server()
        }
    }

    parchment {
        minecraftVersion = "minecraft_version"()
        mappingsVersion = "parchment_version"()
    }

    mods {
        register("archives_base_name"()) {
            sourceSet(sourceSets.main)
            sourceSet(sourceSets.client)
        }
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to "mod_version"(),
        "minecraft_version" to "minecraft_version"(),
        "neoforge_version" to "neoforge_version"(),
    )

    inputs.properties(props)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

tasks.jar {
    from("LICENSE") {
        into("META-INF")
    }

    from(sourceSets.client.output)
}

// accessors
val SourceSetContainer.client get() = maybeCreate("client")
val SourceSetContainer.main get() = maybeCreate("main")
operator fun SourceSet.invoke(action: Action<SourceSet>) = action.execute(this)

operator fun String.invoke() = rootProject.ext[this] as? String ?: error("No property \"$this\"")