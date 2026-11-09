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

    public static <K extends Enum<K>> Map<BlockCoord, K> load(Class<K> kinds, String resource) throws IOException {
        try (InputStream stream = LiveFixture.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Нет живой фикстуры: " + resource);
            }
            return parse(kinds, new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), resource);
        }
    }

    static <K extends Enum<K>> Map<BlockCoord, K> parse(Class<K> kinds, BufferedReader reader, String source)
            throws IOException {
        Map<BlockCoord, K> cells = new HashMap<>();
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
                cells.put(cell, Enum.valueOf(kinds, name));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Строка " + number + " живой фикстуры " + source + ": вид блока " + name + " не из "
                                + kinds.getSimpleName(),
                        e);
            }
        }
        return cells;
    }
}
