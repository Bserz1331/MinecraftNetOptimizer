package work.spacecat.twnetoptimizer.latency;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import work.spacecat.twnetoptimizer.profiler.ProfileSnapshot;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class LatencyGuardian {
    private static final Set<String> MOVEMENT_PACKETS = Set.of(
            "PLAYER_FLYING",
            "PLAYER_POSITION",
            "PLAYER_ROTATION",
            "PLAYER_POSITION_AND_ROTATION",
            "VEHICLE_MOVE"
    );

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerState> states =
            new ConcurrentHashMap<>();
    private final Map<Class<?>, ChannelMethods> channelMethods =
            new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile long combatWindowMs;
    private volatile long probeIntervalNanos;
    private volatile long backpressureProbeIntervalNanos;
    private volatile int sampleLimit;
    private volatile double handoffPressureMs;

    public LatencyGuardian(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean(
                "latency-guardian.enabled",
                true
        );

        combatWindowMs = Math.max(
                250L,
                plugin.getConfig().getLong(
                        "latency-guardian.combat-window-ms",
                        2500L
                )
        );

        long probeIntervalMs = Math.max(
                50L,
                plugin.getConfig().getLong(
                        "latency-guardian.handoff-probe-interval-ms",
                        100L
                )
        );
        probeIntervalNanos = probeIntervalMs * 1_000_000L;

        long backpressureProbeMs = Math.max(
                25L,
                plugin.getConfig().getLong(
                        "latency-guardian.backpressure-probe-interval-ms",
                        50L
                )
        );
        backpressureProbeIntervalNanos =
                backpressureProbeMs * 1_000_000L;

        sampleLimit = Math.max(
                16,
                plugin.getConfig().getInt(
                        "latency-guardian.handoff-sample-limit",
                        128
                )
        );

        handoffPressureMs = Math.max(
                10.0,
                plugin.getConfig().getDouble(
                        "latency-guardian.handoff-pressure-ms",
                        60.0
                )
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void observeInbound(
            UUID playerId,
            PacketReceiveEvent event
    ) {
        if (!enabled
                || playerId == null
                || event == null
                || event.isCancelled()) {
            return;
        }

        String packetName = event.getPacketName();
        PlayerState state = state(playerId);

        if (MOVEMENT_PACKETS.contains(packetName)) {
            state.recordMovement(System.currentTimeMillis());
            sampleMainThreadHandoff(playerId, state);
            return;
        }

        if (!"INTERACT_ENTITY".equals(packetName)) {
            return;
        }

        try {
            WrapperPlayClientInteractEntity interaction =
                    new WrapperPlayClientInteractEntity(event);

            if (interaction.getAction()
                    == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                long now = System.currentTimeMillis();

                state.recordAttack(now);
                state.combatUntilMs.set(now + combatWindowMs);
                sampleMainThreadHandoff(playerId, state);
            }
        } catch (RuntimeException ignored) {
            // Diagnostics must never affect packet processing.
        }
    }

    public void observeOutbound(UUID playerId, Object channel) {
        if (!enabled || playerId == null || channel == null) {
            return;
        }

        PlayerState state = state(playerId);
        long now = System.nanoTime();
        long previous = state.lastChannelProbeNanos.get();

        if (previous != 0L
                && now - previous < backpressureProbeIntervalNanos) {
            return;
        }

        if (!state.lastChannelProbeNanos.compareAndSet(previous, now)) {
            return;
        }

        ChannelMethods methods = channelMethods.computeIfAbsent(
                channel.getClass(),
                LatencyGuardian::resolveChannelMethods
        );

        if (!methods.supported()) {
            return;
        }

        try {
            boolean writable =
                    (Boolean) methods.isWritable.invoke(channel);

            long beforeUnwritable = invokeLong(
                    methods.bytesBeforeUnwritable,
                    channel
            );

            long beforeWritable = invokeLong(
                    methods.bytesBeforeWritable,
                    channel
            );

            state.updateChannel(
                    writable,
                    beforeUnwritable,
                    beforeWritable
            );
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Channel diagnostics are optional and never affect delivery.
        }
    }

    public Mode mode(UUID playerId, ProfileSnapshot profile) {
        if (!enabled || playerId == null) {
            return Mode.NORMAL;
        }

        PlayerState state = states.get(playerId);
        long now = System.currentTimeMillis();

        if (state != null
                && state.combatUntilMs.get() > now) {
            return Mode.COMBAT;
        }

        if (state != null
                && state.channelWritableKnown
                && !state.channelWritable) {
            return Mode.PRESSURE;
        }

        if ((profile != null && profile.burst())
                || (state != null
                && state.cachedP95Ms >= handoffPressureMs)) {
            return Mode.PRESSURE;
        }

        return Mode.NORMAL;
    }

    public Snapshot snapshot(
            UUID playerId,
            ProfileSnapshot profile
    ) {
        PlayerState state = states.get(playerId);
        Mode mode = mode(playerId, profile);

        if (state == null) {
            return new Snapshot(
                    mode,
                    0L,
                    0L,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    false,
                    true,
                    -1L,
                    -1L,
                    0L
            );
        }

        return state.snapshot(mode);
    }

    public void reset() {
        states.clear();
    }

    public void remove(UUID playerId) {
        states.remove(playerId);
    }

    private PlayerState state(UUID playerId) {
        return states.computeIfAbsent(
                playerId,
                ignored -> new PlayerState()
        );
    }

    private void sampleMainThreadHandoff(
            UUID playerId,
            PlayerState state
    ) {
        long now = System.nanoTime();
        long previous = state.lastProbeNanos.get();

        if (previous != 0L
                && now - previous < probeIntervalNanos) {
            return;
        }

        if (!state.lastProbeNanos.compareAndSet(previous, now)) {
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) {
                    return;
                }

                double delayMs =
                        (System.nanoTime() - now) / 1_000_000.0;

                PlayerState current = states.get(playerId);

                if (current != null) {
                    current.addHandoffSample(
                            delayMs,
                            sampleLimit
                    );
                }
            });
        } catch (RuntimeException ignored) {
            // Shutdown/scheduler state must not affect packet processing.
        }
    }

    private static ChannelMethods resolveChannelMethods(Class<?> type) {
        try {
            Method isWritable = type.getMethod("isWritable");
            Method bytesBeforeUnwritable =
                    optionalMethod(type, "bytesBeforeUnwritable");
            Method bytesBeforeWritable =
                    optionalMethod(type, "bytesBeforeWritable");

            isWritable.trySetAccessible();

            return new ChannelMethods(
                    isWritable,
                    bytesBeforeUnwritable,
                    bytesBeforeWritable
            );
        } catch (NoSuchMethodException | RuntimeException ignored) {
            return ChannelMethods.UNSUPPORTED;
        }
    }

    private static Method optionalMethod(
            Class<?> type,
            String name
    ) {
        try {
            Method method = type.getMethod(name);
            method.trySetAccessible();
            return method;
        } catch (NoSuchMethodException | RuntimeException ignored) {
            return null;
        }
    }

    private static long invokeLong(
            Method method,
            Object target
    ) throws ReflectiveOperationException {
        if (method == null) {
            return -1L;
        }

        Object result = method.invoke(target);

        return result instanceof Number number
                ? number.longValue()
                : -1L;
    }

    public enum Mode {
        NORMAL,
        PRESSURE,
        COMBAT
    }

    private record ChannelMethods(
            Method isWritable,
            Method bytesBeforeUnwritable,
            Method bytesBeforeWritable
    ) {
        private static final ChannelMethods UNSUPPORTED =
                new ChannelMethods(null, null, null);

        private boolean supported() {
            return isWritable != null;
        }
    }

    private static final class PlayerState {
        private final AtomicLong combatUntilMs =
                new AtomicLong();
        private final AtomicLong lastProbeNanos =
                new AtomicLong();
        private final AtomicLong lastChannelProbeNanos =
                new AtomicLong();
        private final Deque<Double> handoffSamples =
                new ArrayDeque<>();

        private long rateSecond = -1L;
        private long movementCurrent;
        private long attackCurrent;

        private volatile double cachedP95Ms;

        private volatile boolean channelWritableKnown;
        private volatile boolean channelWritable = true;
        private volatile long bytesBeforeUnwritable = -1L;
        private volatile long bytesBeforeWritable = -1L;
        private volatile long unwritableObservations;

        private synchronized void recordMovement(long nowMs) {
            rollSecond(nowMs / 1000L);
            movementCurrent++;
        }

        private synchronized void recordAttack(long nowMs) {
            rollSecond(nowMs / 1000L);
            attackCurrent++;
        }

        private synchronized void addHandoffSample(
                double delayMs,
                int limit
        ) {
            handoffSamples.addLast(delayMs);

            while (handoffSamples.size() > limit) {
                handoffSamples.removeFirst();
            }

            cachedP95Ms = percentile95(handoffSamples);
        }

        private void updateChannel(
                boolean writable,
                long beforeUnwritable,
                long beforeWritable
        ) {
            channelWritableKnown = true;
            channelWritable = writable;
            bytesBeforeUnwritable = beforeUnwritable;
            bytesBeforeWritable = beforeWritable;

            if (!writable) {
                unwritableObservations++;
            }
        }

        private synchronized Snapshot snapshot(Mode mode) {
            rollSecond(System.currentTimeMillis() / 1000L);

            ArrayList<Double> samples =
                    new ArrayList<>(handoffSamples);

            double average = samples.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            double max = samples.stream()
                    .mapToDouble(Double::doubleValue)
                    .max()
                    .orElse(0.0);

            return new Snapshot(
                    mode,
                    movementCurrent,
                    attackCurrent,
                    samples.size(),
                    average,
                    cachedP95Ms,
                    max,
                    channelWritableKnown,
                    channelWritable,
                    bytesBeforeUnwritable,
                    bytesBeforeWritable,
                    unwritableObservations
            );
        }

        private void rollSecond(long second) {
            if (rateSecond == -1L) {
                rateSecond = second;
                return;
            }

            if (second != rateSecond) {
                movementCurrent = 0L;
                attackCurrent = 0L;
                rateSecond = second;
            }
        }

        private static double percentile95(
                Deque<Double> values
        ) {
            if (values.isEmpty()) {
                return 0.0;
            }

            ArrayList<Double> sorted =
                    new ArrayList<>(values);

            Collections.sort(sorted);

            int index =
                    (int) Math.ceil(sorted.size() * 0.95) - 1;

            index = Math.max(
                    0,
                    Math.min(index, sorted.size() - 1)
            );

            return sorted.get(index);
        }
    }

    public record Snapshot(
            Mode mode,
            long movementPacketsCurrentSecond,
            long attackPacketsCurrentSecond,
            int handoffSamples,
            double averageHandoffMs,
            double p95HandoffMs,
            double maxHandoffMs,
            boolean channelWritableKnown,
            boolean channelWritable,
            long bytesBeforeUnwritable,
            long bytesBeforeWritable,
            long unwritableObservations
    ) {
    }
}
