package com.theminespirit.neoforge.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

public final class CraftTerminalMenu extends AbstractContainerMenu {
    public static final int RESULT_SLOT = 0;
    public static final int GRID_FIRST = 1;
    public static final int GRID_SIZE = 9;
    public static final int PLAYER_FIRST = GRID_FIRST + GRID_SIZE;
    public static final int PLAYER_END = PLAYER_FIRST + 36;
    public static final int GRID_X = 9;
    public static final int GRID_Y = 119;
    public static final int RESULT_X = 103;
    public static final int RESULT_Y = 137;
    public static final int INVENTORY_X = 9;
    public static final int INVENTORY_Y = 176;
    public static final int HOTBAR_Y = 234;
    private static final int CELL = 18;

    private final Player player;
    private final CraftingContainer grid = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer result = new ResultContainer();

    public CraftTerminalMenu(int containerId, Inventory inventory) {
        super(TerminalMenus.CRAFT_TERMINAL.get(), containerId);
        this.player = inventory.player;
        addSlot(new TerminalResultSlot(player, grid, result));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(grid, column + row * 3, GRID_X + column * CELL, GRID_Y + row * CELL));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory, column + row * 9 + 9, INVENTORY_X + column * CELL, INVENTORY_Y + row * CELL));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * CELL, HOTBAR_Y));
        }
    }

    public CraftingContainer grid() {
        return grid;
    }

    @Override
    public void slotsChanged(Container changed) {
        if (player instanceof ServerPlayer server) {
            ItemStack out = craftResult(server);
            result.setItem(0, out);
            setRemoteSlot(RESULT_SLOT, out);
            server.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, out));
        }
    }

    private ItemStack craftResult(ServerPlayer server) {
        CraftingInput input = grid.asCraftInput();
        Optional<RecipeHolder<CraftingRecipe>> holder =
                server.serverLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, server.serverLevel());
        if (holder.isEmpty() || !result.setRecipeUsed(server.serverLevel(), server, holder.get())) {
            return ItemStack.EMPTY;
        }
        ItemStack out =
                holder.get().value().assemble(input, server.serverLevel().registryAccess());
        return out.isItemEnabled(server.serverLevel().enabledFeatures()) ? out : ItemStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index == RESULT_SLOT) {
            stack.getItem().onCraftedBy(stack, who.level(), who);
            if (!moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, before);
        } else if (index < PLAYER_FIRST) {
            if (!moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (who instanceof ServerPlayer server) {
            slot.set(CraftTerminalService.deposit(server, stack));
            return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(who, stack);
        if (index == RESULT_SLOT) {
            who.drop(stack, false);
        }
        return before;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index != RESULT_SLOT && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player who) {
        return who.isAlive();
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        result.clearContent();
        if (who instanceof ServerPlayer) {
            clearContainer(who, grid);
        }
    }

    public List<ItemStack> takeGrid() {
        List<ItemStack> taken = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            taken.add(grid.removeItemNoUpdate(slot));
        }
        slotsChanged(grid);
        return taken;
    }

    private final class TerminalResultSlot extends ResultSlot {
        private TerminalResultSlot(Player owner, CraftingContainer craftSlots, ResultContainer output) {
            super(owner, craftSlots, output, 0, RESULT_X, RESULT_Y);
        }

        @Override
        public boolean mayPickup(Player who) {
            return !(who instanceof ServerPlayer server)
                    || CraftTerminalService.access(server).allowed();
        }

        @Override
        public void onTake(Player who, ItemStack taken) {
            List<ItemStack> pattern = new ArrayList<>(GRID_SIZE);
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                pattern.add(grid.getItem(slot).copyWithCount(1));
            }
            super.onTake(who, taken);
            if (who instanceof ServerPlayer server) {
                CraftTerminalService.refill(server, grid, pattern);
            }
        }
    }
}
