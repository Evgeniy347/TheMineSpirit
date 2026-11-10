package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public final class LiveFixture {
    private LiveFixture() {}

    public static Map<BlockCoord, BlockKind> load(String resource) throws IOException {
        try (InputStream stream = LiveFixture.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Нет живой фикстуры: " + resource);
            }
            return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), resource);
        }
    }

    static Map<BlockCoord, BlockKind> parse(BufferedReader reader, String source) throws IOException {
        Map<BlockCoord, BlockKind> cells = new HashMap<>();
        String line;
        int number = 0;
        while ((line = reader.readLine()) != null) {
            number++;
            if (line.isBlank() || line.startsWith("#") || line.startsWith("feet")) {
                continue;
            }
            StringTokenizer parts = new StringTokenizer(line);
            if (parts.countTokens() != 4) {
                throw new IllegalArgumentException(
                        "Строка " + number + " живой фикстуры " + source + " не из четырёх полей: " + line);
            }
            BlockCoord cell = new BlockCoord(
                    Integer.parseInt(parts.nextToken()),
                    Integer.parseInt(parts.nextToken()),
                    Integer.parseInt(parts.nextToken()));
            String name = parts.nextToken();
            try {
                cells.put(cell, BlockKind.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Строка " + number + " живой фикстуры " + source + ": вид блока " + name + " не из "
                                + BlockKind.class.getSimpleName(),
                        e);
            }
        }
        return cells;
    }
}
