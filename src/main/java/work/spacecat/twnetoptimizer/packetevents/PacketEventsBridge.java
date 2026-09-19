package work.spacecat.twnetoptimizer.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import org.bukkit.entity.Player;
import work.spacecat.twnetoptimizer.optimizer.PacketOptimizationEngine;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;
import work.spacecat.twnetoptimizer.profiler.TrafficDirection;
import work.spacecat.twnetoptimizer.trace.EntityTraceService;

import java.util.UUID;

public final class PacketEventsBridge {
    private final NetworkProfiler profiler;
    private final PacketOptimizationEngine optimizer;
    private final EntityTraceService traceService;
    private PacketListenerCommon registeredListener;

    public PacketEventsBridge(
            NetworkProfiler profiler,
            PacketOptimizationEngine optimizer,
            EntityTraceService traceService
    ) {
        this.profiler = profiler;
        this.optimizer = optimizer;
        this.traceService = traceService;
    }

    public void register() {
        if (registeredListener != null) {
            return;
        }

        registeredListener = PacketEvents.getAPI()
                .getEventManager()
                .registerListener(new OptimizingListener());
    }

    public void unregister() {
        if (registeredListener == null) {
            return;
        }

        PacketEvents.getAPI()
                .getEventManager()
                .unregisterListener(registeredListener);
        registeredListener = null;
    }

    private final class OptimizingListener extends PacketListenerAbstract {
        private OptimizingListener() {
            super(PacketListenerPriority.HIGHEST);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }

            profiler.record(
                    player.getUniqueId(),
                    TrafficDirection.INBOUND,
                    readableBytes(event.getByteBuf()),
                    event.getPacketName()
            );
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }

            UUID playerId = player.getUniqueId();
            String packetName = event.getPacketName();
            int observedBytes = readableBytes(event.getByteBuf());

            profiler.record(
                    playerId,
                    TrafficDirection.OUTBOUND,
                    observedBytes,
                    packetName
            );

            if (event.isCancelled()) {
                return;
            }

            long now = System.currentTimeMillis();

            switch (packetName) {
                case "ENTITY_METADATA" -> handleMetadata(event, playerId, now);
                case "ENTITY_TELEPORT", "ENTITY_VELOCITY" -> traceEntityPacket(
                        event,
                        playerId,
                        packetName
                );
                case "DESTROY_ENTITIES" -> clearDestroyedEntities(event, playerId);
                case "RESPAWN", "JOIN_GAME" -> optimizer.clearPlayer(playerId);
                case "PARTICLE" -> {
                    if (optimizer.shouldSuppressParticle(playerId, now)) {
                        event.setCancelled(true);
                    }
                }
                default -> {
                    if (optimizer.isUiStatePacket(packetName)) {
                        byte[] payload = copyPayload(event.getByteBuf());
                        if (optimizer.shouldSuppressUi(playerId, packetName, payload, now)) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }

        private void handleMetadata(
                PacketSendEvent event,
                UUID playerId,
                long now
        ) {
            byte[] payload = copyPayload(event.getByteBuf());
            int entityId = readFirstVarInt(event.getByteBuf());

            if (entityId < 0) {
                return;
            }

            if (traceService.isActive(playerId)) {
                traceService.record(playerId, entityId, "ENTITY_METADATA");
            }

            if (optimizer.shouldSuppressMetadata(playerId, entityId, payload, now)) {
                event.setCancelled(true);
            }
        }

        private void traceEntityPacket(
                PacketSendEvent event,
                UUID playerId,
                String packetName
        ) {
            if (!traceService.isActive(playerId)) {
                return;
            }

            int entityId = readFirstVarInt(event.getByteBuf());
            if (entityId >= 0) {
                traceService.record(playerId, entityId, packetName);
            }
        }

        private void clearDestroyedEntities(
                PacketSendEvent event,
                UUID playerId
        ) {
            Object duplicate = null;
            try {
                duplicate = ByteBufHelper.duplicate(event.getByteBuf());
                int count = ByteBufHelper.readVarInt(duplicate);

                for (int i = 0; i < count; i++) {
                    optimizer.clearEntity(playerId, ByteBufHelper.readVarInt(duplicate));
                }
            } catch (RuntimeException ignored) {
                optimizer.clearPlayer(playerId);
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
}
