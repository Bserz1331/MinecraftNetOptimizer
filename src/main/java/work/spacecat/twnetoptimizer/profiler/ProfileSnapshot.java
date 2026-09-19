package work.spacecat.twnetoptimizer.profiler;

import java.util.List;
import java.util.UUID;

public record ProfileSnapshot(
        UUID playerId,
        int pingMs,
        long inboundPackets,
        long outboundPackets,
        long rawOutboundPackets,
        long inboundObservedBytes,
        long outboundObservedBytes,
        long rawOutboundObservedBytes,
        long chunkPackets,
        long entityPackets,
        long uiPackets,
        long otherPackets,
        long rawChunkPackets,
        long rawEntityPackets,
        long rawUiPackets,
        long rawOtherPackets,
        boolean burst,
        List<PacketTypeCount> topInboundTypes,
        List<PacketTypeCount> topOutboundTypes,
        List<PacketTypeCount> topRawOutboundTypes,
        long capturedAtEpochMs
) {
    public static ProfileSnapshot empty(UUID playerId) {
        return new ProfileSnapshot(
                playerId,
                -1,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                false,
                List.of(),
                List.of(),
                List.of(),
                System.currentTimeMillis()
        );
    }

    public long notForwardedPackets() {
        return Math.max(0L, rawOutboundPackets - outboundPackets);
    }

    public long notForwardedObservedBytes() {
        return Math.max(0L, rawOutboundObservedBytes - outboundObservedBytes);
    }

    public double packetReductionPercent() {
        if (rawOutboundPackets <= 0L) {
            return 0.0;
        }

        return notForwardedPackets() * 100.0 / rawOutboundPackets;
    }

    public double observedByteReductionPercent() {
        if (rawOutboundObservedBytes <= 0L) {
            return 0.0;
        }

        return notForwardedObservedBytes() * 100.0 / rawOutboundObservedBytes;
    }

    public record PacketTypeCount(String packetName, long count) {
    }
}
