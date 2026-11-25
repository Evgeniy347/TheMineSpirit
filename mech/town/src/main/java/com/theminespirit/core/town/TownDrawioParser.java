package com.theminespirit.core.town;

import com.theminespirit.core.town.TownDrawioXml.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TownDrawioParser {
    private static final int MAX_SCALE = 8;

    private TownDrawioParser() {}

    static TownDrawio.Decoded decode(String xml) {
        Objects.requireNonNull(xml, "Текст .drawio не может быть null: разбирать нечего");
        List<Cell> cells = TownDrawioXml.cells(TownDrawioXml.content(xml));
        Meta meta = meta(cells);
        List<TownMap.MapPlot> plots = new ArrayList<>();
        List<TownMap.MapStreet> streets = new ArrayList<>();
        List<TownMap.WallSegment> wall = new ArrayList<>();
        for (Cell cell : cells) {
            String kind = cell.attrs().get("tmKind");
            if (kind == null || "meta".equals(kind)) {
                continue;
            }
            switch (kind) {
                case "plot" -> plots.add(plot(cell, meta));
                case "street" -> streets.add(street(cell, meta));
                case "wall" -> wall.add(wall(cell, meta));
                default ->
                    throw new IllegalArgumentException(
                            "Фигура tmKind='" + kind + "' неизвестна: .drawio принимает участки, улицы, стену и meta");
            }
        }
        TownMap map = new TownMap(
                new TownMap.MapPoint(meta.anchorX(), meta.anchorZ()),
                List.copyOf(plots),
                List.copyOf(streets),
                List.copyOf(wall));
        return new TownDrawio.Decoded(map, meta.revision());
    }

    private static Meta meta(List<Cell> cells) {
        Meta found = null;
        for (Cell cell : cells) {
            if (!"meta".equals(cell.attrs().get("tmKind"))) {
                continue;
            }
            if (found != null) {
                throw new IllegalArgumentException("В .drawio две скрытые ячейки meta: ревизия и рамка не "
                        + "определены однозначно — оставьте одну выгрузку");
            }
            Map<String, String> attrs = cell.attrs();
            int format = attrs.containsKey("tmFormat") ? integer(attrs, "tmFormat") : TownDrawio.FORMAT;
            if (format < 1 || format > TownDrawio.FORMAT) {
                throw new IllegalArgumentException("Формат .drawio " + format + " не поддерживается: ожидается 1.."
                        + TownDrawio.FORMAT + " — скачайте план заново");
            }
            int scale = integer(attrs, "tmScale");
            if (scale < 1 || scale > MAX_SCALE) {
                throw new IllegalArgumentException(
                        "Масштаб meta " + scale + ": допустимо 1.." + MAX_SCALE + " пикселей на блок");
            }
            int revision = attrs.containsKey("tmRevision") ? integer(attrs, "tmRevision") : -1;
            found = new Meta(
                    revision,
                    integer(attrs, "tmAnchorX"),
                    integer(attrs, "tmAnchorZ"),
                    integer(attrs, "tmX1"),
                    integer(attrs, "tmZ1"),
                    scale);
        }
        if (found == null) {
            throw new IllegalArgumentException("В .drawio нет ячейки tmKind=\"meta\": это не выгрузка карты — "
                    + "скачайте план по /v1/town/plan.drawio и правьте его, не удаляя скрытую meta");
        }
        return found;
    }

    private static TownMap.MapPlot plot(Cell cell, Meta meta) {
        String id = need(cell, "tmId");
        int[] box = box(cell, meta);
        return new TownMap.MapPlot(
                id,
                cell.attrs().getOrDefault("tmBuilding", id),
                box[0],
                box[1],
                box[2],
                box[3],
                cell.attrs().getOrDefault("tmFacing", "north"));
    }

    private static TownMap.MapStreet street(Cell cell, Meta meta) {
        String kind = cell.attrs().getOrDefault("tmKindValue", "side");
        if (!TownMapRules.MAIN.equals(kind) && !TownMapRules.SIDE.equals(kind)) {
            throw new IllegalArgumentException("Улица '" + cell.attrs().getOrDefault("tmId", "?") + "' вида '" + kind
                    + "': допустимы только main и side — кольцевых улиц больше нет");
        }
        int[] box = box(cell, meta);
        return new TownMap.MapStreet(need(cell, "tmId"), kind, box[0], box[1], box[2], box[3]);
    }

    private static TownMap.WallSegment wall(Cell cell, Meta meta) {
        Map<String, String> geometry = cell.geometry();
        if (!geometry.containsKey("x") || !geometry.containsKey("width") || !geometry.containsKey("height")) {
            return new TownMap.WallSegment(
                    integer(cell.attrs(), "tmX1"),
                    integer(cell.attrs(), "tmZ1"),
                    integer(cell.attrs(), "tmX2"),
                    integer(cell.attrs(), "tmZ2"));
        }
        double centerX = decimal(geometry, "x") + decimal(geometry, "width") / 2;
        double centerY = decimal(geometry, "y") + decimal(geometry, "height") / 2;
        double half = decimal(geometry, "width") / 2;
        double angle = Math.toRadians(rotation(cell.attrs().getOrDefault("style", "")));
        double dx = Math.cos(angle) * half;
        double dy = Math.sin(angle) * half;
        return new TownMap.WallSegment(
                block(centerX - dx, meta.x1(), meta.scale(), true),
                block(centerY - dy, meta.z1(), meta.scale(), true),
                block(centerX + dx, meta.x1(), meta.scale(), true),
                block(centerY + dy, meta.z1(), meta.scale(), true));
    }

    private static int[] box(Cell cell, Meta meta) {
        Map<String, String> geometry = cell.geometry();
        if (geometry.containsKey("x")
                && geometry.containsKey("y")
                && geometry.containsKey("width")
                && geometry.containsKey("height")) {
            int minX = block(decimal(geometry, "x"), meta.x1(), meta.scale(), false);
            int minZ = block(decimal(geometry, "y"), meta.z1(), meta.scale(), false);
            int width = Math.max(1, (int) Math.round(decimal(geometry, "width") / meta.scale()));
            int depth = Math.max(1, (int) Math.round(decimal(geometry, "height") / meta.scale()));
            return new int[] {minX, minZ, minX + width - 1, minZ + depth - 1};
        }
        return new int[] {
            integer(cell.attrs(), "tmX1"),
            integer(cell.attrs(), "tmZ1"),
            integer(cell.attrs(), "tmX2"),
            integer(cell.attrs(), "tmZ2")
        };
    }

    private static int block(double pixel, int origin, int scale, boolean centered) {
        double shift = centered ? scale / 2.0 : 0;
        return origin + (int) Math.round((pixel - shift) / scale);
    }

    private static double rotation(String style) {
        String key = "rotation=";
        int start = style.indexOf(key);
        if (start < 0) {
            return 0;
        }
        int end = style.indexOf(';', start + key.length());
        String value = end < 0 ? style.substring(start + key.length()) : style.substring(start + key.length(), end);
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Поворот стены '" + value + "' не число: фигура повреждена", error);
        }
    }

    private static int integer(Map<String, String> attrs, String key) {
        String raw = attrs.get(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("У фигуры нет атрибута " + key
                    + ": старый .drawio без координат не импортируется — выгрузите план заново");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Атрибут " + key + " = '" + raw + "' не целое число: координаты " + "фигур задаются блоками",
                    error);
        }
    }

    private static double decimal(Map<String, String> values, String key) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "У фигуры нет геометрии " + key + ": подвигайте её в diagrams.net или выгрузите план заново");
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Геометрия " + key + " = '" + raw + "' не число: файл повреждён", error);
        }
    }

    private static String need(Cell cell, String key) {
        String value = cell.attrs().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "У фигуры нет атрибута " + key + ": без него участок или улицу не сопоставить с миром");
        }
        return value;
    }

    private record Meta(int revision, int anchorX, int anchorZ, int x1, int z1, int scale) {}
}
