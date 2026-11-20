package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import org.jspecify.annotations.Nullable;

final class SchematicDraft {
    private final String origin;
    private final Optional<String> name;
    private final Optional<BuildingType> type;
    private final Optional<Integer> stage;
    private final int level;
    private final Optional<String> requires;
    private final int @Nullable [] size;
    private final int sizeLine;
    private final @Nullable String entrance;
    private final int entranceLine;
    private final Schematic.@Nullable Repeat repeat;
    private final Map<Character, SchematicTextParser.LegendEntry> legend;
    private final List<SchematicDocument.Exemption> exemptions;
    private final List<SchematicTextParser.RawLayer> layers;

    SchematicDraft(
            String origin,
            Optional<String> name,
            Optional<BuildingType> type,
            Optional<Integer> stage,
            int level,
            Optional<String> requires,
            int @Nullable [] size,
            int sizeLine,
            @Nullable String entrance,
            int entranceLine,
            Schematic.@Nullable Repeat repeat,
            Map<Character, SchematicTextParser.LegendEntry> legend,
            List<SchematicDocument.Exemption> exemptions,
            List<SchematicTextParser.RawLayer> layers) {
        this.origin = origin;
        this.name = name;
        this.type = type;
        this.stage = stage;
        this.level = level;
        this.requires = requires;
        this.size = size;
        this.sizeLine = sizeLine;
        this.entrance = entrance;
        this.entranceLine = entranceLine;
        this.repeat = repeat;
        this.legend = legend;
        this.exemptions = exemptions;
        this.layers = layers;
    }

    SchematicDocument assemble() {
        if (size == null) {
            throw error(-1, "нет ключа size: габариты Ш×В×Д обязательны");
        }
        int sx = size[0];
        int sy = size[1];
        int sz = size[2];
        if (sx < 1 || sy < 1 || sz < 1) {
            throw error(sizeLine, "размер " + sx + "×" + sy + "×" + sz + ": стороны обязаны быть хотя бы 1");
        }
        int max = CatalogLimits.SCHEMATIC_SIZE_MAX;
        if (sx > max || sy > max || sz > max) {
            throw error(
                    sizeLine,
                    "размер " + sx + "×" + sy + "×" + sz + " больше предела " + max
                            + ": большая схема строится слишком долго");
        }
        if (legend.isEmpty()) {
            throw error(-1, "нет раздела legend: нечем расшифровать слои");
        }
        char[][][] grid = grid(sx, sy, sz);
        SchematicGrid.Result cells = SchematicGrid.resolve(grid, legend, origin);
        if (cells.blocks().size() > CatalogLimits.SCHEMATIC_BLOCKS_MAX) {
            throw error(
                    -1,
                    "блоков " + cells.blocks().size() + ", а предел " + CatalogLimits.SCHEMATIC_BLOCKS_MAX
                            + ": большая схема строится слишком долго");
        }
        Schematic schematic = new Schematic(sx, sy, sz, cells.blocks(), cells.air(), repeat);
        SchematicDocument.Header header =
                new SchematicDocument.Header(name, type, stage, level, requires, entrance(schematic));
        return new SchematicDocument(schematic, header, exemptions);
    }

    private char[][][] grid(int sx, int sy, int sz) {
        SchematicTextParser.RawLayer[] byY = new SchematicTextParser.RawLayer[sy];
        for (SchematicTextParser.RawLayer layer : layers) {
            if (layer.y() >= sy) {
                throw error(
                        layer.line(),
                        "слой y" + layer.y() + " выше габарита: по size высота " + sy + " (слои y0…y" + (sy - 1) + ")");
            }
            if (byY[layer.y()] != null) {
                throw error(layer.line(), "слой y" + layer.y() + " описан дважды");
            }
            byY[layer.y()] = layer;
        }
        char[][][] grid = new char[sy][sz][sx];
        for (int y = 0; y < sy; y++) {
            SchematicTextParser.RawLayer layer = byY[y];
            if (layer == null) {
                throw error(-1, "нет слоя y" + y + ": по size слоёв " + sy + ", каждый описывается явно");
            }
            List<String> rows = new ArrayList<>(layer.rows());
            while (!rows.isEmpty() && rows.get(rows.size() - 1).isBlank()) {
                rows.remove(rows.size() - 1);
            }
            if (rows.size() != sz) {
                throw error(
                        layer.line(),
                        "в слое y" + y + " строк " + rows.size() + ", а по size глубина "
                                + sz + ": первая строка — фронт (z=0), последняя — тыл; пустые строки в конце слоя "
                                + "не считаются");
            }
            for (int z = 0; z < sz; z++) {
                String row = rows.get(z);
                if (row.length() > sx) {
                    throw error(
                            layer.line() + 1 + z,
                            "в строке " + row.length() + " символов, а по size ширина " + sx + " (лишнее: '"
                                    + row.substring(sx) + "')");
                }
                for (int x = 0; x < sx; x++) {
                    char symbol = x < row.length() ? row.charAt(x) : SchematicTextParser.ANY;
                    if (symbol != SchematicTextParser.AIR
                            && symbol != SchematicTextParser.ANY
                            && !legend.containsKey(symbol)) {
                        throw error(layer.line() + 1 + z, "символ '" + symbol + "' (x=" + x + ") не описан в легенде");
                    }
                    grid[y][z][x] = symbol;
                }
            }
        }
        return grid;
    }

    private Optional<BlockCoord> entrance(Schematic schematic) {
        if (entrance == null) {
            return Optional.empty();
        }
        if ("front".equals(entrance)) {
            Schematic.Entry best = null;
            for (Schematic.Entry entry : schematic.blocks()) {
                if (!SchematicBlocks.isEntranceBlock(entry.block())
                        || SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                    continue;
                }
                if (best == null
                        || entry.dz() < best.dz()
                        || (entry.dz() == best.dz() && entry.dy() < best.dy())
                        || (entry.dz() == best.dz() && entry.dy() == best.dy() && entry.dx() < best.dx())) {
                    best = entry;
                }
            }
            if (best == null) {
                throw error(
                        entranceLine,
                        "entrance: front, но в слоях нет двери или калитки: "
                                + "поставьте дверь во фронт или укажите вход координатами x,y,z");
            }
            return Optional.of(new BlockCoord(best.dx(), best.dy(), best.dz()));
        }
        Matcher coords = SchematicTextParser.COORDS.matcher(entrance);
        if (!coords.matches()) {
            throw error(entranceLine, "вход '" + entrance + "' не по формату: ожидается front или x,y,z");
        }
        BlockCoord cell = new BlockCoord(
                Integer.parseInt(coords.group(1)),
                Integer.parseInt(coords.group(2)),
                Integer.parseInt(coords.group(3)));
        if (cell.x() >= schematic.sizeX() || cell.y() >= schematic.sizeY() || cell.z() >= schematic.sizeZ()) {
            throw error(
                    entranceLine,
                    "вход " + entrance + " вне габаритов " + schematic.sizeX() + "×" + schematic.sizeY() + "×"
                            + schematic.sizeZ());
        }
        return Optional.of(cell);
    }

    private IllegalStateException error(int line, String message) {
        return SchematicTextSyntax.error(origin, line, message);
    }
}
