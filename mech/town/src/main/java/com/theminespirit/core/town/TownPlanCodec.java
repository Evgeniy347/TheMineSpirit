package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class TownPlanCodec {
    private static final String FIELD = "|";
    private static final String FIELD_SPLIT = "\\|";

    private TownPlanCodec() {}

    public static String encode(TownPlan plan) {
        Objects.requireNonNull(plan, "План не может быть null: сохраняется целиком");
        List<String> lines = new ArrayList<>();
        BlockCoord anchor = plan.anchor();
        lines.add("anchor" + FIELD + anchor.x() + FIELD + anchor.y() + FIELD + anchor.z());
        lines.add("region" + FIELD + plan.region().encode());
        lines.add("main" + FIELD + plan.streets().main().encode());
        lines.add("side" + FIELD + plan.streets().side().encode());
        for (TownStreets.Street street : plan.streets().named()) {
            lines.add("street" + FIELD + street.id() + FIELD + street.kind() + FIELD
                    + street.cells().encode());
        }
        lines.add("posts" + FIELD
                + String.join(
                        ";",
                        plan.fence().posts().stream()
                                .map(cell -> cell.x() + "," + cell.z())
                                .toList()));
        lines.add("gates" + FIELD
                + String.join(
                        ";", plan.fence().gates().stream().map(String::valueOf).toList()));
        for (Placement place : plan.placements()) {
            lines.add("place" + FIELD
                    + String.join(
                            FIELD,
                            List.of(
                                    place.id(),
                                    place.name(),
                                    String.valueOf(place.stage()),
                                    String.valueOf(place.x()),
                                    String.valueOf(place.z()),
                                    String.valueOf(place.width()),
                                    String.valueOf(place.depth()),
                                    place.facing().id())));
        }
        for (PathLine path : plan.paths()) {
            lines.add("path" + FIELD + path.id() + FIELD
                    + String.join(
                            ";",
                            path.cells().stream()
                                    .map(cell -> cell.x() + "," + cell.z())
                                    .toList()));
        }
        lines.add("unplaced" + FIELD + String.join(";", plan.unplaced()));
        return String.join("\n", lines);
    }

    public static TownPlan decode(String text) {
        Objects.requireNonNull(text, "Текст плана не может быть null");
        Map<String, String> single = new LinkedHashMap<>();
        List<Placement> places = new ArrayList<>();
        List<PathLine> paths = new ArrayList<>();
        List<TownStreets.Street> streetLines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            int bar = line.indexOf(FIELD);
            if (bar < 0) {
                throw broken(line);
            }
            String key = line.substring(0, bar);
            String value = line.substring(bar + 1);
            if ("place".equals(key)) {
                places.add(place(value));
            } else if ("street".equals(key)) {
                streetLines.add(street(value));
            } else if ("path".equals(key)) {
                paths.add(path(value));
            } else {
                single.put(key, value);
            }
        }
        String[] anchor = need(single, "anchor").split(FIELD_SPLIT, -1);
        if (anchor.length != 3) {
            throw broken("anchor|" + need(single, "anchor"));
        }
        TownStreets streets = new TownStreets(
                ColumnMask.decode(need(single, "main")), ColumnMask.decode(need(single, "side")), streetLines);
        FenceLine fence = new FenceLine(cells(need(single, "posts")), numbers(need(single, "gates")));
        return new TownPlan(
                new BlockCoord(Integer.parseInt(anchor[0]), Integer.parseInt(anchor[1]), Integer.parseInt(anchor[2])),
                ColumnMask.decode(need(single, "region")),
                streets,
                places,
                fence,
                words(need(single, "unplaced")),
                paths);
    }

    private static TownStreets.Street street(String value) {
        String[] parts = value.split(FIELD_SPLIT, -1);
        if (parts.length != 3) {
            throw broken("street|" + value);
        }
        return new TownStreets.Street(parts[0], parts[1], ColumnMask.decode(parts[2]));
    }

    public static byte[] encodePacked(TownPlan plan) {
        return gzip(encode(plan));
    }

    public static TownPlan decodePacked(byte[] packed) {
        Objects.requireNonNull(packed, "Сжатый план не может быть null: читать нечего");
        if (packed.length == 0) {
            throw broken("сжатый план пуст");
        }
        return decode(ungzip(packed));
    }

    private static byte[] gzip(String text) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException(
                    "План поселения не сжат для сохранения в мире: " + error.getMessage(), error);
        }
    }

    private static String ungzip(byte[] packed) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Сжатый план поселения не распакован: " + error.getMessage(), error);
        }
    }

    private static PathLine path(String value) {
        String[] parts = value.split(FIELD_SPLIT, -1);
        if (parts.length != 2) {
            throw broken("path|" + value);
        }
        return new PathLine(parts[0], cells(parts[1]));
    }

    private static Placement place(String value) {
        String[] parts = value.split(FIELD_SPLIT, -1);
        if (parts.length != 8) {
            throw broken("place|" + value);
        }
        return new Placement(
                parts[0],
                parts[1],
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]),
                Integer.parseInt(parts[5]),
                Integer.parseInt(parts[6]),
                Placement.Facing.valueOf(parts[7].toUpperCase(Locale.ROOT)));
    }

    private static List<ColumnMask.Cell> cells(String value) {
        return words(value).stream()
                .map(pair -> pair.split(",", -1))
                .map(xz -> new ColumnMask.Cell(Integer.parseInt(xz[0]), Integer.parseInt(xz[1])))
                .toList();
    }

    private static List<Integer> numbers(String value) {
        return words(value).stream().map(Integer::parseInt).toList();
    }

    private static List<String> words(String value) {
        return value.isEmpty() ? List.of() : Arrays.asList(value.split(";", -1));
    }

    private static String need(Map<String, String> single, String key) {
        String value = single.get(key);
        if (value == null) {
            throw broken("нет строки " + key);
        }
        return value;
    }

    private static IllegalArgumentException broken(String line) {
        return new IllegalArgumentException("Сохранённый план повреждён в строке '" + line
                + "': план читается целиком — повреждённый план строится заново по рельефу");
    }
}
