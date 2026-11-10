package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EnchantRoomWatch {
    private static final String PREFIX = "enchant_room";

    private EnchantRoomWatch() {}

    static boolean post(GraphPost post, String node, int priority, Map<String, Integer> need) {
        List<SpiritEntity> spirits = post.spirits;
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty() || !node.startsWith(PREFIX)) {
            return false;
        }
        int levelValue = level(node);
        if (levelValue < 1) {
            return false;
        }
        String id = PREFIX + "_" + levelValue;
        BoardStatus status = post.service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.DONE
                || status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED) {
            return true;
        }
        Optional<BlockCoord> home = HomePoint.of(post.level, spirits);
        if (home.isEmpty()) {
            return false;
        }
        post.submit(
                post.leveled(
                        post.ticket(
                                id,
                                BoardCase.ENCHANT_ROOM,
                                Optional.empty(),
                                priority,
                                BoardTicket.ALWAYS_CAUSE,
                                Map.copyOf(need)),
                        levelValue,
                        List.of()),
                TicketTarget.at(home.orElseThrow()));
        return true;
    }

    static String ticketId(String node) {
        return PREFIX + "_" + level(node);
    }

    private static int level(String node) {
        String suffix = node.substring(PREFIX.length());
        if (suffix.isEmpty()) {
            return 1;
        }
        if (!suffix.startsWith("_")) {
            return 0;
        }
        try {
            return Integer.parseInt(suffix.substring(1));
        } catch (NumberFormatException notLevel) {
            return 0;
        }
    }
}
