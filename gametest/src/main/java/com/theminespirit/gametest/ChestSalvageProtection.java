package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.ChestDemolition;
import com.theminespirit.neoforge.board.ChestTickets;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

final class ChestSalvageProtection {
    private ChestSalvageProtection() {}

    static void check(GameTestHelper helper, SpiritEntity spirit, BlockPos left, BlockPos right) {
        ServerLevel level = helper.getLevel();
        var placed = PlacedBlocksData.get(level);
        placed.mark(right);
        helper.assertTrue(
                ChestDemolition.removable(level, coord(left)),
                "Метка игрока на второй половине не защищает пару от сноса: сундук уходит в задание переноса вещей, "
                        + "иначе ремонт плановой клетки падает каждый цикл, а содержимое остаётся запертым");
        var service = BoardRegistry.get(level);
        String ticket = "demolish_chest:" + left.getX() + "," + left.getY() + "," + left.getZ();
        service.submit(
                new BoardTicket(
                        ticket,
                        BoardCase.DISMANTLE_SHELTER,
                        Optional.of(spirit.getUUID()),
                        BoardRules.priorityOf(BoardCase.DISMANTLE_SHELTER),
                        level.getGameTime(),
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(coord(left)));
        helper.assertTrue(
                ChestTickets.assigned(level, spirit.getUUID(), coord(left))
                        && spirit.protectionGate().checkDemolish(left, level.getBlockState(left))
                                instanceof BlockProtection.Allowed,
                "Духу с выданным заданием сноса разрешено сломать сундук после переноса: без этого разрешения "
                        + "вещи не вынести, а плановая клетка не освободится; при смене проверки переноса проверь "
                        + "задание demolish_chest");
        service.cancel(level, ticket);
        helper.assertTrue(
                !ChestTickets.assigned(level, spirit.getUUID(), coord(left)),
                "Без выданного задания сноса дух не получает разрешение на снос сундука: иначе случайная расчистка "
                        + "уничтожит вещи игрока без переноса; при смене проверки переноса проверь задание demolish_chest");
        placed.unmark(right);
    }

    static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
