package dev.denismasterherobrine.arenaworldmanager.util;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class VoidGenerator extends ChunkGenerator {
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {}

    @Override
    public boolean shouldGenerateStructures() { return false; }

    @Override
    public boolean shouldGenerateMobs() { return false; }
}
