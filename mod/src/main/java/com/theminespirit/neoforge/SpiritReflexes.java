package com.theminespirit.neoforge;

final class SpiritReflexes {
    private final PersonalSpaceKeeper personalSpace;
    private final ProjectileDodgeKeeper projectileDodge;

    SpiritReflexes(SpiritEntity spirit) {
        this.personalSpace = new PersonalSpaceKeeper(spirit);
        this.projectileDodge = new ProjectileDodgeKeeper(spirit);
    }

    void tick() {
        personalSpace.tick();
        projectileDodge.tick();
    }
}
