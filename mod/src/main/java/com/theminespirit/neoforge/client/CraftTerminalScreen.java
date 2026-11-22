package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.core.ui.BoardLayout;
import com.theminespirit.core.ui.CraftLayout;
import com.theminespirit.core.ui.RefreshClock;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.Action;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.Kind;
import com.theminespirit.neoforge.terminal.CraftTerminalMenu;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class CraftTerminalScreen extends AbstractContainerScreen<CraftTerminalMenu> {
    private static final String KEY = ModIdentity.MOD_ID + ".craft.";
    private static final int REFRESH_TICKS = 20;
    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int BORDER = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int GOOD = 0xFF2E7D32;
    private static final int BAD = 0xFFB71C1C;

    private final StockPanel list = new StockPanel();
    private final ChainPanel chain = new ChainPanel();
    private final Map<StockPanel.Mode, ShellTabButton> modeButtons = new EnumMap<>(StockPanel.Mode.class);
    private @Nullable EditBox search;
    private @Nullable Button sortButton;
    private @Nullable Button craftButton;
    private @Nullable Button maxButton;
    private Component reason = Component.empty();
    private SettlementReach.Verdict verdict = SettlementReach.Verdict.NO_SETTLEMENT;
    private int distance;
    private int ticks;
    private int ageTicks;
    private String query = "";
    private RefreshClock clock = RefreshClock.NEVER;

    public CraftTerminalScreen(CraftTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = CraftLayout.WIDTH;
        imageHeight = CraftLayout.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        list.place(leftPos + CraftLayout.LIST_X, topPos + CraftLayout.LIST_Y);
        BoardLayout.Rect chainPanel = CraftLayout.panel();
        chain.place(leftPos + chainPanel.x(), topPos + chainPanel.y(), chainPanel.width(), chainPanel.height());
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.CRAFT);
        BoardLayout.Rect searchBox = CraftLayout.search();
        EditBox box = new EditBox(
                font,
                leftPos + searchBox.x(),
                topPos + searchBox.y(),
                searchBox.width(),
                searchBox.height(),
                Component.translatable(KEY + "search"));
        box.setHint(Component.translatable(KEY + "search.placeholder"));
        box.setMaxLength(64);
        box.setValue(query);
        box.setResponder(text -> {
            query = text;
            list.query(text);
            SearchSync.publish(text);
        });
        search = addRenderableWidget(box);
        modeButtons.clear();
        StockPanel.Mode[] modes = StockPanel.Mode.values();
        for (int index = 0; index < modes.length; index++) {
            StockPanel.Mode mode = modes[index];
            Button.Builder builder = Button.builder(
                            Component.translatable(KEY + "tab." + mode.name().toLowerCase(Locale.ROOT)),
                            pressed -> setMode(mode))
                    .bounds(
                            leftPos + CraftLayout.modeTab(index).x(),
                            topPos + CraftLayout.modeTab(index).y(),
                            CraftLayout.modeTab(index).width(),
                            CraftLayout.modeTab(index).height());
            ShellTabButton button = new ShellTabButton(builder, list.mode() == mode);
            button.active = list.mode() != mode;
            modeButtons.put(mode, button);
            addRenderableWidget(button);
        }
        BoardLayout.Rect raise = CraftLayout.button(CraftLayout.RAISE_Y);
        sortButton = addRenderableWidget(Button.builder(sortLabel(), pressed -> cycleSort())
                .bounds(leftPos + raise.x(), topPos + raise.y(), raise.width(), raise.height())
                .build());
        BoardLayout.Rect clear = CraftLayout.button(CraftLayout.CLEAR_Y);
        addRenderableWidget(
                Button.builder(Component.translatable(KEY + "clear"), pressed -> send(Action.of(Kind.CLEAR)))
                        .bounds(leftPos + clear.x(), topPos + clear.y(), clear.width(), clear.height())
                        .build());
        BoardLayout.Rect craftRow = CraftLayout.button(CraftLayout.CRAFT_Y);
        craftButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "make"), pressed -> craft(false))
                .bounds(leftPos + craftRow.x(), topPos + craftRow.y(), craftRow.width(), craftRow.height())
                .build());
        BoardLayout.Rect maxRow = CraftLayout.button(CraftLayout.MAX_Y);
        maxButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "make_max"), pressed -> craft(true))
                .bounds(leftPos + maxRow.x(), topPos + maxRow.y(), maxRow.width(), maxRow.height())
                .build());
        updateButtons();
        send(Action.of(Kind.REFRESH));
    }

    public void show(CraftTerminalChannel.Snapshot snapshot) {
        verdict = snapshot.verdict();
        distance = snapshot.distance();
        clock = RefreshClock.at(ageTicks);
        list.showStock(snapshot.entries());
        list.showOrders(snapshot.orders());
        updateButtons();
    }

    public void show(CraftTerminalChannel.ChainReply reply) {
        chain.show(reply);
        clock = RefreshClock.at(ageTicks);
        updateButtons();
    }

    private void updateButtons() {
        boolean allowed = verdict == SettlementReach.Verdict.ALLOWED;
        boolean feasible =
                chain.reply().map(CraftTerminalChannel.ChainReply::feasible).orElse(false);
        boolean orderable =
                chain.reply().map(reply -> !reply.missing().isEmpty()).orElse(false);
        int crafts = chain.reply().map(CraftTerminalChannel.ChainReply::crafts).orElse(0);
        if (craftButton != null) {
            craftButton.active = allowed && (feasible || orderable);
            craftButton.setMessage(
                    Component.translatable(orderable && !feasible ? KEY + "queue_count" : KEY + "make_count", crafts));
        }
        if (maxButton != null) {
            maxButton.active = allowed && feasible;
        }
        if (!allowed) {
            reason = Component.translatable(KEY + "reason.denied");
        } else if (feasible) {
            reason = Component.translatable(KEY + "reason.ready");
        } else if (orderable) {
            reason = Component.translatable(KEY + "reason.queue");
        } else {
            reason = Component.translatable(KEY + "reason.empty");
        }
        for (var entry : modeButtons.entrySet()) {
            entry.getValue().current(entry.getKey() == list.mode());
            entry.getValue().active = entry.getKey() != list.mode();
        }
    }

    private void setMode(StockPanel.Mode mode) {
        list.mode(mode);
        updateButtons();
    }

    private void cycleSort() {
        if (Screen.hasShiftDown()) {
            list.toggleDirection();
        } else {
            list.cycleSort();
        }
        if (sortButton != null) {
            sortButton.setMessage(sortLabel());
        }
    }

    private Component sortLabel() {
        return Component.translatable(
                        KEY + "sort",
                        Component.translatable(
                                KEY + "sort." + list.sort().name().toLowerCase(Locale.ROOT)))
                .copy()
                .append(list.reversed() ? " ▲" : " ▼");
    }

    private void craft(boolean max) {
        chain.reply().ifPresent(reply -> {
            boolean queue = !max && !reply.missing().isEmpty();
            send(new Action(
                    queue ? Kind.ORDER : Kind.CHAIN_CRAFT,
                    ItemStack.EMPTY,
                    max ? 0 : reply.crafts(),
                    true,
                    reply.recipe()));
        });
    }

    private static void send(Action action) {
        PacketDistributor.sendToServer(action);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ageTicks++;
        if (++ticks % REFRESH_TICKS == 0) {
            send(Action.of(Kind.REFRESH));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search != null && !query.isEmpty()) {
            int clearX = search.getX() + search.getWidth() - 11;
            if (mouseX >= clearX
                    && mouseX < clearX + 10
                    && mouseY >= search.getY()
                    && mouseY < search.getY() + search.getHeight()) {
                search.setValue("");
                query = "";
                return true;
            }
        }
        if (list.cancelZone(mouseX, mouseY)) {
            list.orderAt(mouseX, mouseY)
                    .ifPresent(order -> send(new Action(Kind.CANCEL, ItemStack.EMPTY, order.id(), false, "")));
            return true;
        }
        if (!list.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        boolean right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (list.mode() == StockPanel.Mode.ORDERS) {
            list.orderAt(mouseX, mouseY)
                    .filter(order -> CraftOrdersView.WAITING.equals(order.status()) && right)
                    .ifPresent(order -> send(new Action(Kind.CANCEL, ItemStack.EMPTY, order.id(), false, "")));
            return true;
        }
        if (list.mode() == StockPanel.Mode.RECIPES) {
            list.at(mouseX, mouseY)
                    .ifPresent(stack -> send(
                            new Action(Kind.CHAIN_PREVIEW, ItemStack.EMPTY, 1, false, ItemIds.of(stack.getItem()))));
            return true;
        }
        if (!menu.getCarried().isEmpty()) {
            send(new Action(Kind.PUT, ItemStack.EMPTY, 0, right, ""));
            return true;
        }
        list.stockAt(mouseX, mouseY).ifPresent(entry -> {
            int max = entry.sample().getMaxStackSize();
            send(new Action(Kind.TAKE, entry.sample(), right ? Math.max(1, max / 2) : max, Screen.hasShiftDown(), ""));
        });
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (list.contains(mouseX, mouseY)) {
            list.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_F) {
            if (search != null) {
                search.setFocused(true);
            }
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && craftButton != null
                && craftButton.active) {
            craft(false);
            return true;
        }
        if (search != null && search.isFocused() && keyCode != InputConstants.KEY_ESCAPE) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
                graphics,
                font,
                Component.translatable(ShellPainter.KEY + "hint.craft"),
                clock,
                ageTicks,
                width,
                height);
        if (search != null && !query.isEmpty()) {
            graphics.drawString(font, "×", search.getX() + search.getWidth() - 9, search.getY() + 3, 0xFF6A6A6A, false);
        }
        Optional<CraftTerminalChannel.Order> order = list.orderAt(mouseX, mouseY);
        Optional<ItemStack> hovered = list.at(mouseX, mouseY);
        if (order.isPresent()) {
            graphics.renderComponentTooltip(font, CraftOrdersView.tooltip(order.get()), mouseX, mouseY);
        } else if (hovered.isPresent() && menu.getCarried().isEmpty()) {
            graphics.renderTooltip(font, hovered.get(), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, BORDER);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BACKGROUND);
        for (Slot slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, SLOT);
        }
        list.render(graphics, font);
        graphics.drawString(
                font,
                "→",
                leftPos + CraftTerminalMenu.RESULT_X - 22,
                topPos + CraftTerminalMenu.RESULT_Y + 4,
                TEXT,
                false);
        chain.render(graphics, font);
        int reasonColor = verdict != SettlementReach.Verdict.ALLOWED
                ? BAD
                : chain.reply().map(CraftTerminalChannel.ChainReply::feasible).orElse(false) ? GOOD : 0xFF8A5A00;
        graphics.drawString(
                font,
                TextClip.text(font, reason, CraftLayout.PANEL_WIDTH),
                leftPos + CraftLayout.PANEL_X,
                topPos + CraftLayout.REASON_Y,
                reasonColor,
                false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component status = statusText();
        graphics.drawString(
                font,
                TextClip.text(font, status, CraftLayout.PANEL_WIDTH),
                CraftLayout.PANEL_X,
                6,
                verdict == SettlementReach.Verdict.ALLOWED ? GOOD : BAD,
                false);
    }

    private Component statusText() {
        return verdict == SettlementReach.Verdict.ALLOWED
                ? Component.translatable(KEY + "status.allowed")
                : Component.translatable(
                        KEY + "denied." + verdict.name().toLowerCase(Locale.ROOT), distance, SettlementReach.MARGIN);
    }
}
