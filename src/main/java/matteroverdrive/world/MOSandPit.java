package matteroverdrive.world;

import matteroverdrive.Reference;
import matteroverdrive.init.MatterOverdriveBlocks;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Random;

public class MOSandPit extends MOWorldGenBuilding {
    private static final int MIN_DISTANCE_APART = 512;
    private int airLeeway;

    public MOSandPit(String name, int airLeeway) {
        super(name, new ResourceLocation(Reference.PATH_WORLD_TEXTURES + "sand_pit.png"), 24, 24);
        setMaxDistanceToAir(airLeeway);
        setyOffset(-9);
        validSpawnBlocks = new Block[]{Blocks.sand};
        this.airLeeway = airLeeway;
        this.name = name;
        addMapping(0xe1db35, Blocks.sandstone);
        addMapping(0xf1f1f1, Blocks.air);
        addMapping(0xffff00, Blocks.sand);
        addMapping(0xc735e1, Blocks.glowstone);
        addMapping(0x35a2e1, Blocks.water);
        addMapping(0x359ae1, MatterOverdriveBlocks.decorative_tritanium_plate);
        addMapping(0xff8400, MatterOverdriveBlocks.decorative_coils);
        addMapping(0x6b4400, Blocks.fence);
    }

    public boolean isFlat(World world, int x, int y, int z) {
        int y10 = world.getHeightValue(x + layerWidth, z);
        int y11 = world.getHeightValue(x + layerWidth, z + layerHeight);
        int y01 = world.getHeightValue(x, z + layerHeight);
        if (Math.abs(y - y10) <= airLeeway && Math.abs(y - y11) <= airLeeway && Math.abs(y - y01) <= airLeeway) {
            return blockBelowMatches(airLeeway, world, Blocks.sand, x, y, z) && blockBelowMatches(airLeeway, world, Blocks.sand, x + layerWidth, y, z) && blockBelowMatches(airLeeway, world, Blocks.sand, x, y, z + layerHeight) && blockBelowMatches(airLeeway, world, Blocks.sand, x + layerWidth, y, z + layerHeight);
        }
        return false;
    }

    private boolean blockBelowMatches(int airLeeway, World world, Block block, int x, int y, int z) {
        for (int i = 0; i < airLeeway; i++) {
            if (world.getBlock(x, y - i, z) == block) {
                return true;
            }
        }
        return false;
    }

    private boolean isPointOnSurface(World world, int x, int y, int z) {
        return world.getBlock(x, y + 1, z) == Blocks.air;
    }

    @Override
    public void placeBlock(World world, int color, int x, int y, int z, int layer, Random random, int placeNotify) {
        if ((color & 0xffffff) == 0xc735e1) {
            Block block = getBlockFromColor(color, random);
            int meta = getMetaFromColor(color, random);
            if (block != null) {
                world.setBlock(x, y, z, block, meta, 3);
                onBlockPlace(world, block, x, y, z, random, color);
            }
        } else {
            super.placeBlock(world, color, x, y, z, layer, random, placeNotify);
        }
    }

    @Override
    public void onBlockPlace(World world, Block block, int x, int y, int z, Random random, int color) {

    }

    @Override
    protected void onGeneration(Random random, World world, int x, int y, int z) {

    }

    @Override
    protected boolean shouldGenerate(Random random, World world, int x, int y, int z) {
        return world.getBiomeGenForCoords(x, y) == BiomeGenBase.desert && isFarEnoughFromOthers(world, x, z, MIN_DISTANCE_APART);
    }

    @Override
    public void onGenerationWorkerCreated(WorldGenBuildingWorker worldGenBuildingWorker) {

    }
}
