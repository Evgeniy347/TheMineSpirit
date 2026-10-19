package com.theminespirit.core.plan;

public sealed interface TaskNode
        permits ScriptCall, SequenceNode, RepeatNode, FallbackNode, ParallelNode, ConditionNode {}
