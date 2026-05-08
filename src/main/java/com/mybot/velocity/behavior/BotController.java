package com.mybot.velocity.behavior;

import com.mybot.velocity.action.BotAction;
import com.mybot.velocity.action.BotActionQueue;
import com.mybot.velocity.bot.BotPhysics;
import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.MovementInput;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;
import com.mybot.velocity.navigation.PathFollower;
import com.mybot.velocity.navigation.PathNode;
import com.mybot.velocity.navigation.Pathfinder;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class BotController {
    private static final Duration REPATH_INTERVAL = Duration.ofMillis(750);
    private static final double BOT_AVOIDANCE_RADIUS = 1.35;
    private static final double BOT_AVOIDANCE_STRENGTH = 0.65;
    private static final double PASSIVE_AVOIDANCE_RADIUS = 2.85;
    private static final double PASSIVE_AVOIDANCE_STRENGTH = 0.95;
    private static final double BOT_AVOIDANCE_HEIGHT = 2.2;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final Duration DEFAULT_PASSIVE_TAUNT_INTERVAL = Duration.ofSeconds(18);

    private final HgBehaviorConfig config;
    private final BotActionQueue actions;
    private final BotPersonality personality;
    private final BotSkillProfile skill;
    private final BotMemory memory = new BotMemory();
    private final BotGameStateDetector lifecycle = new BotGameStateDetector();
    private final BotGamePlan gamePlan = new BotGamePlan();
    private final BotDecisionSystem decisions;
    private final HumanInputLayer inputLayer;
    private final BotInvincibilityPlan invincibilityPlan;
    private final Pathfinder pathfinder = new Pathfinder();
    private final PathFollower follower = new PathFollower();
    private final Random random;
    private final double passiveAngleOffset;

    private BotIntent intent = BotIntent.IDLE;
    private EBotLifecycleState lifecycleState = EBotLifecycleState.Unknown;
    private BotGamePlan.Phase phase = BotGamePlan.Phase.Lobby;
    private BotBlackboard lastBoard;
    private BotDebugSnapshot debug = BotDebugSnapshot.empty();
    private List<PathNode> path = List.of();
    private Vec3 pathTarget = Vec3.ZERO;
    private Instant lastPathAt = Instant.EPOCH;
    private Vec3 roamTarget = Vec3.ZERO;
    private Vec3 roamAnchor;
    private Vec3 lookAroundPoint = Vec3.ZERO;
    private int passiveLookTargetEntityId = -1;
    private Instant passiveLookTargetUntil = Instant.EPOCH;
    private Instant nextPassiveLookDecisionAt = Instant.EPOCH;
    private Instant nextRoamAt = Instant.EPOCH;
    private Instant passivePauseUntil = Instant.EPOCH;
    private Instant passiveMoveUntil = Instant.EPOCH;
    private Instant nextLookAroundAt = Instant.EPOCH;
    private Instant nextPassiveTauntAt = Instant.EPOCH;
    private Instant passiveTauntUntil = Instant.EPOCH;
    private Instant nextPassiveTauntSwingAt = Instant.EPOCH;
    private String activePassiveTaunt = "";
    private boolean passiveTauntSneaking;
    private Instant passiveLastProgressAt = Instant.EPOCH;
    private Instant pregameFlyUntil = Instant.EPOCH;
    private Vec3 passiveLastPosition = Vec3.ZERO;
    private int passiveRoamStep;
    private boolean pregameFlying;
    private boolean legacyActiveTick;

    public BotController(HgBehaviorConfig config, BotActionQueue actions) {
        this(config, actions, 0xC0FFEE);
    }

    public BotController(HgBehaviorConfig config, BotActionQueue actions, long seed) {
        this.config = config;
        this.actions = actions;
        this.personality = BotPersonality.fromTraits(config.traits());
        this.skill = BotSkillProfile.fromTraits(config.traits(), personality.skillLevel());
        this.decisions = new BotDecisionSystem(seed);
        this.inputLayer = new HumanInputLayer(seed);
        this.invincibilityPlan = new BotInvincibilityPlan(actions, config.traits(), seed);
        this.random = new Random(seed ^ 0xB01B01L);
        this.passiveAngleOffset = this.random.nextDouble() * Math.PI * 2.0;
    }

    public ControlPlan tick(BotWorldState state, BotPhysics physics) {
        String server = state.serverName() == null || state.serverName().isBlank() ? "hg0" : state.serverName();
        if (state.matchStartedAt().equals(Instant.EPOCH)) {
            state.markMatchStarted();
        }
        return tick(state, physics, server, state.serverName() == null || state.serverName().isBlank());
    }

    public ControlPlan tick(BotWorldState state, BotPhysics physics, String serverName) {
        return tick(state, physics, serverName, false);
    }

    private ControlPlan tick(BotWorldState state, BotPhysics physics, String serverName, boolean legacyActiveTick) {
        this.legacyActiveTick = legacyActiveTick;
        Instant now = Instant.now();
        EBotLifecycleState previousLifecycle = lifecycleState;
        lifecycleState = legacyActiveTick ? EBotLifecycleState.ActiveHG : lifecycle.detect(serverName, state, physics.position(), now, state.scoreboard().snapshot());
        if (previousLifecycle != lifecycleState) {
            resetActionState();
            memory.resetVolatile();
            if (lifecycleState == EBotLifecycleState.LobbyRoaming || lifecycleState == EBotLifecycleState.PregameWaiting) {
                state.resetMatchTiming();
            } else if (lifecycleState == EBotLifecycleState.InvincibilityStart) {
                state.markMatchStarted();
            }
        }
        memory.update(state, physics.position(), now);
        WorldFacts facts = WorldFacts.from(state, physics.position(), memory, personality, config, now);
        phase = gamePlan.update(lifecycleState, lastBoard, facts, now);
        BotBlackboard board = BotBlackboard.from(now, serverName, lifecycleState, phase, physics.position(), state, memory, personality, config, facts);
        lastBoard = board;

        if (lifecycleState == EBotLifecycleState.SpectatorDead) {
            intent = BotIntent.IDLE;
            debug = new BotDebugSnapshot(lifecycleState, intent, "", 0, board.panic(), 0, 0, 0, 0, 0, "", "spectator/dead");
            return new ControlPlan(MovementInput.NONE, Optional.empty());
        }
        if (lifecycleState == EBotLifecycleState.LobbyRoaming || lifecycleState == EBotLifecycleState.PregameWaiting || lifecycleState == EBotLifecycleState.Unknown) {
            return passiveRoaming(board, physics);
        }
        if (lifecycleState == EBotLifecycleState.InvincibilityStart) {
            BotInvincibilityPlan.Plan plan = invincibilityPlan.tick(board, physics);
            intent = BotIntent.CRAFT_STONE_SWORD;
            debug = new BotDebugSnapshot(lifecycleState, intent, "",
                    0, board.panic(), 0, 0, 0, 80, 0, "",
                    "invincibility:" + plan.stage() + " " + plan.reason(),
                    invincibilityPlan.stage().name(), invincibilityPlan.lastCraft(), invincibilityPlan.craftFailure(),
                    invincibilityPlan.lastMine(), state.inventory().openContainerId(),
                    invincibilityPlan.goal(), invincibilityPlan.subGoal(), invincibilityPlan.nextStep(),
                    invincibilityPlan.blocker(), invincibilityPlan.requiredItems(), invincibilityPlan.lastResourceTarget());
            return new ControlPlan(applyWaterAndCrowdMovement(humanize(plan.movement(), board.panic()), physics, board), Optional.empty());
        }

        BotDecisionSystem.Decision decision = decisions.decide(board, personality, skill, memory, config);
        intent = decision.intent();
        Optional<TrackedPlayer> target = decision.target();
        maybeSocial(board, decision);
        if (intent == BotIntent.FOLLOW && decision.teamScore() > 12.0 && target.filter(TrackedPlayer::isBot).isPresent()
                && memory.socialReady(now, Duration.ofSeconds(8))) {
            memory.setTeammate(target.get(), now);
            if (random.nextDouble() < personality.teamingLikelihood()) {
                actions.enqueueIfAbsent(BotAction.StartSneak.class, new BotAction.StartSneak());
            }
        } else if (intent != BotIntent.FOLLOW && random.nextDouble() < 0.04) {
            actions.enqueueIfAbsent(BotAction.StopSneak.class, new BotAction.StopSneak());
        }
        debug = new BotDebugSnapshot(lifecycleState, intent, target.map(TrackedPlayer::username).orElse(""),
                decision.confidence(), decision.panic(), decision.threatScore(), decision.fightScore(),
                decision.fleeScore(), decision.lootScore(), decision.teamScore(),
                board.teammate().map(TrackedPlayer::username).orElse(""), decision.strategy() + ":" + decision.reason());

        inputLayer.maybeSwitchWeapon(state, actions, skill, now, intent == BotIntent.FIGHT || intent == BotIntent.FLEE);
        if (intent == BotIntent.HEAL) {
            actions.enqueueIfAbsent(BotAction.RightClickItem.class, new BotAction.RightClickItem());
            return new ControlPlan(applyWaterAndCrowdMovement(MovementInput.NONE, physics, board), target);
        }
        if (shouldCraftStoneSwordFirst(board, decision)) {
            BotInvincibilityPlan.Plan plan = invincibilityPlan.tick(board, physics);
            intent = BotIntent.CRAFT_STONE_SWORD;
            debug = new BotDebugSnapshot(lifecycleState, intent, "",
                    decision.confidence(), board.panic(), decision.threatScore(), decision.fightScore(),
                    decision.fleeScore(), 100, decision.teamScore(), "",
                    "stone-sword-first:" + plan.stage() + " " + plan.reason(),
                    invincibilityPlan.stage().name(), invincibilityPlan.lastCraft(), invincibilityPlan.craftFailure(),
                    invincibilityPlan.lastMine(), state.inventory().openContainerId(),
                    invincibilityPlan.goal(), invincibilityPlan.subGoal(), invincibilityPlan.nextStep(),
                    invincibilityPlan.blocker(), invincibilityPlan.requiredItems(), invincibilityPlan.lastResourceTarget());
            return new ControlPlan(applyWaterAndCrowdMovement(humanize(plan.movement(), board.panic()), physics, board), Optional.empty());
        }
        if (intent == BotIntent.LOOT) {
            MovementInput lootMove = searchRouteInput(board, physics);
            return new ControlPlan(applyWaterAndCrowdMovement(humanize(lootMove, board.panic()), physics, board), Optional.empty());
        }
        if (target.isEmpty()) {
            return passiveRoaming(board, physics);
        }
        Vec3 targetPosition = target.get().position();
        inputLayer.updateLook(physics, targetPosition, skill, board.panic(), now);
        if (intent == BotIntent.FLEE) {
            MovementInput flee = humanize(fleeInput(physics.position(), targetPosition), board.panic());
            return new ControlPlan(applyWaterAndCrowdMovement(flee, physics, board), target);
        }
        if (targetPosition.y() > physics.position().y() + 0.45) {
            MovementInput climb = humanize(pathInput(state, physics.position(), targetPosition), board.panic());
            return new ControlPlan(applyWaterAndCrowdMovement(climb, physics, board), target);
        }
        if (intent == BotIntent.FIGHT && canAttemptMelee(state, physics, target.get())) {
            inputLayer.tryAttack(state, physics, target.get(), actions, skill, config, board.panic(), now);
            MovementInput combat = new MovementInput(combatForward(physics.position(), targetPosition), strafeOscillation(), random.nextDouble() < skill.critAttemptChance(), true, false);
            return new ControlPlan(applyWaterAndCrowdMovement(humanize(combat, board.panic()), physics, board), target);
        }
        MovementInput followInput = pathInput(state, physics.position(), targetPosition);
        return new ControlPlan(applyWaterAndCrowdMovement(humanize(followInput, board.panic()), physics, board), target);
    }

    private boolean shouldCraftStoneSwordFirst(BotBlackboard board, BotDecisionSystem.Decision decision) {
        if (invincibilityPlan.stoneSwordReady()) {
            return false;
        }
        if (board.phase() != BotGamePlan.Phase.EarlyGame && board.phase() != BotGamePlan.Phase.Invincibility) {
            return false;
        }
        if (decision.intent() == BotIntent.FLEE || decision.intent() == BotIntent.HEAL) {
            return false;
        }
        return decision.intent() != BotIntent.FIGHT || decision.confidence() < 0.68;
    }

    public BotIntent intent() {
        return intent;
    }

    public List<PathNode> path() {
        return List.copyOf(path);
    }

    public Vec3 pathTarget() {
        return pathTarget;
    }

    public boolean pathStuck() {
        return follower.stuck();
    }

    public EBotLifecycleState lifecycleState() {
        return lifecycleState;
    }

    public BotDebugSnapshot debug() {
        return debug;
    }

    public void resetAfterTeleport() {
        resetActionState();
        memory.resetVolatile();
        inputLayer.reset();
    }

    private void resetActionState() {
        intent = BotIntent.IDLE;
        path = List.of();
        pathTarget = Vec3.ZERO;
        follower.reset();
        roamTarget = Vec3.ZERO;
        roamAnchor = null;
        lookAroundPoint = Vec3.ZERO;
        passiveLookTargetEntityId = -1;
        passiveLookTargetUntil = Instant.EPOCH;
        nextPassiveLookDecisionAt = Instant.EPOCH;
        nextRoamAt = Instant.EPOCH;
        passivePauseUntil = Instant.EPOCH;
        passiveMoveUntil = Instant.EPOCH;
        nextLookAroundAt = Instant.EPOCH;
        nextPassiveTauntAt = Instant.EPOCH;
        passiveTauntUntil = Instant.EPOCH;
        nextPassiveTauntSwingAt = Instant.EPOCH;
        activePassiveTaunt = "";
        if (passiveTauntSneaking) {
            actions.enqueueIfAbsent(BotAction.StopSneak.class, new BotAction.StopSneak());
        }
        passiveTauntSneaking = false;
        passiveLastProgressAt = Instant.EPOCH;
        pregameFlyUntil = Instant.EPOCH;
        passiveLastPosition = Vec3.ZERO;
        passiveRoamStep = 0;
        if (pregameFlying) {
            actions.enqueueIfAbsent(BotAction.SetFlying.class, new BotAction.SetFlying(false));
        }
        pregameFlying = false;
        if (lifecycleState == EBotLifecycleState.InvincibilityStart
                || lifecycleState == EBotLifecycleState.PregameWaiting
                || lifecycleState == EBotLifecycleState.LobbyRoaming
                || lifecycleState == EBotLifecycleState.Unknown) {
            invincibilityPlan.reset();
        }
    }

    private void maybeSocial(BotBlackboard board, BotDecisionSystem.Decision decision) {
        if (!memory.socialReady(board.now(), Duration.ofSeconds(12)) || random.nextDouble() > personality.tauntLikelihood()) {
            return;
        }
        if (decision.intent() == BotIntent.FLEE && board.panic() > 0.62) {
            actions.enqueueIfAbsent(BotAction.Say.class, new BotAction.Say(random.nextBoolean() ? "nooo" : "team?"));
            memory.markSocial(board.now());
        } else if (decision.intent() == BotIntent.FIGHT && decision.confidence() > 0.68) {
            actions.enqueueIfAbsent(BotAction.Say.class, new BotAction.Say(random.nextBoolean() ? "come" : "ez"));
            memory.markSocial(board.now());
        } else if (decision.intent() == BotIntent.FOLLOW && decision.teamScore() > 12.0) {
            actions.enqueueIfAbsent(BotAction.Say.class, new BotAction.Say("team?"));
            memory.markSocial(board.now());
        }
    }

    private MovementInput pathInput(BotWorldState state, Vec3 position, Vec3 target) {
        if (target.y() > position.y() + 0.45 && position.horizontalDistanceTo(target) < 2.25) {
            path = List.of();
            follower.reset();
            pathTarget = target;
            return directInput(position, target);
        }
        Instant now = Instant.now();
        if (path.isEmpty() || follower.stuck() || target.horizontalDistanceTo(pathTarget) > 2.0 || Duration.between(lastPathAt, now).compareTo(REPATH_INTERVAL) > 0) {
            double goalRadius = target.y() > position.y() + 0.45 ? 0.8 : Math.max(1.4, config.meleeRange() - 0.7);
            path = pathfinder.findPath(state.blocks(), position, target, goalRadius).orElse(List.of());
            pathTarget = target;
            lastPathAt = now;
            follower.reset();
        }
        if (path.isEmpty()) {
            return directInput(position, target);
        }
        return follower.follow(position, BotPhysics.lookAt(BotPhysics.eyePosition(position), BotPhysics.bodyAimPosition(target)).yaw(),
                path, position.horizontalDistanceTo(target) > 6);
    }

    private MovementInput directInput(Vec3 position, Vec3 target) {
        double distance = position.horizontalDistanceTo(target);
        boolean jump = target.y() > position.y() + 0.45 && distance < 4.5;
        if (distance <= 3.25 && !jump) {
            return MovementInput.NONE;
        }
        return new MovementInput(1, 0, jump, distance > 7, false);
    }

    private ControlPlan passiveRoaming(BotBlackboard board, BotPhysics physics) {
        if (roamAnchor == null || board.position().horizontalDistanceTo(roamAnchor) > passiveRadius() + 8.0) {
            roamAnchor = board.position();
            roamTarget = board.position();
            passivePauseUntil = board.now().plusMillis(passivePauseMillis());
            passiveMoveUntil = Instant.EPOCH;
        }
        Optional<TrackedPlayer> lookTarget = passiveLookTarget(board);
        updatePassiveLook(board, physics, lookTarget);
        if (board.now().isBefore(passivePauseUntil)) {
            return passiveIdle(board, physics, lookTarget, "passive pause/look around");
        }
        boolean reachedTarget = board.position().horizontalDistanceTo(roamTarget) < 1.5;
        boolean moveExpired = !passiveMoveUntil.equals(Instant.EPOCH) && board.now().isAfter(passiveMoveUntil);
        if (reachedTarget || moveExpired) {
            path = List.of();
            follower.reset();
            roamTarget = board.position();
            passivePauseUntil = board.now().plusMillis(passivePauseMillis());
            passiveMoveUntil = Instant.EPOCH;
            return passiveIdle(board, physics, lookTarget, moveExpired ? "passive move budget exhausted" : "passive target reached");
        }
        if (passiveMoveUntil.equals(Instant.EPOCH) || board.now().isAfter(nextRoamAt)) {
            roamTarget = choosePassiveRoamTarget(board);
            passiveMoveUntil = board.now().plusMillis(passiveMoveMillis());
            nextRoamAt = passiveMoveUntil;
        }
        MovementInput roam = pathInput(board.state(), board.position(), roamTarget);
        if (lifecycleState == EBotLifecycleState.PregameWaiting || lifecycleState == EBotLifecycleState.LobbyRoaming) {
            double forwardLimit = lifecycleState == EBotLifecycleState.LobbyRoaming ? 0.42 : 0.58;
            double strafeLimit = lifecycleState == EBotLifecycleState.LobbyRoaming ? 0.32 : 0.42;
            roam = new MovementInput(Math.min(forwardLimit, Math.max(-forwardLimit, roam.forward())),
                    Math.min(strafeLimit, Math.max(-strafeLimit, roam.strafe())), roam.jump(), false, roam.sneak());
        }
        if (random.nextDouble() < 0.015) {
            roam = new MovementInput(roam.forward(), roam.strafe(), true, false, false);
        } else if (random.nextDouble() < 0.012) {
            roam = new MovementInput(roam.forward() * 0.2, roam.strafe(), false, false, true);
        }
        intent = BotIntent.IDLE;
        debug = new BotDebugSnapshot(lifecycleState, intent, lookTarget.map(TrackedPlayer::username).orElse(""), 0,
                board.panic(), 0, 0, 0, 0, 0, "", "passive directed roaming");
        MovementInput movement = applyPregameFlyRecovery(humanize(roam, board.panic()), board, physics);
        return new ControlPlan(applyWaterAndCrowdMovement(movement, physics, board), lookTarget);
    }

    private ControlPlan passiveIdle(BotBlackboard board, BotPhysics physics, Optional<TrackedPlayer> lookTarget, String reason) {
        intent = BotIntent.IDLE;
        debug = new BotDebugSnapshot(lifecycleState, intent, lookTarget.map(TrackedPlayer::username).orElse(""), 0,
                board.panic(), 0, 0, 0, 0, 0, "", reason);
        MovementInput recoveredIdle = applyPregameFlyRecovery(passiveTauntInput(board, lookTarget), board, physics);
        return new ControlPlan(applyWaterAndCrowdMovement(recoveredIdle, physics, board), lookTarget);
    }

    private MovementInput applyPregameFlyRecovery(MovementInput input, BotBlackboard board, BotPhysics physics) {
        if (lifecycleState != EBotLifecycleState.PregameWaiting) {
            stopPregameFlyRecovery();
            return input;
        }
        updatePassiveProgress(board, input);
        double targetDistance = board.position().horizontalDistanceTo(roamTarget);
        boolean tryingToMove = input.moving() && targetDistance > 3.5;
        boolean noProgress = tryingToMove && Duration.between(passiveLastProgressAt, board.now()).toMillis() > 2200;
        boolean blocked = tryingToMove && physics.horizontalCollision()
                && Duration.between(passiveLastProgressAt, board.now()).toMillis() > 650;
        if (!pregameFlying && (noProgress || blocked)) {
            pregameFlying = true;
            pregameFlyUntil = board.now().plusMillis(4500 + random.nextInt(1800));
            actions.enqueueIfAbsent(BotAction.SetFlying.class, new BotAction.SetFlying(true));
        }
        if (!pregameFlying) {
            return input;
        }
        if (targetDistance < 2.0 || board.now().isAfter(pregameFlyUntil)) {
            stopPregameFlyRecovery();
            passiveLastProgressAt = board.now();
            passiveLastPosition = board.position();
            return input;
        }
        Vec3 aim = roamTarget.add(0, 1.5, 0);
        BotPhysics.LookAngles look = BotPhysics.lookAt(BotPhysics.eyePosition(physics.position()), aim);
        physics.setLook(smooth(physics.yaw(), look.yaw(), 0.34), smooth(physics.pitch(), look.pitch(), 0.16));
        double desiredY = targetDistance > 2.8
                ? Math.max(roamTarget.y() + 2.0, board.position().y() + 0.7)
                : roamTarget.y() + 0.5;
        boolean ascend = board.position().y() < desiredY - 0.25;
        boolean descend = board.position().y() > desiredY + 0.45;
        return new MovementInput(1.0, 0.0, ascend, false, descend);
    }

    private void updatePassiveProgress(BotBlackboard board, MovementInput input) {
        if (passiveLastProgressAt.equals(Instant.EPOCH)) {
            passiveLastProgressAt = board.now();
            passiveLastPosition = board.position();
            return;
        }
        if (!input.moving() || board.position().horizontalDistanceTo(passiveLastPosition) > 0.22) {
            passiveLastProgressAt = board.now();
            passiveLastPosition = board.position();
        }
    }

    private void stopPregameFlyRecovery() {
        if (pregameFlying) {
            actions.enqueueIfAbsent(BotAction.SetFlying.class, new BotAction.SetFlying(false));
        }
        pregameFlying = false;
        pregameFlyUntil = Instant.EPOCH;
    }

    private MovementInput humanize(MovementInput input, double panic) {
        return legacyActiveTick ? input : inputLayer.humanizeMovement(input, skill, panic);
    }

    private MovementInput searchRouteInput(BotBlackboard board, BotPhysics physics) {
        if (board.facts().bestLootTarget().isPresent() && !board.facts().underImmediateThreat(board.position())) {
            Vec3 lootTarget = board.facts().bestLootTarget().get();
            BotPhysics.LookAngles look = BotPhysics.lookAt(BotPhysics.eyePosition(physics.position()), BotPhysics.bodyAimPosition(lootTarget));
            physics.setLook(smooth(physics.yaw(), look.yaw(), 0.30), smooth(physics.pitch(), look.pitch(), 0.16));
            return pathInput(board.state(), board.position(), lootTarget);
        }
        if (board.now().isAfter(nextRoamAt) || board.position().horizontalDistanceTo(roamTarget) < 2.5) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = switch (board.phase()) {
                case Invincibility, EarlyGame -> 10.0 + random.nextDouble() * 16.0;
                case FeastPhase -> 6.0 + random.nextDouble() * 12.0;
                default -> 5.0 + random.nextDouble() * 8.0;
            };
            roamTarget = board.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            nextRoamAt = board.now().plusMillis(1300 + random.nextInt(2600));
        }
        BotPhysics.LookAngles look = BotPhysics.lookAt(BotPhysics.eyePosition(physics.position()), BotPhysics.bodyAimPosition(roamTarget));
        physics.setLook(smooth(physics.yaw(), look.yaw(), 0.32), smooth(physics.pitch(), look.pitch(), 0.18));
        return pathInput(board.state(), board.position(), roamTarget);
    }

    private Optional<TrackedPlayer> passiveLookTarget(BotBlackboard board) {
        if (board.now().isBefore(passiveLookTargetUntil)) {
            Optional<TrackedPlayer> current = board.visiblePlayers().stream()
                    .filter(player -> player.entityId() == passiveLookTargetEntityId)
                    .findFirst();
            if (current.isPresent()) {
                return current;
            }
        }
        if (board.now().isBefore(nextPassiveLookDecisionAt)) {
            return Optional.empty();
        }
        nextPassiveLookDecisionAt = board.now().plusMillis(longTrait("passive-look-decision-min-millis", 1800L)
                + random.nextLong(Math.max(1L, longTrait("passive-look-decision-jitter-millis", 2600L))));
        Optional<TrackedPlayer> next = board.visiblePlayers().stream()
                .filter(player -> board.position().horizontalDistanceTo(player.position()) < passiveLookRadius())
                .filter(player -> player.username() != null && !player.username().equalsIgnoreCase("unknown"))
                .min(java.util.Comparator.comparingDouble(player -> board.position().horizontalDistanceTo(player.position())));
        if (next.isPresent() && random.nextDouble() < doubleTrait("passive-look-player-chance", 0.72)) {
            passiveLookTargetEntityId = next.get().entityId();
            passiveLookTargetUntil = board.now().plusMillis(longTrait("passive-look-target-min-millis", 3200L)
                    + random.nextLong(Math.max(1L, longTrait("passive-look-target-jitter-millis", 4200L))));
            return next;
        }
        passiveLookTargetEntityId = -1;
        passiveLookTargetUntil = Instant.EPOCH;
        return Optional.empty();
    }

    private void updatePassiveLook(BotBlackboard board, BotPhysics physics, Optional<TrackedPlayer> lookTarget) {
        if (lookTarget.isPresent()) {
            Vec3 target = lookTarget.get().position().add(0, doubleTrait("passive-look-player-height", 0.85), 0);
            BotPhysics.LookAngles look = BotPhysics.lookAt(BotPhysics.eyePosition(physics.position()), target);
            double tracking = doubleTrait("passive-look-tracking", 0.075);
            physics.setLook(smooth(physics.yaw(), look.yaw(), tracking), smooth(physics.pitch(), look.pitch(), tracking * 0.72));
            return;
        }
        if (board.now().isAfter(nextLookAroundAt)) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 5.0 + random.nextDouble() * 7.0;
            double height = random.nextDouble() < 0.10 ? 1.2 + random.nextDouble() * 0.8 : 0.65 + random.nextDouble() * 0.45;
            lookAroundPoint = board.position().add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
            nextLookAroundAt = board.now().plusMillis(longTrait("passive-look-around-min-millis", 2800L)
                    + random.nextLong(Math.max(1L, longTrait("passive-look-around-jitter-millis", 4200L))));
        }
        BotPhysics.LookAngles look = BotPhysics.lookAt(BotPhysics.eyePosition(physics.position()), lookAroundPoint);
        physics.setLook(smooth(physics.yaw(), look.yaw(), doubleTrait("passive-look-around-tracking", 0.055)),
                smooth(physics.pitch(), look.pitch(), doubleTrait("passive-look-around-tracking", 0.055) * 0.8));
    }

    private MovementInput passiveTauntInput(BotBlackboard board, Optional<TrackedPlayer> lookTarget) {
        finishExpiredPassiveTaunt(board);
        if (passiveTauntsEnabled() && lookTarget.isPresent() && board.now().isAfter(nextPassiveTauntAt) && activePassiveTaunt.isBlank()) {
            startPassiveTaunt(board);
        }
        if (activePassiveTaunt.isBlank()) {
            return MovementInput.NONE;
        }
        if ("friendly_swing".equals(activePassiveTaunt)) {
            if (board.now().isAfter(nextPassiveTauntSwingAt)) {
                actions.enqueue(new BotAction.SwingMainHand());
                nextPassiveTauntSwingAt = board.now().plusMillis(650 + random.nextInt(500));
            }
            return MovementInput.NONE;
        }
        if ("goofy_sneak".equals(activePassiveTaunt)) {
            if (!passiveTauntSneaking) {
                actions.enqueueIfAbsent(BotAction.StartSneak.class, new BotAction.StartSneak());
                passiveTauntSneaking = true;
            }
            return MovementInput.NONE;
        }
        if ("air_hop_swing".equals(activePassiveTaunt)) {
            if (board.now().isAfter(nextPassiveTauntSwingAt)) {
                actions.enqueue(new BotAction.SwingMainHand());
                nextPassiveTauntSwingAt = board.now().plusMillis(430 + random.nextInt(220));
            }
            return new MovementInput(0, 0, true, false, false);
        }
        return MovementInput.NONE;
    }

    private void startPassiveTaunt(BotBlackboard board) {
        activePassiveTaunt = choosePassiveTaunt();
        long duration = switch (activePassiveTaunt) {
            case "goofy_sneak" -> 450L + random.nextInt(700);
            case "air_hop_swing" -> 800L + random.nextInt(700);
            default -> 550L + random.nextInt(750);
        };
        passiveTauntUntil = board.now().plusMillis(duration);
        nextPassiveTauntSwingAt = Instant.EPOCH;
        nextPassiveTauntAt = board.now().plus(passiveTauntInterval());
    }

    private void finishExpiredPassiveTaunt(BotBlackboard board) {
        if (activePassiveTaunt.isBlank() || board.now().isBefore(passiveTauntUntil)) {
            return;
        }
        if (passiveTauntSneaking) {
            actions.enqueueIfAbsent(BotAction.StopSneak.class, new BotAction.StopSneak());
            passiveTauntSneaking = false;
        }
        activePassiveTaunt = "";
        passiveTauntUntil = Instant.EPOCH;
        nextPassiveTauntSwingAt = Instant.EPOCH;
    }

    private String choosePassiveTaunt() {
        double friendly = Math.max(0.0, doubleTrait("passive-taunt-friendly-swing-weight", 0.52));
        double sneak = Math.max(0.0, doubleTrait("passive-taunt-goofy-sneak-weight", 0.34));
        double hop = Math.max(0.0, doubleTrait("passive-taunt-air-hop-swing-weight", 0.14));
        double total = friendly + sneak + hop;
        if (total <= 0.0) {
            return "friendly_swing";
        }
        double roll = random.nextDouble() * total;
        if (roll < friendly) {
            return "friendly_swing";
        }
        if (roll < friendly + sneak) {
            return "goofy_sneak";
        }
        return "air_hop_swing";
    }

    private boolean passiveTauntsEnabled() {
        Object value = config.traits().get("passive-taunts-enabled");
        return value == null || Boolean.parseBoolean(value.toString());
    }

    private Duration passiveTauntInterval() {
        long base = longTrait("passive-taunt-interval-millis", DEFAULT_PASSIVE_TAUNT_INTERVAL.toMillis());
        long jitter = longTrait("passive-taunt-jitter-millis", 12_000L);
        return Duration.ofMillis(base + random.nextLong(Math.max(1L, jitter)));
    }

    private double passiveLookRadius() {
        return doubleTrait("passive-look-radius", 13.0);
    }

    private double doubleTrait(String key, double fallback) {
        Object value = config.traits().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private long longTrait(String key, long fallback) {
        Object value = config.traits().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private Vec3 choosePassiveRoamTarget(BotBlackboard board) {
        double radius = passiveRadius();
        Vec3 best = board.position();
        double bestScore = Double.NEGATIVE_INFINITY;
        passiveRoamStep++;
        double preferredDistance = passivePreferredDistance(radius);
        for (int i = 0; i < 14; i++) {
            double baseAngle = passiveAngleOffset + passiveRoamStep * GOLDEN_ANGLE;
            double angle = baseAngle + (i - 6.5) * 0.23 + random.nextGaussian() * 0.08;
            double distance = Math.max(2.2, preferredDistance + random.nextGaussian() * radius * 0.12);
            distance = Math.min(radius, Math.max(radius * 0.22, distance));
            Vec3 candidate = roamAnchor.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            double crowdPenalty = board.visiblePlayers().stream()
                    .mapToDouble(player -> {
                        double d = Math.max(0.5, candidate.horizontalDistanceTo(player.position()));
                        return d < 7.0 ? (7.0 - d) * (7.0 - d) * 1.8 : 0.0;
                    })
                    .sum();
            double anchorPenalty = Math.max(0.0, 2.5 - candidate.horizontalDistanceTo(roamAnchor)) * 4.0;
            double backtrackPenalty = Math.max(0.0, 3.5 - candidate.horizontalDistanceTo(roamTarget)) * 2.5;
            double travel = Math.min(6.0, board.position().horizontalDistanceTo(candidate));
            double score = travel - crowdPenalty - anchorPenalty - backtrackPenalty + random.nextDouble() * 2.0;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private double passiveRadius() {
        return lifecycleState == EBotLifecycleState.PregameWaiting ? config.pregameRoamRadius() : config.lobbyRoamRadius();
    }

    private double passivePreferredDistance(double radius) {
        if (lifecycleState == EBotLifecycleState.LobbyRoaming) {
            double wave = 0.42 + 0.33 * Math.sin(passiveRoamStep * 0.7 + passiveAngleOffset);
            return radius * Math.max(0.28, Math.min(0.82, wave));
        }
        double wave = 0.55 + 0.30 * Math.sin(passiveRoamStep * 0.6 + passiveAngleOffset);
        return radius * Math.max(0.35, Math.min(0.90, wave));
    }

    private long passivePauseMillis() {
        return lifecycleState == EBotLifecycleState.LobbyRoaming
                ? 14_000L + random.nextInt(8_000)
                : 10_000L + random.nextInt(7_000);
    }

    private long passiveMoveMillis() {
        return lifecycleState == EBotLifecycleState.LobbyRoaming
                ? 2_500L + random.nextInt(1_500)
                : 3_000L + random.nextInt(2_000);
    }

    private MovementInput fleeInput(Vec3 position, Vec3 threat) {
        double distance = position.horizontalDistanceTo(threat);
        boolean sneak = distance < 2.5;
        return new MovementInput(-1, strafeOscillation(), false, !sneak, sneak);
    }

    private boolean canAttemptMelee(BotWorldState state, BotPhysics physics, TrackedPlayer target) {
        return physics.position().distanceSquaredTo(target.position()) <= config.meleeRange() * config.meleeRange()
                && state.blocks().hasLineOfSight(BotPhysics.eyePosition(physics.position()), BotPhysics.bodyAimPosition(target.position()));
    }

    private double combatForward(Vec3 position, Vec3 target) {
        double distance = position.horizontalDistanceTo(target);
        if (distance < 2.25) {
            return -0.25;
        }
        if (distance > 3.0) {
            return 0.75;
        }
        return 0.0;
    }

    private MovementInput applyWaterAndCrowdMovement(MovementInput input, BotPhysics physics, BotBlackboard board) {
        MovementInput withCrowd = avoidBots(input, physics, board.visiblePlayers());
        if (board.state().blocks().isLiquidLike(physics.position()) && !withCrowd.moving()) {
            withCrowd = new MovementInput(0.65, withCrowd.strafe(), withCrowd.jump(), true, false);
        }
        if (shouldHoldJumpForSwimming(physics, board) || shouldAutoJumpObstacle(withCrowd, physics, board)) {
            return new MovementInput(withCrowd.forward(), withCrowd.strafe(), true, withCrowd.sprint(), withCrowd.sneak()).clamp();
        }
        return withCrowd;
    }

    private boolean shouldHoldJumpForSwimming(BotPhysics physics, BotBlackboard board) {
        Vec3 position = physics.position();
        if (board.state().blocks().isLiquidLike(position)) {
            return true;
        }
        int feetX = (int) Math.floor(position.x());
        int feetY = (int) Math.floor(position.y());
        int feetZ = (int) Math.floor(position.z());
        if (board.state().blocks().isWaterSurface(feetX, feetY, feetZ)) {
            return true;
        }
        int x = (int) Math.floor(position.x());
        int y = (int) Math.floor(position.y() + 0.1);
        int z = (int) Math.floor(position.z());
        return !physics.onGround()
                && physics.velocity().y() < -0.035
                && !board.state().blocks().isSolid(x, y, z)
                && !board.state().blocks().isSolid(x, y + 1, z)
                && !board.state().blocks().isSolid(x, y - 1, z);
    }

    private boolean shouldAutoJumpObstacle(MovementInput input, BotPhysics physics, BotBlackboard board) {
        if (!input.moving() || !physics.onGround() || !physics.horizontalCollision()) {
            return false;
        }
        if (board.lifecycle() == EBotLifecycleState.LobbyRoaming && random.nextDouble() < 0.30) {
            return false;
        }
        return true;
    }

    private MovementInput avoidBots(MovementInput input, BotPhysics physics, Collection<TrackedPlayer> players) {
        Vec3 position = physics.position();
        boolean passive = lifecycleState == EBotLifecycleState.LobbyRoaming || lifecycleState == EBotLifecycleState.PregameWaiting;
        double avoidanceRadius = passive ? PASSIVE_AVOIDANCE_RADIUS : BOT_AVOIDANCE_RADIUS;
        double avoidanceStrength = passive ? PASSIVE_AVOIDANCE_STRENGTH : BOT_AVOIDANCE_STRENGTH;
        double awayX = 0;
        double awayZ = 0;
        for (TrackedPlayer player : players) {
            if (Math.abs(player.position().y() - position.y()) > BOT_AVOIDANCE_HEIGHT) {
                continue;
            }
            double dx = position.x() - player.position().x();
            double dz = position.z() - player.position().z();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared >= avoidanceRadius * avoidanceRadius) {
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            if (distance < 1.0E-4) {
                double fallbackAngle = Math.toRadians(physics.yaw() + player.entityId() * 37.0);
                dx = Math.cos(fallbackAngle);
                dz = Math.sin(fallbackAngle);
                distance = 1.0;
            }
            double pressure = (avoidanceRadius - distance) / avoidanceRadius;
            awayX += (dx / distance) * pressure;
            awayZ += (dz / distance) * pressure;
        }
        double pressure = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (pressure < 1.0E-6) {
            return input;
        }
        double yawRad = Math.toRadians(physics.yaw());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = Math.cos(yawRad);
        double strafeZ = Math.sin(yawRad);
        double forwardAvoidance = awayX * forwardX + awayZ * forwardZ;
        double strafeAvoidance = awayX * strafeX + awayZ * strafeZ;
        boolean sprint = input.sprint() && pressure < 0.35;
        return new MovementInput(
                input.forward() + forwardAvoidance * avoidanceStrength,
                input.strafe() + strafeAvoidance * avoidanceStrength,
                input.jump(),
                sprint,
                input.sneak()
        ).clamp();
    }

    private double strafeOscillation() {
        double base = (System.currentTimeMillis() / 700L) % 2 == 0 ? 0.45 : -0.45;
        return base * Math.max(0.25, skill.strafeQuality());
    }

    private float smooth(float current, float target, double accuracy) {
        double factor = Math.max(0.05, Math.min(1.0, accuracy));
        return (float) (current + wrapDegrees(target - current) * factor);
    }

    private float wrapDegrees(float degrees) {
        float value = degrees % 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    public record ControlPlan(MovementInput movement, Optional<TrackedPlayer> target) { }
}
