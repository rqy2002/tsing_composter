package com.tsingchih;

import com.google.common.collect.Iterables;
import com.mojang.datafixers.DSL;
import com.mojang.logging.LogUtils;
import com.mojang.datafixers.util.Pair;
import com.tsingchih.block.Composter;
import com.tsingchih.block.entity.ComposterEntity;
import com.tsingchih.gui.ComposterScreen;
import com.tsingchih.inventory.ComposterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.loot.BlockLoot;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.ModelProvider;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.forge.event.lifecycle.GatherDataEvent;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("tsing_composter")
public class TsingComposter {
    public static final String modId = "tsing_composter";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, modId);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, modId);
    public static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, modId);
    public static final CreativeModeTab TAB_TSINGCRAFT = new CreativeModeTab(modId) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(COMPOSTER_ITEM.get());
        }
    };
    public static final RegistryObject<Block> COMPOSTER_BLOCK
            = BLOCKS.register("composter",
            () -> new Composter(BlockBehaviour.Properties.of(Material.METAL).strength(2.5F, 2.5F)));
    public static final RegistryObject<BlockEntityType<ComposterEntity>> COMPOSTER_ENTITY
            = BLOCK_ENTITY_TYPES.register("composter",
            () -> BlockEntityType.Builder.of(ComposterEntity::new, COMPOSTER_BLOCK.get()).build(DSL.remainderType()));
    public static final RegistryObject<MenuType<ComposterMenu>> COMPOSTER_MENU = CONTAINERS.register(
            "composter", () -> new MenuType<>((IContainerFactory)(windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                return new ComposterMenu(windowId, inv, pos);
            }));

    public static final RegistryObject<Item> COMPOSTER_ITEM
            = ITEMS.register("composter",
            () -> new BlockItem(COMPOSTER_BLOCK.get(), new Item.Properties().tab(TAB_TSINGCRAFT)));
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TsingComposter.modId, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public TsingComposter() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        BLOCK_ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
        CONTAINERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        INSTANCE.registerMessage(0, ComposterEntity.EnergyLevelMessage.class,
                ComposterEntity.EnergyLevelMessage::encoder,
                ComposterEntity.EnergyLevelMessage::decoder,
                ComposterEntity.EnergyLevelMessage::messageConsumer);
    }

    @Mod.EventBusSubscriber(modid = modId, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class DataGeneration {
        @SubscribeEvent
        public static void onGatherData(GatherDataEvent event) {
            var gen = event.getGenerator();
            var helper = event.getExistingFileHelper();
            gen.addProvider(new CustomBlockStateProvider(gen, helper));
            gen.addProvider(new CustomLootTableProvider(gen));
            gen.addProvider(new EnglishLanguageProvider(gen));
            gen.addProvider(new CustomBlockTagsProvider(gen, helper));
            gen.addProvider(new CustomRecipeProvider(gen));
        }
    }

    @Mod.EventBusSubscriber(modid = modId, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientRegistration {
        @SubscribeEvent(priority = EventPriority.LOW)
        public static void clientSetup(RegistryEvent.Register<MenuType<?>> event) {
            MenuScreens.register(COMPOSTER_MENU.get(), ComposterScreen::new);
        }
    }

    public static class CustomLootTableProvider extends LootTableProvider {
        public CustomLootTableProvider(DataGenerator gen) {
            super(gen);
        }

        @Nonnull
        @Override
        protected List<Pair<Supplier<Consumer<BiConsumer<ResourceLocation, LootTable.Builder>>>, LootContextParamSet>> getTables() {
            return List.of(Pair.of(CustomBlockLoot::new, LootContextParamSets.BLOCK));
        }

        @Override
        protected void validate(Map<ResourceLocation, LootTable> map, @Nonnull ValidationContext context) {
            map.forEach((key, value) -> LootTables.validate(context, key, value));
        }
    }

    public static class CustomBlockLoot extends BlockLoot {
        @Override
        protected void addTables() {
            this.dropSelf(COMPOSTER_BLOCK.get());
        }

        @Nonnull
        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Iterables.transform(BLOCKS.getEntries(), RegistryObject::get);
        }
    }

    public static class EnglishLanguageProvider extends LanguageProvider {
        public EnglishLanguageProvider(DataGenerator gen) {
            super(gen, modId, "en_us");
        }

        @Override
        protected void addTranslations() {
            this.add(COMPOSTER_BLOCK.get(), "Composting Machine");
            this.add(ComposterScreen.EnergyButton.TOOLTIP_LEVEL, "Working Level: %d");
            this.add(ComposterScreen.EnergyButton.TOOLTIP_SPEED, "Energy cost: %d FE/t");
        }
    }

    public static class CustomBlockStateProvider extends BlockStateProvider {
        public CustomBlockStateProvider(DataGenerator gen, ExistingFileHelper helper) {
            super(gen, modId, helper);
        }

        @Override
        protected void registerStatesAndModels() {
            String name = COMPOSTER_BLOCK.get().getRegistryName().getPath();
            String texture = ModelProvider.BLOCK_FOLDER + "/" + name;
            ModelFile m = models().cubeTop(name, modLoc(texture + "_side"), modLoc(texture + "_top"));
            this.simpleBlock(COMPOSTER_BLOCK.get(), m);
            this.simpleBlockItem(COMPOSTER_BLOCK.get(), m);
        }
    }

    public static class CustomBlockTagsProvider extends TagsProvider<Block> {

        protected CustomBlockTagsProvider(DataGenerator gen, @Nullable ExistingFileHelper existingFileHelper) {
            super(gen, Registry.BLOCK, TsingComposter.modId, existingFileHelper);
        }

        @Override
        protected void addTags() {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(COMPOSTER_BLOCK.get());
            this.tag(BlockTags.MINEABLE_WITH_AXE).add(COMPOSTER_BLOCK.get());
        }

        @Override
        public String getName() {
            return "block tags: " + modId;
        }
    }

    public static class CustomRecipeProvider extends RecipeProvider {
        public CustomRecipeProvider(DataGenerator gen) {
            super(gen);
        }

        @Override
        protected void buildCraftingRecipes(Consumer<FinishedRecipe> writer) {
            ShapedRecipeBuilder.shaped(COMPOSTER_ITEM.get())
                    .pattern("ccc")
                    .pattern("i#i")
                    .pattern("iii")
                    .define('i', Items.IRON_BARS)
                    .define('c', Tags.Items.GLASS)
                    .define('#', Items.COMPOSTER)
                    .unlockedBy("has_iron", has(Items.IRON_BARS))
                    .save(writer);
        }

        @Override
        public String getName() {
            return "recipe: " + TsingComposter.modId;
        }
    }
}
