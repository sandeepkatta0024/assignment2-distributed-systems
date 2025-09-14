package assignment2;

import org.junit.Test;
import static org.junit.Assert.*;

public class LamportClockTest {

    @Test
    public void testInitialClockIsZero() {
        LamportClock clock = new LamportClock();
        assertEquals(0, clock.getTime());
    }

    @Test
    public void testTickIncreasesTime() {
        LamportClock clock = new LamportClock();
        clock.tick();
        assertEquals(1, clock.getTime());
        clock.tick();
        assertEquals(2, clock.getTime());
    }

    @Test
    public void testUpdateSetsToMaxPlusOne() {
        LamportClock clock = new LamportClock();

        clock.tick(); // time=1
        clock.update(5); // time should be max(1,5)+1=6
        assertEquals(6, clock.getTime());

        clock.update(4); // time should stay max(6,4)+1=7
        assertEquals(7, clock.getTime());
    }

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

        assertTrue(clock.getTime() >= 2000);
    }
}
