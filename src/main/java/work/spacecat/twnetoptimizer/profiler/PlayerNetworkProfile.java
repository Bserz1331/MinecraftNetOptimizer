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

    public void recordInbound(int observedBytes, String packetName) {
        activeBucket.get().recordInbound(observedBytes, packetName);
    }

    public void recordRawOutbound(int observedBytes, String packetName) {
        activeBucket.get().recordRawOutbound(observedBytes, packetName);
    }

    public void recordForwardedOutbound(int observedBytes, String packetName) {
        activeBucket.get().recordForwardedOutbound(observedBytes, packetName);
    }

    public ProfileSnapshot rollover(
            int pingMs,
            int topPacketTypes,
            long outboundPacketBurstThreshold,
            long outboundByteBurstThreshold
    ) {
        TrafficBucket bucket = activeBucket.getAndSet(new TrafficBucket());

        long inboundPackets = bucket.inboundPackets.sum();
        long forwardedPackets = bucket.forwardedOutboundPackets.sum();
        long rawPackets = bucket.rawOutboundPackets.sum();

        long inboundBytes = bucket.inboundObservedBytes.sum();
        long forwardedBytes = bucket.forwardedOutboundObservedBytes.sum();
        long rawBytes = bucket.rawOutboundObservedBytes.sum();

        boolean burst =
                forwardedPackets >= outboundPacketBurstThreshold
                        || forwardedBytes >= outboundByteBurstThreshold;

        ProfileSnapshot snapshot = new ProfileSnapshot(
                playerId,
                pingMs,
                inboundPackets,
                forwardedPackets,
                rawPackets,
                inboundBytes,
                forwardedBytes,
                rawBytes,
                bucket.forwardedChunkPackets.sum(),
                bucket.forwardedEntityPackets.sum(),
                bucket.forwardedUiPackets.sum(),
                bucket.forwardedOtherPackets.sum(),
                bucket.rawChunkPackets.sum(),
                bucket.rawEntityPackets.sum(),
                bucket.rawUiPackets.sum(),
                bucket.rawOtherPackets.sum(),
                burst,
                top(bucket.inboundTypes, topPacketTypes),
                top(bucket.forwardedOutboundTypes, topPacketTypes),
                top(bucket.rawOutboundTypes, topPacketTypes),
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
