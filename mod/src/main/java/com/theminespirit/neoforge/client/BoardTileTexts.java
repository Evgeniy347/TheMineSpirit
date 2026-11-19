package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardClock;
import com.theminespirit.core.ui.BoardMaterials;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardTimeView;
import com.theminespirit.core.ui.BoardWaitReasons;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

final class BoardTileTexts {
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final String BUILDING_PREFIX = "building=";

    private BoardTileTexts() {}

    static Component unfinished() {
        return Component.translatable(KEY + "unfinished");
    }

    static Component materialText(BoardMaterials.Material material) {
        if (material.kind() == BoardMaterials.Kind.NEED && material.have() != null) {
            return Component.translatable(
                    KEY + "material.haveNeed",
                    itemName(material.item()),
                    Component.literal(countText(material.have())),
                    Component.literal(countText(material.count())));
        }
        String key = switch (material.kind()) {
            case GATHER -> KEY + "material.gather";
            case SUPPLY -> KEY + "material.supply";
            case NEED -> KEY + "material.need";
            case MISSING -> KEY + "material.missing";
        };
        return Component.translatable(key, itemName(material.item()), Component.literal(countText(material.count())));
    }

    static String countText(int count) {
        return Integer.toString(count);
    }

    static @Nullable Component timeText(BoardTile tile) {
        BoardTimeView time = BoardTimeView.of(tile);
        if (!time.showsMoment()) {
            return null;
        }
        MutableComponent started = Component.translatable(KEY + "time.started", momentText(time.startedAt()));
        if (time.showsFinish()) {
            started.append(" · ").append(Component.translatable(KEY + "time.finished", momentText(time.finishedAt())));
        }
        return started;
    }

    static @Nullable Component spentText(BoardTile tile) {
        BoardTimeView time = BoardTimeView.of(tile);
        if (!time.showsSpent()) {
            return null;
        }
        return Component.translatable(KEY + (time.running() ? "time.running" : "time.spent"), time.minutes());
    }

    private static Component momentText(long tick) {
        BoardClock.Moment moment = BoardClock.moment(tick);
        return Component.translatable(
                KEY + "time.moment",
                moment.day(),
                BoardClock.twoDigits(moment.hour()),
                BoardClock.twoDigits(moment.minute()));
    }

    static MutableComponent taskText(BoardTile tile) {
        String name = tile.boardCase().toLowerCase(Locale.ROOT);
        MutableComponent text = Component.translatableWithFallback(KEY + "case." + name, tile.boardCase());
        String building = buildingText(tile);
        if (building != null) {
            text.append(" · ").append(Component.literal(building));
        }
        if (tile.level() > 1) {
            text.append(" ").append(Component.translatable(KEY + "level", tile.level()));
        }
        return text;
    }

    private static @Nullable String buildingText(BoardTile tile) {
        if (!"REPAIR_BUILDING".equals(tile.boardCase())) {
            return null;
        }
        for (String detail : tile.details()) {
            if (detail.startsWith(BUILDING_PREFIX)) {
                return detail.substring(BUILDING_PREFIX.length());
            }
        }
        return null;
    }

    static Component detailsText(BoardTile tile) {
        Component spirit = tile.spirit() == null ? Component.literal("…") : Component.literal(tile.spirit());
        MutableComponent text = Component.translatable(KEY + "details", tile.priority(), spirit);
        boolean named = buildingText(tile) != null;
        for (String detail : tile.details()) {
            if (named && detail.startsWith(BUILDING_PREFIX)) {
                continue;
            }
            text.append(" · ").append(Component.literal(detail));
        }
        return text;
    }

    static Component waitText(BoardWaitReasons.Wait wait, Map<String, BoardTile> byId) {
        return switch (wait.kind()) {
            case FREE_SPIRIT -> Component.translatable(KEY + "wait.free_spirit");
            case SPIRIT -> Component.translatable(KEY + "wait.spirit", Component.literal(wait.subject()));
            case RESOURCE ->
                Component.translatable(
                        KEY + "wait.resource",
                        itemName(wait.subject()),
                        Component.literal(String.valueOf(wait.count())));
            case TOOL ->
                Component.translatable(
                        KEY + "wait.tool", itemName(wait.subject()), Component.literal(String.valueOf(wait.count())));
            case DEPENDENCY -> Component.translatable(KEY + "wait.dependency", dependencyText(wait.subject(), byId));
            case BLOCKED -> Component.translatable(KEY + "wait.blocked");
            case FAILED -> Component.translatable(KEY + "wait.failed");
            case CANCELLED -> Component.translatable(KEY + "wait.cancelled");
        };
    }

    private static Component dependencyText(String id, Map<String, BoardTile> byId) {
        BoardTile dependency = byId.get(id);
        return dependency == null ? Component.literal(id) : taskText(dependency);
    }

    static Component itemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return Component.literal(id);
        }
        return BuiltInRegistries.ITEM.get(key).getDescription();
    }
}
