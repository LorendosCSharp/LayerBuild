package com.lorendos;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public final class LayerBuildServer
{
    private static AutoJob autoJob;

    private LayerBuildServer()
    {
    }

    public static void init()
    {
        ServerTickEvents.END_SERVER_TICK.register(
                LayerBuildServer::onServerTick
        );
    }

    /*
     * One block from the schematic.
     *
     * We pass the BlockState ID instead of BlockState itself.
     * That keeps all Litematica classes completely out of the
     * common/server source set.
     */
    public record BuildBlock(
            BlockPos pos,
            int stateId
    )
    {
    }

    /*
     * One complete Litematica layer.
     */
    public record BuildLayer(
            int coordinate,
            List<BuildBlock> blocks
    )
    {
        public BuildLayer
        {
            blocks = List.copyOf(blocks);
        }
    }

    /*
     * MANUAL MODE
     *
     * Builds the entire requested layer immediately on the server.
     */
    public static void buildLayer(
            MinecraftServer server,
            UUID playerId,
            ResourceKey<Level> dimension,
            List<BuildBlock> blocks)
    {
        List<BuildBlock> safeBlocks = List.copyOf(blocks);

        server.execute(() ->
        {
            ServerPlayer player =
                    server.getPlayerList().getPlayer(playerId);

            if (player == null)
            {
                return;
            }

            if (!player.getAbilities().instabuild)
            {
                player.sendSystemMessage(
                        Component.literal(
                                "[Layer Builder] Creative mode required."
                        )
                );

                return;
            }

            ServerLevel level =
                    server.getLevel(dimension);

            if (level == null)
            {
                return;
            }

            int changed =
                    buildWholeLayer(
                            level,
                            player,
                            safeBlocks
                    );

            player.sendSystemMessage(
                    Component.literal(
                            "[Layer Builder] Built layer: "
                                    + changed
                                    + " blocks changed."
                    )
            );
        });
    }

    /*
     * AUTOMATIC MODE
     *
     * First layer -> wait -> next layer -> wait -> ...
     */
    public static void toggleAutomatic(
            MinecraftServer server,
            UUID playerId,
            ResourceKey<Level> dimension,
            List<BuildLayer> layers,
            int delayTicks)
    {
        List<BuildLayer> safeLayers =
                layers.stream()
                        .map(layer ->
                                new BuildLayer(
                                        layer.coordinate(),
                                        layer.blocks()
                                )
                        )
                        .toList();

        server.execute(() ->
        {
            /*
             * Pressing the auto key again stops it.
             */
            if (autoJob != null
                    && autoJob.playerId.equals(playerId))
            {
                autoJob = null;

                ServerPlayer player =
                        server.getPlayerList().getPlayer(playerId);

                if (player != null)
                {
                    player.sendSystemMessage(
                            Component.literal(
                                    "[Layer Builder] Automatic build stopped."
                            )
                    );
                }

                return;
            }

            ServerPlayer player =
                    server.getPlayerList().getPlayer(playerId);

            if (player == null)
            {
                return;
            }

            if (!player.getAbilities().instabuild)
            {
                player.sendSystemMessage(
                        Component.literal(
                                "[Layer Builder] Creative mode required."
                        )
                );

                return;
            }

            if (safeLayers.isEmpty())
            {
                player.sendSystemMessage(
                        Component.literal(
                                "[Layer Builder] No layers to build."
                        )
                );

                return;
            }

            autoJob = new AutoJob(
                    playerId,
                    dimension,
                    safeLayers,
                    Math.max(1, delayTicks)
            );

            player.sendSystemMessage(
                    Component.literal(
                            "[Layer Builder] Automatic build started. "
                                    + safeLayers.size()
                                    + " layers queued."
                    )
            );
        });
    }

    private static void onServerTick(
            MinecraftServer server)
    {
        if (autoJob == null)
        {
            return;
        }

        /*
         * Wait between WHOLE layers.
         *
         * There is intentionally no per-block delay anymore.
         */
        if (autoJob.cooldownTicks > 0)
        {
            autoJob.cooldownTicks--;

            if (autoJob.cooldownTicks > 0)
            {
                return;
            }
        }

        ServerPlayer player =
                server.getPlayerList()
                        .getPlayer(autoJob.playerId);

        if (player == null)
        {
            autoJob = null;
            return;
        }

        if (!player.getAbilities().instabuild)
        {
            player.sendSystemMessage(
                    Component.literal(
                            "[Layer Builder] Auto build stopped: "
                                    + "creative mode required."
                    )
            );

            autoJob = null;
            return;
        }

        ServerLevel level =
                server.getLevel(autoJob.dimension);

        if (level == null)
        {
            autoJob = null;
            return;
        }

        if (autoJob.layerIndex
                >= autoJob.layers.size())
        {
            player.sendSystemMessage(
                    Component.literal(
                            "[Layer Builder] Automatic build finished."
                    )
            );

            autoJob = null;
            return;
        }

        BuildLayer layer =
                autoJob.layers.get(
                        autoJob.layerIndex
                );

        int changed =
                buildWholeLayer(
                        level,
                        player,
                        layer.blocks()
                );

        player.sendSystemMessage(
                Component.literal(
                        "[Layer Builder] Layer "
                                + layer.coordinate()
                                + " built ("
                                + changed
                                + " changes)."
                )
        );

        autoJob.layerIndex++;

        if (autoJob.layerIndex
                >= autoJob.layers.size())
        {
            player.sendSystemMessage(
                    Component.literal(
                            "[Layer Builder] Automatic build finished."
                    )
            );

            autoJob = null;
            return;
        }

        /*
         * Delay starts AFTER the whole layer was built.
         */
        autoJob.cooldownTicks =
                autoJob.delayTicks;
    }

    /*
     * THIS is now the actual builder.
     *
     * No fake clicks.
     * No reach checks.
     * No hotbar handling.
     * No useItemOn().
     * No placement orientation calculations.
     * No retry queue.
     */
    private static int buildWholeLayer(
            ServerLevel level,
            ServerPlayer player,
            List<BuildBlock> blocks)
    {
        int changed = 0;

        for (BuildBlock block : blocks)
        {
            BlockPos pos = block.pos();

            BlockState target =
                    Block.stateById(
                            block.stateId()
                    );

            BlockState current =
                    level.getBlockState(pos);

            if (current.equals(target))
            {
                continue;
            }

            /*
             * Wrong block already there:
             *
             * Actually destroy it server-side.
             * No item drops because this is our creative builder.
             */
            if (!current.isAir())
            {
                level.destroyBlock(
                        pos,
                        false,
                        player,
                        Block.UPDATE_LIMIT
                );
            }

            if (!target.isAir())
            {
                /*
                 * Exact schematic BlockState.
                 *
                 * This means stairs, slabs, logs, pistons,
                 * observers etc. get the exact schematic state.
                 */
                level.setBlockAndUpdate(
                        pos,
                        target
                );

                /*
                 * Visible block fragments.
                 *
                 * Vanilla doesn't really have a special
                 * "block placement particles" event, so we
                 * use the target block's fragments as the
                 * construction effect.
                 */
                level.levelEvent(
                        null,
                        LevelEvent.PARTICLES_DESTROY_BLOCK,
                        pos,
                        Block.getId(target)
                );
            }

            changed++;
        }

        return changed;
    }

    private static final class AutoJob
    {
        private final UUID playerId;
        private final ResourceKey<Level> dimension;
        private final List<BuildLayer> layers;
        private final int delayTicks;

        private int layerIndex;
        private int cooldownTicks;

        private AutoJob(
                UUID playerId,
                ResourceKey<Level> dimension,
                List<BuildLayer> layers,
                int delayTicks)
        {
            this.playerId = playerId;
            this.dimension = dimension;
            this.layers = layers;
            this.delayTicks = delayTicks;

            this.layerIndex = 0;
            this.cooldownTicks = 0;
        }
    }
}