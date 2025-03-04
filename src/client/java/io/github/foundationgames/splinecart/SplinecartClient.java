package io.github.foundationgames.splinecart;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.foundationgames.splinecart.block.entity.TrackTiesBlockEntityRenderer;
import io.github.foundationgames.splinecart.config.Config;
import io.github.foundationgames.splinecart.config.ConfigOption;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.commands.CommandSourceStack;

import java.io.IOException;

@Mod(value = Splinecart.ID, dist = Dist.CLIENT)
public class SplinecartClient {
	public static final Config CONFIG = new Config("splinecart_client",
			() -> FMLPaths.CONFIGDIR.get()
					.resolve("splinecart").resolve("splinecart_client.properties"));

	public static final ConfigOption.BooleanOption CFG_ROTATE_CAMERA = CONFIG.optBool("rotate_camera", true);
	public static final ConfigOption.IntOption CFG_TRACK_RESOLUTION = CONFIG.optInt("track_resolution", 3, 1, 16);
	public static final ConfigOption.IntOption CFG_TRACK_RENDER_DISTANCE = CONFIG.optInt("track_render_distance", 8, 4, 32);


	public SplinecartClient(IEventBus bus) {
		try {
			CONFIG.load();
		} catch (IOException e) {
			Splinecart.LOGGER.error("Error loading client config on mod init", e);
		}

		bus.addListener(FMLClientSetupEvent.class, event -> {
			ItemBlockRenderTypes.setRenderLayer(Splinecart.TRACK_TIES.get(), RenderType.cutout());
		});

		bus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
			event.registerBlockEntityRenderer(Splinecart.TRACK_TIES_BE.get(), TrackTiesBlockEntityRenderer::new);
			event.registerEntityRenderer(Splinecart.TRACK_FOLLOWER.get(), NoopRenderer::new);
		});

		NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> {
			event.getDispatcher().register(
				LiteralArgumentBuilder.<CommandSourceStack>literal("splinecartc")
						.then(CONFIG.command(LiteralArgumentBuilder.literal("config"),
								CommandSourceStack::sendSystemMessage))
			);
		});
	}
}