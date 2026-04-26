package com.mybot.velocity.bot;

import com.mybot.velocity.config.BotDefinition;
import com.mybot.velocity.config.GlobalConfig;
import com.mybot.velocity.action.BotActionQueue;
import com.mybot.velocity.behavior.BotController;
import com.mybot.velocity.behavior.HgBehaviorConfig;
import org.cloudburstmc.math.vector.Vector3i;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EntityEvent;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoRemovePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityMotionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.DropItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minecraft protocol client session for a configured bot.
 */
public class BotSession {

    private final BotDefinition definition;
    private final GlobalConfig config;
    private final Logger logger;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final CompletableFuture<String> disconnectedFuture = new CompletableFuture<>();
    private final BotPhysics physics = new BotPhysics();
    private final WorldBlockCache blocks;
    private final BotWorldState worldState;
    private final BotActionQueue actions = new BotActionQueue();
    private final BotController controller;
    private volatile Instant lastActivity = Instant.now();
    private volatile String currentServer;
    private volatile int entityId = -1;
    private volatile ClientSession session;
    private volatile boolean sprinting;
    private volatile boolean sneaking;
    private volatile MovementInput lastInput = MovementInput.NONE;
    private int sequence;

    public BotSession(BotDefinition definition, GlobalConfig config, Logger logger) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.currentServer = definition.initialServer();
        this.blocks = new WorldBlockCache(logger);
        this.worldState = new BotWorldState(blocks);
        this.controller = new BotController(HgBehaviorConfig.fromTraits(definition.traits()), actions);
    }

    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
        UUID uuid = definition.uuid() != null ? definition.uuid() : offlineUuid(definition.username());
        GameProfile profile = new GameProfile(uuid, definition.username());
        MinecraftProtocol protocol = new MinecraftProtocol(profile, "");
        ClientSession client = ClientNetworkSessionFactory.factory()
                .setAddress(config.velocityEndpoint().getHostString(), config.velocityEndpoint().getPort())
                .setProtocol(protocol)
                .create();
        client.addListener(new SessionAdapter() {
            @Override
            public void connected(ConnectedEvent event) {
                lastActivity = Instant.now();
                logger.info("Network connected bot {} to {}:{}", definition.username(),
                        config.velocityEndpoint().getHostString(), config.velocityEndpoint().getPort());
            }

            @Override
            public void packetReceived(Session eventSession, Packet packet) {
                lastActivity = Instant.now();
                if (packet instanceof ClientboundLoginPacket) {
                    entityId = ((ClientboundLoginPacket) packet).getEntityId();
                    eventSession.send(ServerboundPlayerLoadedPacket.INSTANCE);
                    connected.set(true);
                    logger.info("Bot {} joined game on initial server {}", definition.username(), currentServer);
                    connectedFuture.complete(null);
                    return;
                }
                handleWorldPacket(eventSession, packet);
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                connected.set(false);
                session = null;
                String reason = PlainTextComponentSerializer.plainText().serialize(event.getReason());
                disconnectedFuture.complete(reason);
                logger.info("Bot {} disconnected: {}", definition.username(), reason);
                if (!connectedFuture.isDone()) {
                    Throwable cause = event.getCause();
                    connectedFuture.completeExceptionally(cause != null
                            ? cause
                            : new IllegalStateException("Disconnected before login: " + reason));
                }
            }
        });

        logger.info("Connecting bot {} to {}:{}", definition.username(),
                config.velocityEndpoint().getHostString(), config.velocityEndpoint().getPort());
        session = client;
        try {
            client.connect(false);
        } catch (RuntimeException ex) {
            session = null;
            connected.set(false);
            connectedFuture.completeExceptionally(ex);
        }
        return connectedFuture;
    }

    public void disconnect(String reason) {
        ClientSession active = session;
        if (active != null && active.isConnected()) {
            active.disconnect(reason);
        }
        if (connected.compareAndSet(true, false)) {
            logger.info("Disconnected bot {}: {}", definition.username(), reason);
        }
        disconnectedFuture.complete(reason);
    }

    public boolean isConnected() {
        ClientSession active = session;
        return connected.get() && active != null && active.isConnected();
    }

    public void sendCommand(String commandLine) {
        ClientSession active = session;
        if (!isConnected()) {
            logger.warn("[{}] cannot execute command while disconnected: {}", definition.username(), commandLine);
            return;
        }
        String command = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        active.send(new ServerboundChatCommandPacket(command));
        logger.info("[{}] executing command: {}", definition.username(), commandLine);
        lastActivity = Instant.now();
    }

    public void hopServer(String serverId) {
        ClientSession active = session;
        if (!isConnected()) {
            logger.warn("[{}] cannot hop to server {} while disconnected", definition.username(), serverId);
            return;
        }
        active.send(new ServerboundChatCommandPacket("server " + serverId));
        logger.info("[{}] hopping to server {}", definition.username(), serverId);
        currentServer = serverId;
        lastActivity = Instant.now();
    }

    public String currentServer() {
        return currentServer;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    public CompletableFuture<String> disconnectedFuture() {
        return disconnectedFuture;
    }

    public void tickPhysics() {
        ClientSession active = session;
        if (!isConnected() || active == null) {
            return;
        }
        BotController.ControlPlan plan = controller.tick(worldState, physics);
        BotPhysics.PhysicsTick tick = physics.tick(blocks, worldState.trackedPlayers(), plan.movement());
        if (!tick.chunksLoaded()) {
            return;
        }
        sendInputPacket(active, tick.input());
        active.send(new ServerboundMovePlayerPosRotPacket(
                tick.onGround(),
                tick.horizontalCollision(),
                tick.position().x(),
                tick.position().y(),
                tick.position().z(),
                tick.yaw(),
                tick.pitch()
        ));
        actions.tick(this);
        tick.target().ifPresent(target -> logger.debug("[{}] tracking real player {}", definition.username(), target.username()));
        lastActivity = Instant.now();
    }

    public BotWorldState worldState() {
        return worldState;
    }

    public int entityId() {
        return entityId;
    }

    public float yaw() {
        return physics.yaw();
    }

    public float pitch() {
        return physics.pitch();
    }

    public boolean sneaking() {
        return sneaking;
    }

    public void setHotbarSlot(int slot) {
        ClientSession active = session;
        if (active == null || !isConnected()) {
            return;
        }
        int normalized = Math.floorMod(slot, 9);
        active.send(new ServerboundSetCarriedItemPacket(normalized));
        worldState.inventory().setSelectedHotbarSlot(normalized);
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public void swingMainHand() {
        ClientSession active = session;
        if (active != null && isConnected()) {
            active.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
        }
    }

    public void attackEntity(int targetEntityId) {
        ClientSession active = session;
        if (active != null && isConnected()) {
            active.send(new ServerboundInteractPacket(targetEntityId, InteractAction.ATTACK, sneaking));
        }
    }

    public void useItem() {
        ClientSession active = session;
        if (active != null && isConnected()) {
            active.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, nextSequence(), physics.yaw(), physics.pitch()));
        }
    }

    public void useItemOn(Vector3i position, Direction face) {
        ClientSession active = session;
        if (active != null && isConnected()) {
            active.send(new ServerboundUseItemOnPacket(position, face, Hand.MAIN_HAND, 0.5f, 0.5f, 0.5f, false, false, nextSequence()));
        }
    }

    public void dropSelectedItem() {
        ClientSession active = session;
        if (active != null && isConnected()) {
            active.send(new ServerboundContainerClickPacket(
                    0,
                    worldState.inventory().stateId(),
                    ServerboundContainerClickPacket.CLICK_OUTSIDE_NOT_HOLDING_SLOT,
                    ContainerActionType.DROP_ITEM,
                    DropItemAction.DROP_FROM_SELECTED,
                    new HashedStack(0, 0, Map.of(), java.util.Set.of()),
                    Map.of()
            ));
        }
    }

    private void handleWorldPacket(Session eventSession, Packet packet) {
        if (packet instanceof ClientboundPlayerPositionPacket positionPacket) {
            applyPositionCorrection(eventSession, positionPacket);
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket infoPacket) {
            applyPlayerInfo(infoPacket);
        } else if (packet instanceof ClientboundPlayerInfoRemovePacket removePacket) {
            removePacket.getProfileIds().forEach(worldState::removePlayerName);
        } else if (packet instanceof ClientboundAddEntityPacket addEntityPacket) {
            applyAddEntity(addEntityPacket);
        } else if (packet instanceof ClientboundMoveEntityPosPacket movePacket) {
            moveEntity(movePacket.getEntityId(), movePacket.getMoveX(), movePacket.getMoveY(), movePacket.getMoveZ(), null, null);
        } else if (packet instanceof ClientboundMoveEntityPosRotPacket moveRotPacket) {
            moveEntity(moveRotPacket.getEntityId(), moveRotPacket.getMoveX(), moveRotPacket.getMoveY(), moveRotPacket.getMoveZ(),
                    moveRotPacket.getYaw(), moveRotPacket.getPitch());
        } else if (packet instanceof ClientboundTeleportEntityPacket teleportPacket) {
            teleportEntity(teleportPacket);
        } else if (packet instanceof ClientboundRemoveEntitiesPacket removeEntitiesPacket) {
            for (int removed : removeEntitiesPacket.getEntityIds()) {
                worldState.removeEntity(removed);
            }
        } else if (packet instanceof ClientboundSetEntityMotionPacket motionPacket) {
            applyEntityMotion(motionPacket);
        } else if (packet instanceof ClientboundEntityEventPacket eventPacket) {
            applyEntityEvent(eventPacket);
        } else if (packet instanceof ClientboundSetHealthPacket healthPacket) {
            worldState.updateHealth(healthPacket.getHealth(), healthPacket.getFood(), healthPacket.getSaturation());
        } else if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
            worldState.inventory().setContent(contentPacket.getContainerId(), contentPacket.getStateId(), contentPacket.getItems());
        } else if (packet instanceof ClientboundContainerSetSlotPacket slotPacket) {
            worldState.inventory().setSlot(slotPacket.getContainerId(), slotPacket.getStateId(), slotPacket.getSlot(), slotPacket.getItem());
        } else if (packet instanceof ClientboundOpenScreenPacket openPacket) {
            logger.debug("[{}] opened container {}", definition.username(), openPacket.getContainerId());
        } else if (packet instanceof ClientboundContainerClosePacket closePacket) {
            logger.debug("[{}] closed container {}", definition.username(), closePacket.getContainerId());
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            blocks.handleChunk(chunkPacket);
        } else if (packet instanceof ClientboundForgetLevelChunkPacket forgetPacket) {
            blocks.handleForget(forgetPacket);
        } else if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
            blocks.handleBlockUpdate(blockPacket);
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
            blocks.handleSectionBlocksUpdate(sectionPacket);
        }
    }

    private void applyPositionCorrection(Session eventSession, ClientboundPlayerPositionPacket packet) {
        Vec3 current = physics.position();
        Vec3 packetPosition = new Vec3(packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ());
        Vec3 nextPosition = applyRelativePosition(current, packetPosition, packet.getRelatives());
        Vec3 nextVelocity = new Vec3(packet.getDeltaMovement().getX(), packet.getDeltaMovement().getY(), packet.getDeltaMovement().getZ());
        float nextYaw = packet.getYRot();
        float nextPitch = packet.getXRot();
        physics.correctPosition(nextPosition, nextVelocity, nextYaw, nextPitch);
        eventSession.send(new ServerboundAcceptTeleportationPacket(packet.getId()));
    }

    private Vec3 applyRelativePosition(Vec3 current, Vec3 packetPosition, List<PositionElement> relatives) {
        double x = relatives.contains(PositionElement.X) ? current.x() + packetPosition.x() : packetPosition.x();
        double y = relatives.contains(PositionElement.Y) ? current.y() + packetPosition.y() : packetPosition.y();
        double z = relatives.contains(PositionElement.Z) ? current.z() + packetPosition.z() : packetPosition.z();
        return new Vec3(x, y, z);
    }

    private void applyPlayerInfo(ClientboundPlayerInfoUpdatePacket packet) {
        if (!packet.getActions().contains(PlayerListEntryAction.ADD_PLAYER)) {
            return;
        }
        for (var entry : packet.getEntries()) {
            if (entry.getProfile() != null) {
                worldState.rememberPlayerName(entry.getProfileId(), entry.getProfile().getName());
            }
        }
    }

    private void applyAddEntity(ClientboundAddEntityPacket packet) {
        if (packet.getType() != EntityType.PLAYER || packet.getEntityId() == entityId) {
            return;
        }
        Vec3 position = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        worldState.putPlayer(new TrackedPlayer(packet.getEntityId(), packet.getUuid(), null, position, packet.getYaw(), packet.getPitch(), Instant.now()));
    }

    private void moveEntity(int movedEntityId, double dx, double dy, double dz, Float yaw, Float pitch) {
        worldState.movePlayer(movedEntityId, new Vec3(dx, dy, dz), yaw, pitch);
    }

    private void teleportEntity(ClientboundTeleportEntityPacket packet) {
        worldState.teleportPlayer(packet.getId(), new Vec3(packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ()),
                packet.getYRot(), packet.getXRot());
    }

    private void applyEntityMotion(ClientboundSetEntityMotionPacket packet) {
        Vec3 motion = new Vec3(packet.getMovement().getX(), packet.getMovement().getY(), packet.getMovement().getZ());
        if (packet.getEntityId() == entityId) {
            physics.applyKnockback(motion);
        }
    }

    private void applyEntityEvent(ClientboundEntityEventPacket packet) {
        if (packet.getEntityId() == entityId && packet.getEvent() == EntityEvent.LIVING_HURT) {
            worldState.markDamage(-1);
        }
    }

    private void sendInputPacket(ClientSession active, MovementInput input) {
        MovementInput merged = new MovementInput(input.forward(), input.strafe(), input.jump(), input.sprint() || sprinting, input.sneak() || sneaking).clamp();
        if (!merged.equals(lastInput)) {
            active.send(new ServerboundPlayerInputPacket(
                    merged.forward() > 0.1,
                    merged.forward() < -0.1,
                    merged.strafe() > 0.1,
                    merged.strafe() < -0.1,
                    merged.jump(),
                    merged.sneak(),
                    merged.sprint()
            ));
            lastInput = merged;
        }
    }

    private int nextSequence() {
        return ++sequence;
    }

    private static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }
}
