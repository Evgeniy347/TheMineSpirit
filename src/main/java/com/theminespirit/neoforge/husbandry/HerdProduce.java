package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.husbandry.AnimalRef;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class HerdProduce {
    private final HerdSession session;
    private final HerdFlock flock;

    HerdProduce(HerdSession session, HerdFlock flock) {
        this.session = session;
        this.flock = flock;
    }

    void pickShear() {
        if (session.done >= session.goal) {
            session.finishWithTally();
            return;
        }
        if (session.actions.shearsStack().isEmpty()) {
            session.fail("нет ножниц в инвентаре: стричь нечем, мир не изменён");
            return;
        }
        AnimalRef next = flock.nearestOf(Species.SHEEP, true, true);
        if (next == null) {
            session.finishWithTally();
            return;
        }
        Animal animal = session.live.get(next.id());
        if (animal == null || animal.isRemoved()) {
            flock.refreshSnapshot(true);
            return;
        }
        session.current = animal;
        session.currentId = next.id();
        session.act = HerdSession.Act.SHEAR_ONE;
        session.beginApproach(animal);
    }

    void pickMilk() {
        if (session.done >= session.goal) {
            session.finishWithTally();
            return;
        }
        if (session.actions.count(Items.BUCKET) == 0) {
            session.fail("нет пустых вёдер в инвентаре: доить не во что, мир не изменён");
            return;
        }
        AnimalRef next = flock.nearestOf(Species.COW, false, false);
        if (next == null) {
            session.finishWithTally();
            return;
        }
        Animal animal = session.live.get(next.id());
        if (animal == null || animal.isRemoved()) {
            flock.refreshSnapshot(true);
            return;
        }
        session.current = animal;
        session.currentId = next.id();
        session.act = HerdSession.Act.MILK_ONE;
        session.beginApproach(animal);
    }

    void shearOne() {
        ItemStack shears = session.actions.shearsStack();
        if (shears.isEmpty()) {
            session.fail("ножницы сломались посреди стрижки: пострижено " + session.done + " из " + session.goal);
            return;
        }
        if (!(session.current instanceof Sheep sheep) || sheep.isSheared()) {
            session.current = null;
            session.currentId = null;
            session.state = HerdSession.State.PICK;
            return;
        }
        session.recordAction();
        session.actions.shearWool(sheep, shears);
        session.sheared++;
        session.done++;
        session.handledIds.add(session.currentId);
        String woolId = "minecraft:" + sheep.getColor().getName() + "_wool";
        session.startCollect(IdFilter.of(List.of(woolId)), session.current.blockPosition(), HerdSession.COLLECT_RADIUS);
    }

    void milkOne() {
        Optional<String> failure = session.actions.milkIntoInventory();
        if (failure.isPresent()) {
            session.fail(failure.get() + ": подоено " + session.done + " из " + session.goal);
            return;
        }
        session.recordAction();
        session.milked++;
        session.done++;
        session.handledIds.add(session.currentId);
        session.current = null;
        session.currentId = null;
        session.state = HerdSession.State.PICK;
    }
}
