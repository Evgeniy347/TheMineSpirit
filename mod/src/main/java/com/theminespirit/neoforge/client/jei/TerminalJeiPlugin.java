package com.theminespirit.neoforge.client.jei;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.client.CraftTerminalScreen;
import com.theminespirit.neoforge.client.SearchSync;
import com.theminespirit.neoforge.terminal.CraftTerminalMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class TerminalJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "craft_tab");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new TerminalTransferHandler(), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(CraftTerminalScreen.class, new TerminalGuiHandler());
        registration.addRecipeClickArea(
                CraftTerminalScreen.class,
                CraftTerminalMenu.RESULT_X - 1,
                CraftTerminalMenu.RESULT_Y - 1,
                18,
                18,
                RecipeTypes.CRAFTING);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        SearchSync.attach(text -> runtime.getIngredientFilter().setFilterText(text));
    }

    @Override
    public void onRuntimeUnavailable() {
        SearchSync.detach();
    }
}
