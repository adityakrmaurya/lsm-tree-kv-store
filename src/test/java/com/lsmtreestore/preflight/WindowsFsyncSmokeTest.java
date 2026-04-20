package com.lsmtreestore.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-flight diagnostic for the WAL module's group-commit coordinator.
 *
 * <p>Verifies that {@link FileChannel#force(boolean)} behaves correctly on the current platform
 * under concurrent load from many virtual threads calling it frequently. This is step 0 of the PR
 * #1 build order: before investing a weekend on a coordinator whose marquee test assumes {@code
 * force(false)} works, prove the OS can actually do it.
 *
 * <p>Disabled by default. To run:
 *
 * <pre>
 *   ./gradlew test --tests "com.lsmtreestore.preflight.WindowsFsyncSmokeTest" \
 *                  -Dpreflight.run=true [-Dpreflight.durationSec=60]
 * </pre>
 *
 * <p>Spawns 100 virtual threads that each repeatedly (i) acquire a shared lock, (ii) append a 10 KB
 * payload to a single {@link FileChannel}, (iii) call {@code force(false)}, (iv) release the lock.
 * Runs for a configurable duration (default 60 seconds). At the end asserts that no thread threw,
 * the file size on disk matches the sum of reported writes, and no torn write is visible at the
 * length level.
 *
 * <p>Pass criteria &rarr; Windows handles the coordinator's access pattern; the marquee test can
 * run on Windows CI. Fail criteria &rarr; either gate the marquee test to Linux only, or switch the
 * I/O strategy to {@code RandomAccessFile} + {@code FileDescriptor.sync()}.
 *
 * <p>This test is intentionally a diagnostic (not a unit test); it writes hundreds of MB to
 * {@code @TempDir} and runs for a full minute by default. The {@code @TempDir} infrastructure
 * cleans up the file after the test.
 */
@EnabledIfSystemProperty(named = "preflight.run", matches = "true")
class WindowsFsyncSmokeTest {

  private static final Logger LOG = LoggerFactory.getLogger(WindowsFsyncSmokeTest.class);

  private static final int WRITER_COUNT = 100;
  private static final int PAYLOAD_SIZE = 10 * 1024;
  private static final long DEFAULT_DURATION_SEC = 60L;
  private static final long JOIN_SLACK_SEC = 30L;

  /**
   * Smoke test: many virtual threads, tight append + force loop, for a minute.
   *
   * @param dir per-test temp directory provided by JUnit Jupiter
   * @throws Exception if the test harness itself fails (file creation, interrupt, etc.); errors
   *     from worker threads are captured via {@link AtomicReference} and surfaced as assertion
   *     failures rather than being rethrown here
   */
  @Test
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void force_under100VirtualThreadsAppendingFor60Sec_noDeadlockNoDataLoss(@TempDir Path dir)
      throws Exception {
    long durationSec = Long.getLong("preflight.durationSec", DEFAULT_DURATION_SEC);
    Path file = dir.resolve("preflight.log");

    byte[] payloadBytes = new byte[PAYLOAD_SIZE];
    Arrays.fill(payloadBytes, (byte) 0x5A);

    AtomicLong totalWrites = new AtomicLong(0);
    AtomicReference<Throwable> firstError = new AtomicReference<>();

    LOG.info(
        "preflight start: writers={}, payloadBytes={}, durationSec={}, file={}",
        WRITER_COUNT,
        PAYLOAD_SIZE,
        durationSec,
        file);

    try (FileChannel channel =
        FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.APPEND)) {
      ReentrantLock lock = new ReentrantLock();
      CountDownLatch start = new CountDownLatch(1);
      long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSec);
      List<Thread> threads = new ArrayList<>(WRITER_COUNT);

      for (int i = 0; i < WRITER_COUNT; i++) {
        Thread t =
            Thread.ofVirtual()
                .name("preflight-writer-" + i)
                .unstarted(
                    () -> {
                      ByteBuffer buf = ByteBuffer.wrap(payloadBytes);
                      try {
                        start.await();
                        while (System.nanoTime() < deadlineNanos && firstError.get() == null) {
                          buf.clear();
                          lock.lock();
                          try {
                            while (buf.hasRemaining()) {
                              channel.write(buf);
                            }
                            channel.force(false);
                          } finally {
                            lock.unlock();
                          }
                          totalWrites.incrementAndGet();
                        }
                      } catch (Throwable err) {
                        firstError.compareAndSet(null, err);
                      }
                    });
        threads.add(t);
        t.start();
      }

      start.countDown();

      long joinDeadlineMs =
          System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationSec + JOIN_SLACK_SEC);
      for (Thread t : threads) {
        long remainingMs = joinDeadlineMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
          fail("Timed out waiting for worker threads to finish — probable deadlock");
        }
        t.join(remainingMs);
        if (t.isAlive()) {
          fail("Worker thread " + t.getName() + " still alive after join timeout");
        }
      }

      long finalWrites = totalWrites.get();
      long onDiskBytes = Files.size(file);
      long expectedBytes = finalWrites * (long) PAYLOAD_SIZE;

      LOG.info(
          "preflight end: writes={}, onDiskBytes={}, expectedBytes={}, throughputPerSec={}",
          finalWrites,
          onDiskBytes,
          expectedBytes,
          finalWrites / Math.max(durationSec, 1L));

      assertThat(firstError.get()).as("no worker thread threw an exception").isNull();
      assertThat(onDiskBytes)
          .as("file size on disk matches total bytes reported by workers")
          .isEqualTo(expectedBytes);
      assertThat(onDiskBytes % PAYLOAD_SIZE)
          .as("file size is an exact multiple of payload size — no torn writes at length level")
          .isZero();
      assertThat(finalWrites).as("at least one write actually happened").isPositive();
    }

    long reopenedSize = Files.size(file);
    LOG.info("preflight reopened size after channel close: {}", reopenedSize);
    assertThat(reopenedSize).as("file size stable after close").isPositive();
  }
}
