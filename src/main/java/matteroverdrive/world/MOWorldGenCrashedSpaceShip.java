package matteroverdrive.world;

import matteroverdrive.MatterOverdrive;
import matteroverdrive.Reference;
import matteroverdrive.api.quest.QuestStack;
import matteroverdrive.blocks.BlockTritaniumCrate;
import matteroverdrive.blocks.BlockWeaponStation;
import matteroverdrive.init.MatterOverdriveBlocks;
import matteroverdrive.init.MatterOverdriveQuests;
import matteroverdrive.tile.TileEntityHoloSign;
import matteroverdrive.tile.TileEntityWeaponStation;
import matteroverdrive.util.MOInventoryHelper;
import matteroverdrive.util.WeaponFactory;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Random;

public class MOWorldGenCrashedSpaceShip extends MOWorldGenBuilding {
    private static final int MIN_DISTANCE_APART = 512;
    private String[] holoTexts;

    public MOWorldGenCrashedSpaceShip(String name) {
        super(name, new ResourceLocation(Reference.PATH_WORLD_TEXTURES + "crashed_space_ship.png"), 11, 35);
        holoTexts = new String[]{"Critical\nError", "Contacting\nSection 9", "System\nFailure", "Emergency\nPower\nOffline", "System\nReboot\nFailure", "Help Me", "I Need\nWater"};
        setyOffset(-1);
        addMapping(0x38c8df, MatterOverdriveBlocks.decorative_clean);
        addMapping(0x187b8b, MatterOverdriveBlocks.decorative_vent_bright);
        addMapping(0xaa38df, MatterOverdriveBlocks.forceGlass);
        addMapping(0x00ff78, Blocks.grass);
        addMapping(0xd8ff00, MatterOverdriveBlocks.holoSign);
        addMapping(0xaccb00, MatterOverdriveBlocks.holoSign);
        addMapping(0x3896df, MatterOverdriveBlocks.decorative_tritanium_plate);
        addMapping(0xdfd938, MatterOverdriveBlocks.decorative_tritanium_plate_stripe);
        addMapping(0x5d89ab, MatterOverdriveBlocks.decorative_holo_matrix);
        addMapping(0x77147d, MatterOverdriveBlocks.weapon_station);
        addMapping(0xb04a90, MatterOverdriveBlocks.tritaniumCrate);
        addMapping(0x94deea, MatterOverdriveBlocks.decorative_separator);
        addMapping(0xff9c00, MatterOverdriveBlocks.decorative_coils);
        addMapping(0xaca847, MatterOverdriveBlocks.decorative_matter_tube);
        addMapping(0x0c3b60, MatterOverdriveBlocks.decorative_carbon_fiber_plate);
        addMapping(0xc5ced0, Blocks.air);
    }

    @Override
    public void onBlockPlace(World world, Block block, int x, int y, int z, Random random, int color) {
        if (block == MatterOverdriveBlocks.holoSign) {
            if (colorsMatch(color, 0xd8ff00)) {
                world.setBlockMetadataWithNotify(x, y, z, ForgeDirection.EAST.ordinal(), 3);
            } else if (colorsMatch(color, 0xaccb00)) {
                world.setBlockMetadataWithNotify(x, y, z, ForgeDirection.WEST.ordinal(), 3);
            }
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TileEntityHoloSign) {
                if (random.nextInt(100) < 30) {
                    ((TileEntityHoloSign) tileEntity).setText(holoTexts[random.nextInt(holoTexts.length)]);
                }
            }
        } else if (block instanceof BlockTritaniumCrate) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof IInventory) {
                WeightedRandomChestContent.generateChestContents(random, ChestGenHooks.getInfo(Reference.CHEST_GEN_ANDROID_HOUSE).getItems(random), (IInventory) tileEntity, random.nextInt(10) + 10);
                QuestStack questStack = MatterOverdrive.questFactory.generateQuestStack(random, MatterOverdriveQuests.crashLanding);
                questStack.getTagCompound().setIntArray("Pos", new int[]{x, y, z});
                MOInventoryHelper.insertItemStackIntoInventory((IInventory) tileEntity, questStack.getContract(), 0);
            }

        } else if (block instanceof BlockWeaponStation) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TileEntityWeaponStation) {
                if (random.nextInt(200) < 10) {
                    ((TileEntityWeaponStation) tileEntity).setInventorySlotContents(((TileEntityWeaponStation) tileEntity).INPUT_SLOT, MatterOverdrive.weaponFactory.getRandomDecoratedEnergyWeapon(new WeaponFactory.WeaponGenerationContext(3, null, true)));
                }
            }
        }
    }

    @Override
    protected void onGeneration(Random random, World world, int x, int y, int z) {

    }

    @Override
    protected boolean shouldGenerate(Random random, World world, int x, int y, int z) {
        return world.provider.dimensionId == 0 && isFarEnoughFromOthers(world, x, z, MIN_DISTANCE_APART);
    }

    @Override
    public void onGenerationWorkerCreated(WorldGenBuildingWorker worldGenBuildingWorker) {

    }
}
