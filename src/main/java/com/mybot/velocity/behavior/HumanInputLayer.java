package com.mybot.velocity.behavior;

import com.mybot.velocity.action.BotAction;
import com.mybot.velocity.action.BotActionQueue;
import com.mybot.velocity.bot.BotPhysics;
import com.mybot.velocity.bot.BotWorldState;
import com.mybot.velocity.bot.MovementInput;
import com.mybot.velocity.bot.TrackedPlayer;
import com.mybot.velocity.bot.Vec3;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

public final class HumanInputLayer {
    private final Random random;
    private Instant nextAttackAt = Instant.EPOCH;
    private Instant nextSlotSwitchAt = Instant.EPOCH;
    private double aimYawOffset;
    private double aimPitchOffset;
    private Instant nextAimErrorChangeAt = Instant.EPOCH;

    public HumanInputLayer(long seed) {
        this.random = new Random(seed ^ 0x5eed5eedL);
    }

    public void updateLook(BotPhysics physics, Vec3 targetPosition, BotSkillProfile skill, double panic, Instant now) {
        if (now.isAfter(nextAimErrorChangeAt)) {
            double scale = skill.aimErrorDegrees() * (1.0 + panic * 0.8);
            aimYawOffset = gaussian(scale);
            aimPitchOffset = gaussian(scale * 0.55);
            nextAimErrorChangeAt = now.plusMillis(180 + random.nextInt(420));
        }
        BotPhysics.LookAngles look = BotPhysics.lookAt(BotPhysics.eyePosition(physics.position()), BotPhysics.bodyAimPosition(targetPosition));
        double tracking = switch (skillLevel(skill)) {
            case NOOB -> 0.22;
            case TRYHARD -> 0.62;
            default -> 0.40;
        };
        if (skill.aimErrorDegrees() <= 0.01) {
            tracking = 1.0;
        }
        tracking *= Math.max(0.25, 1.0 - panic * 0.35);
        physics.setLook(
                smooth(physics.yaw(), (float) (look.yaw() + aimYawOffset), tracking),
                smooth(physics.pitch(), (float) (look.pitch() + aimPitchOffset), tracking)
        );
    }

    public void maybeSwitchWeapon(BotWorldState state, BotActionQueue actions, BotSkillProfile skill, Instant now, boolean urgent) {
        Optional<java.util.OptionalInt> best = Optional.of(state.inventory().bestWeaponHotbarSlot());
        if (best.get().isEmpty() || state.inventory().selectedHotbarSlot() == best.get().getAsInt()) {
            return;
        }
        if (!urgent && random.nextDouble() < skill.inventoryDelayChance()) {
            return;
        }
        if (now.isBefore(nextSlotSwitchAt)) {
            return;
        }
        int slot = best.get().getAsInt();
        actions.enqueueIfAbsent(BotAction.SetHotbarSlot.class, new BotAction.SetHotbarSlot(slot));
        nextSlotSwitchAt = now.plusMillis(skill.randomReactionDelay(random) / 2 + 80);
    }

    public boolean tryAttack(BotWorldState state,
                             BotPhysics physics,
                             TrackedPlayer target,
                             BotActionQueue actions,
                             BotSkillProfile skill,
                             HgBehaviorConfig config,
                             double panic,
                             Instant now) {
        if (now.isBefore(nextAttackAt)) {
            return false;
        }
        long nextDelay = Math.max(330L, skill.randomReactionDelay(random) + 260L + random.nextInt(180));
        nextAttackAt = now.plusMillis(nextDelay);
        boolean armed = state.inventory().hasUsableWeaponSelected() || state.inventory().bestWeaponHotbarSlot().isPresent();
        if (!state.blocks().hasLineOfSight(BotPhysics.eyePosition(physics.position()), BotPhysics.bodyAimPosition(target.position()))) {
            return false;
        }
        double distance = physics.position().distanceTo(target.position());
        if (distance > config.meleeRange()) {
            if (random.nextDouble() < skill.missedHitChance() + panic * 0.15) {
                actions.enqueue(new BotAction.SwingMainHand());
            }
            return false;
        }
        double missChance = Math.min(0.78, skill.missedHitChance() + panic * 0.20 + (armed ? 0.0 : 0.18));
        if (random.nextDouble() < missChance) {
            actions.enqueue(new BotAction.SwingMainHand());
            return false;
        }
        actions.enqueue(new BotAction.SwingMainHand());
        actions.enqueue(new BotAction.LeftClickEntity(target.entityId()));
        return true;
    }

    public MovementInput humanizeMovement(MovementInput input, BotSkillProfile skill, double panic) {
        double quality = Math.max(0.05, skill.strafeQuality() - panic * 0.18);
        double forward = input.forward();
        double strafe = input.strafe();
        if (random.nextDouble() > quality) {
            strafe *= random.nextBoolean() ? -0.65 : 0.25;
        }
        if (random.nextDouble() < panic * 0.08 + skill.lootMistakeChance() * 0.05) {
            forward *= 0.35;
        }
        boolean jump = input.jump() || (input.sprint() && random.nextDouble() < skill.critAttemptChance() * 0.08);
        boolean sprint = input.sprint() && random.nextDouble() > skill.lootMistakeChance() * 0.10;
        return new MovementInput(forward, strafe, jump, sprint, input.sneak()).clamp();
    }

    public void reset() {
        nextAttackAt = Instant.EPOCH;
        nextSlotSwitchAt = Instant.EPOCH;
        nextAimErrorChangeAt = Instant.EPOCH;
        aimYawOffset = 0;
        aimPitchOffset = 0;
    }

    private BotSkillLevel skillLevel(BotSkillProfile skill) {
        if (skill.aimErrorDegrees() >= 7.0) {
            return BotSkillLevel.NOOB;
        }
        if (skill.aimErrorDegrees() <= 3.0) {
            return BotSkillLevel.TRYHARD;
        }
        return BotSkillLevel.AVERAGE;
    }

    private double gaussian(double scale) {
        return random.nextGaussian() * scale;
    }

    private float smooth(float current, float target, double factor) {
        double bounded = Math.max(0.04, Math.min(1.0, factor));
        return (float) (current + wrapDegrees(target - current) * bounded);
    }

    private float wrapDegrees(float degrees) {
        float value = degrees % 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }
}
