package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.core.ui.ScreenTabs;
import com.theminespirit.core.ui.StockListing;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.Action;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel.Kind;
import com.theminespirit.neoforge.terminal.CraftTerminalMenu;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

public final class CraftTerminalScreen extends AbstractContainerScreen<CraftTerminalMenu> {
    private static final String KEY = ModIdentity.MOD_ID + ".craft.";
    private static final int WIDTH = 290;
    private static final int HEIGHT = 256;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 20;
    private static final int PANEL_X = 182;
    private static final int PANEL_WIDTH = 102;
    private static final int BUTTON_HEIGHT = 14;
    private static final int REFRESH_TICKS = 20;
    private static final int BACKGROUND = 0xFFC6C6C6;
    private static final int BORDER = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int GOOD = 0xFF2E7D32;
    private static final int BAD = 0xFFB71C1C;

    private final StockPanel list = new StockPanel();
    private final ChainPanel chain = new ChainPanel();
    private @Nullable EditBox search;
    private @Nullable Button sortButton;
    private @Nullable Button modeButton;
    private @Nullable Button craftButton;
    private @Nullable Button maxButton;
    private SettlementReach.Verdict verdict = SettlementReach.Verdict.NO_SETTLEMENT;
    private int distance;
    private int ticks;

    public CraftTerminalScreen(CraftTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        list.place(leftPos + LIST_X, topPos + LIST_Y);
        chain.place(leftPos + PANEL_X, topPos + 96, PANEL_WIDTH, 100);
        ScreenTabButtons.add(this::addRenderableWidget, width, ScreenTabs.Tab.CRAFT);
        EditBox box = new EditBox(font, leftPos + LIST_X, topPos + 6, 110, 12, Component.translatable(KEY + "search"));
        box.setMaxLength(64);
        box.setResponder(text -> {
            list.query(text);
            SearchSync.publish(text);
        });
        search = addRenderableWidget(box);
        int panel = leftPos + PANEL_X;
        modeButton = addRenderableWidget(Button.builder(modeLabel(), pressed -> toggleMode())
                .bounds(panel, topPos + 30, PANEL_WIDTH, BUTTON_HEIGHT)
                .build());
        sortButton = addRenderableWidget(Button.builder(sortLabel(StockListing.Sort.COUNT), pressed -> cycleSort())
                .bounds(panel, topPos + 46, PANEL_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(
                Button.builder(Component.translatable(KEY + "clear"), pressed -> send(Action.of(Kind.CLEAR)))
                        .bounds(panel, topPos + 62, PANEL_WIDTH, BUTTON_HEIGHT)
                        .build());
        craftButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "make"), pressed -> craft(false))
                .bounds(panel, topPos + 200, PANEL_WIDTH, BUTTON_HEIGHT)
                .build());
        maxButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "make_max"), pressed -> craft(true))
                .bounds(panel, topPos + 216, PANEL_WIDTH, BUTTON_HEIGHT)
                .build());
        updateButtons();
        send(Action.of(Kind.REFRESH));
    }

    public void show(CraftTerminalChannel.Snapshot snapshot) {
        verdict = snapshot.verdict();
        distance = snapshot.distance();
        list.showStock(snapshot.entries());
        list.showOrders(snapshot.orders());
        updateButtons();
    }

    public void show(CraftTerminalChannel.ChainReply reply) {
        chain.show(reply);
        updateButtons();
    }

    private void updateButtons() {
        boolean allowed = verdict == SettlementReach.Verdict.ALLOWED;
        boolean feasible =
                chain.reply().map(CraftTerminalChannel.ChainReply::feasible).orElse(false);
        boolean orderable =
                chain.reply().map(reply -> !reply.missing().isEmpty()).orElse(false);
        if (craftButton != null) {
            craftButton.active = allowed && (feasible || orderable);
            craftButton.setMessage(Component.translatable(orderable ? KEY + "queue" : KEY + "make"));
        }
        if (maxButton != null) {
            maxButton.active = allowed && feasible;
        }
    }

    private void toggleMode() {
        list.cycleMode();
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
    }

    private Component modeLabel() {
        return Component.translatable(KEY + "mode." + list.mode().name().toLowerCase(Locale.ROOT));
    }

    private void cycleSort() {
        StockListing.Sort next = list.cycleSort();
        if (sortButton != null) {
            sortButton.setMessage(sortLabel(next));
        }
    }

    private static Component sortLabel(StockListing.Sort sort) {
        return Component.translatable(
                KEY + "sort", Component.translatable(KEY + "sort." + sort.name().toLowerCase(Locale.ROOT)));
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
        if (++ticks % REFRESH_TICKS == 0) {
            send(Action.of(Kind.REFRESH));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        if (search != null && search.isFocused() && keyCode != InputConstants.KEY_ESCAPE) {
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
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
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component status = verdict == SettlementReach.Verdict.ALLOWED
                ? Component.translatable(KEY + "status.allowed")
                : Component.translatable(
                        KEY + "denied." + verdict.name().toLowerCase(Locale.ROOT), distance, SettlementReach.MARGIN);
        int line = 6;
        for (FormattedCharSequence part : font.split(status, PANEL_WIDTH)) {
            graphics.drawString(
                    font, part, PANEL_X, line, verdict == SettlementReach.Verdict.ALLOWED ? GOOD : BAD, false);
            line += 9;
        }
    }
}
