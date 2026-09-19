package work.spacecat.twnetoptimizer.optimizer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerEntryBudgetTest {
    @Test
    void enforcesPerPlayerAndGlobalLimits() {
        PlayerEntryBudget budget = new PlayerEntryBudget(3, 2);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertTrue(budget.tryAcquire(a));
        assertTrue(budget.tryAcquire(a));
        assertFalse(budget.tryAcquire(a));

        assertTrue(budget.tryAcquire(b));
        assertFalse(budget.tryAcquire(b));

        PlayerEntryBudget.Snapshot snapshotA =
                budget.snapshot(a);

        assertEquals(3, snapshotA.total());
        assertEquals(2, snapshotA.player());
        assertEquals(3, snapshotA.highWater());
        assertEquals(1, snapshotA.skippedPlayer());
        assertEquals(1, snapshotA.skippedGlobal());
    }

    @Test
    void releaseRestoresCapacity() {
        PlayerEntryBudget budget = new PlayerEntryBudget(2, 2);
        UUID player = UUID.randomUUID();

        assertTrue(budget.tryAcquire(player));
        assertTrue(budget.tryAcquire(player));
        assertFalse(budget.tryAcquire(player));

        budget.release(player);

        assertTrue(budget.tryAcquire(player));
        assertEquals(2, budget.totalCount());

        budget.release(player, 2);

        assertEquals(0, budget.totalCount());
        assertEquals(0, budget.playerCount(player));
    }
}
