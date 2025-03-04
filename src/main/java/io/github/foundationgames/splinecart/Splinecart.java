package io.github.foundationgames.splinecart;

import io.github.foundationgames.splinecart.block.TrackTiesBlock;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.component.OriginComponent;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.item.TrackItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Mod(Splinecart.ID)
public class Splinecart {
	public static final String ID = "splinecart";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

	private static final DeferredRegister<Block> BLOCKS = DeferredRegister.createBlocks(ID);
	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ID);
	private static final DeferredRegister<Item> ITEMS = DeferredRegister.createItems(ID);
	private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ID);
	private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ID);

	public static final DeferredHolder<Block, TrackTiesBlock>
		TRACK_TIES = BLOCKS.register("track_ties", () -> new TrackTiesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.RAIL)));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrackTiesBlockEntity>>
		TRACK_TIES_BE = BLOCK_ENTITIES.register("track_ties", () -> Builder.of(TrackTiesBlockEntity::new, TRACK_TIES.get()).build(null));

	public static final DeferredHolder<Item, TrackItem>
		TRACK = ITEMS.register("track", () -> new TrackItem(TrackType.DEFAULT, new Item.Properties().component(DataComponents.LORE,
				lore(Component.translatable("item.splinecart.track.desc").withStyle(ChatFormatting.GRAY))
		))),
		CHAIN_DRIVE_TRACK = ITEMS.register("chain_drive_track", () -> new TrackItem(TrackType.CHAIN_DRIVE, new Item.Properties().component(DataComponents.LORE,
				lore(Component.translatable("item.splinecart.chain_drive_track.desc").withStyle(ChatFormatting.GRAY))
		))),
		MAGNETIC_TRACK = ITEMS.register("magnetic_track", () -> new TrackItem(TrackType.MAGNETIC, new Item.Properties().component(DataComponents.LORE,
				lore(Component.translatable("item.splinecart.magnetic_track.desc").withStyle(ChatFormatting.GRAY))
		)));

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<OriginComponent>>
		ORIGIN_POS = DATA_COMPONENT_TYPES.register("origin", () -> DataComponentType.<OriginComponent>builder().persistent(OriginComponent.CODEC).build());

	public static final DeferredHolder<EntityType<?>, EntityType<TrackFollowerEntity>>
		TRACK_FOLLOWER = ENTITIES.register("track_follower", () -> EntityType.Builder.<TrackFollowerEntity>of(TrackFollowerEntity::new, MobCategory.MISC).updateInterval(2).sized(0.25f, 0.25f).build("track_follower"));

	public static final TagKey<EntityType<?>> CARTS = TagKey.create(Registries.ENTITY_TYPE, id("carts"));

	public Splinecart(IEventBus bus) {
		var tieItem = ITEMS.register("track_ties", () -> new BlockItem(TRACK_TIES.get(), new Item.Properties().component(DataComponents.LORE,
				lore(Component.translatable("item.splinecart.track_ties.desc").withStyle(ChatFormatting.GRAY))
		)));

		bus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
			if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
				event.accept(tieItem.get().getDefaultInstance());
				event.accept(TRACK.get().getDefaultInstance());
				event.accept(CHAIN_DRIVE_TRACK.get().getDefaultInstance());
				event.accept(MAGNETIC_TRACK.get().getDefaultInstance());
			}
		});

		BLOCKS.register(bus);
		BLOCK_ENTITIES.register(bus);
		ITEMS.register(bus);
		DATA_COMPONENT_TYPES.register(bus);
		ENTITIES.register(bus);
	}

	public static ItemLore lore(Component lore) {
		return new ItemLore(List.of(lore));
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("splinecart", path);
	}
}