package work.spacecat.twnetoptimizer.profiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

final class TrafficBucket {
    final LongAdder inboundPackets = new LongAdder();
    final LongAdder inboundObservedBytes = new LongAdder();
    final Map<String, LongAdder> inboundTypes = new ConcurrentHashMap<>();

    final LongAdder rawOutboundPackets = new LongAdder();
    final LongAdder rawOutboundObservedBytes = new LongAdder();
    final LongAdder rawChunkPackets = new LongAdder();
    final LongAdder rawEntityPackets = new LongAdder();
    final LongAdder rawUiPackets = new LongAdder();
    final LongAdder rawOtherPackets = new LongAdder();
    final Map<String, LongAdder> rawOutboundTypes = new ConcurrentHashMap<>();

    final LongAdder forwardedOutboundPackets = new LongAdder();
    final LongAdder forwardedOutboundObservedBytes = new LongAdder();
    final LongAdder forwardedChunkPackets = new LongAdder();
    final LongAdder forwardedEntityPackets = new LongAdder();
    final LongAdder forwardedUiPackets = new LongAdder();
    final LongAdder forwardedOtherPackets = new LongAdder();
    final Map<String, LongAdder> forwardedOutboundTypes = new ConcurrentHashMap<>();

    void recordInbound(int observedBytes, String packetName) {
        String safeName = safeName(packetName);
        inboundPackets.increment();
        inboundObservedBytes.add(Math.max(0, observedBytes));
        inboundTypes.computeIfAbsent(safeName, ignored -> new LongAdder()).increment();
    }

    void recordRawOutbound(int observedBytes, String packetName) {
        String safeName = safeName(packetName);
        PacketCategory category = PacketCategory.classify(packetName);

        rawOutboundPackets.increment();
        rawOutboundObservedBytes.add(Math.max(0, observedBytes));
        rawOutboundTypes.computeIfAbsent(safeName, ignored -> new LongAdder()).increment();
        incrementCategory(
                category,
                rawChunkPackets,
                rawEntityPackets,
                rawUiPackets,
                rawOtherPackets
        );
    }

    void recordForwardedOutbound(int observedBytes, String packetName) {
        String safeName = safeName(packetName);
        PacketCategory category = PacketCategory.classify(packetName);

        forwardedOutboundPackets.increment();
        forwardedOutboundObservedBytes.add(Math.max(0, observedBytes));
        forwardedOutboundTypes.computeIfAbsent(safeName, ignored -> new LongAdder()).increment();
        incrementCategory(
                category,
                forwardedChunkPackets,
                forwardedEntityPackets,
                forwardedUiPackets,
                forwardedOtherPackets
        );
    }

    private static void incrementCategory(
            PacketCategory category,
            LongAdder chunk,
            LongAdder entity,
            LongAdder ui,
            LongAdder other
    ) {
        switch (category) {
            case CHUNK -> chunk.increment();
            case ENTITY -> entity.increment();
            case UI -> ui.increment();
            case OTHER -> other.increment();
        }
    }

    private static String safeName(String packetName) {
        return packetName == null || packetName.isBlank()
                ? "UNKNOWN"
                : packetName;
    }
}
