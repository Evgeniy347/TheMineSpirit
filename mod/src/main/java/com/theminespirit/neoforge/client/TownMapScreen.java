package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.MapDock;
import com.theminespirit.core.ui.MapHint;
import com.theminespirit.core.ui.MapHit;
import com.theminespirit.core.ui.MapMarker;
import com.theminespirit.core.ui.MapMarkers;
import com.theminespirit.core.ui.MapPaint;
import com.theminespirit.core.ui.MapQuarry;
import com.theminespirit.core.ui.MapStyle;
import com.theminespirit.core.ui.MapViewport;
import com.theminespirit.core.ui.PlanNames;
import com.theminespirit.core.ui.RefreshClock;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.core.ui.ShellLayout;
import com.theminespirit.core.ui.TerrainRaster;
import com.theminespirit.core.ui.TownMapView;
import com.theminespirit.neoforge.net.TownMapChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

public final class TownMapScreen extends Screen {
    private static final String KEY = "theminespirit.map.";
    private static final String SHELL_KEY = ShellPainter.KEY;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF98A0AB;
    private static final int DIM = 0xFF6B7480;
    private static final int GOLD = 0xFFE0B050;
    private static final int LINE = 0xFF2B323B;
    private static final int CHIP_BG = 0xFF171B21;
    private static final int CHIP_ACTIVE = 0xFF262116;
    private static final int DOCK_WIDTH = 212;
    private static final int DOCK_MIN_SCREEN = 860;
    private static final int DOCK_GAP = 8;
    private static final double FOCUS_SCALE = 3.0;
    private static final int MARKER_TICKS = 20;

    private @Nullable TownMapView view;
    private TerrainRaster terrain = TerrainRaster.EMPTY;
    private @Nullable TerrainTexture texture;
    private final int focusX;
    private final int focusZ;
    private final boolean focused;
    private @Nullable MapViewport viewport;
    private boolean answered;
    private boolean offline;
    private boolean showMarkers = true;
    private boolean showLabels = true;
    private boolean showPaths = true;
    private int markerAge;
    private int ageTicks;
    private RefreshClock clock = RefreshClock.NEVER;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(BoardLayout.Rect rect, Runnable action) {}

    public TownMapScreen() {
        this(0, 0, false);
    }

    public TownMapScreen(int focusX, int focusZ) {
        this(focusX, focusZ, true);
    }

    private TownMapScreen(int focusX, int focusZ, boolean focused) {
        super(Component.translatable(KEY + "title"));
        this.focusX = focusX;
        this.focusZ = focusZ;
        this.focused = focused;
    }

    void show(TownMapChannel.Reply received) {
        answered = true;
        clock = RefreshClock.at(ageTicks);
        view = received.view().orElse(null);
        terrain = TerrainRaster.of(received.tiles());
        viewport = null;
        closeTexture();
        if (!terrain.isEmpty()) {
            texture = TerrainTexture.of(terrain);
        }
    }

    void showMarkers(List<MapMarker> markers, List<MapQuarry> quarries) {
        TownMapView current = view;
        if (current != null) {
            view = current.withMarkers(markers);
        }
    }

    @Override
    protected void init() {
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.PLAN);
        viewport = null;
        if (!answered) {
            requestSnapshot();
        }
        requestMarkers();
    }

    @Override
    public void tick() {
        super.tick();
        ageTicks++;
        markerAge++;
        if (markerAge >= MARKER_TICKS) {
            markerAge = 0;
            requestMarkers();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ShellPainter.background(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ShellPainter.titles(graphics, font, title, Component.translatable(KEY + "subtitle"));
        ShellPainter.footer(
                graphics, font, Component.translatable(SHELL_KEY + "hint.plan"), clock, ageTicks, width, height);
        hits.clear();
        drawToolbar(graphics);
        BoardLayout.Rect body = body();
        MapViewport window = window();
        TownMapView current = view;
        if (current == null) {
            graphics.fill(body.x(), body.y(), body.right(), body.bottom(), MapStyle.BACKGROUND);
            String state = offline ? "offline" : answered ? "empty" : "loading";
            graphics.drawCenteredString(
                    font,
                    Component.translatable(KEY + state),
                    body.x() + body.width() / 2,
                    body.y() + body.height() / 2,
                    TEXT);
            drawDock(graphics);
            return;
        }
        MapPaint.Font metrics = new MapPaint.Font(font::width, font.lineHeight);
        TownMapView shown = names(withPaths(current));
        TownMapView painted = showLabels ? shown : shown.withLotLabels(id -> "");
        boolean overMap = window.inside(mouseX, mouseY);
        Optional<MapMarker> hoveredMarker = showMarkers && overMap
                ? MapMarkers.at(shown.markers(), window, metrics, mouseX, mouseY)
                : Optional.empty();
        Optional<TownMapView.MapRect> hovered =
                overMap ? MapHit.lotAt(shown, window, mouseX, mouseY) : Optional.empty();
        List<MapPaint.Shape> shapes =
                new ArrayList<>(MapPaint.of(painted, Optional.of(terrain), window, hovered, metrics));
        if (showMarkers) {
            shapes.addAll(MapMarkers.paint(shown.markers(), window, hoveredMarker, metrics, showLabels));
        }
        for (MapPaint.Shape shape : shapes) {
            switch (shape) {
                case MapPaint.Fill fill -> graphics.fill(fill.x1(), fill.y1(), fill.x2(), fill.y2(), fill.color());
                case MapPaint.Text text -> graphics.drawString(font, text.text(), text.x(), text.y(), text.color());
                case MapPaint.Raster raster -> drawTerrain(graphics, raster, window);
            }
        }
        drawCompass(graphics, window);
        drawRulerAndCursor(graphics, window, mouseX, mouseY);
        drawDock(graphics);
        if (hoveredMarker.isPresent()) {
            graphics.renderComponentTooltip(font, hint(MapHint.of(hoveredMarker.orElseThrow())), mouseX, mouseY);
        } else if (hovered.isPresent()) {
            graphics.renderComponentTooltip(font, hint(MapHint.of(hovered.orElseThrow())), mouseX, mouseY);
        }
    }

    private void drawToolbar(GuiGraphics graphics) {
        BoardLayout.Rect toolbar = toolbar();
        int y = toolbar.y();
        int x = toolbar.x();
        x += chip(graphics, x, y, Component.literal("−"), false, () -> zoom(1));
        MapViewport window = window();
        double home = homeScale(window);
        int percent = (int) Math.round(window.scale() / Math.max(0.0001, home) * 100);
        int percentWidth = font.width(percent + "%") + 12;
        graphics.fill(x, y, x + percentWidth, y + 16, CHIP_BG);
        graphics.fill(x, y, x + percentWidth, y + 1, LINE);
        graphics.fill(x, y + 15, x + percentWidth, y + 16, LINE);
        graphics.drawString(font, percent + "%", x + 6, y + 4, TEXT, false);
        x += percentWidth + 5;
        x += chip(graphics, x, y, Component.literal("+"), false, () -> zoom(-1));
        x += chip(graphics, x, y, Component.translatable(KEY + "toolbar.fit"), false, () -> viewport = null);
        chip(
                graphics,
                x,
                y,
                Component.translatable(KEY + "toolbar.labels"),
                showLabels,
                () -> showLabels = !showLabels);
    }

    private int chip(GuiGraphics graphics, int x, int y, Component label, boolean active, Runnable action) {
        int width = font.width(label) + 12;
        graphics.fill(x, y, x + width, y + 16, active ? CHIP_ACTIVE : CHIP_BG);
        graphics.fill(x, y, x + width, y + 1, active ? GOLD : LINE);
        graphics.fill(x, y + 15, x + width, y + 16, active ? GOLD : LINE);
        graphics.drawString(font, label.getString(), x + 6, y + 4, active ? 0xFFF0DFAE : MUTED, false);
        hits.add(new Hit(new BoardLayout.Rect(x, y, width, 16), action));
        return width + 5;
    }

    private void zoom(int direction) {
        MapViewport window = window();
        double centerX = window.left() + window.width() / 2.0;
        double centerY = window.top() + window.height() / 2.0;
        viewport = window.zoom(centerX, centerY, direction);
    }

    private double homeScale(MapViewport window) {
        TownMapView current = view;
        if (current == null) {
            return window.scale();
        }
        return MapViewport.fit(
                        mapBounds(current),
                        window.left(),
                        window.top(),
                        Math.max(1, window.width()),
                        Math.max(1, window.height()))
                .scale();
    }

    private void drawCompass(GuiGraphics graphics, MapViewport window) {
        int x = window.left() + 16;
        int y = window.top() + 10;
        graphics.drawString(font, "N", x - 3, y, TEXT, false);
        graphics.fill(x, y + 10, x + 1, y + 24, TEXT);
        graphics.fill(x - 3, y + 10, x + 4, y + 12, TEXT);
    }

    private void drawRulerAndCursor(GuiGraphics graphics, MapViewport window, int mouseX, int mouseY) {
        int length = (int) Math.round(20 * window.scale());
        int x = window.left() + 8;
        int y = window.top() + window.height() - 14;
        graphics.fill(x, y, x + Math.min(length, window.width() - 60), y + 2, TEXT);
        graphics.drawString(
                font,
                Component.translatable(KEY + "ruler").getString(),
                x + Math.min(length, window.width() - 60) + 4,
                y - 4,
                MUTED,
                false);
        if (window.inside(mouseX, mouseY)) {
            int worldX = (int) Math.floor(window.worldX(mouseX));
            int worldZ = (int) Math.floor(window.worldZ(mouseY));
            graphics.drawString(
                    font, Component.translatable(KEY + "cursor", worldX, worldZ).getString(), x, y - 18, DIM, false);
        }
    }

    private void drawDock(GuiGraphics graphics) {
        BoardLayout.Rect dock = dock();
        if (dock.width() == 0) {
            return;
        }
        graphics.fill(dock.x(), dock.y(), dock.right(), dock.bottom(), 0xF00F1216);
        graphics.fill(dock.x(), dock.y(), dock.x() + 1, dock.bottom(), LINE);
        int x = dock.x() + 10;
        int width = dock.width() - 20;
        int y = dock.y() + 9;
        label(graphics, Component.translatable(KEY + "dock.layers"), x, y);
        y += 13;
        y = toggle(
                graphics,
                x,
                y,
                Component.translatable(KEY + "dock.markers"),
                showMarkers,
                () -> showMarkers = !showMarkers);
        y = toggle(
                graphics,
                x,
                y,
                Component.translatable(KEY + "dock.labels"),
                showLabels,
                () -> showLabels = !showLabels);
        y = toggle(graphics, x, y, Component.translatable(KEY + "dock.paths"), showPaths, () -> showPaths = !showPaths);
        y += 6;
        label(graphics, Component.translatable(KEY + "dock.states"), x, y);
        y += 13;
        TownMapView current = view;
        if (current == null) {
            graphics.drawString(font, Component.translatable(KEY + "loading"), x, y, DIM, false);
            return;
        }
        MapDock.Stats stats = MapDock.stats(current.rects());
        y = statRow(graphics, x, width, y, Component.translatable(KEY + "state.done"), stats.done(), GOLD);
        y = statRow(graphics, x, width, y, Component.translatable(KEY + "state.started"), stats.started(), 0xFF6FA548);
        y = statRow(graphics, x, width, y, Component.translatable(KEY + "state.empty"), stats.empty(), DIM);
        y = statRow(graphics, x, width, y, Component.translatable(KEY + "state.damaged"), stats.damaged(), 0xFFC45A3C);
        y += 10;
        label(graphics, Component.translatable(KEY + "dock.nearby"), x, y);
        y += 13;
        MapViewport window = window();
        double centerX = window.centerX();
        double centerZ = window.centerZ();
        if (Minecraft.getInstance().player != null) {
            centerX = Minecraft.getInstance().player.getX();
            centerZ = Minecraft.getInstance().player.getZ();
        }
        List<TownMapView.MapRect> nearby = MapDock.nearby(current.rects(), centerX, centerZ, 3);
        for (TownMapView.MapRect lot : nearby) {
            String name =
                    TextClip.text(font, Component.literal(PlanNames.title(lot.label(), this::planTitle)), width - 40);
            graphics.drawString(font, name, x, y, MUTED, false);
            String percent = lot.percent() + "%";
            graphics.drawString(
                    font, percent, x + width - font.width(percent), y, lot.percent() >= 100 ? GOLD : 0xFF6FA548, false);
            int barWidth = width - 10;
            graphics.fill(x, y + 10, x + barWidth, y + 12, 0xFF0C0E11);
            int filled = Math.max(0, Math.min(barWidth - 2, (barWidth - 2) * lot.percent() / 100));
            if (filled > 0) {
                graphics.fill(x + 1, y + 11, x + 1 + filled, y + 12 + 1, lot.percent() >= 100 ? GOLD : 0xFF6FA548);
            }
            y += 17;
        }
        graphics.drawString(
                font,
                TextClip.text(font, Component.translatable(KEY + "dock.note"), width),
                x,
                dock.bottom() - 24,
                DIM,
                false);
    }

    private int toggle(GuiGraphics graphics, int x, int y, Component label, boolean on, Runnable action) {
        int box = 12;
        graphics.fill(x, y, x + box, y + box, on ? 0xFF3D5227 : CHIP_BG);
        graphics.fill(x, y, x + box, y + 1, on ? 0xFF6FA548 : LINE);
        graphics.fill(x, y + box - 1, x + box, y + box, on ? 0xFF6FA548 : LINE);
        if (on) {
            graphics.drawString(font, "✓", x + 3, y + 1, 0xFFD8F0B8, false);
        }
        graphics.drawString(font, TextClip.text(font, label, 160), x + box + 5, y + 2, on ? TEXT : MUTED, false);
        hits.add(new Hit(new BoardLayout.Rect(x, y, 190, 16), action));
        return y + 18;
    }

    private int statRow(GuiGraphics graphics, int x, int width, int y, Component label, int value, int color) {
        graphics.fill(x, y + 3, x + 5, y + 8, color);
        graphics.drawString(font, label.getString(), x + 10, y, MUTED, false);
        String count = String.valueOf(value);
        graphics.drawString(font, count, x + width - font.width(count), y, TEXT, false);
        return y + 12;
    }

    private void label(GuiGraphics graphics, Component text, int x, int y) {
        graphics.drawString(font, text.getString(), x, y, DIM, false);
    }

    private String planTitle(String id) {
        String key = KEY + "lot." + id;
        String title = Component.translatableWithFallback(key, "").getString();
        return title.isEmpty() ? null : title;
    }

    private TownMapView names(TownMapView map) {
        return map.withLotLabels(id -> PlanNames.title(id, this::planTitle));
    }

    private TownMapView withPaths(TownMapView map) {
        return showPaths ? map : map.withoutPaths();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ScreenTabButtons.hotkey(keyCode, false, ScreenTabs.Tab.PLAN)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MapViewport window = window();
        if (view != null && window.inside(mouseX, mouseY)) {
            viewport = window.zoom(mouseX, mouseY, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                if (hit.rect().contains((int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && view != null && window().inside(mouseX, mouseY)) {
            viewport = window().pan(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        closeTexture();
        super.removed();
    }

    private MapViewport window() {
        MapViewport window = viewport;
        if (window != null) {
            return window;
        }
        BoardLayout.Rect map = mapArea();
        if (focused) {
            window = new MapViewport(
                    focusX, focusZ, FOCUS_SCALE, map.x(), map.y(), Math.max(1, map.width()), Math.max(1, map.height()));
            viewport = window;
            return window;
        }
        TownMapView current = view;
        TownMapView.Bounds bounds = current == null ? new TownMapView.Bounds(0, 0, 0, 0) : mapBounds(current);
        window = MapViewport.fit(bounds, map.x(), map.y(), Math.max(1, map.width()), Math.max(1, map.height()));
        if (current != null) {
            viewport = window;
        }
        return window;
    }

    private BoardLayout.Rect body() {
        return ShellLayout.of(width, height).body();
    }

    private BoardLayout.Rect toolbar() {
        return ShellLayout.of(width, height).toolbar();
    }

    private BoardLayout.Rect mapArea() {
        BoardLayout.Rect body = body();
        int dock = dockWidth(body);
        int gap = dock > 0 ? DOCK_GAP : 0;
        return new BoardLayout.Rect(body.x(), body.y(), Math.max(1, body.width() - dock - gap), body.height());
    }

    private BoardLayout.Rect dock() {
        BoardLayout.Rect body = body();
        int dock = dockWidth(body);
        return new BoardLayout.Rect(body.right() - dock, body.y(), dock, body.height());
    }

    private int dockWidth(BoardLayout.Rect body) {
        if (width < DOCK_MIN_SCREEN) {
            return 0;
        }
        return Math.min(DOCK_WIDTH, Math.max(0, body.width() - 120));
    }

    private TownMapView.Bounds mapBounds(TownMapView current) {
        return terrain.isEmpty() ? current.bounds() : current.bounds().union(terrain.bounds());
    }

    private void drawTerrain(GuiGraphics graphics, MapPaint.Raster raster, MapViewport window) {
        TerrainTexture current = texture;
        if (current == null) {
            return;
        }
        graphics.enableScissor(
                window.left(), window.top(), window.left() + window.width(), window.top() + window.height());
        graphics.blit(
                current.id(),
                raster.x1(),
                raster.y1(),
                raster.x2() - raster.x1(),
                raster.y2() - raster.y1(),
                0.0F,
                0.0F,
                current.width(),
                current.height(),
                current.width(),
                current.height());
        graphics.disableScissor();
    }

    private void closeTexture() {
        TerrainTexture current = texture;
        if (current != null) {
            current.close();
            texture = null;
        }
    }

    private void requestSnapshot() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(TownMapChannel.REQUEST)) {
            offline = true;
            answered = true;
            return;
        }
        PacketDistributor.sendToServer(new TownMapChannel.Request());
    }

    private void requestMarkers() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(TownMapChannel.MARKERS_REQUEST)) {
            PacketDistributor.sendToServer(new TownMapChannel.MarkersRequest());
        }
    }

    private static List<Component> hint(MapHint hint) {
        return List.of(
                Component.literal(hint.title()),
                Component.translatable(hint.key(), hint.args().toArray()));
    }
}
