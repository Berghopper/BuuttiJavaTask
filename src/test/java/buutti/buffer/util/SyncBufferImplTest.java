package buutti.buffer.util;

import buutti.buffer.exceptions.SyncBufferSupplierExhaustedException;
import buutti.buffer.exceptions.SyncBufferTimeoutException;
import buutti.buffer.interfaces.SyncBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SyncBufferImplTest {
    /**
     * Test expected serial behavior with timers (don't close resource).
     * @throws InterruptedException on thread interruption.
     * @throws IOException on buffer supply error.
     */
    @Test
    public void testSyncBufferSerial() throws InterruptedException, IOException {
        SyncBuffer<String> syncBuffer = new SyncBufferImpl<>(2, 1L);

        // Add to mant and expect thrown.
        List<String> in = List.of("1", "2", "3");
        boolean thrownIn = false;
        for (String s: in) {
            try {
                syncBuffer.supply(s);
            } catch (SyncBufferTimeoutException e) {
                thrownIn = true;
                break;
            }
        }
        assertTrue(thrownIn);

        List<String> out = new ArrayList<>();
        boolean thrownOut = false;
        while (!syncBuffer.isEmpty()) {
            try {
                out.add(syncBuffer.consume());
            } catch (SyncBufferTimeoutException e) {
                thrownOut = true;
                break;
            }
        }
        assertFalse(thrownOut);

        // Expect only 2 back.
        assertArrayEquals(new String[] {"1", "2"}, out.toArray());
    }

    /**
     * Tests threaded supply with timers (don't close resource).
     * @throws InterruptedException on thread interruption.
     */
    @Test
    public void testSyncBufferThreadedSupply() throws InterruptedException {
        SyncBuffer<String> syncBuffer = new SyncBufferImpl<>(1, 1L);

        List<String> in1 = List.of("1");
        List<String> in2 = List.of("2");

        CountDownLatch latch = new CountDownLatch(2);

        AtomicBoolean t1Thrown = new AtomicBoolean(false);
        AtomicBoolean t2Thrown = new AtomicBoolean(false);
        Thread t1 = new Thread(() -> {
            in1.forEach(s -> {
                try {
                    syncBuffer.supply(s);
                } catch (SyncBufferTimeoutException e) {
                    t1Thrown.set(true);
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            });
            latch.countDown();
        });
        Thread t2 = new Thread(() -> {
            in2.forEach(s -> {
                try {
                    syncBuffer.supply(s);
                } catch (SyncBufferTimeoutException e) {
                    t2Thrown.set(true);
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }
            });
            latch.countDown();
        });

        t1.start();
        t2.start();
        latch.await();

        // Either one needs to have thrown, but not both.
        assertTrue(t1Thrown.get() || t2Thrown.get());
    }

    /**
     * Test whether race condition works properly with the buffer.
     * @throws InterruptedException on thread interruption.
     * @throws IOException on buffer error.
     */
    @Test
    public void testConsumerRaceCondition() throws IOException, InterruptedException {
        try (AbstractSyncBuffer<String> syncBuffer = new SyncBufferImpl<>(1, 10L)) {
            syncBuffer.supply("1");
            syncBuffer.supplierIsExhausted();

            int nThreads = 1_000;
            ExecutorService executorService = Executors.newFixedThreadPool(nThreads);

            // Use synced list to make sure our result is accurate.
            List<Boolean> threadResults = Collections.synchronizedList(new ArrayList<>());

            // Use CyclicBarrier to start all threads at the same time.
            final CyclicBarrier gate = new CyclicBarrier(nThreads);
            for (int i = 0; i < nThreads; i++) {
                //creating instance of the Task1 class and pass a value to its constructor
                Runnable task = new Thread(() -> {
                    try {
                        gate.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        syncBuffer.consume();
                        synchronized (threadResults) {
                            threadResults.add(true);
                        }
                    } catch (IOException | InterruptedException e) {
                        synchronized (threadResults) {
                            threadResults.add(false);
                        }
                    }
                });
                //executing task using execute() method of the executor
                executorService.execute(task);
            }
            //closing executor
            executorService.shutdown();
            while (!executorService.isTerminated()) {
                // wait
            }
            // check that only 1 thread succesfully consumed.
            assertEquals(1, threadResults.stream().filter(Boolean::booleanValue).toList().size());
        }
    }


    /**
     * Test whether supplier exhaustion flag works properly.
     */
    @Test
    public void testSupplierExhaustion() throws IOException, InterruptedException {
        List<String> in = List.of("1", "2");
        try (AbstractSyncBuffer<String> syncBuffer = new SyncBufferImpl<>(2, 10L)) {
            boolean thrownIn = false;
            for (String s: in) {
                try {
                    syncBuffer.supply(s);
                } catch (SyncBufferTimeoutException e) {
                    thrownIn = true;
                    break;
                }
            }
            syncBuffer.supplierIsExhausted();
            assertTrue(syncBuffer.isSupplierIsExhausted());
            assertFalse(thrownIn);

            List<String> out = new ArrayList<>();
            boolean thrownOut = false;
            while (true) {
                try {
                    out.add(syncBuffer.consume());
                } catch (SyncBufferSupplierExhaustedException e) {
                    thrownOut = true;
                    break;
                }
            }
            assertTrue(thrownOut);
            assertArrayEquals(new String[] {"1", "2"}, out.toArray());
        }
    }
}
