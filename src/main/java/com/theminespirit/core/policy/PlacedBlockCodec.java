package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PlacedBlockCodec {
    private PlacedBlockCodec() {
    }

    public record ChunkEntry(int chunkX, int chunkZ, boolean overflowed, long[] positions) {
        public ChunkEntry {
            Objects.requireNonNull(positions, "Позиции чанка не могут быть null: пустой чанк в набор не записывают");
        }
    }

    public static List<ChunkEntry> encode(PlacedBlockSet set) {
        Objects.requireNonNull(set, "Набор не может быть null: кодировать нечего");
        List<ChunkEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, PlacedBlockSet.Chunk> entry : set.chunkEntries()) {
            long[] packed = entry.getValue().positions.stream().mapToLong(Long::longValue).toArray();
            entries.add(new ChunkEntry(PlacedBlockSet.chunkXOf(entry.getKey()), PlacedBlockSet.chunkZOf(entry.getKey()),
                    entry.getValue().overflowed, packed));
        }
        return entries;
    }

    public static PlacedBlockSet decode(List<ChunkEntry> entries) {
        Objects.requireNonNull(entries, "Список чанков не может быть null: пустой набор кодируется пустым списком");
        PlacedBlockSet set = new PlacedBlockSet();
        Set<Long> seenChunks = new HashSet<>();
        for (ChunkEntry entry : entries) {
            long key = PlacedBlockSet.chunkKey(entry.chunkX(), entry.chunkZ());
            if (!seenChunks.add(key)) {
                throw new IllegalArgumentException("Чанк " + entry.chunkX() + " " + entry.chunkZ() + " записан дважды: сохранение повреждено, "
                        + "метки блоков игроков не загружены");
            }
            if (entry.positions().length == 0) {
                throw new IllegalArgumentException("Чанк " + entry.chunkX() + " " + entry.chunkZ() + " записан без позиций: пустые чанки не сохраняются");
            }
            if (entry.positions().length > PlacedBlockSet.MAX_PER_CHUNK) {
                throw new IllegalArgumentException("В чанке " + entry.chunkX() + " " + entry.chunkZ() + " записано " + entry.positions().length
                        + " позиций, предел " + PlacedBlockSet.MAX_PER_CHUNK + ": сохранение повреждено");
            }
            Set<Long> distinct = new HashSet<>();
            for (long packed : entry.positions()) {
                BlockCoord coord = PlacedBlockSet.unpack(packed);
                if ((coord.x() >> 4) != entry.chunkX() || (coord.z() >> 4) != entry.chunkZ()) {
                    throw new IllegalArgumentException("Позиция " + coord + " лежит вне чанка " + entry.chunkX() + " " + entry.chunkZ()
                            + ": сохранение повреждено");
                }
                if (!distinct.add(packed)) {
                    throw new IllegalArgumentException("Позиция " + coord + " записана в чанке дважды: сохранение повреждено");
                }
            }
            set.restore(key, entry.overflowed(), entry.positions());
        }
        return set;
    }
}
