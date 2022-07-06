package matteroverdrive.world;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.*;

public abstract class MOImageGen {
    public static HashMap<Block, Integer> worldGenerationBlockColors = new HashMap<>();
    private HashMap<Integer, BlockMapping> blockMap;
    protected ResourceLocation texture;
    private List<int[][]> layers;
    private int textureWidth;
    private int textureHeight;
    private int layerCount;
    protected int placeNotify;
    protected final int layerWidth;
    protected final int layerHeight;
    protected final Random localRandom;

    public MOImageGen(ResourceLocation texture, int layerWidth, int layerHeight) {
        localRandom = new Random();
        blockMap = new HashMap<>();
        this.layerWidth = layerWidth;
        this.layerHeight = layerHeight;
        setTexture(texture);
    }

    public void placeBlock(World world, int color, int x, int y, int z, int layer, Random random, int placeNotify) {
        Block block = getBlockFromColor(color, random);
        Block preBlock = world.getBlock(x, y, z);
        int meta = getMetaFromColor(color, random);
        String unname = preBlock.getUnlocalizedName();
        //warn("%s", unname);
        if (block != null
            && preBlock.getBlockHardness(world, x, y, z) != -1.0F
            && !unname.equalsIgnoreCase("tile.ModelledChromaticTile3")
            && !unname.equalsIgnoreCase("tile.chroma.loot")) {
            world.setBlock(x, y, z, block, meta, placeNotify);
            onBlockPlace(world, block, x, y, z, random, color);
        }
    }

    public abstract void onBlockPlace(World world, Block block, int x, int y, int z, Random random, int color);

    public Block getBlockFromColor(int color, Random random) {
        BlockMapping blockMapping = blockMap.get(color & 0xffffff);
        if (blockMapping != null) {
            return blockMapping.getBlock(random);
        }
        return null;
    }

    public int getMetaFromColor(int color, Random random) {
        return 0;
    }

    public void generateFromImage(World world, Random random, int startX, int startY, int startZ, int layer, int placeNotify) {
        if (layers != null && layers.size() > 0) {
            for (BlockMapping blockMapping : blockMap.values()) {
                blockMapping.reset(localRandom);
            }
            generateFromImage(world, random, startX, Math.min(startY, world.getHeight() - layerCount), startZ, layers, layer, placeNotify);
        }
    }

    public void generateFromImage(World world, Random random, int startX, int startY, int startZ, List<int[][]> layers, int layer, int placeNotify) {
        for (int x = 0; x < layerWidth; x++) {
            for (int z = 0; z < layerHeight; z++) {

                placeBlock(world, layers.get(layer)[x][z], startX + x, startY + layer, startZ + z, layer, random, placeNotify);
            }
        }
    }

    public static void generateFromImage(World world, int startX, int startY, int startZ, int layerWidth, int layerHeight, List<int[][]> layers, Map<Integer, Block> blockMap) {
        for (int layer = 0; layer < layers.size(); layer++) {
            for (int x = 0; x < layerWidth; x++) {
                for (int z = 0; z < layerHeight; z++) {

                    int color = layers.get(layer)[x][z];
                    Color c = new Color(color, true);
                    int alpha = c.getAlpha();
                    Block block = blockMap.get(color & 0xffffff);
                    int meta = 255 - alpha;
                    if (block != null) {
                        world.setBlock(startX + x, startY + layer, startZ + z, block, meta, 2);
                    }
                }
            }
        }
    }

    public boolean isOnSolidGround(World world, int x, int y, int z, int leaway) {
        return isPointOnSolidGround(world, x, y, z, leaway) && isPointOnSolidGround(world, x + layerWidth, y, z, leaway) && isPointOnSolidGround(world, x + layerWidth, y, z + layerHeight, leaway) && isPointOnSolidGround(world, x, y, z + layerHeight, leaway);
    }

    public boolean isPointOnSolidGround(World world, int x, int y, int z, int leaway) {
        for (int i = 0; i < leaway; i++) {
            if (isBlockSolid(world, x, y - i, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean canFit(World world, int x, int y, int z) {
        return !isBlockSolid(world, x, y + layerCount, z) && !isBlockSolid(world, x + layerWidth, y + layerCount, z) && !isBlockSolid(world, x + layerWidth, y + layerCount, z + layerHeight) && !isBlockSolid(world, x, y + layerCount, z + layerHeight);
    }

    public boolean isBlockSolid(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block == Blocks.log || block == Blocks.log2 && block == Blocks.leaves2 || block == Blocks.leaves) {
            return false;
        }
        return block.isBlockSolid(world, x, y, z, ForgeDirection.UP.ordinal());
    }

    private boolean inAirFloatRange(World world, int x, int y, int z, int maxAirRange) {
        for (int i = 0; i < maxAirRange; i++) {
            if (isBlockSolid(world, x, y - i, z) && !isBlockSolid(world, x, y - i + 1, z)) {
                return true;
            }
        }
        return false;
    }

    protected boolean colorsMatch(int color0, int color1) {
        return (color0 & 0xffffff) == (color1 & 0xffffff);
    }

    public void manageTextureLoading() {
        if (layers == null || layers.size() == 0) {
            loadTexture(getTexture());
        }
    }

    private void loadTexture(ResourceLocation textureLocation) throws RuntimeException {
        try {

            String path = "/assets/" + textureLocation.getResourceDomain() + "/" + textureLocation.getResourcePath();
            InputStream imageStream = getClass().getResourceAsStream(path);
            BufferedImage image = ImageIO.read(imageStream);

            textureWidth = image.getWidth();
            textureHeight = image.getHeight();
            layerCount = (image.getWidth() / layerWidth) * (image.getHeight() / layerHeight);
            for (int i = 0; i < layerCount; i++) {
                layers.add(new int[layerWidth][layerHeight]);
            }
            convertTo2DWithoutUsingGetRGB(image, layerWidth, layerHeight, textureWidth, layers);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<int[][]> loadTexture(File textureLocation, int layerWidth, int layerHeight) {
        try {
            BufferedImage image = ImageIO.read(textureLocation);

            int textureWidth = image.getWidth();
            int textureHeight = image.getHeight();
            int layerCount = (image.getWidth() / layerWidth) * (image.getHeight() / layerHeight);
            List<int[][]> layers = new ArrayList<>();
            for (int i = 0; i < layerCount; i++) {
                layers.add(new int[layerWidth][layerHeight]);
            }
            convertTo2DWithoutUsingGetRGB(image, layerWidth, layerHeight, textureWidth, layers);
            return layers;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void convertTo2DWithoutUsingGetRGB(BufferedImage image, int layerWidth, int layerHeight, int textureWidth, List<int[][]> layers) {

        final byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        final boolean hasAlphaChannel = image.getAlphaRaster() != null;

        if (hasAlphaChannel) {
            final int pixelLength = 4;
            for (int pixel = 0, row = 0, col = 0; pixel < pixels.length; pixel += pixelLength) {
                int argb = 0;
                argb += (((int) pixels[pixel] & 0xff) << 24); // alpha
                argb += ((int) pixels[pixel + 1] & 0xff); // blue
                argb += (((int) pixels[pixel + 2] & 0xff) << 8); // green
                argb += (((int) pixels[pixel + 3] & 0xff) << 16); // red
                int layerIndex = Math.floorDiv(col, layerWidth) + ((textureWidth / layerWidth) * (Math.floorDiv(row, layerHeight)));
                layers.get(layerIndex)[col % layerWidth][row % layerHeight] = argb;
                col++;
                if (col == textureWidth) {
                    col = 0;
                    row++;
                }
            }
        } else {
            final int pixelLength = 3;
            for (int pixel = 0, row = 0, col = 0; pixel < pixels.length; pixel += pixelLength) {
                int argb = 0;
                argb += -16777216; // 255 alpha
                argb += ((int) pixels[pixel] & 0xff); // blue
                argb += (((int) pixels[pixel + 1] & 0xff) << 8); // green
                argb += (((int) pixels[pixel + 2] & 0xff) << 16); // red
                int layerIndex = Math.floorDiv(col, layerWidth) + ((textureWidth / layerWidth) * (Math.floorDiv(row, layerHeight)));
                layers.get(layerIndex)[col % layerWidth][row % layerHeight] = argb;
                col++;
                if (col == textureWidth) {
                    col = 0;
                    row++;
                }
            }
        }
    }

    private static void transpose(int[][] m) {
        for (int i = 0; i < m.length; i++) {
            for (int j = i; j < m[0].length; j++) {
                int x = m[i][j];
                m[i][j] = m[j][i];
                m[j][i] = x;
            }
        }
    }

    public static void swapRows(int[][] m) {
        for (int i = 0, k = m.length - 1; i < k; ++i, --k) {
            int[] x = m[i];
            m[i] = m[k];
            m[k] = x;
        }
    }

    public static void rotateByNinetyToLeft(int[][] m) {
        transpose(m);
        swapRows(m);
    }

    public static void rotateByNinetyToRight(int[][] m) {
        swapRows(m);
        transpose(m);
    }

    public void rotateByNinetyToLeft() {
        layers.forEach(MOImageGen::rotateByNinetyToLeft);
    }

    public int getRedFromColor(int color) {
        return color >> 16 & 255;
    }

    public int getGreenFromColor(int color) {
        return color >> 8 & 255;
    }

    public int getBlueFromColor(int color) {
        return color >> 0 & 255;
    }

    public int getAlphaFromColor(int color) {
        return color >> 24 & 255;
    }

    public int getColorAt(int x, int y, int layer) {
        if (x < textureWidth && y < textureHeight) {
            return layers.get(layer)[textureHeight][textureWidth];
        }
        return 0;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public int getLayerCount() {
        return layerCount;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public void addMapping(int color, Block... blocks) {
        this.addMapping(color, new BlockMapping(blocks));
    }

    public void addMapping(int color, boolean noise, Block... blocks) {
        this.addMapping(color, new BlockMapping(noise, blocks));
    }

    public void addMapping(int color, BlockMapping blockMapping) {
        blockMap.put(color, blockMapping);
    }

    public BlockMapping getMapping(int color) {
        return blockMap.get(color);
    }

    public void setTexture(ResourceLocation textureLocation) {
        this.texture = textureLocation;
        if (layers == null)
            layers = new ArrayList<>();
        else
            layers.clear();
    }

    public static class BlockMapping {
        private Block[] blocks;
        private boolean noise;
        private int lastSelected;

        public BlockMapping(boolean noise, Block... blocks) {
            this.blocks = blocks;
            this.noise = noise;
        }

        public BlockMapping(Block... blocks) {
            this.blocks = blocks;
        }

        public void reset(Random random) {
            if (!noise) {
                lastSelected = random.nextInt(blocks.length);
            }
        }

        public Block getBlock(Random random) {
            if (noise) {
                return blocks[random.nextInt(blocks.length)];
            } else {
                return blocks[lastSelected];
            }
        }

        public Block[] getBlocks() {
            return blocks;
        }

        public boolean isNoise() {
            return noise;
        }
    }
}
