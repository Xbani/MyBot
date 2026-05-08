package com.mybot.velocity.bot;

/**
 * Small server-side palette derived from Dynmap's default color scheme.
 * The cache only has protocol block-state ids, so we map broad vanilla state ranges.
 */
final class DynmapBlockColors {
    static final int AIR = 0x000000;
    static final int WATER = 0x4676d6;

    private DynmapBlockColors() {
    }

    static int topColor(int blockState) {
        if (blockState <= 0) {
            return AIR;
        }
        if (between(blockState, 1, 1)) return rgb(125, 125, 125);      // stone
        if (between(blockState, 2, 3)) return rgb(149, 103, 85);       // granite
        if (between(blockState, 4, 5)) return rgb(188, 188, 188);      // diorite
        if (between(blockState, 6, 7)) return rgb(136, 136, 136);      // andesite
        if (between(blockState, 8, 9)) return rgb(69, 110, 51);        // grass_block
        if (between(blockState, 10, 12)) return rgb(134, 96, 67);      // dirt / podzol
        if (between(blockState, 13, 13)) return rgb(127, 127, 127);    // cobblestone
        if (between(blockState, 14, 19)) return rgb(162, 130, 78);     // planks
        if (between(blockState, 29, 49)) return WATER;                 // water
        if (between(blockState, 20, 31)) return rgb(77, 106, 40);      // saplings
        if (between(blockState, 32, 32)) return rgb(85, 85, 85);       // bedrock
        if (between(blockState, 50, 65)) return rgb(216, 104, 26);     // lava
        if (between(blockState, 66, 67)) return rgb(219, 207, 163);    // sand
        if (between(blockState, 68, 68)) return rgb(190, 102, 33);     // red sand
        if (between(blockState, 69, 69)) return rgb(131, 127, 126);    // gravel
        if (between(blockState, 70, 83)) return rgb(116, 106, 84);     // ores
        if (between(blockState, 84, 117)) return rgb(109, 85, 50);     // logs / wood
        if (between(blockState, 118, 197)) return rgb(54, 93, 38);     // leaves
        if (between(blockState, 1987, 2034)) return rgb(108, 172, 72); // plants
        if (between(blockState, 6570, 6725)) return rgb(109, 85, 50);  // newer log state ranges seen by scanner
        if (between(blockState, 8133, 8444)) return rgb(54, 93, 38);   // newer leaves/plants ranges
        if (between(blockState, 10457, 10712)) return rgb(127, 127, 127);
        if (between(blockState, 12713, 13044)) return rgb(54, 93, 38);
        if (between(blockState, 14945, 15064)) return rgb(162, 130, 78);
        if (between(blockState, 20739, 20829)) return rgb(70, 118, 214);
        if (between(blockState, 21264, 21311)) return rgb(216, 104, 26);
        return fallback(blockState);
    }

    static int shaded(int color, int y) {
        if (color == AIR) {
            return AIR;
        }
        double shade = Math.max(0.72, Math.min(1.18, 0.88 + (y - 62) / 260.0));
        int r = clamp((int) Math.round(((color >> 16) & 0xff) * shade));
        int g = clamp((int) Math.round(((color >> 8) & 0xff) * shade));
        int b = clamp((int) Math.round((color & 0xff) * shade));
        return rgb(r, g, b);
    }

    private static int fallback(int blockState) {
        int value = blockState * 1103515245 + 12345;
        int r = 82 + ((value >>> 16) & 0x3f);
        int g = 82 + ((value >>> 10) & 0x3f);
        int b = 82 + ((value >>> 4) & 0x3f);
        return rgb(r, g, b);
    }

    private static boolean between(int value, int min, int max) {
        return value >= min && value <= max;
    }

    private static int rgb(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
