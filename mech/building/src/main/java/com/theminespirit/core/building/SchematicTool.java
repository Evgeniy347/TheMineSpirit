package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class SchematicTool {
    static final Path ROOT = Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);

    private SchematicTool() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    static int run(String[] args, PrintStream out) {
        if (args.length == 0) {
            out.println("Использование: schematic.sh show <файл|id> [0|90|180|270]  |  schematic.sh check [файл|id…]");
            return 2;
        }
        try {
            return switch (args[0]) {
                case "show" -> show(args, out);
                case "check" -> check(args, out);
                default -> {
                    out.println("Неизвестная команда '" + args[0] + "': допустимы show, check");
                    yield 2;
                }
            };
        } catch (IllegalStateException | IllegalArgumentException error) {
            out.println("ОШИБКА: " + error.getMessage());
            return 1;
        }
    }

    private static int show(String[] args, PrintStream out) {
        if (args.length < 2) {
            out.println("show: укажите файл или id постройки");
            return 2;
        }
        Path file = resolve(args[1]);
        BuildRotation rotation = args.length > 2 ? BuildRotation.parse(args[2]) : BuildRotation.NONE;
        SchematicDocument document = SchematicTextParser.parse(read(file), file.toString());
        out.print(describe(idOf(file), document, rotation));
        SchematicCheck.Report report = SchematicCheck.check(idOf(file), document);
        report.problems().forEach(problem -> out.println("НАРУШЕНИЕ " + problem));
        return report.ok() ? 0 : 1;
    }

    private static int check(String[] args, PrintStream out) {
        List<Path> files = new ArrayList<>();
        if (args.length > 1) {
            for (int i = 1; i < args.length; i++) {
                files.add(resolve(args[i]));
            }
        } else {
            files.addAll(all());
        }
        int failed = 0;
        for (Path file : files) {
            try {
                SchematicCheck.Report report =
                        SchematicCheck.check(idOf(file), SchematicTextParser.parse(read(file), file.toString()));
                if (report.ok()) {
                    out.println("OK " + idOf(file));
                } else {
                    failed++;
                    report.problems().forEach(problem -> out.println("НАРУШЕНИЕ " + problem));
                }
            } catch (IllegalStateException error) {
                failed++;
                out.println("ОШИБКА " + error.getMessage());
            }
        }
        out.println(
                failed == 0
                        ? "Все постройки прошли: " + files.size()
                        : "С ошибками: " + failed + " из " + files.size());
        return failed == 0 ? 0 : 1;
    }

    static String describe(String id, SchematicDocument document, BuildRotation rotation) {
        Schematic schematic = document.schematic();
        StringBuilder out = new StringBuilder();
        out.append("Постройка ").append(id);
        document.header().name().ifPresent(name -> out.append(" — ").append(name));
        out.append('\n')
                .append("Габариты ")
                .append(schematic.sizeX())
                .append('×')
                .append(schematic.sizeY())
                .append('×')
                .append(schematic.sizeZ())
                .append(", поворот ")
                .append(rotation.id())
                .append('\n');
        out.append("Материалы:\n");
        for (Map.Entry<String, Integer> material :
                PresetLibrary.countMaterials(schematic).entrySet()) {
            out.append("  ")
                    .append(material.getKey())
                    .append(" × ")
                    .append(material.getValue())
                    .append('\n');
        }
        out.append(slices(schematic, rotation));
        return out.toString();
    }

    static String slices(Schematic schematic, BuildRotation rotation) {
        int sx = rotation.rotatedSizeX(schematic.sizeX(), schematic.sizeZ());
        int sz = rotation.rotatedSizeZ(schematic.sizeX(), schematic.sizeZ());
        char[][][] grid = new char[schematic.sizeY()][sz][sx];
        for (char[][] layer : grid) {
            for (char[] row : layer) {
                Arrays.fill(row, ' ');
            }
        }
        BlockCoord origin = new BlockCoord(0, 0, 0);
        for (BlockCoord cell : schematic.airCells(origin, rotation)) {
            grid[cell.y()][cell.z()][cell.x()] = '.';
        }
        Map<String, Character> symbols = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : schematic.entries(origin, rotation)) {
            char symbol = symbols.computeIfAbsent(entry.block(), block -> symbolFor(symbols.size()));
            grid[entry.pos().y()][entry.pos().z()][entry.pos().x()] = symbol;
        }
        StringBuilder out = new StringBuilder("Легенда среза:");
        symbols.forEach(
                (block, symbol) -> out.append(' ').append(symbol).append('=').append(SchematicBlocks.path(block)));
        out.append('\n');
        for (int y = 0; y < grid.length; y++) {
            out.append("y").append(y).append(":\n");
            for (char[] row : grid[y]) {
                out.append("  |").append(row).append("|\n");
            }
        }
        return out.toString();
    }

    private static char symbolFor(int index) {
        String spare = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#=+*%&@$";
        return spare.charAt(index % spare.length());
    }

    private static List<Path> all() {
        try (Stream<Path> files = Files.list(ROOT)) {
            return files.filter(path -> path.toString().endsWith(SchematicTextParser.EXTENSION))
                    .sorted()
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Каталог построек " + ROOT + " не прочитан: запускайте из корня проекта", error);
        }
    }

    private static Path resolve(String argument) {
        Path direct = Path.of(argument);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path byId = ROOT.resolve(argument + SchematicTextParser.EXTENSION);
        if (Files.isRegularFile(byId)) {
            return byId;
        }
        throw new IllegalArgumentException("Постройка '" + argument + "' не найдена: ни файла, ни " + byId);
    }

    private static String idOf(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(SchematicTextParser.EXTENSION)
                ? name.substring(0, name.length() - SchematicTextParser.EXTENSION.length())
                : name;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Файл " + file + " не прочитан: проверьте путь и кодировку UTF-8", error);
        }
    }
}
