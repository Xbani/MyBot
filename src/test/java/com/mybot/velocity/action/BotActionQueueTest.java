package com.mybot.velocity.action;

import com.mybot.velocity.bot.BotSession;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BotActionQueueTest {
    @Test
    void respectsActionOrderAndCooldown() {
        MutableClock clock = new MutableClock();
        BotActionQueue queue = new BotActionQueue(clock, 4);
        AtomicInteger counter = new AtomicInteger();
        queue.enqueue(new CountingAction("attack", counter));
        queue.enqueue(new CountingAction("attack", counter));

        assertThat(queue.tick(null)).isEqualTo(1);
        assertThat(counter).hasValue(1);
        assertThat(queue.tick(null)).isZero();

        clock.advance(600);

        assertThat(queue.tick(null)).isEqualTo(1);
        assertThat(counter).hasValue(2);
    }

    private record CountingAction(String key, AtomicInteger counter) implements BotAction {
        public long cooldownMillis() {
            return 500;
        }

        public void execute(BotSession session) {
            counter.incrementAndGet();
        }
    }

    private static final class MutableClock extends Clock {
        private long millis;

        void advance(long amount) {
            millis += amount;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
