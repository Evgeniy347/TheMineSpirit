package com.theminespirit.neoforge.terminal;

import com.theminespirit.core.ModIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TerminalMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(Registries.MENU, ModIdentity.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CraftTerminalMenu>> CRAFT_TERMINAL = REGISTRY.register(
            "craft_terminal", () -> new MenuType<>(CraftTerminalMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private TerminalMenus() {}

    public static void register(IEventBus modBus) {
        REGISTRY.register(modBus);
    }
}
