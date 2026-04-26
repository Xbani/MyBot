package com.mybot.velocity.graph.node;

import com.mybot.velocity.config.GraphDefinition;

@FunctionalInterface
public interface GraphNodeExecutor {

    GraphStepResult execute(GraphDefinition.GraphNodeDefinition node, GraphRuntimeContext context, long ticksInNode);

    static GraphStepResult stay() {
        return GraphStepResult.STAY;
    }

    static GraphStepResult transition(String outcome) {
        return new GraphStepResult(true, outcome);
    }

    static GraphStepResult completedDefault() {
        return transition("default");
    }
}
