package com.mybot.velocity.behavior;

public record BotDebugSnapshot(
        EBotLifecycleState lifecycle,
        BotIntent intent,
        String targetName,
        double confidence,
        double panic,
        double threatScore,
        double fightScore,
        double fleeScore,
        double lootScore,
        double teamScore,
        String teammateName,
        String reason
) {
    public static BotDebugSnapshot empty() {
        return new BotDebugSnapshot(EBotLifecycleState.Unknown, BotIntent.IDLE, "", 0, 0, 0, 0, 0, 0, 0, "", "boot");
    }
}
