package work.spacecat.twnetoptimizer.profiler;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public final class PlayerNetworkProfile {
    private final UUID playerId;
    private final AtomicReference<TrafficBucket> activeBucket =
            new AtomicReference<>(new TrafficBucket());
    private volatile ProfileSnapshot latestSnapshot;

    public PlayerNetworkProfile(UUID playerId) {
        this.playerId = playerId;
        this.latestSnapshot = ProfileSnapshot.empty(playerId);
    }

    public void record(TrafficDirection direction, int observedBytes, String packetName) {
        activeBucket.get().record(
                direction,
                observedBytes,
                packetName,
                PacketCategory.classify(packetName)
        );
    }

    public ProfileSnapshot rollover(
            int pingMs,
            int topPacketTypes,
            long outboundPacketBurstThreshold,
            long outboundByteBurstThreshold
    ) {
        TrafficBucket bucket = activeBucket.getAndSet(new TrafficBucket());

        long inboundPackets = bucket.inboundPackets.sum();
        long outboundPackets = bucket.outboundPackets.sum();
        long inboundBytes = bucket.inboundObservedBytes.sum();
        long outboundBytes = bucket.outboundObservedBytes.sum();

        boolean burst =
                outboundPackets >= outboundPacketBurstThreshold
                        || outboundBytes >= outboundByteBurstThreshold;

        ProfileSnapshot snapshot = new ProfileSnapshot(
                playerId,
                pingMs,
                inboundPackets,
                outboundPackets,
                inboundBytes,
                outboundBytes,
                bucket.chunkPackets.sum(),
                bucket.entityPackets.sum(),
                bucket.uiPackets.sum(),
                bucket.otherPackets.sum(),
                burst,
                top(bucket.inboundTypes, topPacketTypes),
                top(bucket.outboundTypes, topPacketTypes),
                System.currentTimeMillis()
        );

        latestSnapshot = snapshot;
        return snapshot;
    }

    public ProfileSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    private static List<ProfileSnapshot.PacketTypeCount> top(
            Map<String, LongAdder> source,
            int limit
    ) {
        return source.entrySet().stream()
                .map(entry -> new ProfileSnapshot.PacketTypeCount(
                        entry.getKey(),
                        entry.getValue().sum()
                ))
                .sorted(Comparator.comparingLong(
                        ProfileSnapshot.PacketTypeCount::count
                ).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }
}
