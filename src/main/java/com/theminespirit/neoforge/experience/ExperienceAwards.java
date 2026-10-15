package com.theminespirit.neoforge.experience;

import com.theminespirit.core.experience.ExperienceShare;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Стекает опыт духов реальным игрокам: делит поровну между всеми, кто сейчас в сети, независимо от
 * расстояния и измерения. Если игроков нет — опыт уходит в пул поселения {@link SettlementExperience}.
 */
public final class ExperienceAwards {
    private ExperienceAwards() {
    }

    public static void award(MinecraftServer server, int amount) {
        if (amount <= 0) {
            return;
        }
        List<ServerPlayer> players = onlinePlayers(server);
        if (players.isEmpty()) {
            SettlementExperience.get(server).add(amount);
            return;
        }
        giveSplit(players, amount);
    }

    /** Раздаёт накопленный пул текущим игрокам. Вызывается при входе игрока на сервер. */
    public static void flushPool(MinecraftServer server) {
        int pending = SettlementExperience.get(server).drain();
        if (pending <= 0) {
            return;
        }
        List<ServerPlayer> players = onlinePlayers(server);
        if (players.isEmpty()) {
            SettlementExperience.get(server).add(pending);
            return;
        }
        giveSplit(players, pending);
    }

    private static void giveSplit(List<ServerPlayer> players, int amount) {
        int[] shares = ExperienceShare.split(amount, players.size());
        for (int i = 0; i < shares.length; i++) {
            if (shares[i] > 0) {
                players.get(i).giveExperiencePoints(shares[i]);
            }
        }
    }

    private static List<ServerPlayer> onlinePlayers(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.sort(Comparator.comparing(player -> player.getUUID()));
        return players;
    }
}