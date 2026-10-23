package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.net.BoardViewChannel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Клиентская часть мода: клавиша экрана поселения (J по умолчанию, переназначается в настройках управления)
 * и приём снимка доски. Снимок запрашивается один раз при открытии экрана.
 */
@Mod(value = ModIdentity.MOD_ID, dist = Dist.CLIENT)
public final class TheMineSpiritClient {
    static final String CATEGORY = "key.categories." + ModIdentity.MOD_ID;
    static final KeyMapping OPEN_BOARD = new KeyMapping(
            "key." + ModIdentity.MOD_ID + ".board",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_J,
            CATEGORY);

    public TheMineSpiritClient(IEventBus modBus) {
        modBus.addListener(TheMineSpiritClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(TheMineSpiritClient::onClientTick);
        BoardViewChannel.onClientReply(tiles -> {
            if (Minecraft.getInstance().screen instanceof BoardScreen screen) {
                screen.show(tiles);
            }
        });
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BOARD);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_BOARD.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(new BoardScreen());
            }
        }
    }
}
