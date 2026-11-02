package com.theminespirit.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TaskSnapshotChunks {
    public static final int MAX_CHUNK_CHARS = 16_000;

    private TaskSnapshotChunks() {}

    public static List<String> split(String snapshotJson) {
        Objects.requireNonNull(snapshotJson, "Снимок задачи не может быть null: делить на куски нечего");
        if (snapshotJson.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>((snapshotJson.length() + MAX_CHUNK_CHARS - 1) / MAX_CHUNK_CHARS);
        for (int start = 0; start < snapshotJson.length(); start += MAX_CHUNK_CHARS) {
            int end = Math.min(start + MAX_CHUNK_CHARS, snapshotJson.length());
            chunks.add(snapshotJson.substring(start, end));
        }
        return List.copyOf(chunks);
    }

    public static String join(List<String> chunks) {
        Objects.requireNonNull(chunks, "Куски снимка задачи не могут быть null: склеивать нечего");
        StringBuilder snapshotJson = new StringBuilder();
        for (String chunk : chunks) {
            snapshotJson.append(
                    Objects.requireNonNull(chunk, "Кусок снимка задачи не может быть null: снимок потерян"));
        }
        return snapshotJson.toString();
    }
}
