package com.theminespirit.core.town;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.observation.BlockCoord;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class StreetWork {
    private StreetWork() {}

    public static int level(StageRules rules, int stage) {
        Objects.requireNonNull(rules, "Правила этапов не могут быть null: уровень плиток улиц — данные этапа");
        if (stage < 1 || stage > rules.stages().size()) {
            throw new IllegalArgumentException("Этапа " + stage + " нет в stages.json: уровень плиток улиц не узнать");
        }
        Integer level = rules.stages().get(stage - 1).buildings().get(BoardCase.BUILD_STREETS);
        if (level == null) {
            throw new IllegalStateException("stages.json: у этапа " + stage
                    + " нет BUILD_STREETS — улицам этапа не из чего выбрать материал плиток");
        }
        return level;
    }

    public static List<SchemeBuild> of(
            StageSite site, TownPlan town, TownRules.Streets rules, PresetLibrary presets, int level) {
        Objects.requireNonNull(site, "Места этапа не могут быть null: улицы кладутся по выровненным площадкам");
        Objects.requireNonNull(town, "План поселения не может быть null: улицы берутся из него");
        List<StreetTile> tiles = StreetLayout.of(presets)
                .town(town.streets(), town.anchor().x(), town.anchor().z(), rules, within(site));
        int ground = site.anchor().y() - 1;
        return tiles.stream()
                .map(tile -> new SchemeBuild(
                        scheme(tile.scheme(), level), new BlockCoord(tile.x(), ground, tile.z()), tile.rotation()))
                .toList();
    }

    public static String scheme(String tile, int level) {
        return DemoBuildings.require(tile).level(level).scheme();
    }

    static ColumnMask within(StageSite site) {
        ColumnMask level = site.level();
        if (level.isEmpty()) {
            return level;
        }
        BitSet bits = new BitSet(level.width() * level.depth());
        for (ColumnMask.Cell post : site.ring().posts()) {
            int dx = post.x() - level.minX();
            int dz = post.z() - level.minZ();
            if (dx >= 0 && dz >= 0 && dx < level.width() && dz < level.depth()) {
                bits.set(dz * level.width() + dx);
            }
        }
        ColumnMask posts = new ColumnMask(level.minX(), level.minZ(), level.width(), level.depth(), bits);
        ColumnMask work = level.minus(posts)
                .minus(site.hut().mask())
                .minus(site.yard().mask())
                .minus(site.mine().mask())
                .minus(site.orchard().mask());
        for (OrchardPlot plot : site.extraOrchards()) {
            work = work.minus(plot.place().mask());
        }
        return work;
    }
}
