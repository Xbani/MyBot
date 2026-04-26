package com.mybot.velocity.graph.node;

import com.mybot.velocity.bot.BotPacketBridge;
import com.mybot.velocity.bot.BotSession;
import com.mybot.velocity.bot.WorldSnapshot;
import com.mybot.velocity.config.BotDefinition;
import com.mybot.velocity.navigation.NavigationService;
import com.mybot.velocity.schematic.SchematicService;

public interface GraphRuntimeContext {
    BotDefinition definition();
    BotSession session();
    BotPacketBridge packets();
    NavigationService navigation();
    SchematicService schematics();
    WorldSnapshot snapshot();
    void onNodeTransition(String from, String to, String reason);
}
