package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CraterNeed;
import com.theminespirit.core.board.CraterProfiles;
import com.theminespirit.core.board.Point;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public final class CraterWatch {
    private CraterWatch() {}

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
        Map<String, Integer> need =
                CraterNeed.estimate(blast, event.getExplosion().radius());
        if (need.isEmpty()) {
            return;
        }
        List<BlockCoord> profile = new ArrayList<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            profile.add(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
        }
        BoardRegistry.get(level).craters().remember(craterCause(center), profile);
    }

    static Set<String> liveCauses(ServerLevel level, BoardService service) {
        return service.craters()
                .retainLive(pos -> level.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()))
                        .isAir());
    }

    static List<BlockCoord> centers(BoardService service) {
        List<BlockCoord> centers = new ArrayList<>();
        for (String cause : service.craters().causes()) {
            Optional<BlockCoord> center = CraterProfiles.center(cause);
            center.ifPresent(centers::add);
        }
        return centers;
    }

    static String craterCause(BlockPos center) {
        return "crater:" + center.getX() + ":" + center.getY() + ":" + center.getZ();
    }

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
