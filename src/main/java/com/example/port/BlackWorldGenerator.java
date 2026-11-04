package com.example.port;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Every chunk is a flat black concrete floor, repeating forever in every horizontal direction.
 * No ceiling, no mobs, no structures.
 */
public final class BlackWorldGenerator extends ChunkGenerator {

    public static final int FLOOR_Y = 64;

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        data.setRegion(0, FLOOR_Y, 0, 16, FLOOR_Y + 1, 16, Material.BLACK_CONCRETE);
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, FLOOR_Y + 1, 0.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        // Nether Wastes has a nearly black fog color, unlike the void biome's light blue.
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.NETHER_WASTES;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.NETHER_WASTES);
            }
        };
    }
}
