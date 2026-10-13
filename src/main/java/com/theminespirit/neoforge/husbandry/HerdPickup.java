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
    private final HerdSession session;

    HerdPickup(HerdSession session) {
        this.session = session;
    }

    void findEggs() {
        List<ItemEntity> eggs = session.spirit.level().getEntitiesOfClass(
                ItemEntity.class,
                new AABB(session.home.x() - session.searchRadius, session.home.y() - session.searchRadius,
                        session.home.z() - session.searchRadius,
                        session.home.x() + session.searchRadius + 1.0,
                        session.home.y() + session.searchRadius + 1.0,
                        session.home.z() + session.searchRadius + 1.0),
                drop -> !drop.isRemoved() && drop.getItem().is(Items.EGG)
                        && session.pen.inside(new BlockCoord(drop.blockPosition().getX(),
                                drop.blockPosition().getY(), drop.blockPosition().getZ())));
        session.entityQueries++;
        session.maxEntityQueriesPerTick = Math.max(session.maxEntityQueriesPerTick, 1);
        if (eggs.isEmpty()) {
            session.fail("яиц в загоне нет: куры несутся медленно, мир не изменён");
            return;
        }
        session.startCollect(IdFilter.of(List.of("minecraft:egg")),
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
