package work.spacecat.twnetoptimizer.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import org.bukkit.entity.Player;
import work.spacecat.twnetoptimizer.latency.LatencyGuardian;
import work.spacecat.twnetoptimizer.optimizer.PacketOptimizationEngine;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;
import work.spacecat.twnetoptimizer.trace.EntityTraceService;
import work.spacecat.twnetoptimizer.trace.VirtualEntityRegistry;

import java.util.UUID;

public final class PacketEventsBridge {
    private final NetworkProfiler profiler;
    private final PacketOptimizationEngine optimizer;
    private final EntityTraceService traceService;
    private final VirtualEntityRegistry virtualEntityRegistry;
    private final LatencyGuardian latencyGuardian;

    private PacketListenerCommon optimizingListener;
    private PacketListenerCommon monitorListener;

    public PacketEventsBridge(
            NetworkProfiler profiler,
            PacketOptimizationEngine optimizer,
            EntityTraceService traceService,
            VirtualEntityRegistry virtualEntityRegistry,
            LatencyGuardian latencyGuardian
    ) {
        this.profiler = profiler;
        this.optimizer = optimizer;
        this.traceService = traceService;
        this.virtualEntityRegistry = virtualEntityRegistry;
        this.latencyGuardian = latencyGuardian;
    }

    public void register() {
        if (optimizingListener != null || monitorListener != null) return;

        optimizingListener = PacketEvents.getAPI()
                .getEventManager()
                .registerListener(new OptimizingListener());

        monitorListener = PacketEvents.getAPI()
                .getEventManager()
                .registerListener(new FinalOutcomeListener());
    }

    public void unregister() {
        if (optimizingListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(optimizingListener);
            optimizingListener = null;
        }

        if (monitorListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(monitorListener);
            monitorListener = null;
        }
    }

    private final class OptimizingListener extends PacketListenerAbstract {
        private OptimizingListener() {
            super(PacketListenerPriority.HIGHEST);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPlayer() instanceof Player player) {
                latencyGuardian.observeInbound(player.getUniqueId(), event);
            }
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;

            UUID playerId = player.getUniqueId();
            String packetName = event.getPacketName();
            int observedBytes = readableBytes(event.getByteBuf());

            profiler.recordRawOutbound(playerId, observedBytes, packetName);

            if (event.isCancelled()) return;

            long now = System.currentTimeMillis();

            switch (packetName) {
                case "ENTITY_METADATA" -> handleMetadata(event, playerId, now);
                case "ENTITY_TELEPORT", "ENTITY_VELOCITY" ->
                        observeEntityActivity(event, playerId, packetName);
                case "PARTICLE" -> {
                    if (optimizer.shouldSuppressParticle(playerId, now)) {
                        event.setCancelled(true);
                    }
                }
                default -> {
                    if (optimizer.isUiStatePacket(packetName)) {
                        byte[] payload = copyPayload(event.getByteBuf());

                        if (payload.length > 0
                                && optimizer.shouldSuppressUi(
                                playerId,
                                packetName,
                                payload,
                                now
                        )) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }

        private void handleMetadata(PacketSendEvent event, UUID playerId, long now) {
            byte[] payload = copyPayload(event.getByteBuf());
            int entityId = readFirstVarInt(event.getByteBuf());

            if (entityId < 0 || payload.length == 0) return;

            virtualEntityRegistry.recordActivity(playerId, entityId, "ENTITY_METADATA");

            if (traceService.isActive(playerId)) {
                traceService.record(playerId, entityId, "ENTITY_METADATA");
            }

            if (optimizer.shouldSuppressMetadata(playerId, entityId, payload, now)) {
                event.setCancelled(true);
            }
        }

        private void observeEntityActivity(
                PacketSendEvent event,
                UUID playerId,
                String packetName
        ) {
            int entityId = readFirstVarInt(event.getByteBuf());
            if (entityId < 0) return;

            virtualEntityRegistry.recordActivity(playerId, entityId, packetName);

            if (traceService.isActive(playerId)) {
                traceService.record(playerId, entityId, packetName);
            }
        }
    }

    private final class FinalOutcomeListener extends PacketListenerAbstract {
        private FinalOutcomeListener() {
            super(PacketListenerPriority.MONITOR);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled()
                    || !(event.getPlayer() instanceof Player player)) {
                return;
            }

            profiler.recordInbound(
                    player.getUniqueId(),
                    readableBytes(event.getByteBuf()),
                    event.getPacketName()
            );
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (!(event.getPlayer() instanceof Player player) || event.isCancelled()) {
                return;
            }

            UUID playerId = player.getUniqueId();

            profiler.recordForwardedOutbound(
                    playerId,
                    readableBytes(event.getByteBuf()),
                    event.getPacketName()
            );

            switch (event.getPacketName()) {
                case "SPAWN_ENTITY" -> recordSpawnEntity(event, playerId);
                case "SPAWN_PLAYER" -> recordSpawnPlayer(event, playerId);
                case "SPAWN_EXPERIENCE_ORB" -> recordSpawnExperienceOrb(event, playerId);
                case "DESTROY_ENTITIES" -> handleDestroy(event, playerId);
                case "RESPAWN", "JOIN_GAME" -> {
                    optimizer.clearPlayer(playerId);
                    virtualEntityRegistry.clearPlayer(playerId);
                }
                default -> {
                }
            }
        }

        private void recordSpawnEntity(PacketSendEvent event, UUID playerId) {
            try {
                Object duplicate = ByteBufHelper.duplicate(event.getByteBuf());
                int entityId = ByteBufHelper.readVarInt(duplicate);

                UUID entityUuid = new UUID(
                        ByteBufHelper.readLong(duplicate),
                        ByteBufHelper.readLong(duplicate)
                );

                int typeId = ByteBufHelper.readVarInt(duplicate);

                EntityType entityType = EntityTypes.getById(
                        event.getClientVersion(),
                        typeId
                );

                String typeName = entityType == null
                        ? "UNKNOWN_TYPE_ID_" + typeId
                        : entityType.getName().toString();

                virtualEntityRegistry.recordSpawn(
                        playerId,
                        entityId,
                        entityUuid,
                        "SPAWN_ENTITY",
                        typeName
                );
            } catch (RuntimeException ignored) {
            }
        }

        private void recordSpawnPlayer(PacketSendEvent event, UUID playerId) {
            try {
                Object duplicate = ByteBufHelper.duplicate(event.getByteBuf());
                int entityId = ByteBufHelper.readVarInt(duplicate);

                UUID entityUuid = new UUID(
                        ByteBufHelper.readLong(duplicate),
                        ByteBufHelper.readLong(duplicate)
                );

                virtualEntityRegistry.recordSpawn(
                        playerId,
                        entityId,
                        entityUuid,
                        "SPAWN_PLAYER",
                        "minecraft:player"
                );
            } catch (RuntimeException ignored) {
            }
        }

        private void recordSpawnExperienceOrb(PacketSendEvent event, UUID playerId) {
            int entityId = readFirstVarInt(event.getByteBuf());

            if (entityId >= 0) {
                virtualEntityRegistry.recordSpawn(
                        playerId,
                        entityId,
                        null,
                        "SPAWN_EXPERIENCE_ORB",
                        "minecraft:experience_orb"
                );
            }
        }

        private void handleDestroy(PacketSendEvent event, UUID playerId) {
            try {
                Object duplicate = ByteBufHelper.duplicate(event.getByteBuf());
                int count = ByteBufHelper.readVarInt(duplicate);

                for (int i = 0; i < count; i++) {
                    int entityId = ByteBufHelper.readVarInt(duplicate);
                    optimizer.clearEntity(playerId, entityId);
                    virtualEntityRegistry.recordDestroy(playerId, entityId);
                }
            } catch (RuntimeException ignored) {
                optimizer.clearPlayer(playerId);
                virtualEntityRegistry.clearPlayer(playerId);
            }
        }
    }

    private int readFirstVarInt(Object byteBuf) {
        try {
            Object duplicate = ByteBufHelper.duplicate(byteBuf);
            return ByteBufHelper.readVarInt(duplicate);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private byte[] copyPayload(Object byteBuf) {
        try {
            return ByteBufHelper.copyBytes(byteBuf);
        } catch (RuntimeException ignored) {
            return new byte[0];
        }
    }

    private int readableBytes(Object byteBuf) {
        try {
            return byteBuf == null ? 0 : ByteBufHelper.readableBytes(byteBuf);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
