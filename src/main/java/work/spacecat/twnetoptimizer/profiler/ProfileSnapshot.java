package work.spacecat.twnetoptimizer.profiler;

import java.util.List;
import java.util.UUID;

public record ProfileSnapshot(
        UUID playerId,
        int pingMs,
        long inboundPackets,
        long outboundPackets,
        long inboundObservedBytes,
        long outboundObservedBytes,
        long chunkPackets,
        long entityPackets,
        long uiPackets,
        long otherPackets,
        boolean burst,
        List<PacketTypeCount> topInboundTypes,
        List<PacketTypeCount> topOutboundTypes,
        long capturedAtEpochMs
) {
    public static ProfileSnapshot empty(UUID playerId) {
        return new ProfileSnapshot(
                playerId, -1,
                0, 0, 0, 0,
                0, 0, 0, 0,
                false,
                List.of(), List.of(),
                System.currentTimeMillis()
        );
    }

    public record PacketTypeCount(String packetName, long count) {
    }
}
