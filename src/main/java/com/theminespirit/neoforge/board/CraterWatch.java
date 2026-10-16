package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CraterNeed;
import com.theminespirit.core.board.Point;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Источник доски «воронка от крипера»: взрыв крипера за территорией поселения ставит
 * задание {@code FILL_CRATER} с гейтом по земле. Внутри территории воронку закрывает
 * координатор (31), задание доски не ставится. Слушатель — O(1) на взрыв, мир не сканирует.
 */
public final class CraterWatch {
    private CraterWatch() {
    }

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины слушатель воронок не встанет");
        }
        bus.addListener(CraterWatch::onDetonate);
    }

    private static void onDetonate(ExplosionEvent.Detonate event) {
        if (!SpiritConfig.boardEnabled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (!(source instanceof Creeper)) {
            return;
        }
        BlockPos center = BlockPos.containing(event.getExplosion().center());
        Point blast = new Point(center.getX(), center.getZ());
        if (!CraterNeed.outsideTerritory(blast, homes(level), BoardTuning.craterTerritoryRadius())) {
            return;
        }
        Map<String, Integer> need = CraterNeed.estimate(blast, event.getExplosion().radius());
        if (need.isEmpty()) {
            return;
        }
        BoardService service = BoardRegistry.get(level);
        BlockCoord target = new BlockCoord(center.getX(), center.getY(), center.getZ());
        service.submit(new BoardTicket(craterId(center), BoardCase.FILL_CRATER, Optional.empty(),
                BoardRules.priorityOf(BoardCase.FILL_CRATER), level.getGameTime(),
                BoardTuning.DEFAULT_TTL_TICKS, List.of(), need),
                TicketTarget.at(target).withCraterBlocks(need.values().stream().mapToInt(Integer::intValue).sum()));
    }

    /** Задание воронки адресуется её клеткой: повторный взрыв той же точки — дубль, а не второе задание. */
    static String craterId(BlockPos center) {
        return "crater:" + center.getX() + "," + center.getY() + "," + center.getZ();
    }

    private static List<Point> homes(ServerLevel level) {
        List<Point> homes = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SpiritEntity spirit && !spirit.isRemoved()) {
                spirit.home().ifPresent(home -> homes.add(new Point(home.x(), home.z())));
            }
        }
        return homes;
    }
}
