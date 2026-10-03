package com.lorendos.client;

import com.lorendos.LayerBuildServer;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

public final class LayerBuilderClient
        implements ClientModInitializer
{
    public static final String MOD_ID =
            "layerbuilder";

    /*
     * TIME BETWEEN COMPLETE LAYERS.
     *
     * 20 ticks = 1 second
     * 40 ticks = 2 seconds
     * 100 ticks = 5 seconds
     */
    private static int AUTO_LAYER_DELAY_TICKS = 40;

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(
                            MOD_ID,
                            "main"
                    )
            );

    private static final KeyMapping BUILD_LAYER =
            KeyMappingHelper.registerKeyMapping(
                    new KeyMapping(
                            "key.layerbuilder.build_layer",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_B,
                            CATEGORY
                    )
            );

    private static final KeyMapping AUTO_LAYER =
            KeyMappingHelper.registerKeyMapping(
                    new KeyMapping(
                            "key.layerbuilder.auto_layer",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_N,
                            CATEGORY
                    )
            );

    @Override
    public void onInitializeClient()
    {
        ClientTickEvents.END_CLIENT_TICK.register(
                client ->
                {
                    while (BUILD_LAYER.consumeClick())
                    {
                        manualBuild(client);
                    }

                    while (AUTO_LAYER.consumeClick())
                    {
                        automaticBuild(client);
                    }
                }
        );
    }

    private static void manualBuild(
            Minecraft mc)
    {
        if (!checkPlayer(mc))
        {
            return;
        }

        IntegratedServer server =
                mc.getSingleplayerServer();

        if (server == null)
        {
            message(
                    mc,
                    "This version currently requires "
                            + "singleplayer/integrated server."
            );

            return;
        }

        var blocks =
                LitematicaPlanReader
                        .readCurrentLayer();

        if (blocks.isEmpty())
        {
            message(
                    mc,
                    "No blocks found in the selected layer."
            );

            return;
        }

        /*
         * Client stops here.
         *
         * From this point onward the SERVER changes
         * the actual Minecraft world.
         */
        LayerBuildServer.buildLayer(
                server,
                mc.player.getUUID(),
                mc.level.dimension(),
                blocks
        );
    }

    private static void automaticBuild(
            Minecraft mc)
    {
        if (!checkPlayer(mc))
        {
            return;
        }

        IntegratedServer server =
                mc.getSingleplayerServer();

        if (server == null)
        {
            message(
                    mc,
                    "This version currently requires "
                            + "singleplayer/integrated server."
            );

            return;
        }

        if (!LitematicaPlanReader
                .isSingleLayerMode())
        {
            message(
                    mc,
                    "Set Litematica to Single Layer mode "
                            + "before starting automatic build."
            );

            return;
        }

        var layers =
                LitematicaPlanReader
                        .readRemainingLayers();

        /*
         * N acts as start/stop.
         */
        LayerBuildServer.toggleAutomatic(
                server,
                mc.player.getUUID(),
                mc.level.dimension(),
                layers,
                AUTO_LAYER_DELAY_TICKS
        );
    }

    private static boolean checkPlayer(
            Minecraft mc)
    {
        if (mc.player == null
                || mc.level == null)
        {
            return false;
        }

        if (!mc.player.getAbilities().instabuild)
        {
            message(
                    mc,
                    "Creative mode required."
            );

            return false;
        }

        return true;
    }

    private static void message(
            Minecraft mc,
            String text)
    {
        if (mc.player != null)
        {
            mc.player.sendSystemMessage(
                    Component.literal(
                            "[Layer Builder] " + text
                    )
            );
        }
    }
}