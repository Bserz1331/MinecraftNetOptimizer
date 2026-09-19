package work.spacecat.twnetoptimizer.trace;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTraceServiceTest {
    @Test
    void boundsEntitiesPerSessionAndReleasesOnRemove() {
        EntityTraceService service = new EntityTraceService();
        service.configure(128, 60000L);

        UUID player = UUID.randomUUID();
        service.start(player, 60);

        for (int i = 0; i < 160; i++) {
            service.record(player, i, "ENTITY_METADATA");
        }

        EntityTraceService.PlayerMetricsSnapshot playerMetrics =
                service.metricsSnapshot(player);

        EntityTraceService.MetricsSnapshot metrics =
                service.metricsSnapshot();

        assertEquals(128, playerMetrics.entities());
        assertEquals(128, metrics.entities());
        assertTrue(metrics.skippedEntities() >= 32L);
        assertEquals(128, metrics.highWaterEntities());

        service.remove(player);

        assertEquals(0, service.metricsSnapshot().entities());
        assertEquals(0, service.metricsSnapshot().sessions());
    }

    @Test
    void restartingTraceDoesNotRetainPreviousCounters() {
        EntityTraceService service = new EntityTraceService();
        UUID player = UUID.randomUUID();

        service.start(player, 60);
        service.record(player, 1, "ENTITY_METADATA");
        service.record(player, 2, "ENTITY_TELEPORT");

        assertEquals(2, service.metricsSnapshot().entities());

        service.start(player, 60);

        assertEquals(0, service.metricsSnapshot().entities());
        assertEquals(0, service.metricsSnapshot(player).entities());
    }
}
