package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.TaskSnapshotChunks;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

public final class SpiritTaskStore {
    public static final String KEY = "SpiritTask";
    public static final String CHUNKS_KEY = "SpiritTaskChunks";
    private static final int LARGE_SNAPSHOT_WARN_BYTES = 48 * 1024;
    private static final Logger LOGGER = LogUtils.getLogger();

    private SpiritTaskStore() {}

    public static void write(CompoundTag tag, String snapshotJson, UUID spiritId) {
        if (tag == null) {
            return;
        }
        tag.remove(KEY);
        tag.remove(CHUNKS_KEY);
        if (snapshotJson == null) {
            return;
        }
        int bytes = snapshotJson.getBytes(StandardCharsets.UTF_8).length;
        if (bytes >= LARGE_SNAPSHOT_WARN_BYTES) {
            LOGGER.warn(
                    "Дух {} сохраняет крупный снимок задачи: {} байт JSON. Одна строка NBT держит не больше 65535 байт, поэтому снимок пишется кусками",
                    spiritId,
                    bytes);
        }
        ListTag chunks = new ListTag();
        for (String chunk : TaskSnapshotChunks.split(snapshotJson)) {
            chunks.add(StringTag.valueOf(chunk));
        }
        tag.put(CHUNKS_KEY, chunks);
    }

    public static Optional<String> read(CompoundTag tag) {
        if (tag == null) {
            return Optional.empty();
        }
        Tag stored = tag.get(CHUNKS_KEY);
        if (stored instanceof ListTag chunks) {
            String value = TaskSnapshotChunks.join(chunkStrings(chunks));
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        }
        if (!tag.contains(KEY, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        String value = tag.getString(KEY);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static List<String> chunkStrings(ListTag chunks) {
        if (!chunks.isEmpty() && chunks.getElementType() != Tag.TAG_STRING) {
            throw new IllegalStateException(
                    "Снимок задачи повреждён: " + CHUNKS_KEY + " обязан быть списком строк, а хранит тип "
                            + chunks.getElementType() + ": задачу духа не восстановить, файл не правьте вручную");
        }
        List<String> values = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            values.add(chunks.getString(i));
        }
        return values;
    }
}
