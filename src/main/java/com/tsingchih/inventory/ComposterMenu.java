package com.tsingchih.inventory;

import com.tsingchih.TsingComposter;
import com.tsingchih.block.entity.ComposterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class ComposterMenu extends AbstractContainerMenu {
    public static final int INGREDIENT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    public static final int SLOT_COUNT = 2;
    public static final int DATA_COUNT = ComposterEntity.NUM_DATA_VALUES;
    private static final int INV_SLOT_START = 2;
    private static final int INV_SLOT_END = 29;
    private static final int HOT_BAR_SLOT_START = 29;
    private static final int HOT_BAR_SLOT_END = 38;
    private final Container container;
    private final ContainerData data;
    protected final Level level;
    @Nullable
    final ComposterEntity entity;

    public ComposterMenu(int containerId, Inventory inv, @Nullable BlockPos pos) {
        this(containerId, inv, new SimpleContainer(SLOT_COUNT), new SimpleContainerData(DATA_COUNT), pos);
    }

    public ComposterMenu(int containerId, Inventory inv, Container container, ContainerData data, @Nullable BlockPos pos) {
        super(TsingComposter.COMPOSTER_MENU.get(), containerId);
        checkContainerSize(container, SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.container = container;
        if (pos != null && inv.player.level.getBlockEntity(pos) instanceof ComposterEntity composterEntity)
            this.entity = composterEntity;
        else
            this.entity = null;
        this.data = data;
        this.level = inv.player.level;
        this.addSlot(new IngredientSlot(container, INGREDIENT_SLOT, 44, 35));
        this.addSlot(new ResultSlot(container, RESULT_SLOT, 116, 35));

        // player inventory
        for(int i = 0; i < 3; ++i) {
            for(int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        //
        for(int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(inv, k, 8 + k * 18, 142));
        }

        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int id) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(id);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (id == RESULT_SLOT) {
                if (!this.moveItemStackTo(itemstack1, INV_SLOT_START, INV_SLOT_END, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemstack1, itemstack);
            } else if (id == INGREDIENT_SLOT) {
                if (!this.moveItemStackTo(itemstack1, INV_SLOT_START, INV_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (ComposterEntity.canCompost(itemstack1)) {
                    if (!this.moveItemStackTo(itemstack1, INGREDIENT_SLOT, INGREDIENT_SLOT + 1, false))
                        return ItemStack.EMPTY;
                } else if (id >= INV_SLOT_START && id < INV_SLOT_END) {
                    if (!this.moveItemStackTo(itemstack1, HOT_BAR_SLOT_START, HOT_BAR_SLOT_END, false))
                        return ItemStack.EMPTY;
                } else if (id >= HOT_BAR_SLOT_START && id < HOT_BAR_SLOT_END) {
                    if (!this.moveItemStackTo(itemstack1, INV_SLOT_START, INV_SLOT_END, false))
                        return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
        }

        return itemstack;
    }

    // 0 ~ 8
    public int getCompostTime() {
        return this.data.get(0);
    }

    public void setEnergyLevel(int v) {
        if (entity == null) {
            TsingComposter.LOGGER.warn("set energy level to null");
        } else {
            TsingComposter.INSTANCE.sendToServer(new ComposterEntity.EnergyLevelMessage(this.entity.getBlockPos(), v));
        }
    }
    // 0 ~ 3
    public int getEnergyLevel() {
        return this.data.get(1);
    }

    public boolean enoughEnergy() {
        return this.data.get(3) > ComposterEntity.energyCost(getEnergyLevel());
    }

    // 0 ~ 6
    public int getCompostProgress() {
        return this.data.get(2);
    }

    // 0 ~ 40
    public int getEnergyStorage() {
        return this.data.get(3);
    }

    public static class ResultSlot extends Slot {
        public ResultSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }
    }

    public static class IngredientSlot extends Slot {
        public IngredientSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }
        public boolean mayPlace(ItemStack itemStack) {
            return ComposterEntity.canCompost(itemStack);
        }
    }
}
