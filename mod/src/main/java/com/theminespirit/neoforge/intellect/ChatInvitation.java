package com.theminespirit.neoforge.intellect;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.UUID;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class ChatInvitation {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChatInvitation() {}

    public static int send(ServerLevel level, SpiritEntity inviter, UUID expedition, int teamSize) {
        if (level == null || inviter == null || expedition == null) {
            throw new IllegalArgumentException(
                    "Приглашение без уровня, духа или похода не отправить: игрок идёт с конкретной командой");
        }
        String id = expedition.toString();
        Component message = Component.literal("<" + inviter.getDisplayName().getString()
                        + "> Нужна помощь в шахте! Команда " + teamSize + " духа ждёт решения. ")
                .append(Component.literal("[Иду с вами]")
                        .setStyle(Style.EMPTY.withClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/spirit invite " + id + " join"))))
                .append(Component.literal(" "))
                .append(Component.literal("[Подождите, я собираюсь (+60 с)]")
                        .setStyle(Style.EMPTY.withClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/spirit invite " + id + " wait"))))
                .append(Component.literal(" "))
                .append(Component.literal("[Идите без меня]")
                        .setStyle(Style.EMPTY.withClickEvent(
                                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/spirit invite " + id + " go"))));
        int heard = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(inviter) > CatalogLimits.SAY_HEARING_RADIUS) {
                continue;
            }
            player.sendSystemMessage(message);
            heard++;
        }
        LOGGER.info(
                "Дух {} зовёт в шахту команду {} в радиусе {}: слышат {} игроков",
                inviter.getDisplayName().getString(),
                teamSize,
                CatalogLimits.SAY_HEARING_RADIUS,
                heard);
        return heard;
    }
}
