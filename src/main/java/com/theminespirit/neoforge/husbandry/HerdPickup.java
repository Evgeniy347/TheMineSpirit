package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.HerdJob;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.DropCollector;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

final class HerdPickup {
    private static final IdFilter EGGS = IdFilter.of(List.of("minecraft:egg"));

    private final HerdSession session;

    HerdPickup(HerdSession session) {
        this.session = session;
    }

    void findEggs() {
        AABB area = new AABB(session.home.x() - session.searchRadius, session.home.y() - session.searchRadius,
                session.home.z() - session.searchRadius,
                session.home.x() + session.searchRadius + 1.0,
                session.home.y() + session.searchRadius + 1.0,
                session.home.z() + session.searchRadius + 1.0);
        List<ItemEntity> eggs = session.spirit.level().getEntitiesOfClass(
                ItemEntity.class,
                area,
                drop -> !drop.isRemoved() && drop.getItem().is(Items.EGG)
                        && session.pen.inside(new BlockCoord(drop.blockPosition().getX(),
                                drop.blockPosition().getY(), drop.blockPosition().getZ())));
        session.entityQueries++;
        session.maxEntityQueriesPerTick = Math.max(session.maxEntityQueriesPerTick, 1);
        if (eggs.isEmpty()) {
            int pulled = session.spirit.claimMagnetPulls(area, EGGS,
                    session.spirit.level().getGameTime() - session.ticks);
            if (pulled > 0) {
                session.eggsPicked += pulled;
                session.done = session.eggsPicked;
                session.finishWithTally();
                return;
            }
            session.fail("яиц в загоне нет: куры несутся медленно, мир не изменён");
            return;
        }
        session.startCollect(EGGS,
                new BlockPos(session.home.x(), session.home.y(), session.home.z()), session.searchRadius);
    }

    void collectTick() {
        int before = session.collector.entityQueries();
        DropCollector.State collected = session.collector.tick();
        session.maxEntityQueriesPerTick = Math.max(session.maxEntityQueriesPerTick,
                session.collector.entityQueries() - before);
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        session.entityQueries += session.collector.entityQueries();
        if (collected == DropCollector.State.FAILED) {
            session.fail("не удалось подобрать рядом с " + session.currentId + ": "
                    + session.collector.failureReason());
            return;
        }
        if (session.job == HerdJob.COLLECT_EGGS) {
            session.eggsPicked += session.collector.collected();
            session.done = session.eggsPicked;
            session.collector = null;
            session.current = null;
            session.currentId = null;
            session.finishWithTally();
            return;
        }
        session.collector = null;
        session.current = null;
        session.currentId = null;
        session.state = HerdSession.State.PICK;
    }
}
