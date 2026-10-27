package com.theminespirit.neoforge.client;

import com.theminespirit.core.ui.StockListing;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

final class StockPanel {
    static final int COLUMNS = 9;
    static final int ROWS = 5;
    static final int CELL = 18;
    private static final int THOUSAND = 1000;
    private static final int CELL_COLOR = 0xFF8B8B8B;
    private static final int RESERVED_COLOR = 0x80FF5555;

    private int x;
    private int y;
    private List<CraftTerminalChannel.Entry> stock = List.of();
    private List<ItemStack> recipeOutputs = List.of();
    private List<StockListing.Entry> shown = List.of();

    enum Mode {
        STOCK,
        RECIPES,
        ORDERS
    }

    private Mode mode = Mode.STOCK;
    private List<CraftTerminalChannel.Order> orders = List.of();
    private String query = "";
    private StockListing.Sort sort = StockListing.Sort.COUNT;
    private int scrollRow;

    void place(int left, int top) {
        x = left;
        y = top;
    }

    void showStock(List<CraftTerminalChannel.Entry> entries) {
        stock = List.copyOf(entries);
        refresh();
    }

    void query(String text) {
        query = text;
        scrollRow = 0;
        refresh();
    }

    StockListing.Sort cycleSort() {
        sort = sort.next();
        refresh();
        return sort;
    }

    Mode cycleMode() {
        mode = switch (mode) {
            case STOCK -> Mode.RECIPES;
            case RECIPES -> Mode.ORDERS;
            case ORDERS -> Mode.STOCK;
        };
        if (mode == Mode.RECIPES && recipeOutputs.isEmpty()) {
            recipeOutputs = craftableOutputs();
        }
        scrollRow = 0;
        refresh();
        return mode;
    }

    Mode mode() {
        return mode;
    }

    void showOrders(List<CraftTerminalChannel.Order> next) {
        orders = List.copyOf(next);
        refresh();
    }

    private void refresh() {
        List<StockListing.Entry> all = new ArrayList<>();
        if (mode == Mode.ORDERS) {
            for (int index = 0; index < orders.size(); index++) {
                ItemStack stack = orders.get(index).target();
                all.add(new StockListing.Entry(
                        index,
                        idOf(stack),
                        stack.getHoverName().getString(),
                        orders.get(index).count()));
            }
        } else if (mode == Mode.RECIPES) {
            for (int index = 0; index < recipeOutputs.size(); index++) {
                ItemStack stack = recipeOutputs.get(index);
                all.add(new StockListing.Entry(
                        index, idOf(stack), stack.getHoverName().getString(), 0));
            }
        } else {
            for (int index = 0; index < stock.size(); index++) {
                CraftTerminalChannel.Entry entry = stock.get(index);
                all.add(new StockListing.Entry(
                        index,
                        idOf(entry.sample()),
                        entry.sample().getHoverName().getString(),
                        entry.count()));
            }
        }
        shown = StockListing.view(all, query, mode == Mode.STOCK ? sort : StockListing.Sort.NAME, false);
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    private static String idOf(ItemStack stack) {
        return ItemIds.of(stack.getItem());
    }

    private static List<ItemStack> craftableOutputs() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return List.of();
        }
        Map<String, ItemStack> outputs = new LinkedHashMap<>();
        for (RecipeHolder<CraftingRecipe> holder :
                minecraft.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(minecraft.level.registryAccess());
            if (!holder.value().isSpecial() && !result.isEmpty()) {
                outputs.putIfAbsent(idOf(result), result.copyWithCount(1));
            }
        }
        return List.copyOf(outputs.values());
    }

    void scroll(double amount) {
        scrollRow = Math.clamp(scrollRow - (int) Math.signum(amount), 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + COLUMNS * CELL && mouseY >= y && mouseY < y + ROWS * CELL;
    }

    Optional<ItemStack> at(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        int column = (int) ((mouseX - x) / CELL);
        int row = (int) ((mouseY - y) / CELL);
        int index = (scrollRow + row) * COLUMNS + column;
        return index < shown.size() ? Optional.of(sampleOf(shown.get(index))) : Optional.empty();
    }

    private ItemStack sampleOf(StockListing.Entry entry) {
        return switch (mode) {
            case STOCK -> stock.get(entry.key()).sample();
            case RECIPES -> recipeOutputs.get(entry.key());
            case ORDERS -> orders.get(entry.key()).target();
        };
    }

    void render(GuiGraphics graphics, Font font) {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int cellX = x + column * CELL;
                int cellY = y + row * CELL;
                graphics.fill(cellX, cellY, cellX + CELL - 1, cellY + CELL - 1, CELL_COLOR);
                int index = (scrollRow + row) * COLUMNS + column;
                if (index < shown.size()) {
                    renderEntry(graphics, font, shown.get(index), cellX + 1, cellY + 1);
                }
            }
        }
    }

    private void renderEntry(GuiGraphics graphics, Font font, StockListing.Entry entry, int itemX, int itemY) {
        ItemStack sample = sampleOf(entry);
        graphics.renderItem(sample, itemX, itemY);
        if (mode == Mode.ORDERS) {
            graphics.renderItemDecorations(
                    font, sample, itemX, itemY, compact(orders.get(entry.key()).count()));
        } else if (mode == Mode.STOCK) {
            CraftTerminalChannel.Entry source = stock.get(entry.key());
            if (source.free() < source.count()) {
                graphics.fill(itemX, itemY, itemX + 16, itemY + 16, RESERVED_COLOR);
            }
            graphics.renderItemDecorations(font, sample, itemX, itemY, compact(source.count()));
        }
    }

    Optional<CraftTerminalChannel.Entry> stockAt(double mouseX, double mouseY) {
        if (mode != Mode.STOCK) {
            return Optional.empty();
        }
        return keyAt(mouseX, mouseY).map(stock::get);
    }

    Optional<CraftTerminalChannel.Order> orderAt(double mouseX, double mouseY) {
        if (mode != Mode.ORDERS) {
            return Optional.empty();
        }
        return keyAt(mouseX, mouseY).map(orders::get);
    }

    private Optional<Integer> keyAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        int index = (scrollRow + (int) ((mouseY - y) / CELL)) * COLUMNS + (int) ((mouseX - x) / CELL);
        return index < shown.size() ? Optional.of(shown.get(index).key()) : Optional.empty();
    }

    private static String compact(int count) {
        return count >= THOUSAND ? count / THOUSAND + "k" : Integer.toString(count);
    }
}
