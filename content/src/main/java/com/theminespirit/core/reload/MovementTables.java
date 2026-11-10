package com.theminespirit.core.reload;

import com.theminespirit.core.movement.PersonalSpace;
import com.theminespirit.core.movement.ProjectileDodge;
import com.theminespirit.core.movement.WalkOutRules;
import com.theminespirit.core.observation.ObservationLimits;

record MovementTables(
        TableHolder<PersonalSpace> personalSpace,
        TableHolder<ObservationLimits> observation,
        TableHolder<ProjectileDodge> dodge,
        TableHolder<WalkOutRules> walkout) {
    static MovementTables register(TableRegistry registry) {
        return new MovementTables(
                DataTableDefinitions.single(
                        registry, "personal_space", PersonalSpace.RESOURCE, PersonalSpace::parse, PersonalSpace::load),
                DataTableDefinitions.single(
                        registry,
                        "observation",
                        ObservationLimits.RESOURCE,
                        ObservationLimits::parse,
                        ObservationLimits::load),
                DataTableDefinitions.single(
                        registry, "dodge", ProjectileDodge.RESOURCE, ProjectileDodge::parse, ProjectileDodge::load),
                DataTableDefinitions.single(
                        registry, "walkout", WalkOutRules.RESOURCE, WalkOutRules::parse, WalkOutRules::load));
    }
}
