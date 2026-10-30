package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchemeFilter;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.SchemeBuild;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSchemes;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.WallLayout;
import com.theminespirit.core.town.WallPiece;
import com.theminespirit.core.town.YardScheme;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class PlotPlanBlocks {
    public record Plan(Map<BlockCoord, String> blocks) {
        public Plan {
            blocks = Map.copyOf(Objects.requireNonNull(blocks, "Блоки плана не могут быть null: пусто — пустая карта"));
        }

        public boolean planned(BlockCoord at) {
            return blocks.containsKey(at);
        }

        public List<BlockCoord> expected(String blockId) {
            String path = FloatingBlocks.pathOf(blockId);
            return blocks.entrySet().stream()
                    .filter(entry -> path.equals(FloatingBlocks.pathOf(entry.getValue())))
                    .map(Map.Entry::getKey)
                    .toList();
        }
    }

    private PlotPlanBlocks() {}

    public static Plan of(StageSite site) {
        Objects.requireNonNull(site, "Место этапа не может быть null: план строится по нему");
        PresetLibrary presets = DataTables.INSTANCE.presets().current();
        Map<BlockCoord, String> blocks = new HashMap<>();
        String hut = DataTables.INSTANCE
                .stages()
                .current()
                .scheme(BoardCase.BUILD_HOUSE, 1)
                .orElseThrow(() ->
                        new IllegalStateException("stages.json: нет схемы дома уровня 1 — план поселения без хижины"));
        add(blocks, scheme(presets, hut), site.houseOrigin(), BuildRotation.NONE, List.of());
        for (SchemeBuild build : yard(site)) {
            add(blocks, scheme(presets, build.scheme()), build.origin(), build.rotation(), build.only());
        }
        int fenceY = SiteLevels.fenceY(site);
        for (WallPiece piece : WallLayout.of(presets, StageSchemes.fence(site.stage()))
                .closed(site.ring().posts(), site.ring().gates())) {
            add(
                    blocks,
                    scheme(presets, piece.scheme()),
                    new BlockCoord(piece.x(), fenceY, piece.z()),
                    piece.rotation(),
                    List.of());
        }
        site.orchardSaplings().forEach(at -> blocks.putIfAbsent(at, "minecraft:oak_sapling"));
        return new Plan(blocks);
    }

    private static List<SchemeBuild> yard(StageSite site) {
        return Stream.of(YardScheme.stock(site, 1), YardScheme.lighting(site), List.of(YardScheme.furnace(site)))
                .flatMap(List::stream)
                .toList();
    }

    private static Schematic scheme(PresetLibrary presets, String id) {
        return presets.byId(id)
                .orElseThrow(() -> new IllegalStateException("presets.json: нет схемы '" + id
                        + "' — план поселения не собрать, аудит висящих объектов ослепнет"))
                .schematic();
    }

    private static void add(
            Map<BlockCoord, String> blocks,
            Schematic schematic,
            BlockCoord origin,
            BuildRotation rotation,
            List<String> only) {
        for (Schematic.PlacedEntry entry : SchemeFilter.only(schematic, only).entries(origin, rotation)) {
            blocks.putIfAbsent(entry.pos(), entry.block());
        }
    }
}
