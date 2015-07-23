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

package matteroverdrive.machines;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import matteroverdrive.MatterOverdrive;
import matteroverdrive.Reference;
import matteroverdrive.api.IUpgradeable;
import matteroverdrive.api.inventory.IUpgrade;
import matteroverdrive.api.inventory.UpgradeTypes;
import matteroverdrive.blocks.includes.MOBlock;
import matteroverdrive.blocks.includes.MOBlockMachine;
import matteroverdrive.client.sound.MachineSound;
import matteroverdrive.data.Inventory;
import matteroverdrive.data.TileEntityInventory;
import matteroverdrive.data.inventory.UpgradeSlot;
import matteroverdrive.fx.VentParticle;
import matteroverdrive.items.SecurityProtocol;
import matteroverdrive.network.packet.server.PacketSaveConfigs;
import matteroverdrive.tile.IMOTileEntity;
import matteroverdrive.tile.MOTileEntity;
import matteroverdrive.util.MatterHelper;
import matteroverdrive.util.math.MOMathHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;
import org.apache.logging.log4j.Level;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.util.*;

/**
 * Created by Simeon on 3/11/2015.
 */
public abstract class MOTileEntityMachine extends MOTileEntity implements IMOTileEntity, ISidedInventory, IUpgradeable
{

    protected static Random random = new Random();

    //client syncs
    protected boolean lastActive;

    @SideOnly(Side.CLIENT)
    protected MachineSound sound;

    protected ResourceLocation soundRes;
    protected boolean redstoneState;
    protected boolean redstoneStateDirty = true;
    protected byte redstoneMode;
    protected boolean forceClientUpdate;
    protected UUID owner;

    protected Inventory inventory;
    private int upgradeSlotCount;
    private int[] upgrade_slots;
    protected List<IMachineComponent> components;

    public MOTileEntityMachine(int upgradeCount)
    {
        components = new ArrayList<>();
        soundRes = getSoundFor(getSound());
        this.upgradeSlotCount = upgradeCount;
        upgrade_slots = new int[upgradeCount];
        inventory = new TileEntityInventory(this,"");
        registerComponents();
        RegisterSlots(inventory);
    }

    @Override
    public void updateEntity()
    {
        super.updateEntity();

        if(worldObj.isRemote)
        {
            manageSound();

            if(forceClientUpdate)
            {
                updateBlock();
                forceClientUpdate = false;
            }

            return;
        }

        manageRedstoneState();
        manageClientSync();

        if(lastActive != isActive())
        {
            onActiveChange();
            lastActive = isActive();
        }

        for (IMachineComponent component : components)
        {
            component.update(this);
        }
    }

    protected void RegisterSlots(Inventory inventory)
    {
        for (int i = 0;i < upgrade_slots.length; i++)
        {
            upgrade_slots[i] = inventory.AddSlot(new UpgradeSlot(false,this));
        }
        for (IMachineComponent component : components)
        {
            component.registerSlots(inventory);
        }
    }

    protected abstract void registerComponents();

    private static ResourceLocation getSoundFor(String sound)
    {
        return sound == null ? null : new ResourceLocation(Reference.MOD_ID + ":" + sound);
    }
    public abstract String getSound();
    public abstract boolean hasSound();
    public abstract boolean isActive();
    public abstract float soundVolume();
    public void onContainerOpen(Side side)
    {

    }
    public boolean getRedstoneActive()
    {
        if (redstoneMode == Reference.MODE_REDSTONE_HIGH)
        {
            return redstoneState;
        }else if (redstoneMode == Reference.MODE_REDSTONE_LOW)
        {
            return !redstoneState;
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    protected void manageSound()
    {
        if(hasSound())
        {
            if (isActive() && !isInvalid())
            {
                if (sound == null) {
                    float soundMultiply = 1;
                    if (getBlockType() instanceof MOBlockMachine)
                    {
                        soundMultiply = ((MOBlockMachine) getBlockType()).volume;
                    }
                    sound = new MachineSound(soundRes, xCoord, yCoord, zCoord, soundVolume() * soundMultiply, 1);
                    FMLClientHandler.instance().getClient().getSoundHandler().playSound(sound);
                }
                else if (FMLClientHandler.instance().getClient().getSoundHandler().isSoundPlaying(sound))
                {
                    sound.setVolume(soundVolume());
                }
                else
                {
                    sound.startPlaying();
                }
            }
            else if (sound != null)
            {
                stopSound();
            }
        }
    }

    @SideOnly(Side.CLIENT)
    void stopSound()
    {
        if (sound != null)
        {
            sound.stopPlaying();
            FMLClientHandler.instance().getClient().getSoundHandler().stopSound(sound);
            sound = null;
        }
    }

    @Override
    public void onChunkUnload()
    {
        super.onChunkUnload();

        if (worldObj.isRemote)
        {
            stopSound();
        }
    }

    @Override
    public void readCustomNBT(NBTTagCompound nbt,EnumSet<MachineNBTCategory> categories)
    {
        if (categories.contains(MachineNBTCategory.CONFIGS)) {
            redstoneMode = nbt.getByte("redstoneMode");
        }
        if (categories.contains(MachineNBTCategory.DATA))
        {
            redstoneState = nbt.getBoolean("redstoneState");
            forceClientUpdate = nbt.getBoolean("forceClientUpdate");
            if (nbt.hasKey("Owner", 8) && !nbt.getString("Owner").isEmpty()) {
                try {
                    owner = UUID.fromString(nbt.getString("Owner"));
                } catch (Exception e) {
                    FMLLog.log(Level.ERROR, "Invalid Owner ID: " + nbt.getString("Owner"));
                }
            }
        }
        if (categories.contains(MachineNBTCategory.INVENTORY))
        {
            inventory.readFromNBT(nbt);
        }
        for (IMachineComponent component : components)
        {
            component.readFromNBT(nbt, categories);
        }
    }

    @Override
    public void  writeCustomNBT(NBTTagCompound nbt,EnumSet<MachineNBTCategory> categories)
    {
        if (categories.contains(MachineNBTCategory.CONFIGS))
        {
            nbt.setByte("redstoneMode", redstoneMode);
        }
        if (categories.contains(MachineNBTCategory.DATA))
        {
            nbt.setBoolean("forceClientUpdate", forceClientUpdate);
            nbt.setBoolean("redstoneState", redstoneState);
            if (owner != null)
                nbt.setString("Owner", owner.toString());
            else if (nbt.hasKey("Owner", 6)) {
                nbt.removeTag("Owner");
            }
        }
        forceClientUpdate = false;
        if (categories.contains(MachineNBTCategory.INVENTORY))
        {
            inventory.writeToNBT(nbt);
        }
        for (IMachineComponent component : components)
        {
            component.writeToNBT(nbt,categories);
        }
    }

    @Override
    public void writeToDropItem(ItemStack itemStack)
    {
        if (!itemStack.hasTagCompound())
            itemStack.setTagCompound(new NBTTagCompound());

        NBTTagCompound machineTag = new NBTTagCompound();
        NBTTagList itemTagList = new NBTTagList();
        for (int i = 0; i < getSizeInventory(); ++i)
        {
            if (inventory.getSlot(i).keepOnDismatle() && inventory.getStackInSlot(i) != null)
            {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte)i);
                getStackInSlot(i).writeToNBT(itemTag);
                itemTagList.appendTag(itemTag);
            }
        }
        machineTag.setTag("Items", itemTagList);
        writeCustomNBT(machineTag, EnumSet.of(MachineNBTCategory.CONFIGS, MachineNBTCategory.DATA));
        machineTag.setByte("redstoneMode", redstoneMode);
        if (hasOwner())
            machineTag.setString("Owner", owner.toString());

        itemStack.getTagCompound().setTag("Machine", machineTag);
    }

    @Override
    public void readFromPlaceItem(ItemStack itemStack)
    {
        if (itemStack.hasTagCompound())
        {
            NBTTagCompound machineTag = itemStack.getTagCompound().getCompoundTag("Machine");
            NBTTagList itemTagList = machineTag.getTagList("Items", 10);
            for (int i = 0; i < itemTagList.tagCount(); ++i)
            {
                NBTTagCompound itemTag = itemTagList.getCompoundTagAt(i);
                byte b0 = itemTag.getByte("Slot");
                inventory.setInventorySlotContents(b0, ItemStack.loadItemStackFromNBT(itemTag));
            }
            readCustomNBT(machineTag, EnumSet.of(MachineNBTCategory.CONFIGS, MachineNBTCategory.DATA));
            this.redstoneMode = machineTag.getByte("redstoneMode");
            if (machineTag.hasKey("Owner", 8)) {
                try {
                    this.owner = UUID.fromString(machineTag.getString("Owner"));
                }catch (Exception e)
                {
                    FMLLog.log(Level.ERROR,e,"Invalid Owner ID: " + machineTag.getString("Owner"));
                }
            }
        }
    }

    @Override
    public Packet getDescriptionPacket()
    {
        //System.out.println("Sending Packet To Client");
        NBTTagCompound syncData = new NBTTagCompound();
        writeCustomNBT(syncData, MachineNBTCategory.ALL_OPTS);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, syncData);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt)
    {
        //System.out.println("Receiving Packet From Server");
        NBTTagCompound syncData = pkt.func_148857_g();
        if(syncData != null)
        {
            readCustomNBT(syncData, MachineNBTCategory.ALL_OPTS);
        }
    }

    protected void manageRedstoneState()
    {
        if(redstoneStateDirty)
        {
            boolean flag = redstoneState;
            redstoneState = worldObj.getBlockPowerInput(xCoord,yCoord,zCoord) > 0;
            redstoneStateDirty = false;
            if(flag != redstoneState)
                forceClientUpdate = true;

        }
    }

    protected void manageClientSync()
    {
        if(forceClientUpdate)
        {
            updateBlock();
            markDirty();
        }
    }

    @Override
    public void invalidate()
    {
        super.invalidate();
        if(worldObj.isRemote)
        {
            manageSound();
        }
    }

    protected abstract void onActiveChange();

    @Override
    public void onNeighborBlockChange()
    {
        redstoneStateDirty = true;
    }

    //region Inventory Methods
    public boolean isItemValidForSlot(int slot, ItemStack item)
    {
        return getInventory() != null && getInventory().isItemValidForSlot(slot, item);
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side)
    {
        return new int[0];
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack item, int side)
    {
        return isItemValidForSlot(slot, item);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack item, int side)
    {
        return false;
    }

    @Override
    public int getSizeInventory()
    {
        if (getInventory() != null)
            return getInventory().getSizeInventory();
        else
            return 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot)
    {
        if (getInventory() != null)
            return getInventory().getStackInSlot(slot);
        else
            return null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int size)
    {
        if (getInventory() != null)
            return getInventory().decrStackSize(slot, size);
        else
            return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot)
    {
        if (getInventory() != null)
            return getInventory().getStackInSlotOnClosing(slot);
        else
            return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack itemStack)
    {
        if (getInventory() != null)
            getInventory().setInventorySlotContents(slot, itemStack);
    }

    @Override
    public String getInventoryName()
    {
        if (getInventory() != null)
            return getInventory().getInventoryName();
        else
            return "";
    }

    @Override
    public boolean hasCustomInventoryName()
    {
        return getInventory() != null && getInventory().hasCustomInventoryName();
    }

    @Override
    public int getInventoryStackLimit()
    {
        if (getInventory() != null)
            return getInventory().getInventoryStackLimit();
        else
            return 0;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player)
    {
        if (hasOwner())
        {
            if (player.getGameProfile().getId().equals(owner) || player.capabilities.isCreativeMode)
            {
                return true;
            }else
            {
                for (int i = 0;i < player.inventory.getSizeInventory();i++)
                {
                    ItemStack itemStack = player.inventory.getStackInSlot(i);
                    if (itemStack != null && itemStack.getItem() instanceof SecurityProtocol)
                    {
                        if (itemStack.hasTagCompound() && itemStack.getItemDamage() == 2 &&  UUID.fromString(itemStack.getTagCompound().getString("Owner")).equals(owner))
                        {
                            return true;
                        }
                    }
                }
            }
        }else
        {
            return true;
        }

        return false;
    }

    @Override
    public void openInventory()
    {
        System.out.println("Inventory Open");
    }

    @Override
    public void closeInventory() {

    }

    public IInventory getInventory()
    {
        return inventory;
    }

    public Inventory getInventoryContainer()
    {
        return inventory;
    }
    //endregion

    public void ForceSync()
    {
        forceClientUpdate = true;
    }

    public void sendConfigsToServer()
    {
        MatterOverdrive.packetPipeline.sendToServer(new PacketSaveConfigs(this));
    }

    //region Upgrades
    public double getUpgradeMultiply(UpgradeTypes type)
    {
        double multiply = 1;

        //check to see if the machine is affected by this type of Update
        if (isAffectedByUpgrade(type)) {
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                if (inventory.getSlot(i) instanceof UpgradeSlot) {
                    ItemStack upgradeItem = inventory.getStackInSlot(i);
                    if (upgradeItem != null && MatterHelper.isUpgrade(upgradeItem)) {
                        Map<UpgradeTypes, Double> upgrades = ((IUpgrade) upgradeItem.getItem()).getUpgrades(upgradeItem);

                        if (upgrades.containsKey(type)) {
                            multiply *= upgrades.get(type);
                        }
                    }
                }
            }
        }

        return multiply;
    }
    //endregion

    @SideOnly(Side.CLIENT)
    protected void SpawnVentParticles(float speed,ForgeDirection side,int count)
    {
        for (int i = 0;i < count;i++)
        {
            Matrix4f rotation = new Matrix4f();
            Vector3f offset = new Vector3f();

            if (side == ForgeDirection.UP)
            {
                rotation.rotate((float) Math.PI / 2f, new Vector3f(0, 0, 1));
                offset = new Vector3f(0.5f,0.7f,0.5f);
            }
            else if (side == ForgeDirection.WEST)
            {
                rotation.rotate((float) Math.PI / 2f, new Vector3f(0, 0, 1));
                offset = new Vector3f(-0.2f,0.5f,0.5f);
            }
            else if (side == ForgeDirection.EAST)
            {
                rotation.rotate((float) Math.PI / 2f, new Vector3f(0, 0, -1));
                offset = new Vector3f(1.2f,0.5f,0.5f);
            }
            else if (side == ForgeDirection.SOUTH)
            {
                rotation.rotate((float) Math.PI / 2f, new Vector3f(1, 0, 0));
                offset = new Vector3f(0.5f,0.5f,1.2f);
            }
            else if (side == ForgeDirection.NORTH)
            {
                rotation.rotate((float) Math.PI / 2f, new Vector3f(-1, 0, 0));
                offset = new Vector3f(0.5f,0.5f,-0.2f);
            }


            Vector3f circle = MOMathHelper.randomCirclePoint(random.nextFloat(), random);
            circle.scale(0.4f);
            Vector4f circleTransformed = new Vector4f(circle.x,circle.y,circle.z,1);
            Matrix4f.transform(rotation, circleTransformed, circleTransformed);

            float scale = 3f;

            VentParticle ventParticle = new VentParticle(this.worldObj,this.xCoord + offset.x + circleTransformed.x,this.yCoord + offset.y + circleTransformed.y,this.zCoord + offset.z + circleTransformed.z,side.offsetX * speed,side.offsetY * speed,side.offsetZ * speed,scale);
            ventParticle.setAlphaF(0.05f);
            Minecraft.getMinecraft().effectRenderer.addEffect(ventParticle);
        }
    }

    //region Getters and settrs
    public <T extends MOBlock> T getBlockType(Class<T> type)
    {
        if (this.blockType == null)
        {
            this.blockType = this.worldObj.getBlock(this.xCoord, this.yCoord, this.zCoord);
        }
        if (type.isInstance(this.blockType))
        {
            return type.cast(this.blockType);
        }
        return null;
    }
    public byte getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(byte redstoneMode) {
        this.redstoneMode = redstoneMode;
    }

    public UUID getOwner()
    {
        return owner;
    }

    public boolean hasOwner()
    {
        return owner != null;
    }

    public boolean claim(ItemStack security_protocol)
    {
        try {
            if (owner == null) {
                if (security_protocol.hasTagCompound() && security_protocol.getTagCompound().hasKey("Owner", 8)) {
                    owner = UUID.fromString(security_protocol.getTagCompound().getString("Owner"));
                    ForceSync();
                    return true;
                }
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }

    public boolean unclaim(ItemStack security_protocol)
    {
        try {
            if (owner != null) {
                if (security_protocol.hasTagCompound() && security_protocol.getTagCompound().hasKey("Owner", 8) && owner.equals(UUID.fromString(security_protocol.getTagCompound().getString("Owner")))) {
                    owner = null;
                    ForceSync();
                    return true;
                }
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }

    public void addComponent(IMachineComponent component)
    {
        components.add(component);
    }
    public boolean removeComponent(IMachineComponent component)
    {
        return components.remove(component);
    }
    public IMachineComponent removeComponent(int index)
    {
        return components.remove(index);
    }
    public IMachineComponent getComponent(int index)
    {
        return components.get(index);
    }
    public <T extends IMachineComponent> T getComponent(Class<T> componentClasss)
    {
        for (IMachineComponent component : components)
        {
            if (componentClasss.isInstance(component))
            {
                return componentClasss.cast(component);
            }
        }
        return null;
    }
    //endregion
}
