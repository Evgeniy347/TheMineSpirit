package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.MapHint;
import com.theminespirit.core.ui.MapMarker;
import com.theminespirit.core.ui.MapMarkers;
import com.theminespirit.core.ui.MapPaint;
import com.theminespirit.core.ui.MapViewport;
import com.theminespirit.core.ui.MineHit;
import com.theminespirit.core.ui.MinePaint;
import com.theminespirit.core.ui.MineSnapshot;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.core.ui.TownMapView;
import com.theminespirit.neoforge.net.MineMapChannel;
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
import org.lwjgl.glfw.GLFW;

public final class MineMapScreen extends Screen {
    private static final String KEY = "theminespirit.mine.";
    private static final int TOP = ScreenTabs.TOP + ScreenTabs.HEIGHT + BoardLayout.BUTTON_GAP;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TITLE_Y = 6;
    private static final int MARKER_TICKS = 20;
    private @Nullable MineSnapshot view;
    private @Nullable MapViewport viewport;
    private boolean answered;
    private boolean offline;
    private int markerAge;
    private int requestedY = MineSnapshot.ANY_Y;

    public MineMapScreen() {
        super(Component.translatable(KEY + "title"));
    }

    void show(MineMapChannel.Reply received) {
        boolean first = view == null;
        answered = true;
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
        MineSnapshot current = view;
        if (current == null) {
            drawPlaceholder(graphics, window);
            return;
        }
        MapPaint.Font metrics = new MapPaint.Font(font::width, font.lineHeight);
        Optional<MapMarker> hoveredMarker = MapMarkers.at(current.markers(), window, metrics, mouseX, mouseY);
        Optional<MineSnapshot.Rect> hovered =
                hoveredMarker.isPresent() ? Optional.empty() : MineHit.rectAt(current, window, mouseX, mouseY);
        List<MapPaint.Shape> shapes = new ArrayList<>(MinePaint.of(current, window, hovered, metrics));
        shapes.addAll(MapMarkers.paint(current.markers(), window, hoveredMarker, metrics));
        for (MapPaint.Shape shape : shapes) {
            drawShape(graphics, shape);
        }
        Component height = Component.translatable(KEY + "height", current.y(), current.minY(), current.maxY());
        int heightWidth = font.width(height) + 6;
        graphics.fill(window.left() + 2, window.top() + 2, window.left() + heightWidth, window.top() + 14, 0xAA000000);
        graphics.drawString(font, height, window.left() + 5, window.top() + 4, TEXT);
        if (hoveredMarker.isPresent()) {
            graphics.renderComponentTooltip(font, hint(MapHint.of(hoveredMarker.orElseThrow())), mouseX, mouseY);
        } else {
            hovered.ifPresent(rect -> graphics.renderComponentTooltip(font, hint(rect), mouseX, mouseY));
        }
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && view != null) {
            viewport = window().pan(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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

    private void drawPlaceholder(GuiGraphics graphics, MapViewport window) {
        int right = window.left() + window.width();
        graphics.fill(window.left(), window.top(), right, window.top() + window.height(), 0xFF15130F);
        String state = offline ? "offline" : answered ? "empty" : "loading";
        graphics.drawCenteredString(
                font, Component.translatable(KEY + state), width / 2, window.top() + window.height() / 2, TEXT);
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
        int areaWidth = Math.max(1, width - 2 * BoardLayout.MARGIN);
        int areaHeight = Math.max(1, height - TOP - BoardLayout.MARGIN);
        MineSnapshot current = view;
        TownMapView.Bounds bounds = current == null ? new TownMapView.Bounds(0, 0, 0, 0) : current.bounds();
        window = MapViewport.fit(bounds, BoardLayout.MARGIN, TOP, areaWidth, areaHeight);
        if (current != null) {
            viewport = window;
        }
        return window;
    }

    private void changeHeight(int delta) {
        MineSnapshot current = view;
        if (current == null) {
            return;
        }
        int y = Math.max(current.minY(), Math.min(current.maxY(), current.y() + delta));
        if (y != current.y()) {
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
}
