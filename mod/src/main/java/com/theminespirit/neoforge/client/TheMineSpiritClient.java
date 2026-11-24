package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.net.BoardViewChannel;
import com.theminespirit.neoforge.net.MineMapChannel;
import com.theminespirit.neoforge.net.SettlementTreeChannel;
import com.theminespirit.neoforge.net.TownMapChannel;
import com.theminespirit.neoforge.terminal.CraftTerminalChannel;
import com.theminespirit.neoforge.terminal.TerminalMenus;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ModIdentity.MOD_ID, dist = Dist.CLIENT)
public final class TheMineSpiritClient {
    static final String CATEGORY = "key.categories." + ModIdentity.MOD_ID;
    static final KeyMapping OPEN_BOARD = new KeyMapping(
            "key." + ModIdentity.MOD_ID + ".board",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            CATEGORY);
    static final KeyMapping OPEN_TREE = new KeyMapping(
            "key." + ModIdentity.MOD_ID + ".tree",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            CATEGORY);
    static final KeyMapping HIGHLIGHT = new KeyMapping(
            "key." + ModIdentity.MOD_ID + ".highlight",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            CATEGORY);

    public TheMineSpiritClient(IEventBus modBus) {
        modBus.addListener(TheMineSpiritClient::registerKeys);
        modBus.addListener(TheMineSpiritClient::registerScreens);
        CraftTerminalChannel.onClientSnapshot(snapshot -> {
            if (Minecraft.getInstance().screen instanceof CraftTerminalScreen screen) {
                screen.show(snapshot);
            }
        });
        CraftTerminalChannel.onClientChain(reply -> {
            if (Minecraft.getInstance().screen instanceof CraftTerminalScreen screen) {
                screen.show(reply);
            }
        });
        NeoForge.EVENT_BUS.addListener(TheMineSpiritClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut ignoredEvent) -> applyConstructionAudit(Set.of(), Set.of()));
        TownMapChannel.onClientReply(reply -> {
            if (Minecraft.getInstance().screen instanceof TownMapScreen screen) {
                screen.show(reply);
            }
        });
        TownMapChannel.onClientMarkers(reply -> {
            if (Minecraft.getInstance().screen instanceof TownMapScreen screen) {
                screen.showMarkers(reply.markers(), reply.quarries());
            }
        });
        MineMapChannel.onClientReply(reply -> {
            if (Minecraft.getInstance().screen instanceof MineMapScreen screen) {
                screen.show(reply);
            }
        });
        MineMapChannel.onClientMarkers(reply -> {
            if (Minecraft.getInstance().screen instanceof MineMapScreen screen) {
                screen.showMarkers(reply.markers());
            }
        });
        BoardViewChannel.onClientReply(reply -> {
            if (Minecraft.getInstance().screen instanceof BoardScreen screen) {
                screen.show(reply);
            }
        });
        SettlementTreeChannel.onClientReply(tree -> {
            if (Minecraft.getInstance().screen instanceof SettlementTreeScreen screen) {
                screen.show(tree);
            }
        });
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(TerminalMenus.CRAFT_TERMINAL.get(), CraftTerminalScreen::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BOARD);
        event.register(OPEN_TREE);
        event.register(HIGHLIGHT);
    }

    private static void onClientTick(ClientTickEvent.Post ignoredEvent) {
        Minecraft minecraft = Minecraft.getInstance();
        while (HIGHLIGHT.consumeClick()) {
            if (controlHeld() && minecraft.screen == null && minecraft.player != null) {
                toggleConstructionHighlight(minecraft);
            }
        }
        while (OPEN_TREE.consumeClick()) {
            if (!controlHeld() && minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new SettlementTreeScreen());
            }
        }
        while (OPEN_BOARD.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new BoardScreen());
            }
        }
    }

    public static void applyConstructionAudit(Set<BlockPos> wrong, Set<BlockPos> missing) {
        ConstructionHighlightManager.instance().updateData(wrong, missing);
    }

    private static boolean controlHeld() {
        return KeyModifier.CONTROL.isActive(KeyConflictContext.IN_GAME);
    }

    private static void toggleConstructionHighlight(Minecraft minecraft) {
        ConstructionHighlightManager manager = ConstructionHighlightManager.instance();
        manager.toggle();
        Component message = manager.isEnabled()
                ? Component.translatable(
                        "theminespirit.highlight.on",
                        manager.wrongBlocks().size(),
                        manager.missingBlocks().size())
                : Component.translatable("theminespirit.highlight.off");
        minecraft.player.displayClientMessage(message, true);
    }
}
