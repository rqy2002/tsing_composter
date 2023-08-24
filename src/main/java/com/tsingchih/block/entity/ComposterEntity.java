package com.tsingchih.block.entity;

import com.tsingchih.TsingComposter;
import com.tsingchih.inventory.ComposterMenu;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class ComposterEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    protected static final int SLOT_INPUT = 0;
    protected static final int SLOT_RESULT = 1;
    private static final int[] SLOTS_FOR_DOWN = new int[]{ 1 };
    private static final int[] SLOTS_FOR_OTHER = new int[]{ 0 };
    public static final int DATA_COMPOST_TIME = 0;
    public static final int DATA_ENERGY_LEVEL = 1;
    public static final int DATA_COMPOST_LEVEL = 2;
    public static final int DATA_ENERGY_STORAGE = 3;
    public static final int NUM_DATA_VALUES = 4;
    public static final int[] COMPOST_SPEED = new int[]{ 1, 2, 4, 8 };
    public static final int[] ENERGY_COST = new int[]{ 0, 5, 20, 40 };
    public static final int COMPOST_DURATION = 8;
    public static final int COMPOST_MAX_LEVEL = 7;
    public static final int ENERGY_CAP = 8000;
    public final CustomEnergyStorage energyStorage;
    private LazyOptional<CustomEnergyStorage> energy;
    public static final String NAME = TsingComposter.COMPOSTER_BLOCK.get().getDescriptionId();
    protected NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    int compostTime;
    int energyLevel;
    int compostLevel;
    protected final ContainerData dataAccess = new ContainerData() {
        private final ComposterEntity entity = ComposterEntity.this;
        @Override
        public int get(int i) {
            return switch (i) {
                case DATA_COMPOST_TIME -> entity.compostTime;
                case DATA_ENERGY_LEVEL -> entity.energyLevel;
                case DATA_COMPOST_LEVEL -> entity.compostLevel;
                case DATA_ENERGY_STORAGE -> entity.energyStorage.getEnergyStored();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case DATA_COMPOST_TIME -> entity.compostTime = v;
                case DATA_ENERGY_LEVEL -> entity.setEnergyLevel(v);
                case DATA_COMPOST_LEVEL -> entity.compostLevel = v;
                case DATA_ENERGY_STORAGE -> entity.energyStorage.setEnergyStored(v);
            }
        }

        @Override
        public int getCount() {
            return NUM_DATA_VALUES;
        }
    };

    public ComposterEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        energyLevel = 0;
        this.energyStorage = new CustomEnergyStorage(this, ENERGY_CAP);
        this.energy = LazyOptional.of(() -> this.energyStorage);
    }

    public ComposterEntity(BlockPos pos, BlockState state) {
        this(TsingComposter.COMPOSTER_ENTITY.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return new TranslatableComponent(NAME);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inv) {
        return new ComposterMenu(containerId, inv, this, this.dataAccess, this.getBlockPos());
    }

    public static boolean canCompost(ItemStack stack) {
        return ComposterBlock.COMPOSTABLES.containsKey(stack.getItem());
    }

    public static float compostChance(ItemStack stack) {
        return ComposterBlock.COMPOSTABLES.getFloat(stack.getItem());
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
        this.compostTime = tag.getInt("CompostTime");
        this.energyLevel = tag.getInt("EnergyLevel");
        this.compostLevel = tag.getInt("CompostLevel");
        this.energyStorage.setEnergyStored(tag.getInt("Energy"));
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("CompostTime", this.compostTime);
        tag.putInt("EnergyLevel", this.energyLevel);
        tag.putInt("CompostLevel", this.compostLevel);
        tag.putInt("Energy", this.energyStorage.getEnergyStored());
        ContainerHelper.saveAllItems(tag, this.items);
    }

    public void setEnergyLevel(int level) {
        this.energyLevel = Math.min(3, Math.max(0, level));
    }

    private int compostTick() {
        energyLevel = Math.min(3, Math.max(0, energyLevel));
        if (energyStorage.getEnergyStored() < ENERGY_COST[energyLevel])
            return 1;
        else {
            energyStorage.extractEnergy(ENERGY_COST[energyLevel], false);
            return COMPOST_SPEED[energyLevel];
        }
    }

    private boolean canOutput() {
        ItemStack result = items.get(SLOT_RESULT);
        return (result.isEmpty() || result.is(Items.BONE_MEAL)) && result.getCount() < result.getMaxStackSize();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ComposterEntity entity) {
        boolean changed = false;
        ItemStack input = entity.items.get(SLOT_INPUT);
        ItemStack result = entity.items.get(SLOT_RESULT);

        if (entity.compostLevel >= COMPOST_MAX_LEVEL && entity.canOutput()) {
            entity.compostLevel = 0;
            if (result.isEmpty()) {
                entity.items.set(SLOT_RESULT, new ItemStack(Items.BONE_MEAL, 1));
            } else {
                result.grow(1);
            }
        }

        if (!input.isEmpty() && canCompost(input)) {
            if (entity.compostTime >= COMPOST_DURATION && entity.canOutput()) {
                input.shrink(1);
                float prob = compostChance(input);
                entity.compostTime = 0;
                if (level.getRandom().nextFloat() <= prob)
                    ++entity.compostLevel;
                changed = true;
            }

            if (!input.isEmpty() && entity.compostTime < COMPOST_DURATION) {
                changed = true;
                entity.compostTime = Math.min(COMPOST_DURATION, entity.compostTime + entity.compostTick());
            }
        } else {
            changed = true;
            entity.compostTime = 0;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public int[] getSlotsForFace(Direction direction) {
        if (direction == Direction.DOWN) {
            return SLOTS_FOR_DOWN;
        } else {
            return SLOTS_FOR_OTHER;
        }
    }

    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
        return this.canPlaceItem(slot, stack);
    }

    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return true;
    }

    public int getContainerSize() {
        return this.items.size();
    }

    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    public ItemStack removeItem(int slot, int num) {
        return ContainerHelper.removeItem(this.items, slot, num);
    }

    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    public void setItem(int slot, ItemStack stack) {
        ItemStack itemstack = this.items.get(slot);
        boolean flag = !stack.isEmpty() && stack.sameItem(itemstack) && ItemStack.tagMatches(stack, itemstack);
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        if (slot == SLOT_INPUT && !flag) {
            this.compostTime = 0;
            this.setChanged();
        }

    }

    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        } else {
            return player.distanceToSqr((double)this.worldPosition.getX() + 0.5D, (double)this.worldPosition.getY() + 0.5D, (double)this.worldPosition.getZ() + 0.5D) <= 64.0D;
        }
    }

    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == SLOT_RESULT) {
            return false;
        } else {
            return canCompost(stack);
        }
    }

    public void clearContent() {
        this.items.clear();
    }

    LazyOptional<IItemHandlerModifiable>[] handlers =
            SidedInvWrapper.create(this, Direction.UP, Direction.DOWN, Direction.NORTH);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (!this.remove && facing != null) {
            if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
                if (facing == Direction.UP)
                    return handlers[0].cast();
                else if (facing == Direction.DOWN)
                    return handlers[1].cast();
                else
                    return handlers[2].cast();
            }
            if (capability == CapabilityEnergy.ENERGY) {
                return energy.cast();
            }
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (LazyOptional<IItemHandlerModifiable> handler : handlers)
            handler.invalidate();
        this.energy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        this.handlers = SidedInvWrapper.create(this, Direction.UP, Direction.DOWN, Direction.NORTH);
        this.energy = LazyOptional.of(() -> this.energyStorage);
    }

    public static class CustomEnergyStorage extends EnergyStorage  {
        private final ComposterEntity entity;
        public CustomEnergyStorage(ComposterEntity entity, int capacity) {
            super(capacity);
            this.entity = entity;
        }

        @Override
        public int receiveEnergy(int receive, boolean simulate)
        {
            if (!simulate) entity.setChanged();
            return super.receiveEnergy(receive, simulate);
        }

        @Override
        public int extractEnergy(int extract, boolean simulate)
        {
            if (!simulate) entity.setChanged();
            return super.extractEnergy(extract, simulate);
        }

        public void setEnergyStored(int energy) {
            entity.setChanged();
            this.energy = Math.max(0, Math.min(this.capacity, energy));
        }
    }

    public static class EnergyLevelMessage {
        private final BlockPos pos;
        private final int energyLevel;

        public EnergyLevelMessage(BlockPos pos, int energyLevel) {
            this.pos = pos;
            this.energyLevel = energyLevel;
        }

        public void encoder(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeInt(energyLevel);
        }
        public static EnergyLevelMessage decoder(FriendlyByteBuf buffer) {
            // Create packet from buffer data
            BlockPos pos = buffer.readBlockPos();
            int energyLevel = buffer.readInt();
            return new EnergyLevelMessage(pos, energyLevel);
        }

        public void messageConsumer(Supplier<NetworkEvent.Context> ctx) {
            // Handle message
            ctx.get().enqueueWork(
                    () -> {
                        ServerPlayer player = ctx.get().getSender();
                        if (player != null && player.level.getBlockEntity(pos) instanceof ComposterEntity composterEntity) {
                            composterEntity.setEnergyLevel(energyLevel);
                        }
                    }
            );
        }
    }
}
