/*
 * This file is part of Matter Overdrive
 * Copyright (c) 2015., Simeon Radivoev, All rights reserved.
 *
 * Matter Overdrive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Matter Overdrive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Matter Overdrive.  If not, see <http://www.gnu.org/licenses>.
 */

package matteroverdrive.tile;

import cofh.lib.util.helpers.MathHelper;
import cpw.mods.fml.relauncher.Side;
import matteroverdrive.Reference;
import matteroverdrive.api.inventory.UpgradeTypes;
import matteroverdrive.api.matter.IRecyclable;
import matteroverdrive.data.Inventory;
import matteroverdrive.data.inventory.RemoveOnlySlot;
import matteroverdrive.data.inventory.SlotRecycler;
import matteroverdrive.machines.MachineNBTCategory;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.EnumSet;

/**
 * Created by Simeon on 5/15/2015.
 */
public class TileEntityMachineMatterRecycler extends MOTileEntityMachineEnergy {

    public static final int ENERGY_STORAGE = 512000;
    public int OUTPUT_SLOT_ID;
    public int INPUT_SLOT_ID;
    public int recycleProgress;
    public int recycleTime;

    public static final int RECYCLE_SPEED_PER_MATTER = 80;
    public static final int RECYCLE_ENERGY_PER_MATTER = 1000;

    public TileEntityMachineMatterRecycler()
    {
        super(4);
        this.energyStorage.setCapacity(ENERGY_STORAGE);
        this.energyStorage.setMaxExtract(ENERGY_STORAGE);
        this.energyStorage.setMaxReceive(ENERGY_STORAGE);
        redstoneMode = Reference.MODE_REDSTONE_LOW;
    }

    @Override
    protected void RegisterSlots(Inventory inventory)
    {
        INPUT_SLOT_ID = inventory.AddSlot(new SlotRecycler(true));
        OUTPUT_SLOT_ID = inventory.AddSlot(new RemoveOnlySlot(false));
        super.RegisterSlots(inventory);
    }

    @Override
    protected void registerComponents() {

    }

    @Override
    public void updateEntity()
    {
        super.updateEntity();
        this.manageRecycle();
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbt, EnumSet<MachineNBTCategory> categories)
    {
        super.readCustomNBT(nbt, categories);
        if (categories.contains(MachineNBTCategory.DATA)) {
            this.recycleTime = nbt.getShort("RecycleTime");
        }
    }

    @Override
    protected void onAwake(Side side) {

    }

    @Override
    protected void onActiveChange()
    {
        ForceSync();
    }

    @Override
    public void writeCustomNBT(NBTTagCompound nbt, EnumSet<MachineNBTCategory> categories)
    {
        super.writeCustomNBT(nbt, categories);
        if (categories.contains(MachineNBTCategory.DATA)) {
            nbt.setShort("RecycleTime", (short) this.recycleTime);
        }
    }

    public void manageRecycle()
    {
        if(!worldObj.isRemote)
        {
            if (this.isRecycling())
            {
                if(this.energyStorage.getEnergyStored() >= getEnergyDrainPerTick())
                {
                    this.recycleTime++;
                    extractEnergy(ForgeDirection.DOWN, getEnergyDrainPerTick(), false);
                    recycleProgress = Math.round(((float) (recycleTime) / (float) getSpeed()) * 100);

                    if (this.recycleTime >= getSpeed())
                    {
                        this.recycleTime = 0;
                        this.recycleItem();
                    }
                }
            }
        }

        if (!this.isRecycling())
        {
            this.recycleTime = 0;
            recycleProgress = 0;
        }
    }

    public boolean isRecycling()
    {
        return getStackInSlot(INPUT_SLOT_ID) != null
                && getStackInSlot(INPUT_SLOT_ID).getItem() instanceof IRecyclable
                && ((IRecyclable) getStackInSlot(INPUT_SLOT_ID).getItem()).canRecycle(getStackInSlot(INPUT_SLOT_ID))
                && canPutInOutput()
                && ((IRecyclable) getStackInSlot(INPUT_SLOT_ID).getItem()).getRecycleMatter(getStackInSlot(INPUT_SLOT_ID)) > 0;
    }

    public int getEnergyDrainPerTick()
    {
        int maxEnergy = getEnergyDrainMax();
        return maxEnergy / getSpeed();
    }

    public int getEnergyDrainMax()
    {
        int matter = ((IRecyclable)getStackInSlot(INPUT_SLOT_ID).getItem()).getRecycleMatter(getStackInSlot(INPUT_SLOT_ID));
        double upgradeMultiply = getUpgradeMultiply(UpgradeTypes.PowerUsage);
        return MathHelper.round((matter * RECYCLE_ENERGY_PER_MATTER) * upgradeMultiply);
    }

    public int getSpeed()
    {
        double matter = Math.log1p(((IRecyclable) getStackInSlot(INPUT_SLOT_ID).getItem()).getRecycleMatter(getStackInSlot(INPUT_SLOT_ID)));
        matter*=matter;
        if (matter > 0) {
            return MathHelper.round(RECYCLE_SPEED_PER_MATTER * matter * getUpgradeMultiply(UpgradeTypes.Speed));
        }else
        {
            return 1;
        }
    }

    private boolean canPutInOutput()
    {
        ItemStack stack = getStackInSlot(OUTPUT_SLOT_ID);
        ItemStack inputStack = getStackInSlot(INPUT_SLOT_ID);

        if(stack == null)
        {
            return true;
        }
        else if (inputStack != null && inputStack.getItem() instanceof IRecyclable)
        {
            ItemStack outputStack = ((IRecyclable)inputStack.getItem()).getOutput(inputStack);
            if(outputStack != null && stack.isItemEqual(outputStack) && stack.stackSize + outputStack.stackSize < stack.getMaxStackSize())
            {
                return true;
            }
        }

        return false;
    }

    public void recycleItem()
    {
        if (getStackInSlot(INPUT_SLOT_ID) != null && canPutInOutput())
        {
            ItemStack outputStack = ((IRecyclable)getStackInSlot(INPUT_SLOT_ID).getItem()).getOutput(getStackInSlot(INPUT_SLOT_ID));
            ItemStack stackInOutput = getStackInSlot(OUTPUT_SLOT_ID);

            if (stackInOutput == null)
            {
                setInventorySlotContents(OUTPUT_SLOT_ID,outputStack);
            }else
            {
                stackInOutput.stackSize++;
            }

            decrStackSize(INPUT_SLOT_ID,1);
            ForceSync();
        }
    }

    @Override
    public String getSound() {
        return "machine";
    }

    @Override
    public boolean hasSound() {
        return true;
    }

    @Override
    public boolean isActive() {
        return isRecycling() && this.energyStorage.getEnergyStored() >= getEnergyDrainPerTick();
    }

    @Override
    public float soundVolume() {
        return 1;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side)
    {
        return new int[]{INPUT_SLOT_ID,OUTPUT_SLOT_ID};
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack item, int side)
    {
        return slot != OUTPUT_SLOT_ID && super.canInsertItem(slot,item,side);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack item, int side)
    {
        return slot == OUTPUT_SLOT_ID;
    }

    @Override
    public boolean isAffectedByUpgrade(UpgradeTypes type)
    {
        return type == UpgradeTypes.Speed || type == UpgradeTypes.PowerStorage || type == UpgradeTypes.PowerUsage;
    }

    @Override
    public void onAdded(World world, int x, int y, int z) {

    }

    @Override
    public void onPlaced(World world, EntityLivingBase entityLiving) {

    }

    @Override
    public void onDestroyed() {

    }
}
