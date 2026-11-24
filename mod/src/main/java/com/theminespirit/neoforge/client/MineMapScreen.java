package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.MapHint;
import com.theminespirit.core.ui.MapMarker;
import com.theminespirit.core.ui.MapMarkers;
import com.theminespirit.core.ui.MapPaint;
import com.theminespirit.core.ui.MapViewport;
import com.theminespirit.core.ui.MineDock;
import com.theminespirit.core.ui.MineHit;
import com.theminespirit.core.ui.MinePaint;
import com.theminespirit.core.ui.MineSnapshot;
import com.theminespirit.core.ui.MineStyle;
import com.theminespirit.core.ui.MineToolbar;
import com.theminespirit.core.ui.RefreshClock;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.core.ui.ShellLayout;
import com.theminespirit.core.ui.TownMapView;
import com.theminespirit.neoforge.net.MineMapChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class MineMapScreen extends Screen {
    private static final String KEY = "theminespirit.mine.";
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF98A0AB;
    private static final int DIM = 0xFF6B7480;
    private static final int GOLD = 0xFFE0B050;
    private static final int LINE = 0xFF2B323B;
    private static final int CHIP_BG = 0xFF171B21;
    private static final int CHIP_ACTIVE = 0xFF262116;
    private static final int DOCK_WIDTH = 190;
    private static final int DOCK_MIN_SCREEN = 760;
    private static final int DOCK_GAP = 8;
    private static final int STRIP_WIDTH = 30;
    private static final int MARKER_TICKS = 20;

    private @Nullable MineSnapshot view;
    private @Nullable MapViewport viewport;
    private boolean answered;
    private boolean offline;
    private boolean showOreOnly;
    private boolean sliderDrag;
    private BoardLayout.@Nullable Rect sliderRect;
    private int markerAge;
    private int ageTicks;
    private RefreshClock clock = RefreshClock.NEVER;
    private int requestedY = MineSnapshot.ANY_Y;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(BoardLayout.Rect rect, Runnable action) {}

    public MineMapScreen() {
        super(Component.translatable(KEY + "title"));
    }

    void show(MineMapChannel.Reply received) {
        boolean first = view == null;
        answered = true;
        clock = RefreshClock.at(ageTicks);
        view = received.view().orElse(null);
        if (first || view == null) {
            viewport = null;
        }
        if (view != null) {
            requestedY = view.y();
        }
    }

    void showMarkers(List<MapMarker> markers) {
        MineSnapshot current = view;
        if (current != null) {
            view = current.withMarkers(markers);
        }
    }

    @Override
    protected void init() {
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.MINE);
        viewport = null;
        if (!answered) {
            request(requestedY);
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
                graphics, font, Component.translatable(ShellPainter.KEY + "hint.mine"), clock, ageTicks, width, height);
        hits.clear();
        sliderRect = null;
        drawToolbar(graphics, mouseX, mouseY);
        MapViewport window = window();
        MineSnapshot current = view;
        if (current == null) {
            drawPlaceholder(graphics, window);
            drawDock(graphics);
            return;
        }
        MapPaint.Font metrics = new MapPaint.Font(font::width, font.lineHeight);
        Optional<MapMarker> hoveredMarker = MapMarkers.at(current.markers(), window, metrics, mouseX, mouseY);
        Optional<MineSnapshot.Rect> hovered =
                hoveredMarker.isPresent() ? Optional.empty() : MineHit.rectAt(current, window, mouseX, mouseY);
        List<MapPaint.Shape> shapes = new ArrayList<>(MinePaint.of(current, window, hovered, metrics, showOreOnly));
        shapes.addAll(MapMarkers.paint(current.markers(), window, hoveredMarker, metrics));
        for (MapPaint.Shape shape : shapes) {
            drawShape(graphics, shape);
        }
        drawLayerStrip(graphics, window, current);
        drawDock(graphics);
        if (hoveredMarker.isPresent()) {
            graphics.renderComponentTooltip(font, hint(MapHint.of(hoveredMarker.orElseThrow())), mouseX, mouseY);
        } else if (hovered.isPresent()) {
            graphics.renderComponentTooltip(font, hint(hovered.orElseThrow()), mouseX, mouseY);
        }
    }

    private void drawToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        BoardLayout.Rect toolbar = toolbar();
        int y = toolbar.y();
        int x = toolbar.x();
        MineSnapshot current = view;
        if (current == null) {
            graphics.drawString(font, Component.translatable(KEY + "loading"), x, y + 4, MUTED, false);
            return;
        }
        MineToolbar bar = MineToolbar.of(current.minY(), current.maxY(), requestedY);
        x += chip(graphics, x, y, Component.literal("−"), false, () -> changeHeight(-1));
        Component label = Component.translatable(KEY + "height.value", bar.y());
        int labelWidth = font.width(label) + 12;
        graphics.fill(x, y, x + labelWidth, y + 16, CHIP_ACTIVE);
        graphics.fill(x, y, x + labelWidth, y + 1, GOLD);
        graphics.fill(x, y + 15, x + labelWidth, y + 16, GOLD);
        graphics.drawString(font, label.getString(), x + 6, y + 4, 0xFFF0DFAE, false);
        x += labelWidth + 5;
        x += chip(graphics, x, y, Component.literal("+"), false, () -> changeHeight(1));
        int sliderWidth = 110;
        BoardLayout.Rect track = new BoardLayout.Rect(x, y, sliderWidth, 16);
        sliderRect = track;
        graphics.fill(x, y + 7, x + sliderWidth, y + 9, 0xFF2A3038);
        int handleX = x + (int) Math.round(bar.fraction() * sliderWidth);
        graphics.fill(x, y + 7, handleX, y + 9, GOLD);
        graphics.fill(handleX - 2, y + 3, handleX + 2, y + 13, 0xFFF0DCA8);
        hits.add(new Hit(track, () -> setFromSlider(mouseX)));
        x += sliderWidth + 8;
        graphics.drawString(
                font,
                Component.translatable(KEY + "layer", bar.layer(), bar.layers()).getString(),
                x,
                y + 4,
                MUTED,
                false);
        x += font.width(Component.translatable(KEY + "layer", bar.layer(), bar.layers())) + 8;
        graphics.drawString(
                font,
                Component.translatable(KEY + "range", bar.minY(), bar.maxY()).getString(),
                x,
                y + 4,
                DIM,
                false);
        x += font.width(Component.translatable(KEY + "range", bar.minY(), bar.maxY())) + 10;
        x += chip(graphics, x, y, Component.translatable(KEY + "fit"), false, () -> viewport = null);
        int percent = (int) Math.round(window().scale() / Math.max(0.0001, homeScale(window())) * 100);
        graphics.drawString(font, percent + "%", x, y + 4, DIM, false);
        if (track.contains(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(KEY + "height.value", requestedY), mouseX, mouseY);
        }
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

    private void setFromSlider(double mouseX) {
        BoardLayout.Rect track = sliderRect;
        MineSnapshot current = view;
        if (track == null || current == null) {
            return;
        }
        MineToolbar bar = MineToolbar.of(current.minY(), current.maxY(), requestedY);
        double fraction = (mouseX - track.x()) / Math.max(1.0, track.width());
        requestedY = bar.yAt(fraction);
    }

    private void drawLayerStrip(GuiGraphics graphics, MapViewport window, MineSnapshot current) {
        MineToolbar bar = MineToolbar.of(current.minY(), current.maxY(), requestedY);
        int x = window.left() + 4;
        int top = window.top() + 4;
        int height = window.height() - 8;
        int rowHeight = Math.max(1, height / bar.layers());
        graphics.fill(x, top, x + STRIP_WIDTH - 6, top + height, 0xB0101216);
        for (int layer = 0; layer < bar.layers(); layer++) {
            int layerY = current.maxY() - layer;
            int rowY = top + layer * rowHeight;
            boolean near = Math.abs(layerY - requestedY) < 4;
            int color = layerY == requestedY ? GOLD : near ? 0xFF3A3222 : (layer % 8 == 0 ? 0xFF20242A : 0xFF161A1F);
            graphics.fill(x + 2, rowY, x + STRIP_WIDTH - 8, rowY + Math.max(1, rowHeight - 1), color);
            if (layer % 8 == 0 || layerY == requestedY) {
                graphics.drawString(
                        font, String.valueOf(layerY), x + 3, rowY, layerY == requestedY ? 0xFFF0DCA8 : DIM, false);
            }
        }
        if (rowHeight > 0) {
            int marker = top + (bar.layers() - bar.layer()) * rowHeight;
            graphics.fill(x + 2, marker - 1, x + STRIP_WIDTH - 8, marker + 1, GOLD);
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
        MineSnapshot current = view;
        if (current == null) {
            graphics.drawString(font, Component.translatable(KEY + "loading"), x, y, DIM, false);
            return;
        }
        graphics.drawString(font, Component.translatable(KEY + "dock.layer").getString(), x, y, DIM, false);
        y += 13;
        graphics.drawString(
                font, Component.translatable(KEY + "height.value", current.y()).getString(), x, y, TEXT, false);
        y += 14;
        var counts = MineDock.counts(current);
        for (MineSnapshot.Kind kind : MineSnapshot.Kind.values()) {
            graphics.fill(x, y + 3, x + 7, y + 10, MineStyle.fill(kind));
            graphics.drawString(font, kindLabel(kind), x + 12, y, MUTED, false);
            String count = String.valueOf(counts.getOrDefault(kind, 0));
            graphics.drawString(font, count, x + width - font.width(count), y, TEXT, false);
            y += 13;
        }
        y += 4;
        int toggleWidth = 120;
        graphics.fill(x, y, x + 12, y + 12, showOreOnly ? 0xFF3D5227 : CHIP_BG);
        graphics.fill(x, y, x + 12, y + 1, showOreOnly ? 0xFF6FA548 : LINE);
        if (showOreOnly) {
            graphics.drawString(font, "✓", x + 3, y + 1, 0xFFD8F0B8, false);
        }
        graphics.drawString(
                font,
                Component.translatable(KEY + "dock.ore_only").getString(),
                x + 17,
                y + 2,
                showOreOnly ? TEXT : MUTED,
                false);
        hits.add(new Hit(new BoardLayout.Rect(x, y, toggleWidth, 14), () -> showOreOnly = !showOreOnly));
        y += 22;
        graphics.drawString(font, Component.translatable(KEY + "dock.tasks").getString(), x, y, DIM, false);
        y += 13;
        for (MineSnapshot.Rect rect : current.rects()) {
            if (y + 22 > dock.bottom() - 30) {
                break;
            }
            String line = TextClip.text(font, Component.literal(rect.label()), width - 34);
            graphics.drawString(font, line, x, y, MUTED, false);
            String percent = rect.percent() + "%";
            graphics.drawString(
                    font,
                    percent,
                    x + width - font.width(percent),
                    y,
                    rect.percent() >= 100 ? GOLD : 0xFF6FA548,
                    false);
            y += 11;
        }
        graphics.drawString(
                font,
                TextClip.text(font, Component.translatable(KEY + "dock.hint.wheel"), width),
                x,
                dock.bottom() - 26,
                DIM,
                false);
        graphics.drawString(
                font,
                TextClip.text(font, Component.translatable(KEY + "dock.hint.shift"), width),
                x,
                dock.bottom() - 15,
                DIM,
                false);
    }

    private String kindLabel(MineSnapshot.Kind kind) {
        return Component.translatable(KEY + "kind." + kind.name().toLowerCase(Locale.ROOT))
                .getString();
    }

    private void drawPlaceholder(GuiGraphics graphics, MapViewport window) {
        graphics.fill(
                window.left(),
                window.top(),
                window.left() + window.width(),
                window.top() + window.height(),
                0xFF15130F);
        String state = offline ? "offline" : answered ? "empty" : "loading";
        graphics.drawCenteredString(
                font,
                Component.translatable(KEY + state),
                window.left() + window.width() / 2,
                window.top() + window.height() / 2,
                TEXT);
    }

    private void drawShape(GuiGraphics graphics, MapPaint.Shape shape) {
        switch (shape) {
            case MapPaint.Fill fill -> graphics.fill(fill.x1(), fill.y1(), fill.x2(), fill.y2(), fill.color());
            case MapPaint.Text text -> graphics.drawString(font, text.text(), text.x(), text.y(), text.color());
            case MapPaint.Raster ignored -> {}
        }
    }

    private MapViewport window() {
        MapViewport window = viewport;
        if (window != null) {
            return window;
        }
        BoardLayout.Rect map = mapArea();
        MineSnapshot current = view;
        TownMapView.Bounds bounds = current == null ? new TownMapView.Bounds(0, 0, 0, 0) : current.bounds();
        window = MapViewport.fit(bounds, map.x(), map.y(), Math.max(1, map.width()), Math.max(1, map.height()));
        if (current != null) {
            viewport = window;
        }
        return window;
    }

    private double homeScale(MapViewport window) {
        MineSnapshot current = view;
        if (current == null) {
            return window.scale();
        }
        return MapViewport.fit(
                        current.bounds(),
                        window.left(),
                        window.top(),
                        Math.max(1, window.width()),
                        Math.max(1, window.height()))
                .scale();
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

    private void changeHeight(int delta) {
        MineSnapshot current = view;
        if (current == null) {
            return;
        }
        MineToolbar bar = MineToolbar.of(
                current.minY(), current.maxY(), requestedY == MineSnapshot.ANY_Y ? current.y() : requestedY);
        int y = bar.step(delta).y();
        if (y != requestedY) {
            requestedY = y;
            request(y);
        }
    }

    private void request(int y) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(MineMapChannel.REQUEST)) {
            offline = true;
            answered = true;
            return;
        }
        PacketDistributor.sendToServer(new MineMapChannel.Request(y));
    }

    private void requestMarkers() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(MineMapChannel.MARKERS_REQUEST)) {
            PacketDistributor.sendToServer(new MineMapChannel.MarkersRequest());
        }
    }

    private static List<Component> hint(MapHint hint) {
        return List.of(
                Component.literal(hint.title()),
                Component.translatable(hint.key(), hint.args().toArray()));
    }

    private static List<Component> hint(MineSnapshot.Rect rect) {
        return List.of(Component.literal(rect.label()), Component.translatable(KEY + "progress", rect.percent()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MapViewport window = window();
        if (view != null && window.inside(mouseX, mouseY)) {
            if (Screen.hasShiftDown()) {
                changeHeight(scrollY > 0 ? 1 : -1);
            } else {
                viewport = window.zoom(mouseX, mouseY, scrollY);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                if (hit.rect().contains((int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
                    sliderDrag = hit.rect().equals(sliderRect);
                    hit.action().run();
                    return true;
                }
            }
            BoardLayout.Rect map = mapArea();
            if (view != null && map.contains((int) mouseX, (int) mouseY) && mouseX < map.x() + STRIP_WIDTH) {
                MineToolbar bar = MineToolbar.of(view.minY(), view.maxY(), requestedY);
                double fraction = (mouseY - map.y()) / Math.max(1.0, map.height());
                requestedY = bar.yAt(fraction);
                request(requestedY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && sliderDrag) {
            setFromSlider(mouseX);
            return true;
        }
        if (button == 0 && view != null) {
            viewport = window().pan(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (sliderDrag) {
            sliderDrag = false;
            setFromSlider(mouseX);
            request(requestedY);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ScreenTabButtons.hotkey(keyCode, false, ScreenTabs.Tab.MINE)) {
            return true;
        }
        if (view != null && keyCode == GLFW.GLFW_KEY_UP) {
            changeHeight(1);
            return true;
        }
        if (view != null && keyCode == GLFW.GLFW_KEY_DOWN) {
            changeHeight(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
