package work.spacecat.twnetoptimizer.profiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

final class TrafficBucket {
    final LongAdder inboundPackets = new LongAdder();
    final LongAdder outboundPackets = new LongAdder();
    final LongAdder inboundObservedBytes = new LongAdder();
    final LongAdder outboundObservedBytes = new LongAdder();

    // These categories intentionally describe outbound traffic only.
    final LongAdder chunkPackets = new LongAdder();
    final LongAdder entityPackets = new LongAdder();
    final LongAdder uiPackets = new LongAdder();
    final LongAdder otherPackets = new LongAdder();

    final Map<String, LongAdder> inboundTypes = new ConcurrentHashMap<>();
    final Map<String, LongAdder> outboundTypes = new ConcurrentHashMap<>();

    void record(TrafficDirection direction, int observedBytes, String packetName, PacketCategory category) {
        String safeName = packetName == null || packetName.isBlank() ? "UNKNOWN" : packetName;
        int safeBytes = Math.max(0, observedBytes);

        if (direction == TrafficDirection.INBOUND) {
            inboundPackets.increment();
            inboundObservedBytes.add(safeBytes);
            inboundTypes.computeIfAbsent(safeName, ignored -> new LongAdder()).increment();
            return;
        }

        outboundPackets.increment();
        outboundObservedBytes.add(safeBytes);
        outboundTypes.computeIfAbsent(safeName, ignored -> new LongAdder()).increment();

        switch (category) {
            case CHUNK -> chunkPackets.increment();
            case ENTITY -> entityPackets.increment();
            case UI -> uiPackets.increment();
            case OTHER -> otherPackets.increment();
        }
    }
}
