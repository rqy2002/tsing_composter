package com.tsingchih.block;

import com.tsingchih.TsingComposter;
import com.tsingchih.block.entity.ComposterEntity;
import com.tsingchih.inventory.ComposterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

public class Composter extends BaseEntityBlock {
    public static final String NAME = TsingComposter.modId + ":composter";
    public Composter(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult blockHitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity blockentity = level.getBlockEntity(pos);
            if (blockentity instanceof ComposterEntity composterEntity && player instanceof ServerPlayer serverPlayer)
                NetworkHooks.openGui(serverPlayer, composterEntity, composterEntity.getBlockPos());
            return InteractionResult.CONSUME;
        }
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity livingEntity, ItemStack stack) {
        if (stack.hasCustomHoverName()) {
            BlockEntity blockentity = level.getBlockEntity(pos);
            if (blockentity instanceof ComposterEntity) {
                ((ComposterEntity)blockentity).setCustomName(stack.getHoverName());
            }
        }

    }

    public void onRemove(BlockState newState, Level level, BlockPos pos, BlockState oldState, boolean p_60519_) {
        if (!newState.is(oldState.getBlock())) {
            BlockEntity blockentity = level.getBlockEntity(pos);
            if (blockentity instanceof ComposterEntity) {
                if (level instanceof ServerLevel) {
                    Containers.dropContents(level, pos, (ComposterEntity)blockentity);
                }

                level.updateNeighbourForOutputSignal(pos, this);
            }

            super.onRemove(newState, level, pos, oldState, p_60519_);
        }
    }

    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return ComposterMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComposterEntity(pos, state);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> entityType) {
        if (level.isClientSide)
            return null;
        return createTickerHelper(entityType, TsingComposter.COMPOSTER_ENTITY.get(), ComposterEntity::serverTick);
    }
}
