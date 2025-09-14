package assignment2;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for LamportClock class.
 * Verifies initial state, tick increments, update behavior,
 * and concurrent access correctness.
 */
public class LamportClockTest {

    /**
     * Tests that a new LamportClock starts at time zero.
     */
    @Test
    public void testInitialClockIsZero() {
        LamportClock clock = new LamportClock();
        assertEquals(0, clock.getTime());
    }

    /**
     * Tests that calling tick() increases the logical time by 1.
     */
    @Test
    public void testTickIncreasesTime() {
        LamportClock clock = new LamportClock();
        clock.tick();
        assertEquals(1, clock.getTime());
        clock.tick();
        assertEquals(2, clock.getTime());
    }

    /**
     * Tests that update(remoteTime) sets the logical time to max(localTime, remoteTime) + 1.
     */
    @Test
    public void testUpdateSetsToMaxPlusOne() {
        LamportClock clock = new LamportClock();

        clock.tick(); // local time = 1
        clock.update(5); // should update to max(1,5)+1 = 6
        assertEquals(6, clock.getTime());

        clock.update(4); // should update to max(6,4)+1 = 7
        assertEquals(7, clock.getTime());
    }

    /**
     * Tests concurrent ticking of the LamportClock from two threads.
     * Verifies logical time reflects all increments (at least 2000 after two threads run 1000 ticks each).
     */
    @Test
    public void testConcurrentUpdates() throws InterruptedException {
        LamportClock clock = new LamportClock();

        Runnable r = () -> {
            for (int i = 0; i < 1000; i++) {
                clock.tick();
            }
        };

        Thread t1 = new Thread(r);
        Thread t2 = new Thread(r);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // The final time should be at least 2000 (sum of increments from both threads)
        assertTrue(clock.getTime() >= 2000);
    }
}
