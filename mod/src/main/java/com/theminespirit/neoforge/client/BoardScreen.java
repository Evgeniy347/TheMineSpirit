package com.theminespirit.neoforge.client;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.ui.BoardColumns;
import com.theminespirit.core.ui.BoardDock;
import com.theminespirit.core.ui.BoardFeed;
import com.theminespirit.core.ui.BoardFilter;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.BoardMaterials;
import com.theminespirit.core.ui.BoardPageQuery;
import com.theminespirit.core.ui.BoardSearch;
import com.theminespirit.core.ui.BoardStage;
import com.theminespirit.core.ui.BoardTile;
import com.theminespirit.core.ui.BoardWaitReasons;
import com.theminespirit.core.ui.RefreshClock;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.core.ui.SearchCount;
import com.theminespirit.core.ui.api.BoardTileGroup;
import com.theminespirit.neoforge.net.BoardViewChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

public final class BoardScreen extends Screen {
    private static final String KEY = ModIdentity.MOD_ID + ".board.";
    private static final int SCROLL_STEP = 18;
    private static final int REFRESH_TICKS = 20;
    private static final int SEARCH_HEIGHT = 14;
    private static final int CHIP_HEIGHT = 16;
    private static final int MUTED = 0xFF98A0AB;
    private static final int TEXT = 0xFFE8ECF1;
    private static final int DIM = 0xFF6B7480;
    private static final int GOLD = 0xFFE0B050;
    private static final int LINE = 0xFF2B323B;
    private static final int CHIP_BG = 0xFF171B21;
    private static final int ACTIVE_BG = 0xFF262116;
    private static final List<BoardTileGroup> GROUPS = List.of(BoardTileGroup.values());

    private BoardLayout layout = BoardLayout.of(0, 0);
    private final BoardFeed feed = new BoardFeed();
    private @Nullable BoardStage stage;
    private List<BoardColumns.BoardColumn> columns = List.of();
    private List<List<BoardTile>> slotTiles = List.of();
    private Map<BoardTileGroup, Integer> totals = Map.of();
    private Map<String, BoardTile> tilesById = Map.of();
    private SearchCount searchCount = new SearchCount(0, 0);
    private Map<String, List<BoardWaitReasons.Wait>> waitReasons = Map.of();
    private final Map<BoardTileGroup, Double> scroll = new EnumMap<>(BoardTileGroup.class);
    private final List<Hit> hits = new ArrayList<>();
    private @Nullable EditBox searchBox;
    private @Nullable BoardTileGroup focusGroup;
    private @Nullable String selectedId;
    private String query = "";
    private Sort sort = Sort.PRIORITY;
    private boolean offline;
    private int refreshTicks;
    private int ageTicks;
    private RefreshClock clock = RefreshClock.NEVER;

    private enum Sort {
        PRIORITY,
        NAME,
        TIME
    }

    private record Hit(BoardLayout.Rect rect, Runnable action) {}

    public BoardScreen() {
        super(Component.translatable(KEY + "title"));
    }

    public void show(BoardViewChannel.Reply reply) {
        stage = reply.stage();
        feed.apply(reply.page());
        clock = RefreshClock.at(ageTicks);
        rebuild();
    }

    @Override
    protected void init() {
        layout = layoutFor(width, height);
        searchBox = addRenderableWidget(searchBox());
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.BOARD);
        if (!feed.seen()) {
            requestAll();
        }
        rebuild();
    }

    private EditBox searchBox() {
        BoardLayout.Rect toolbar = layout.toolbar();
        int searchWidth = layout.screenWidth() < BoardLayout.WIDE_MIN_WIDTH ? 130 : 150;
        int x = toolbar.right() - searchWidth;
        EditBox box =
                new EditBox(font, x, toolbar.y(), searchWidth, SEARCH_HEIGHT, Component.translatable(KEY + "search"));
        box.setHint(Component.translatable(KEY + "search.placeholder"));
        box.setMaxLength(64);
        box.setValue(query);
        box.setResponder(text -> {
            if (BoardSearch.entered(query, text)) {
                requestAll();
            }
            query = text;
            rebuild();
        });
        return box;
    }

    private BoardLayout layoutFor(int screenWidth, int screenHeight) {
        if (BoardLayout.columnsFor(screenWidth) == 1) {
            BoardTileGroup chosen = focusGroup == null ? GROUPS.get(0) : focusGroup;
            return BoardLayout.of(screenWidth, screenHeight, List.of(chosen));
        }
        return BoardLayout.of(screenWidth, screenHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ShellPainter.titles(
                graphics, font, title, Component.translatable(stage == null ? KEY + "stage.none" : stage.nameKey()));
        ShellPainter.footer(
                graphics,
                font,
                Component.translatable(ShellPainter.KEY + "hint.board"),
                clock,
                ageTicks,
                width,
                height);
        hits.clear();
        drawToolbar(graphics);
        BoardTileView.Hover hovered = null;
        if (!feed.seen() || totalAll() == 0) {
            graphics.drawString(
                    font,
                    Component.translatable(KEY + (offline ? "offline" : !feed.seen() ? "loading" : "empty")),
                    layout.window().x() + 8,
                    layout.window().y() + 8,
                    MUTED,
                    false);
        } else if (filteredEmpty()) {
            BoardLayout.Rect window = layout.window();
            graphics.drawString(
                    font, Component.translatable(KEY + "filtered"), window.x() + 8, window.y() + 8, MUTED, false);
            button(
                    graphics,
                    font,
                    window.x() + 8,
                    window.y() + 24,
                    90,
                    Component.translatable(KEY + "search.reset"),
                    this::clearQuery);
        } else {
            hovered = BoardColumnView.render(
                    graphics,
                    font,
                    new BoardColumnView.Context(layout, slotTiles, totals, waitReasons, tilesById),
                    scroll,
                    mouseX,
                    mouseY,
                    selectedId);
        }
        drawDock(graphics);
        if (hovered != null) {
            List<Component> lines = hovered.material() == null
                    ? BoardTileView.tooltip(hovered.tile(), waitReasons, tilesById)
                    : BoardTileView.materialTooltip(hovered.material());
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void drawToolbar(GuiGraphics graphics) {
        BoardLayout.Rect toolbar = layout.toolbar();
        int x = toolbar.x();
        int y = toolbar.y();
        int all = totalAll();
        x += chip(
                graphics,
                font,
                x,
                y,
                Component.translatable(KEY + "chip.all", all),
                focusGroup == null,
                -1,
                () -> setFocus(null));
        for (BoardTileGroup group : GROUPS) {
            int count = totals.getOrDefault(group, 0);
            x += chip(
                    graphics,
                    font,
                    x,
                    y,
                    Component.translatable(KEY + "group." + group.name().toLowerCase(Locale.ROOT))
                            .copy()
                            .append(" " + count),
                    focusGroup == group,
                    BoardTileView.groupColor(group),
                    () -> setFocus(focusGroup == group ? null : group));
        }
        int searchLeft = toolbar.right() - (layout.screenWidth() < BoardLayout.WIDE_MIN_WIDTH ? 130 : 150);
        if (!query.isEmpty()) {
            int free = searchLeft - x - 8;
            if (free >= 90) {
                Component found =
                        Component.translatable(KEY + "search.found", searchCount.found(), searchCount.loaded());
                String text = TextClip.text(font, found, free);
                graphics.drawString(font, text, searchLeft - 6 - font.width(text), y + 4, MUTED, false);
            }
        }
        if (!query.isEmpty() && searchBox != null) {
            int clearX = searchLeft + searchBox.getWidth() - 11;
            graphics.drawString(font, "×", clearX + 1, y + 3, MUTED, false);
            hits.add(new Hit(new BoardLayout.Rect(clearX - 2, y, 13, CHIP_HEIGHT), () -> {
                if (searchBox != null) {
                    searchBox.setValue("");
                }
                query = "";
                rebuild();
            }));
        }
        if (layout.screenWidth() >= BoardLayout.WIDE_MIN_WIDTH) {
            int spiritWidth = font.width(spiritLabel()) + 18;
            int spiritX = searchLeft - spiritWidth - 5;
            chip(graphics, font, spiritX, y, Component.literal(spiritLabel()), false, -1, this::cycleSpirit);
            int sortWidth = font.width(sortLabel()) + 18;
            chip(
                    graphics,
                    font,
                    spiritX - sortWidth - 5,
                    y,
                    Component.literal(sortLabel()),
                    false,
                    -1,
                    this::cycleSort);
        }
    }

    private int chip(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            Component label,
            boolean active,
            int dotColor,
            Runnable action) {
        int width = font.width(label) + 12 + (dotColor >= 0 ? 9 : 0);
        graphics.fill(x, y, x + width, y + CHIP_HEIGHT, active ? ACTIVE_BG : CHIP_BG);
        graphics.fill(x, y, x + width, y + 1, active ? GOLD : LINE);
        graphics.fill(x, y + CHIP_HEIGHT - 1, x + width, y + CHIP_HEIGHT, active ? GOLD : LINE);
        graphics.fill(x, y, x + 1, y + CHIP_HEIGHT, active ? GOLD : LINE);
        graphics.fill(x + width - 1, y, x + width, y + CHIP_HEIGHT, active ? GOLD : LINE);
        int textX = x + 6;
        if (dotColor >= 0) {
            graphics.fill(textX, y + 6, textX + 5, y + 11, dotColor);
            textX += 9;
        }
        graphics.drawString(
                font,
                TextClip.text(font, label, width - (textX - x) - 4),
                textX,
                y + 4,
                active ? 0xFFF0DFAE : MUTED,
                false);
        hits.add(new Hit(new BoardLayout.Rect(x, y, width, CHIP_HEIGHT), action));
        return width + 5;
    }

    private void drawDock(GuiGraphics graphics) {
        if (!layout.hasDock()) {
            return;
        }
        BoardLayout.Rect panel = layout.dock();
        BoardDock dock = BoardDock.of(panel);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF00F1216);
        graphics.fill(panel.x(), panel.y(), panel.x() + 1, panel.bottom(), LINE);
        int x = dock.content().x();
        int y = dock.content().y();
        appendText(graphics, Component.translatable(KEY + "dock.title"), x, y, DIM);
        y += 13;
        BoardTile tile = selectedTile();
        if (tile == null) {
            appendText(graphics, Component.translatable(KEY + "dock.nothing"), x, y, MUTED);
            return;
        }
        appendText(graphics, BoardTileTexts.taskText(tile), x, y, TEXT);
        y += 13;
        appendText(graphics, BoardTileTexts.detailsText(tile), x, y, MUTED);
        y += 15;
        appendText(graphics, Component.translatable(KEY + "dock.materials"), x, y, DIM);
        y += 12;
        List<BoardMaterials.Material> materials = BoardMaterials.of(tile);
        if (materials.isEmpty()) {
            appendText(graphics, Component.translatable(KEY + "dock.no_materials"), x, y, DIM);
            y += 15;
        }
        for (BoardMaterials.Material material : materials) {
            var stack = BoardTileView.stack(material.item());
            if (stack != null) {
                graphics.renderItem(stack, x, y);
            }
            boolean bad = BoardMaterials.shortage(material) || material.kind() == BoardMaterials.Kind.MISSING;
            appendText(
                    graphics,
                    Component.literal(BoardTileTexts.itemName(material.item()).getString()
                            + " "
                            + (material.have() != null
                                    ? material.have() + "/" + material.count()
                                    : "×" + material.count())),
                    x + (stack == null ? 0 : 18),
                    y + 2,
                    bad ? 0xFFFF9C9C : MUTED);
            y += 15;
            if (y + 60 > panel.bottom()) {
                break;
            }
        }
        y += 4;
        if (!tile.dependsOn().isEmpty()) {
            appendText(graphics, Component.translatable(KEY + "dock.depends"), x, y, DIM);
            y += 12;
            BoardTile dependency = tilesById.get(tile.dependsOn().get(0));
            appendText(
                    graphics,
                    dependency == null
                            ? Component.literal(tile.dependsOn().get(0))
                            : BoardTileTexts.taskText(dependency),
                    x,
                    y,
                    MUTED);
            y += 14;
        }
        appendText(graphics, Component.translatable(KEY + "dock.time"), x, y, DIM);
        y += 12;
        Component time = BoardTileTexts.timeText(tile);
        Component spent = BoardTileTexts.spentText(tile);
        if (time == null && spent == null) {
            appendText(graphics, Component.literal("—"), x, y, MUTED);
        } else {
            if (time != null) {
                appendText(graphics, time, x, y, MUTED);
                y += 11;
            }
            if (spent != null) {
                appendText(graphics, spent, x, y, MUTED);
            }
        }
        BoardLayout.Rect map = dock.mapButton();
        BoardLayout.Rect tree = dock.treeButton();
        button(
                graphics,
                font,
                map.x(),
                map.y(),
                map.width(),
                Component.translatable(KEY + "dock.map"),
                () -> Minecraft.getInstance().setScreen(new TownMapScreen()));
        button(
                graphics,
                font,
                tree.x(),
                tree.y(),
                tree.width(),
                Component.translatable(KEY + "dock.tree"),
                () -> Minecraft.getInstance().setScreen(new SettlementTreeScreen(tile.id())));
    }

    private void button(GuiGraphics graphics, Font font, int x, int y, int width, Component label, Runnable action) {
        graphics.fill(x, y, x + width, y + 16, 0xFF171B21);
        graphics.fill(x, y, x + width, y + 1, LINE);
        graphics.fill(x, y + 15, x + width, y + 16, LINE);
        graphics.drawString(font, TextClip.text(font, label, width - 6), x + 4, y + 4, 0xFFE8ECF1, false);
        hits.add(new Hit(new BoardLayout.Rect(x, y, width, 16), action));
    }

    private void appendText(GuiGraphics graphics, Component text, int x, int y, int color) {
        int width = layout.hasDock() ? layout.dock().width() - 20 : 200;
        graphics.drawString(font, TextClip.text(font, text, width), x, y, color, false);
    }

    private @Nullable BoardTile selectedTile() {
        BoardTile tile = selectedId == null ? null : tilesById.get(selectedId);
        if (tile != null) {
            return tile;
        }
        for (List<BoardTile> tiles : slotTiles) {
            if (!tiles.isEmpty()) {
                return tiles.get(0);
            }
        }
        return null;
    }

    private void setFocus(@Nullable BoardTileGroup group) {
        focusGroup = group;
        rebuildWidgets();
    }

    private void cycleSpirit() {
        applyFilter(feed.filter().nextSpiritNames(feed.spirits()));
    }

    private void cycleSort() {
        sort = switch (sort) {
            case PRIORITY -> Sort.NAME;
            case NAME -> Sort.TIME;
            case TIME -> Sort.PRIORITY;
        };
        rebuild();
    }

    private String spiritLabel() {
        return switch (feed.filter().spirit()) {
                    case ANY -> Component.translatable(KEY + "filter.all").getString();
                    case NOBODY -> Component.translatable(KEY + "filter.nobody").getString();
                    case NAMED -> String.valueOf(feed.filter().spiritName());
                }
                + " ▾";
    }

    private String sortLabel() {
        return Component.translatable(KEY + "sort." + sort.name().toLowerCase(Locale.ROOT))
                        .getString() + " ▾";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : hits) {
            if (hit.rect().contains((int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
                hit.action().run();
                return true;
            }
        }
        int slot = layout.slotAt(mouseX, mouseY);
        if (slot >= 0) {
            List<BoardTile> tiles = slotTiles.get(slot);
            if (!tiles.isEmpty()) {
                double localY = mouseY
                        - layout.list(slot).y()
                        + scrolled(layout.slots().get(slot).group());
                int index = (int) (localY / (BoardLayout.TILE_HEIGHT + BoardLayout.TILE_GAP));
                if (index >= 0 && index < tiles.size()) {
                    selectedId = tiles.get(index).id();
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int slot = layout.slotAt(mouseX, mouseY);
        if (slot < 0) {
            return false;
        }
        BoardTileGroup group = layout.slots().get(slot).group();
        setScrolled(group, Mth.clamp(scrolled(group) - scrollY * SCROLL_STEP, 0, maxScroll(group)));
        if (atBottom(group)) {
            ask(group, true);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ScreenTabButtons.hotkey(keyCode, searchBox != null && searchBox.isFocused(), ScreenTabs.Tab.BOARD)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        ageTicks++;
        if (!feed.seen() || offline) {
            return;
        }
        if (++refreshTicks >= REFRESH_TICKS) {
            refreshTicks = 0;
            for (BoardTileGroup group : GROUPS) {
                ask(group, false);
            }
        }
        for (BoardTileGroup group : GROUPS) {
            if (atBottom(group)) {
                ask(group, true);
            }
        }
    }

    private void applyFilter(BoardFilter next) {
        scroll.clear();
        feed.filter(next);
        rebuild();
        requestAll();
    }

    private void requestAll() {
        for (BoardTileGroup group : GROUPS) {
            ask(group, false);
        }
    }

    private void ask(BoardTileGroup group, boolean grow) {
        if (feed.pending(group)) {
            return;
        }
        BoardPageQuery request = feed.query(group, grow);
        if (request == null) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(BoardViewChannel.REQUEST)) {
            offline = true;
            return;
        }
        offline = false;
        feed.markPending(group);
        PacketDistributor.sendToServer(new BoardViewChannel.Request(request));
    }

    private boolean atBottom(BoardTileGroup group) {
        if (feed.complete(group)) {
            return false;
        }
        return scrolled(group) >= maxScroll(group) - 2;
    }

    private void rebuild() {
        columns = feed.columns();
        Map<String, BoardTile> byId = new HashMap<>();
        for (BoardColumns.BoardColumn column : columns) {
            for (BoardTile tile : column.tiles()) {
                byId.put(tile.id(), tile);
            }
        }
        tilesById = Map.copyOf(byId);
        searchCount = SearchCount.of(List.copyOf(byId.values()), query, this::haystack);
        Map<String, List<BoardWaitReasons.Wait>> reasons = new HashMap<>();
        for (BoardTile tile : byId.values()) {
            List<BoardWaitReasons.Wait> waits = BoardWaitReasons.of(tile, tilesById);
            if (!waits.isEmpty()) {
                reasons.put(tile.id(), waits);
            }
        }
        waitReasons = Map.copyOf(reasons);
        Map<BoardTileGroup, Integer> counts = new EnumMap<>(BoardTileGroup.class);
        for (BoardTileGroup group : GROUPS) {
            counts.put(group, feed.total(group));
        }
        totals = Map.copyOf(counts);
        List<List<BoardTile>> visible = new ArrayList<>(layout.slotCount());
        for (BoardLayout.Slot slot : layout.slots()) {
            List<BoardTile> tiles = visibleTiles(slot.group());
            visible.add(tiles);
        }
        slotTiles = List.copyOf(visible);
        if (selectedId == null || !byId.containsKey(selectedId)) {
            selectedId = firstVisible();
        }
        clampScroll();
    }

    private List<BoardTile> visibleTiles(BoardTileGroup group) {
        List<BoardTile> tiles = new ArrayList<>();
        if (focusGroup != null && focusGroup != group) {
            return List.of();
        }
        for (BoardColumns.BoardColumn column : columns) {
            if (column.group() != group) {
                continue;
            }
            for (BoardTile tile : column.tiles()) {
                if (BoardSearch.matches(haystack(tile), query)) {
                    tiles.add(tile);
                }
            }
        }
        tiles.sort(comparator());
        return List.copyOf(tiles);
    }

    private Comparator<BoardTile> comparator() {
        return switch (sort) {
            case PRIORITY ->
                Comparator.comparingInt(BoardTile::priority).reversed().thenComparing(BoardTile::id);
            case NAME ->
                Comparator.comparing(tile -> BoardTileTexts.taskText(tile).getString());
            case TIME ->
                Comparator.comparingLong(BoardTile::doneAt)
                        .thenComparingLong(BoardTile::startedAt)
                        .reversed();
        };
    }

    private String haystack(BoardTile tile) {
        StringBuilder text = new StringBuilder(BoardTileTexts.taskText(tile).getString());
        text.append(' ').append(BoardTileTexts.detailsText(tile).getString());
        text.append(' ').append(tile.status());
        if (tile.spirit() != null) {
            text.append(' ').append(tile.spirit());
        }
        for (String detail : tile.details()) {
            text.append(' ').append(detail);
        }
        return text.toString();
    }

    private @Nullable String firstVisible() {
        for (List<BoardTile> tiles : slotTiles) {
            if (!tiles.isEmpty()) {
                return tiles.get(0).id();
            }
        }
        return null;
    }

    private void clampScroll() {
        for (BoardTileGroup group : GROUPS) {
            setScrolled(group, Mth.clamp(scrolled(group), 0, maxScroll(group)));
        }
    }

    private double scrolled(BoardTileGroup group) {
        return scroll.getOrDefault(group, 0.0);
    }

    private void setScrolled(BoardTileGroup group, double value) {
        scroll.put(group, value);
    }

    private boolean filteredEmpty() {
        return !query.isEmpty() && feed.seen() && totalAll() > 0 && searchCount.empty();
    }

    private void clearQuery() {
        query = "";
        if (searchBox != null) {
            searchBox.setValue("");
        }
        rebuild();
    }

    private int totalAll() {
        int total = 0;
        for (BoardTileGroup group : GROUPS) {
            total += feed.total(group);
        }
        return total;
    }

    private int maxScroll(BoardTileGroup group) {
        int slot = -1;
        for (int i = 0; i < layout.slotCount(); i++) {
            if (layout.slots().get(i).group() == group) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return 0;
        }
        int count = 0;
        for (BoardColumns.BoardColumn column : columns) {
            if (column.group() == group) {
                count = column.tiles().size();
                break;
            }
        }
        int content = count * (BoardLayout.TILE_HEIGHT + BoardLayout.TILE_GAP) - BoardLayout.TILE_GAP;
        return Math.max(0, content - layout.list(slot).height());
    }
}
