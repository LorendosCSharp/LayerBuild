package com.lorendos.client;

import com.lorendos.LayerBuildServer.BuildBlock;
import com.lorendos.LayerBuildServer.BuildLayer;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;

import fi.dy.masa.malilib.util.LayerMode;
import fi.dy.masa.malilib.util.position.LayerRange;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class LitematicaPlanReader
{
    private LitematicaPlanReader()
    {
    }

    /*
     * Used by manual mode.
     *
     * Reads exactly whatever layer/range Litematica
     * currently has selected.
     */
    public static List<BuildBlock> readCurrentLayer()
    {
        WorldSchematic schematicWorld =
                SchematicWorldHandler
                        .getSchematicWorld();

        SchematicPlacement placement =
                DataManager
                        .getSchematicPlacementManager()
                        .getSelectedSchematicPlacement();

        if (schematicWorld == null
                || placement == null)
        {
            return List.of();
        }

        LayerRange range =
                DataManager.getRenderLayerRange();

        List<BuildBlock> result =
                new ArrayList<>();

        Set<BlockPos> seen =
                new HashSet<>();

        for (Box box :
                placement
                        .getSubRegionBoxes(
                                RequiredEnabled.PLACEMENT_ENABLED
                        )
                        .values())
        {
            scanBox(
                    schematicWorld,
                    box,
                    pos ->
                    {
                        if (!range.isPositionWithinRange(pos))
                        {
                            return;
                        }

                        if (!seen.add(pos))
                        {
                            return;
                        }

                        BlockState state =
                                schematicWorld
                                        .getBlockState(pos);

                        if (state.isAir())
                        {
                            return;
                        }

                        result.add(
                                new BuildBlock(
                                        pos.immutable(),
                                        Block.getId(state)
                                )
                        );
                    }
            );
        }

        return result;
    }

    /*
     * Used by automatic mode.
     *
     * Current selected layer
     *      ↓
     * current + 1
     *      ↓
     * current + 2
     *      ↓
     * ...
     *      ↓
     * final schematic layer
     */
    public static List<BuildLayer> readRemainingLayers()
    {
        WorldSchematic schematicWorld =
                SchematicWorldHandler
                        .getSchematicWorld();

        SchematicPlacement placement =
                DataManager
                        .getSchematicPlacementManager()
                        .getSelectedSchematicPlacement();

        if (schematicWorld == null
                || placement == null)
        {
            return List.of();
        }

        LayerRange range =
                DataManager.getRenderLayerRange();

        /*
         * Auto mode has a clear starting layer only
         * when Litematica is in SINGLE_LAYER mode.
         */
        if (range.getLayerMode()
                != LayerMode.SINGLE_LAYER)
        {
            return List.of();
        }

        Direction.Axis axis =
                range.getAxis();

        int startLayer =
                range.getCurrentLayerValue(false);

        Map<Integer, List<BuildBlock>> layers =
                new TreeMap<>();

        Set<BlockPos> seen =
                new HashSet<>();

        for (Box box :
                placement
                        .getSubRegionBoxes(
                                RequiredEnabled.PLACEMENT_ENABLED
                        )
                        .values())
        {
            scanBox(
                    schematicWorld,
                    box,
                    pos ->
                    {
                        if (!seen.add(pos))
                        {
                            return;
                        }

                        int layer =
                                coordinateForAxis(
                                        pos,
                                        axis
                                );

                        /*
                         * Start at the layer currently selected
                         * in Litematica and continue forward.
                         */
                        if (layer < startLayer)
                        {
                            return;
                        }

                        BlockState state =
                                schematicWorld
                                        .getBlockState(pos);

                        if (state.isAir())
                        {
                            return;
                        }

                        layers.computeIfAbsent(
                                layer,
                                ignored ->
                                        new ArrayList<>()
                        ).add(
                                new BuildBlock(
                                        pos.immutable(),
                                        Block.getId(state)
                                )
                        );
                    }
            );
        }

        List<BuildLayer> result =
                new ArrayList<>();

        for (Map.Entry<Integer, List<BuildBlock>>
                entry : layers.entrySet())
        {
            result.add(
                    new BuildLayer(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }

        return result;
    }

    public static boolean isSingleLayerMode()
    {
        return DataManager
                .getRenderLayerRange()
                .getLayerMode()
                == LayerMode.SINGLE_LAYER;
    }

    private static int coordinateForAxis(
            BlockPos pos,
            Direction.Axis axis)
    {
        return switch (axis)
        {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static void scanBox(
            WorldSchematic schematicWorld,
            Box box,
            PositionConsumer consumer)
    {
        BlockPos a = box.getPos1();
        BlockPos b = box.getPos2();

        int minX =
                Math.min(a.getX(), b.getX());

        int maxX =
                Math.max(a.getX(), b.getX());

        int minY =
                Math.min(a.getY(), b.getY());

        int maxY =
                Math.max(a.getY(), b.getY());

        int minZ =
                Math.min(a.getZ(), b.getZ());

        int maxZ =
                Math.max(a.getZ(), b.getZ());

        for (int y = minY; y <= maxY; y++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                for (int x = minX; x <= maxX; x++)
                {
                    consumer.accept(
                            new BlockPos(x, y, z)
                    );
                }
            }
        }
    }

    @FunctionalInterface
    private interface PositionConsumer
    {
        void accept(BlockPos pos);
    }
}