package work.spacecat.twnetoptimizer.trace;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualEntityRegistryTest {
    @Test
    void boundsEntitiesPerViewerAndReleasesOnClear() {
        VirtualEntityRegistry registry = new VirtualEntityRegistry();
        registry.configure(256, 60000L);

        UUID viewer = UUID.randomUUID();

        for (int i = 0; i < 300; i++) {
            registry.recordActivity(
                    viewer,
                    i,
                    "ENTITY_METADATA"
            );
        }

        VirtualEntityRegistry.PlayerMetricsSnapshot playerMetrics =
                registry.metricsSnapshot(viewer);

        VirtualEntityRegistry.MetricsSnapshot metrics =
                registry.metricsSnapshot();

        assertEquals(256, playerMetrics.entities());
        assertEquals(256, metrics.entities());
        assertTrue(metrics.skippedEntities() >= 44L);
        assertEquals(256, metrics.highWaterEntities());

        registry.clearPlayer(viewer);

        assertEquals(0, registry.metricsSnapshot().entities());
        assertEquals(0, registry.metricsSnapshot().viewers());
    }

    @Test
    void replacingExistingEntityDoesNotGrowCount() {
        VirtualEntityRegistry registry = new VirtualEntityRegistry();
        UUID viewer = UUID.randomUUID();

        registry.recordSpawn(
                viewer,
                10,
                UUID.randomUUID(),
                "SPAWN_ENTITY",
                "minecraft:zombie"
        );

        registry.recordSpawn(
                viewer,
                10,
                UUID.randomUUID(),
                "SPAWN_ENTITY",
                "minecraft:skeleton"
        );

        assertEquals(1, registry.metricsSnapshot().entities());
        assertEquals(1, registry.metricsSnapshot(viewer).entities());
    }
}
