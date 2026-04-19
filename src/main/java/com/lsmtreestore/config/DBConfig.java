package com.lsmtreestore.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable record carrying every tunable parameter of the LSM-tree storage engine.
 *
 * <p>Construct a {@code DBConfig} either via {@link #defaultConfig(Path)} for the documented
 * defaults, or via {@link #builder(Path)} for ergonomic override of a subset of fields. The 16-arg
 * canonical constructor is available for test harnesses but is unwieldy for general use.
 *
 * <p>The record's compact constructor performs full validation — construction always fails fast
 * with an {@link IllegalArgumentException} (or {@link NullPointerException} for {@code dbPath})
 * when any invariant is violated, so downstream code never has to re-check.
 *
 * <h2>Defaults (see CLAUDE.md)</h2>
 *
 * <ul>
 *   <li>{@code maxMemTableSize} = 4 MiB
 *   <li>{@code maxImmutableMemTables} = 2
 *   <li>{@code l0CompactionTrigger} = 4, {@code l0SlowdownTrigger} = 8, {@code l0StopTrigger} = 12
 *   <li>{@code l1MaxBytes} = 10 MiB, {@code levelSizeMultiplier} = 10, {@code maxLevels} = 7
 *   <li>{@code blockSize} = 4 KiB (power of two), {@code blockRestartInterval} = 16
 *   <li>{@code bloomFilterBitsPerKey} = 10 (~1% FPR)
 *   <li>{@code blockCacheSize} = 8 MiB, {@code walBlockSize} = 32 KiB
 *   <li>{@code syncWrites} = false (group commit), {@code verifyChecksums} = true
 * </ul>
 *
 * @param dbPath path to the database directory (required, non-null)
 * @param maxMemTableSize flush threshold for the active MemTable, in bytes (> 0)
 * @param maxImmutableMemTables maximum number of pending-flush MemTables before writes stall (> 0)
 * @param l0CompactionTrigger L0 file count that triggers L0 → L1 compaction (> 0)
 * @param l0SlowdownTrigger L0 file count at which writes start slowing
 * @param l0StopTrigger L0 file count at which writes are stopped until compaction catches up
 * @param l1MaxBytes maximum total size for Level 1, in bytes (> 0)
 * @param levelSizeMultiplier each level is this many times larger than the previous (>= 2)
 * @param maxLevels maximum number of levels in the tree (>= 2)
 * @param blockSize target size for SSTable data blocks, in bytes (positive power of two)
 * @param blockRestartInterval prefix-compression restart every N keys (> 0)
 * @param bloomFilterBitsPerKey bloom-filter bits allocated per key (>= 1)
 * @param blockCacheSize LRU data-block cache size, in bytes (>= 0; 0 disables caching)
 * @param walBlockSize WAL block size, in bytes (> 0)
 * @param syncWrites whether every write fsyncs the WAL (true) or group-commits (false)
 * @param verifyChecksums whether to verify CRC32 checksums on reads
 */
@SuppressWarnings("AbbreviationAsWordInName")
public record DBConfig(
    Path dbPath,
    long maxMemTableSize,
    int maxImmutableMemTables,
    int l0CompactionTrigger,
    int l0SlowdownTrigger,
    int l0StopTrigger,
    long l1MaxBytes,
    int levelSizeMultiplier,
    int maxLevels,
    int blockSize,
    int blockRestartInterval,
    int bloomFilterBitsPerKey,
    long blockCacheSize,
    int walBlockSize,
    boolean syncWrites,
    boolean verifyChecksums) {

  /** 1 MiB, used for default sizes. */
  private static final long MB = 1024L * 1024L;

  /** L1 base target file size (LevelDB convention). */
  private static final long L1_TARGET_FILE_SIZE = 2L * MB;

  /**
   * Compact constructor enforcing every invariant documented on the record. Fails fast with a
   * descriptive message on any violation.
   */
  public DBConfig {
    Objects.requireNonNull(dbPath, "dbPath must not be null");
    requirePositive(maxMemTableSize, "maxMemTableSize");
    requirePositive(maxImmutableMemTables, "maxImmutableMemTables");
    requirePositive(l0CompactionTrigger, "l0CompactionTrigger");
    requirePositive(l1MaxBytes, "l1MaxBytes");
    if (levelSizeMultiplier < 2) {
      throw new IllegalArgumentException(
          "levelSizeMultiplier must be >= 2, got " + levelSizeMultiplier);
    }
    if (maxLevels < 2) {
      throw new IllegalArgumentException("maxLevels must be >= 2, got " + maxLevels);
    }
    requirePositive(blockSize, "blockSize");
    if (Integer.bitCount(blockSize) != 1) {
      throw new IllegalArgumentException(
          "blockSize must be a power of 2 for alignment, got " + blockSize);
    }
    requirePositive(blockRestartInterval, "blockRestartInterval");
    if (bloomFilterBitsPerKey < 1) {
      throw new IllegalArgumentException(
          "bloomFilterBitsPerKey must be >= 1, got " + bloomFilterBitsPerKey);
    }
    if (blockCacheSize < 0) {
      throw new IllegalArgumentException("blockCacheSize must be >= 0, got " + blockCacheSize);
    }
    requirePositive(walBlockSize, "walBlockSize");
    if (!(l0CompactionTrigger < l0SlowdownTrigger && l0SlowdownTrigger < l0StopTrigger)) {
      throw new IllegalArgumentException(
          "l0 triggers must satisfy compaction < slowdown < stop, got "
              + l0CompactionTrigger
              + " < "
              + l0SlowdownTrigger
              + " < "
              + l0StopTrigger);
    }
  }

  /**
   * Returns a {@code DBConfig} using every documented default for the given database directory.
   *
   * @param dbPath path to the database directory (required)
   * @return a fully-defaulted configuration
   * @throws NullPointerException if {@code dbPath} is null
   */
  public static DBConfig defaultConfig(Path dbPath) {
    Objects.requireNonNull(dbPath, "dbPath must not be null");
    return new DBConfig(
        dbPath,
        /* maxMemTableSize */ 4L * MB,
        /* maxImmutableMemTables */ 2,
        /* l0CompactionTrigger */ 4,
        /* l0SlowdownTrigger */ 8,
        /* l0StopTrigger */ 12,
        /* l1MaxBytes */ 10L * MB,
        /* levelSizeMultiplier */ 10,
        /* maxLevels */ 7,
        /* blockSize */ 4096,
        /* blockRestartInterval */ 16,
        /* bloomFilterBitsPerKey */ 10,
        /* blockCacheSize */ 8L * MB,
        /* walBlockSize */ 32 * 1024,
        /* syncWrites */ false,
        /* verifyChecksums */ true);
  }

  /**
   * Creates a {@link Builder} pre-populated with the documented defaults, for the given database
   * directory.
   *
   * @param dbPath path to the database directory (required)
   * @return a fluent builder
   * @throws NullPointerException if {@code dbPath} is null
   */
  public static Builder builder(Path dbPath) {
    return new Builder(dbPath);
  }

  /**
   * Returns the maximum total size for the given level, in bytes.
   *
   * <p>Level 1 uses {@link #l1MaxBytes()}; each subsequent level scales by {@link
   * #levelSizeMultiplier()}: {@code l1MaxBytes * levelSizeMultiplier^(level-1)}.
   *
   * @param level level index (must satisfy {@code 1 <= level <= maxLevels})
   * @return cap on total bytes for that level
   * @throws IllegalArgumentException if {@code level} is outside {@code [1, maxLevels]}
   */
  public long maxBytesForLevel(int level) {
    requireValidLevel(level);
    long result = l1MaxBytes;
    for (int i = 1; i < level; i++) {
      result = Math.multiplyExact(result, levelSizeMultiplier);
    }
    return result;
  }

  /**
   * Returns the target SSTable file size for the given level, in bytes.
   *
   * <p>Level 1 uses a 2 MiB base (LevelDB convention); each subsequent level scales by {@link
   * #levelSizeMultiplier()} so the number of files per level stays roughly constant.
   *
   * @param level level index (must satisfy {@code 1 <= level <= maxLevels})
   * @return target SSTable size for that level
   * @throws IllegalArgumentException if {@code level} is outside {@code [1, maxLevels]}
   */
  public long targetFileSize(int level) {
    requireValidLevel(level);
    long result = L1_TARGET_FILE_SIZE;
    for (int i = 1; i < level; i++) {
      result = Math.multiplyExact(result, levelSizeMultiplier);
    }
    return result;
  }

  // --- helpers ---------------------------------------------------------------------------------

  private static void requirePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be > 0, got " + value);
    }
  }

  private static void requirePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be > 0, got " + value);
    }
  }

  private void requireValidLevel(int level) {
    if (level < 1 || level > maxLevels) {
      throw new IllegalArgumentException("level must be in [1, " + maxLevels + "], got " + level);
    }
  }

  /**
   * Fluent builder for {@link DBConfig}.
   *
   * <p>Every setter returns {@code this} so calls can be chained. {@link #build()} invokes the
   * record's compact constructor which performs final validation.
   */
  public static final class Builder {
    private Path dbPath;
    private long maxMemTableSize = 4L * MB;
    private int maxImmutableMemTables = 2;
    private int l0CompactionTrigger = 4;
    private int l0SlowdownTrigger = 8;
    private int l0StopTrigger = 12;
    private long l1MaxBytes = 10L * MB;
    private int levelSizeMultiplier = 10;
    private int maxLevels = 7;
    private int blockSize = 4096;
    private int blockRestartInterval = 16;
    private int bloomFilterBitsPerKey = 10;
    private long blockCacheSize = 8L * MB;
    private int walBlockSize = 32 * 1024;
    private boolean syncWrites = false;
    private boolean verifyChecksums = true;

    private Builder(Path dbPath) {
      this.dbPath = Objects.requireNonNull(dbPath, "dbPath must not be null");
    }

    /**
     * Overrides the database directory.
     *
     * @param dbPath new path (non-null)
     * @return this builder
     */
    public Builder dbPath(Path dbPath) {
      this.dbPath = Objects.requireNonNull(dbPath, "dbPath must not be null");
      return this;
    }

    /**
     * Sets {@link DBConfig#maxMemTableSize()} in bytes.
     *
     * @param v new value
     * @return this builder
     */
    public Builder maxMemTableSize(long v) {
      this.maxMemTableSize = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#maxImmutableMemTables()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder maxImmutableMemTables(int v) {
      this.maxImmutableMemTables = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#l0CompactionTrigger()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder l0CompactionTrigger(int v) {
      this.l0CompactionTrigger = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#l0SlowdownTrigger()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder l0SlowdownTrigger(int v) {
      this.l0SlowdownTrigger = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#l0StopTrigger()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder l0StopTrigger(int v) {
      this.l0StopTrigger = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#l1MaxBytes()} in bytes.
     *
     * @param v new value
     * @return this builder
     */
    public Builder l1MaxBytes(long v) {
      this.l1MaxBytes = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#levelSizeMultiplier()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder levelSizeMultiplier(int v) {
      this.levelSizeMultiplier = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#maxLevels()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder maxLevels(int v) {
      this.maxLevels = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#blockSize()} in bytes (must be a power of two).
     *
     * @param v new value
     * @return this builder
     */
    public Builder blockSize(int v) {
      this.blockSize = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#blockRestartInterval()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder blockRestartInterval(int v) {
      this.blockRestartInterval = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#bloomFilterBitsPerKey()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder bloomFilterBitsPerKey(int v) {
      this.bloomFilterBitsPerKey = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#blockCacheSize()} in bytes (0 disables the cache).
     *
     * @param v new value
     * @return this builder
     */
    public Builder blockCacheSize(long v) {
      this.blockCacheSize = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#walBlockSize()} in bytes.
     *
     * @param v new value
     * @return this builder
     */
    public Builder walBlockSize(int v) {
      this.walBlockSize = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#syncWrites()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder syncWrites(boolean v) {
      this.syncWrites = v;
      return this;
    }

    /**
     * Sets {@link DBConfig#verifyChecksums()}.
     *
     * @param v new value
     * @return this builder
     */
    public Builder verifyChecksums(boolean v) {
      this.verifyChecksums = v;
      return this;
    }

    /**
     * Builds the {@link DBConfig}. Validation happens inside the record's compact constructor.
     *
     * @return a validated, immutable configuration
     * @throws IllegalArgumentException if any invariant is violated
     * @throws NullPointerException if {@code dbPath} is null
     */
    public DBConfig build() {
      return new DBConfig(
          dbPath,
          maxMemTableSize,
          maxImmutableMemTables,
          l0CompactionTrigger,
          l0SlowdownTrigger,
          l0StopTrigger,
          l1MaxBytes,
          levelSizeMultiplier,
          maxLevels,
          blockSize,
          blockRestartInterval,
          bloomFilterBitsPerKey,
          blockCacheSize,
          walBlockSize,
          syncWrites,
          verifyChecksums);
    }
  }
}
