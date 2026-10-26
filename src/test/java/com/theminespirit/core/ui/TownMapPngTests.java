package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownSnapshot;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TownMapPngTests {
    private static final Path OUT = Path.of("build", "map-screens");
    private static final Path LANG = Path.of("src/main/resources/assets/theminespirit/lang/ru_ru.json");
    private static final int WIDTH = 960;
    private static final int HEIGHT = 640;
    private static final int PAD = 4;
    private static final long[] SEEDS = {11, 23, 47};
    private static final List<TownSnapshot.Progress> PROGRESS = List.of(
            new TownSnapshot.Progress(40, 40, false),
            new TownSnapshot.Progress(24, 40, false),
            new TownSnapshot.Progress(28, 40, true),
            new TownSnapshot.Progress(10, 40, false));

    @Test
    void render_ThreeReliefSeeds_LabelsInsideLotsAndApart() throws IOException {
        Files.createDirectories(OUT);
        for (long seed : SEEDS) {
            TownPlan plan = TownFixtures.hillyPlan(seed);
            TownMapView view = TownMapView.of(snapshot(plan));
            TerrainRaster terrain = relief(view);
            MapViewport viewport = MapViewport.fit(view.bounds().union(terrain.bounds()), 0, 0, WIDTH, HEIGHT);
            List<Rectangle> labels = draw(view, terrain, viewport, Optional.empty(), "plan-seed-" + seed + ".png");
            assertApart(labels, seed);
            assertEquals(
                    view.rects().stream()
                            .filter(r -> r.kind() == TownMapView.Kind.LOT)
                            .count(),
                    plan.placements().size(),
                    "Каждая постройка плана — участок на карте, сид " + seed);
        }
    }

    @Test
    void render_HoverDamagedLot_TooltipWithRestorePercent() throws IOException {
        Files.createDirectories(OUT);
        TownMapView view = TownMapView.of(snapshot(TownFixtures.hillyPlan(SEEDS[0])));
        TerrainRaster terrain = relief(view);
        TownMapView.MapRect damaged = view.rects().stream()
                .filter(TownMapView.MapRect::damaged)
                .findFirst()
                .orElseThrow();
        MapViewport fit = MapViewport.fit(view.bounds().union(terrain.bounds()), 0, 0, WIDTH, HEIGHT);
        double x = fit.screenX((damaged.x1() + damaged.x2() + 1) / 2.0);
        double y = fit.screenY((damaged.z1() + damaged.z2() + 1) / 2.0);
        MapViewport zoomed = fit.zoom(x, y, 3);
        Optional<TownMapView.MapRect> hovered = MapHit.lotAt(view, zoomed, x, y);
        assertEquals(Optional.of(damaged), hovered, "Курсор в центре повреждённого участка — подсказка о нём");
        assertApart(draw(view, terrain, zoomed, hovered, "plan-hover-damaged.png"), SEEDS[0]);
    }

    private static TownSnapshot snapshot(TownPlan plan) {
        Map<String, TownSnapshot.Progress> progress = new HashMap<>();
        List<List<BlockCoord>> paths = new ArrayList<>();
        List<Placement> first =
                plan.placements().stream().filter(place -> place.stage() == 1).toList();
        for (int index = 0; index < first.size(); index++) {
            Placement place = first.get(index);
            progress.put(place.id(), PROGRESS.get(index % PROGRESS.size()));
            paths.add(trail(plan.anchor(), place.x() + place.width() / 2, place.z() + place.depth() / 2));
        }
        return TownSnapshot.of(plan, TownFixtures.site(plan), progress, paths);
    }

    private static List<BlockCoord> trail(BlockCoord from, int toX, int toZ) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = Math.min(from.x(), toX); x <= Math.max(from.x(), toX); x++) {
            cells.add(new BlockCoord(x, from.y(), from.z()));
        }
        for (int z = Math.min(from.z(), toZ); z <= Math.max(from.z(), toZ); z++) {
            cells.add(new BlockCoord(toX, from.y(), z));
        }
        return cells;
    }

    private static TerrainRaster relief(TownMapView view) {
        TownMapView.Bounds bounds = view.bounds();
        int margin = 32;
        int minChunkX = Math.floorDiv(bounds.x1() - margin, TerrainTile.SIZE);
        int maxChunkX = Math.floorDiv(bounds.x2() + margin, TerrainTile.SIZE);
        int minChunkZ = Math.floorDiv(bounds.z1() - margin, TerrainTile.SIZE);
        int maxChunkZ = Math.floorDiv(bounds.z2() + margin, TerrainTile.SIZE);
        List<TerrainTile> tiles = new ArrayList<>();
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                tiles.add(new TerrainTile(chunkX, chunkZ, reliefChunk(chunkX, chunkZ)));
            }
        }
        return TerrainRaster.of(tiles);
    }

    private static byte[] reliefChunk(int chunkX, int chunkZ) {
        byte[] ids = new byte[TerrainTile.COLUMNS];
        for (int localZ = 0; localZ < TerrainTile.SIZE; localZ++) {
            for (int localX = 0; localX < TerrainTile.SIZE; localX++) {
                int worldX = chunkX * TerrainTile.SIZE + localX;
                int worldZ = chunkZ * TerrainTile.SIZE + localZ;
                ids[localZ * TerrainTile.SIZE + localX] = (byte) reliefColor(worldX, worldZ);
            }
        }
        return ids;
    }

    private static int reliefColor(int x, int z) {
        double wave = Math.sin(x / 15.0) * Math.cos(z / 19.0) + 0.5 * Math.sin((x + z) / 27.0);
        if (wave < -0.55) {
            return 12;
        }
        if (wave > 0.75) {
            return 11;
        }
        if (wave > 0.35) {
            return 7;
        }
        if (wave < -0.2) {
            return 2;
        }
        return 1;
    }

    private static List<Rectangle> draw(
            TownMapView view,
            TerrainRaster terrain,
            MapViewport viewport,
            Optional<TownMapView.MapRect> hovered,
            String file)
            throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        FontMetrics metrics = g.getFontMetrics();
        List<Rectangle> labels = new ArrayList<>();
        for (MapPaint.Shape shape : MapPaint.of(
                view, Optional.of(terrain), viewport, hovered, new MapPaint.Font(metrics::stringWidth, 9))) {
            switch (shape) {
                case MapPaint.Fill fill -> {
                    g.setColor(new Color(fill.color(), true));
                    g.fillRect(fill.x1(), fill.y1(), fill.x2() - fill.x1(), fill.y2() - fill.y1());
                }
                case MapPaint.Text text -> {
                    g.setColor(new Color(text.color(), true));
                    g.drawString(text.text(), text.x(), text.y() + metrics.getAscent() - 2);
                    labels.add(new Rectangle(text.x(), text.y(), metrics.stringWidth(text.text()), 9));
                }
                case MapPaint.Raster raster -> drawTerrain(g, raster);
            }
        }
        hovered.ifPresent(lot -> tooltip(g, metrics, MapHint.of(lot), viewport, lot));
        g.dispose();
        ImageIO.write(image, "png", OUT.resolve(file).toFile());
        return labels;
    }

    private static void drawTerrain(Graphics2D g, MapPaint.Raster shape) {
        TerrainRaster raster = shape.raster();
        BufferedImage image = new BufferedImage(raster.width(), raster.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, raster.width(), raster.height(), raster.pixels(), 0, raster.width());
        g.drawImage(image, shape.x1(), shape.y1(), shape.x2() - shape.x1(), shape.y2() - shape.y1(), null);
    }

    private static void tooltip(
            Graphics2D g, FontMetrics metrics, MapHint hint, MapViewport viewport, TownMapView.MapRect lot) {
        String detail = String.format(lang(hint.key()), hint.args().toArray());
        int width = Math.max(metrics.stringWidth(hint.title()), metrics.stringWidth(detail)) + 2 * PAD;
        int x = (int) viewport.screenX((lot.x1() + lot.x2() + 1) / 2.0) + 12;
        int y = (int) viewport.screenY((lot.z1() + lot.z2() + 1) / 2.0) - 12;
        int line = metrics.getHeight();
        g.setColor(new Color(0xF0100010, true));
        g.fillRect(x, y, width, 2 * line + 2 * PAD);
        g.setColor(new Color(0xFF5000FF, true));
        g.drawRect(x, y, width, 2 * line + 2 * PAD);
        g.setColor(Color.WHITE);
        g.drawString(hint.title(), x + PAD, y + PAD + metrics.getAscent());
        g.setColor(Color.LIGHT_GRAY);
        g.drawString(detail, x + PAD, y + PAD + line + metrics.getAscent());
    }

    private static String lang(String key) {
        try {
            String json = Files.readString(LANG, StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                    .matcher(json);
            assertTrue(matcher.find(), "Ключ подсказки " + key + " есть в ru_ru.json");
            return matcher.group(1);
        } catch (IOException e) {
            throw new IllegalStateException("Не прочитан " + LANG + ": тест запускается из корня проекта", e);
        }
    }

    private static void assertApart(List<Rectangle> labels, long seed) {
        for (int a = 0; a < labels.size(); a++) {
            for (int b = a + 1; b < labels.size(); b++) {
                assertTrue(
                        !labels.get(a).intersects(labels.get(b)),
                        "Подписи " + labels.get(a) + " и " + labels.get(b) + " налезают, сид " + seed);
            }
        }
    }
}
