package com.theminespirit.neoforge.demo;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.WallDemo;
import com.theminespirit.core.town.WallPiece;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

final class DemoWall {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DemoWall() {}

    static int start(DemoService service, ServerLevel level, CommandSourceStack source, BlockPos explicit) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            service.stopOwnedBy(player.getUUID());
        }
        Optional<BlockPos> resolved = service.anchor(source, explicit);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Не нашёл, где строить стену: посмотрите на площадку или укажите координаты x y z"));
            return 0;
        }
        BlockPos anchor = resolved.orElseThrow();
        List<DemoPlan> plans = new ArrayList<>();
        DemoPlan site;
        try {
            site = plans(anchor, plans);
        } catch (IllegalArgumentException | IllegalStateException refused) {
            source.sendFailure(Component.literal("Стена-витрина не начата: " + refused.getMessage()));
            return 0;
        }
        String refused = service.launch(plans, site, player == null ? null : player.getUUID());
        if (refused != null) {
            source.sendFailure(Component.literal(refused));
            return 0;
        }
        LOGGER.info(
                "Стена-витрина начата в {}: {} участков, углы 90° и 45°, калитка",
                level.dimension().location(),
                plans.size());
        source.sendSuccess(
                () -> Component.literal("Стена-витрина начата: " + plans.size()
                        + " участков ограды — углы 90° и 45°, диагональ и калитка. Отменить — /spirit demo stop"),
                true);
        return plans.size();
    }

    private static DemoPlan plans(BlockPos anchor, List<DemoPlan> plans) {
        FenceLine line = WallDemo.line();
        List<WallPiece> pieces = WallDemo.pieces(DataTables.INSTANCE.presets().current());
        int minX = line.posts().stream().mapToInt(ColumnMask.Cell::x).min().orElseThrow();
        int maxX = line.posts().stream().mapToInt(ColumnMask.Cell::x).max().orElseThrow();
        int minZ = line.posts().stream().mapToInt(ColumnMask.Cell::z).min().orElseThrow();
        int maxZ = line.posts().stream().mapToInt(ColumnMask.Cell::z).max().orElseThrow();
        int baseX = anchor.getX() - minX;
        int baseZ = anchor.getZ() - minZ;
        int y = anchor.getY();
        BlockCoord spawn = new BlockCoord(baseX + minX - 3, y, baseZ + minZ);
        BlockCoord chest = new BlockCoord(baseX + minX - 3, y, baseZ + minZ + 2);
        Map<String, Integer> materials = new TreeMap<>();
        for (WallPiece piece : pieces) {
            DemoBuildings.Level chosen = new DemoBuildings.Level(0, piece.scheme());
            Preset preset = DemoPlanner.preset(chosen);
            int sizeX = piece.rotation().rotatedSizeX(preset.sizeX(), preset.sizeZ());
            int sizeZ = piece.rotation().rotatedSizeZ(preset.sizeX(), preset.sizeZ());
            int x = baseX + piece.x();
            int z = baseZ + piece.z();
            BuildBox platform = BuildBox.of(x, y, z, x + sizeX - 1, y + preset.sizeY() - 1, z + sizeZ - 1);
            BuildBox floor = BuildBox.of(x, y - 1, z, x + sizeX - 1, y - 1, z + sizeZ - 1);
            DemoPlacement placement = new DemoPlacement(
                    piece.rotation(),
                    new BlockCoord(x, y, z),
                    chest,
                    null,
                    null,
                    new DemoPlacement.Look(0, 1),
                    spawn,
                    platform,
                    floor,
                    sizeX,
                    sizeZ);
            DemoPlan plan = DemoPlanner.plan(chosen, placement);
            plans.add(plan);
            plan.materials().forEach((item, count) -> materials.merge(item, count, Integer::sum));
        }
        int siteMinX = baseX + minX - 4;
        int siteMaxX = baseX + maxX + 4;
        int siteMinZ = baseZ + minZ - 4;
        int siteMaxZ = baseZ + maxZ + 4;
        BuildBox sitePlatform = BuildBox.of(siteMinX, y, siteMinZ, siteMaxX, y + 1, siteMaxZ);
        BuildBox siteFloor = BuildBox.of(siteMinX, y - 1, siteMinZ, siteMaxX, y - 1, siteMaxZ);
        DemoPlacement sitePlacement = new DemoPlacement(
                BuildRotation.NONE,
                new BlockCoord(siteMinX, y, siteMinZ),
                chest,
                null,
                null,
                new DemoPlacement.Look(0, 1),
                spawn,
                sitePlatform,
                siteFloor,
                siteMaxX - siteMinX + 1,
                siteMaxZ - siteMinZ + 1);
        return new DemoPlan("Стена-витрина", "fence_long", sitePlacement, materials, List.of(), List.of());
    }
}
