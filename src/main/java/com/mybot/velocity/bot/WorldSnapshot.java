package com.mybot.velocity.bot;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Represents the last-known view of the world around a bot. For now it is a
 * minimal implementation storing scalars required by the behavior engine. It
 * can be expanded with chunk/entity data pulled from MCProtocolLib packets.
 */
public final class WorldSnapshot {

    private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    public Object get(String key) {
        return attributes.get(key);
    }

    public boolean flag(String key) {
        return Boolean.TRUE.equals(attributes.get(key));
    }

    public int integer(String key, int defaultValue) {
        Object value = attributes.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public double number(String key, double defaultValue) {
        Object value = attributes.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public String text(String key, String defaultValue) {
        Object value = attributes.get(key);
        return value == null ? defaultValue : Objects.toString(value);
    }
}
