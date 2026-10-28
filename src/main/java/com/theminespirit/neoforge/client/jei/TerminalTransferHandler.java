package com.theminespirit.neoforge.client.jei;

import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import com.theminespirit.neoforge.terminal.CraftTerminalMenu;
import com.theminespirit.neoforge.terminal.TerminalMenus;
import java.util.Optional;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

final class TerminalTransferHandler implements IRecipeTransferHandler<CraftTerminalMenu, RecipeHolder<CraftingRecipe>> {
    @Override
    public Class<? extends CraftTerminalMenu> getContainerClass() {
        return CraftTerminalMenu.class;
    }

    @Override
    public Optional<MenuType<CraftTerminalMenu>> getMenuType() {
        return Optional.of(TerminalMenus.CRAFT_TERMINAL.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    // lib: JEI 19.70 держит этот метод абстрактным, хотя пометил к удалению; замена появится с новой версией JEI
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            CraftTerminalMenu menu,
            RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView slots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        if (doTransfer) {
            PacketDistributor.sendToServer(new CraftTerminalChannel.Action(
                    CraftTerminalChannel.Kind.FILL,
                    ItemStack.EMPTY,
                    1,
                    maxTransfer,
                    recipe.id().toString()));
        }
        return null;
    }
}
