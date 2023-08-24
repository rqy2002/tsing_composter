package com.tsingchih.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tsingchih.TsingComposter;
import com.tsingchih.block.entity.ComposterEntity;
import com.tsingchih.inventory.ComposterMenu;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ComposterScreen extends AbstractContainerScreen<ComposterMenu> {
    private static ResourceLocation texture = new ResourceLocation(TsingComposter.modId + ":textures/gui/composter.png");
    private EnergyButton energyButton;
    private EnergyBar energyBar;

    public ComposterScreen(ComposterMenu menu, Inventory inv, Component component) {
        super(menu, inv, component);
    }
    
    @Override
    public void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.energyButton = new EnergyButton(this.menu, this.leftPos + 152, this.topPos + 60, 16, 16, 176, 76, 16);
        this.energyBar = new EnergyBar(this.leftPos + 159, this.topPos + 10, 4, 40, 176, 36);
        this.addRenderableWidget(this.energyButton);
    }

    @Override
    protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, texture);
        int i = this.leftPos;
        int j = this.topPos;
        this.blit(pose, i, j, 0, 0, this.imageWidth, this.imageHeight);
        int k = 20 * Math.min(this.menu.getCompostProgress(), ComposterEntity.COMPOST_MAX_LEVEL)
                / ComposterEntity.COMPOST_MAX_LEVEL;
        this.blit(pose, i + 114, j + 33 + 20 - k, 176, 16, 20, k);

        int l = this.menu.getCompostTime();
        this.blit(pose, i + 76, j + 34, 176, 0, l * 3 + 1, 16);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        this.renderTooltip(pose, mouseX, mouseY);
        this.energyButton.render(pose, mouseX, mouseY, partialTick);
        this.energyBar.render(pose, mouseX, mouseY, partialTick, (float)this.menu.getEnergyStorage() / ComposterEntity.ENERGY_CAP);
    }

    @Override
    protected void renderTooltip(PoseStack pose, int mouseX, int mouseY) {
        super.renderTooltip(pose, mouseX, mouseY);
        if (energyBar.isHoveredOrFocused())
            this.renderTooltip(pose,
                    new TextComponent(String.format("%d FE / %d FE", this.menu.getEnergyStorage(), ComposterEntity.ENERGY_CAP)),
                    mouseX, mouseY);
        if (energyButton.isHoveredOrFocused()) {
            int k = this.menu.getEnergyLevel();
            this.renderComponentTooltip(pose,
                    List.of(
                            new TranslatableComponent(EnergyButton.TOOLTIP_LEVEL, k),
                            new TranslatableComponent(EnergyButton.TOOLTIP_SPEED, ComposterEntity.ENERGY_COST[k])
                    ), mouseX, mouseY);
        }
    }

    public static class EnergyBar extends AbstractWidget {
        private final int xTex, yTex;
        private float energy;

        public EnergyBar(int x, int y, int width, int height, int xTex, int yTex) {
            super(x, y, width, height, TextComponent.EMPTY);
            this.xTex = xTex;
            this.yTex = yTex;
        }

        public void render(PoseStack pose, int mouseX, int mouseY, float partialTick, float energy) {
            this.energy = energy;
            super.render(pose, mouseX, mouseY, partialTick);
        }

        @Override
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, ComposterScreen.texture);

            int barHeight = Math.round(this.energy * this.height);
            RenderSystem.enableDepthTest();
            TsingComposter.LOGGER.debug("render bar height {}", barHeight);
            this.blit(pose, this.x, this.y + this.height - barHeight, this.xTex, this.yTex, this.width, barHeight);
        }

        @Override
        public void updateNarration(NarrationElementOutput output) {
        }
    }

    public static class EnergyButton extends AbstractButton {
        public static final String TOOLTIP_LEVEL = TsingComposter.modId + ".composter.tooltip.level";
        public static final String TOOLTIP_SPEED = TsingComposter.modId + ".composter.tooltip.speed";
        private final int xTex, yTex, yDiff;
        private final ComposterMenu menu;
        public EnergyButton(ComposterMenu menu, int x, int y, int width, int height, int xTex, int yTex, int yDiff) {
            super(x, y, width, height, TextComponent.EMPTY);
            this.menu = menu;
            this.xTex = xTex;
            this.yTex = yTex;
            this.yDiff = yDiff;
        }

        @Override
        public void onPress() {
            int k = this.menu.getEnergyLevel();
            this.menu.setEnergyLevel((k + 1) % 4);
        }

        public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
            super.render(pose, mouseX, mouseY, partialTick);
        }

        @Override
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, ComposterScreen.texture);
            int y = this.yTex + this.menu.getEnergyLevel() * this.yDiff;

            RenderSystem.enableDepthTest();
            this.blit(pose, this.x, this.y, this.xTex, y, this.width, this.height);
        }

        @Override
        public void updateNarration(NarrationElementOutput output) {
        }
    }

}
