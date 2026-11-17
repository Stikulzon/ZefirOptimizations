plugins {
    id("dev.kikugie.stonecutter")
    id("com.diffplug.spotless")
}

stonecutter active "26.2"

stonecutter parameters {
    replacements.string(current.parsed < "1.21.11") {
        replace("Identifier", "ResourceLocation")
        replace("import net.minecraft.util.Util;", "import net.minecraft.Util;")
    }
    replacements.string(current.parsed < "26.1") {
        replace("accessWidener\tv2\tofficial", "accessWidener\tv2\tnamed")
        replace("playSeededSound(Lnet/minecraft/world/entity/Entity;", "playSeededSound(Lnet/minecraft/world/entity/player/Player;")
        replace("@Nullable Entity soundSource", "net.minecraft.world.entity.player.@Nullable Player soundSource")
        replace("@Nullable Entity eventSource", "net.minecraft.world.entity.player.@Nullable Player eventSource")
        replace("ChunkPos.pack(", "ChunkPos.asLong(")
        replace(".chunkPosition().pack()", ".chunkPosition().toLong()")
    }
    replacements.string(current.parsed < "26.2") {
        replace("net.minecraft.advancements.triggers.", "net.minecraft.advancements.critereon.")
    }
}

repositories {
    mavenCentral()
}

spotless {
    java {
        target("src/*/java/**/*.java")
        googleJavaFormat(property("google_java_format_version").toString())
        toggleOffOnRegex("""(?s)(\{\*/\n[ \t]*/\*(?!\?).*?\*/\s*/\*\?\}[^\n]*\*/|/\*!stonecutter-disabled.*\*/\s*)""")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
