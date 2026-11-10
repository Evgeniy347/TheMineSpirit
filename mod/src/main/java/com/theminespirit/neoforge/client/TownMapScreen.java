package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.MapHint;
import com.theminespirit.core.ui.MapHit;
import com.theminespirit.core.ui.MapLegend;
import com.theminespirit.core.ui.MapLegendPaint;
import com.theminespirit.core.ui.MapMarker;
import com.theminespirit.core.ui.MapMarkers;
import com.theminespirit.core.ui.MapPaint;
import com.theminespirit.core.ui.MapQuarry;
import com.theminespirit.core.ui.MapStyle;
import com.theminespirit.core.ui.MapViewport;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.core.ui.TerrainRaster;
import com.theminespirit.core.ui.TownMapView;
import com.theminespirit.neoforge.net.TownMapChannel;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int TOP = ScreenTabs.TOP + ScreenTabs.HEIGHT + BoardLayout.BUTTON_GAP;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TITLE_Y = 6;
    private static final int MARKER_TICKS = 20;
    private static final MapLegend.Text LEGEND_TEXT = (key, args) ->
            Component.translatable(key, Arrays.stream(args).boxed().toArray()).getString();

    private MapLegend legend = MapLegend.initial();
    private @Nullable TownMapView view;
    private TerrainRaster terrain = TerrainRaster.EMPTY;
    private @Nullable TerrainTexture texture;
    private @Nullable MapViewport viewport;
    private boolean answered;
    private boolean offline;
    private int markerAge;
    private boolean legendDrag;

    public TownMapScreen() {
        super(Component.translatable(KEY + "title"));
    }

    void show(TownMapChannel.Reply received) {
        answered = true;
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
        legend = legend.withQuarries(quarries);
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
        markerAge++;
        if (markerAge >= MARKER_TICKS) {
            markerAge = 0;
            requestMarkers();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, BoardLayout.MARGIN, TITLE_Y, TEXT);
        MapViewport window = window();
        TownMapView current = view;
        if (current == null) {
            int right = window.left() + window.width();
            graphics.fill(window.left(), window.top(), right, window.top() + window.height(), MapStyle.BACKGROUND);
            String state = offline ? "offline" : answered ? "empty" : "loading";
            graphics.drawCenteredString(
                    font, Component.translatable(KEY + state), width / 2, window.top() + window.height() / 2, TEXT);
            return;
        }
        MapPaint.Font metrics = new MapPaint.Font(font::width, font.lineHeight);
        TownMapView shown = withPaths(current);
        MapLegend.Frame legendFrame = legend.frame(window, metrics, LEGEND_TEXT);
        boolean overLegend = legendFrame.inside(mouseX, mouseY);
        Optional<MapMarker> hoveredMarker =
                overLegend ? Optional.empty() : MapMarkers.at(shown.markers(), window, metrics, mouseX, mouseY);
        Optional<TownMapView.MapRect> hovered =
                overLegend ? Optional.empty() : MapHit.lotAt(shown, window, mouseX, mouseY);
        List<MapPaint.Shape> shapes =
                new ArrayList<>(MapPaint.of(shown, Optional.of(terrain), window, hovered, metrics));
        shapes.addAll(MapMarkers.paint(shown.markers(), window, hoveredMarker, metrics));
        shapes.addAll(MapLegendPaint.of(legend, window, metrics, LEGEND_TEXT));
        if (!legend.open()) {
            shapes.addAll(MapLegendPaint.tab(window));
        }
        for (MapPaint.Shape shape : shapes) {
            switch (shape) {
                case MapPaint.Fill fill -> graphics.fill(fill.x1(), fill.y1(), fill.x2(), fill.y2(), fill.color());
                case MapPaint.Text text -> graphics.drawString(font, text.text(), text.x(), text.y(), text.color());
                case MapPaint.Raster raster -> drawTerrain(graphics, raster, window);
            }
        }
        if (overLegend) {
            return;
        }
        if (hoveredMarker.isPresent()) {
            graphics.renderComponentTooltip(font, hint(MapHint.of(hoveredMarker.orElseThrow())), mouseX, mouseY);
        } else {
            hovered.ifPresent(lot -> graphics.renderComponentTooltip(font, hint(MapHint.of(lot)), mouseX, mouseY));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MapViewport window = window();
        if (legend.open() && legend.frame(window, metrics(), LEGEND_TEXT).inside(mouseX, mouseY)) {
            return true;
        }
        if (view != null && window.inside(mouseX, mouseY)) {
            viewport = window.zoom(mouseX, mouseY, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && view != null) {
            MapViewport window = window();
            if (legend.open()) {
                MapLegend.Frame frame = legend.frame(window, metrics(), LEGEND_TEXT);
                if (frame.closeAt(mouseX, mouseY)) {
                    legend = legend.closed();
                    return true;
                }
                MapLegend.Row row = frame.rowAt(mouseX, mouseY);
                if (row != null && row.kind() == MapLegend.Kind.PATHS) {
                    legend = legend.toggledPaths();
                    return true;
                }
                if (frame.inside(mouseX, mouseY)) {
                    legendDrag = true;
                    return true;
                }
            } else if (MapLegendPaint.tabAt(window, mouseX, mouseY)) {
                legend = legend.opened();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        legendDrag = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && legendDrag && view != null) {
            legend = legend.moved((int) Math.round(dragX), (int) Math.round(dragY), window(), metrics(), LEGEND_TEXT);
            return true;
        }
        if (button == 0 && view != null) {
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

    private MapPaint.Font metrics() {
        return new MapPaint.Font(font::width, font.lineHeight);
    }

    private TownMapView withPaths(TownMapView map) {
        return legend.pathsVisible() ? map : map.withoutPaths();
    }

    private MapViewport window() {
        MapViewport window = viewport;
        if (window != null) {
            return window;
        }
        int areaWidth = Math.max(1, width - 2 * BoardLayout.MARGIN);
        int areaHeight = Math.max(1, height - TOP - BoardLayout.MARGIN);
        TownMapView current = view;
        TownMapView.Bounds bounds = current == null ? new TownMapView.Bounds(0, 0, 0, 0) : mapBounds(current);
        window = MapViewport.fit(bounds, BoardLayout.MARGIN, TOP, areaWidth, areaHeight);
        if (current != null) {
            viewport = window;
        }
        return window;
    }

    private TownMapView.Bounds mapBounds(TownMapView current) {
        return terrain.isEmpty() ? current.bounds() : current.bounds().union(terrain.bounds());
    }

    private void drawTerrain(GuiGraphics graphics, MapPaint.Raster raster, MapViewport window) {
        TerrainTexture current = texture;
        if (current == null) {
            return;
        }
        int right = window.left() + window.width();
        int bottom = window.top() + window.height();
        graphics.enableScissor(window.left(), window.top(), right, bottom);
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
