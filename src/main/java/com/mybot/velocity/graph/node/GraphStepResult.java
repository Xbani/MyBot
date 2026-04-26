package com.mybot.velocity.graph.node;

public record GraphStepResult(boolean completed, String outcomeKey) {
    static final GraphStepResult STAY = new GraphStepResult(false, "");
}
