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
        String reason,
        String invincibilityStage,
        String lastCraft,
        String craftFailure,
        String lastMine,
        int openContainerId,
        String goal,
        String subGoal,
        String nextStep,
        String blocker,
        java.util.Map<String, Object> requiredItems,
        String lastResourceTarget
) {
    public BotDebugSnapshot(EBotLifecycleState lifecycle,
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
                            String reason) {
        this(lifecycle, intent, targetName, confidence, panic, threatScore, fightScore, fleeScore, lootScore, teamScore,
                teammateName, reason, "", "", "", "", 0, "", "", "", "", java.util.Map.of(), "");
    }

    public static BotDebugSnapshot empty() {
        return new BotDebugSnapshot(EBotLifecycleState.Unknown, BotIntent.IDLE, "", 0, 0, 0, 0, 0, 0, 0, "", "boot", "", "", "", "", 0,
                "", "", "", "", java.util.Map.of(), "");
    }
}
