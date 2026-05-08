package com.mybot.velocity.dashboard;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

public final class JsonWriter {
    private final StringBuilder out = new StringBuilder(4096);

    public JsonWriter beginObject() {
        out.append('{');
        return this;
    }

    public JsonWriter endObject() {
        out.append('}');
        return this;
    }

    public JsonWriter beginArray() {
        out.append('[');
        return this;
    }

    public JsonWriter endArray() {
        out.append(']');
        return this;
    }

    public JsonWriter comma() {
        out.append(',');
        return this;
    }

    public JsonWriter name(String name) {
        string(name);
        out.append(':');
        return this;
    }

    public JsonWriter string(String value) {
        if (value == null) {
            out.append("null");
            return this;
        }
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return this;
    }

    public JsonWriter value(Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Instant instant) {
            string(instant.toString());
        } else if (value instanceof Map<?, ?> map) {
            object(map);
        } else if (value instanceof Collection<?> collection) {
            array(collection);
        } else {
            string(value.toString());
        }
        return this;
    }

    private void object(Map<?, ?> map) {
        beginObject();
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                comma();
            }
            first = false;
            name(String.valueOf(entry.getKey())).value(entry.getValue());
        }
        endObject();
    }

    private void array(Collection<?> values) {
        beginArray();
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                comma();
            }
            first = false;
            value(value);
        }
        endArray();
    }

    public JsonWriter raw(String json) {
        out.append(json);
        return this;
    }

    public String json() {
        return out.toString();
    }
}
