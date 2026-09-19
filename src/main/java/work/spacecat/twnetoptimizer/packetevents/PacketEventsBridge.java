package work.spacecat.twnetoptimizer.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import org.bukkit.entity.Player;
import work.spacecat.twnetoptimizer.profiler.NetworkProfiler;
import work.spacecat.twnetoptimizer.profiler.TrafficDirection;

public final class PacketEventsBridge {
    private final NetworkProfiler profiler;
    private PacketListenerCommon registeredListener;

    public PacketEventsBridge(NetworkProfiler profiler) {
        this.profiler = profiler;
    }

    public void register() {
        if (registeredListener != null) {
            return;
        }

        registeredListener = PacketEvents.getAPI()
                .getEventManager()
                .registerListener(new MonitorListener());
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

    private final class MonitorListener extends PacketListenerAbstract {
        private MonitorListener() {
            super(PacketListenerPriority.MONITOR);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            record(
                    event.getPlayer(),
                    event.getPacketName(),
                    event.getByteBuf(),
                    TrafficDirection.INBOUND
            );
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            record(
                    event.getPlayer(),
                    event.getPacketName(),
                    event.getByteBuf(),
                    TrafficDirection.OUTBOUND
            );
        }

        private void record(
                Object playerObject,
                String packetName,
                Object byteBuf,
                TrafficDirection direction
        ) {
            if (!(playerObject instanceof Player player)) {
                return;
            }

            int observedBytes = 0;
            try {
                if (byteBuf != null) {
                    observedBytes = ByteBufHelper.readableBytes(byteBuf);
                }
            } catch (RuntimeException ignored) {
                // Packet count remains useful if this buffer cannot expose its size.
            }

            profiler.record(
                    player.getUniqueId(),
                    direction,
                    observedBytes,
                    packetName
            );
        }
    }
}
