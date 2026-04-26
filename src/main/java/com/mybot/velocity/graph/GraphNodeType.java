package com.mybot.velocity.graph;

/**
 * Types of graph nodes supported by the runtime.
 */
public enum GraphNodeType {
    Command,
    MoveTo,
    PathSequence,
    MineBlock,
    PlaceBlock,
    Craft,
    BuildSchematic,
    InteractEntity,
    Combat,
    Conditional,
    Loop,
    Wait,
    ServerHop
}
