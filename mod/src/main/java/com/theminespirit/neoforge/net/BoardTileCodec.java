package com.theminespirit.neoforge.net;

import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardTileGroup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

final class BoardTileCodec {
    static final int MAX_TILES = 4096;
    private static final List<BoardTileGroup> GROUPS = List.of(BoardTileGroup.values());

    private BoardTileCodec() {}

    static void write(FriendlyByteBuf buf, List<BoardTile> tiles) {
        buf.writeVarInt(tiles.size());
        for (BoardTile tile : tiles) {
            buf.writeUtf(tile.id());
            buf.writeUtf(tile.boardCase());
            buf.writeVarInt(tile.level());
            buf.writeVarInt(tile.priority());
            buf.writeBoolean(tile.spirit() != null);
            if (tile.spirit() != null) {
                buf.writeUtf(tile.spirit());
            }
            buf.writeUtf(tile.status());
            buf.writeVarInt(GROUPS.indexOf(tile.group()));
            buf.writeVarInt(tile.missing().size());
            for (Map.Entry<String, Integer> item : tile.missing().entrySet()) {
                buf.writeUtf(item.getKey());
                buf.writeVarInt(item.getValue());
            }
            buf.writeVarInt(tile.dependsOn().size());
            for (String dependency : tile.dependsOn()) {
                buf.writeUtf(dependency);
            }
            buf.writeVarLong(tile.doneAt());
            buf.writeVarLong(tile.startedAt());
            buf.writeVarLong(tile.finishedAt());
            buf.writeVarLong(tile.durationTicks());
            buf.writeBoolean(tile.supplyItem() != null);
            if (tile.supplyItem() != null) {
                buf.writeUtf(tile.supplyItem());
            }
            buf.writeVarInt(tile.supplyCount());
            buf.writeVarInt(tile.details().size());
            for (String detail : tile.details()) {
                buf.writeUtf(detail);
            }
        }
    }

    static List<BoardTile> read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_TILES) {
            throw new IllegalArgumentException("Снимок доски несёт " + count + " плиток, предел " + MAX_TILES
                    + ": пакет повреждён или сборки клиента и сервера разошлись");
        }
        List<BoardTile> tiles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tiles.add(readTile(buf));
        }
        return List.copyOf(tiles);
    }

    private static BoardTile readTile(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String boardCase = buf.readUtf();
        int level = buf.readVarInt();
        int priority = buf.readVarInt();
        String spirit = buf.readBoolean() ? buf.readUtf() : null;
        String status = buf.readUtf();
        BoardTileGroup group = GROUPS.get(buf.readVarInt());
        int missingCount = buf.readVarInt();
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (int j = 0; j < missingCount; j++) {
            missing.put(buf.readUtf(), buf.readVarInt());
        }
        int dependsCount = buf.readVarInt();
        List<String> dependsOn = new ArrayList<>(dependsCount);
        for (int j = 0; j < dependsCount; j++) {
            dependsOn.add(buf.readUtf());
        }
        long doneAt = buf.readVarLong();
        long startedAt = buf.readVarLong();
        long finishedAt = buf.readVarLong();
        long durationTicks = buf.readVarLong();
        String supplyItem = buf.readBoolean() ? buf.readUtf() : null;
        int supplyCount = buf.readVarInt();
        int detailCount = buf.readVarInt();
        List<String> details = new ArrayList<>(detailCount);
        for (int j = 0; j < detailCount; j++) {
            details.add(buf.readUtf());
        }
        return new BoardTile(
                id,
                boardCase,
                level,
                priority,
                spirit,
                status,
                group,
                missing,
                dependsOn,
                doneAt,
                startedAt,
                finishedAt,
                durationTicks,
                supplyItem,
                supplyCount,
                details);
    }
}
