package com.lsmtreestore.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration record holding all tunable parameters for the LSM Tree KV Store.
 *
 * <p>Uses a builder pattern for ergonomic construction since the record has 16 fields. All
 * parameters have sensible defaults matching LevelDB/RocksDB conventions. Use {@link
 * #defaultConfig(Path)} for quick setup or {@link #builder(Path)} for custom tuning.
 *
 * <p>Validation is performed in the compact constructor to ensure invariants hold regardless of how
 * the record is constructed.
 *
 * @param dbPath path to the database directory (required)
 * @param maxMemTableSize flush threshold in bytes (default 4 MB)
 * @param maxImmutableMemTables stall writes if too many pending flushes (default 2)
 * @param l0CompactionTrigger number of L0 files to trigger L0-to-L1 compaction (default 4)
 * @param l0SlowdownTrigger number of L0 files to start slowing writes (default 8)
 * @param l0StopTrigger number of L0 files to stop writes until compaction catches up (default 12)
 * @param l1MaxBytes max total size for Level 1 in bytes (default 10 MB)
 * @param levelSizeMultiplier each level is Nx the previous (default 10)
 * @param maxLevels maximum number of levels (default 7)
 * @param blockSize target size for SSTable data blocks in bytes (default 4096)
 * @param blockRestartInterval prefix compression restart every N keys (default 16)
 * @param bloomFilterBitsPerKey bloom filter bits per key, ~1% FPR at 10 (default 10)
 * @param blockCacheSize LRU cache size for data blocks in bytes (default 8 MB)
 * @param walBlockSize WAL block size in bytes (default 32 KB)
 * @param syncWrites fsync every write if true, else group commit (default false)
 * @param verifyChecksums verify CRC32 on reads (default true)
 */
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

  /** Default MemTable flush threshold: 4 MB. */
  public static final long DEFAULT_MAX_MEMTABLE_SIZE = 4L * 1024 * 1024;

  /** Default maximum number of immutable MemTables before stalling writes. */
  public static final int DEFAULT_MAX_IMMUTABLE_MEMTABLES = 2;

  /** Default L0 file count to trigger compaction. */
  public static final int DEFAULT_L0_COMPACTION_TRIGGER = 4;

  /** Default L0 file count to start slowing writes. */
  public static final int DEFAULT_L0_SLOWDOWN_TRIGGER = 8;

  /** Default L0 file count to stop writes. */
  public static final int DEFAULT_L0_STOP_TRIGGER = 12;

  /** Default max total size for Level 1: 10 MB. */
  public static final long DEFAULT_L1_MAX_BYTES = 10L * 1024 * 1024;

  /** Default level size multiplier. */
  public static final int DEFAULT_LEVEL_SIZE_MULTIPLIER = 10;

  /** Default maximum number of levels. */
  public static final int DEFAULT_MAX_LEVELS = 7;

  /** Default SSTable data block size: 4 KB. */
  public static final int DEFAULT_BLOCK_SIZE = 4096;

  /** Default prefix compression restart interval. */
  public static final int DEFAULT_BLOCK_RESTART_INTERVAL = 16;

  /** Default bloom filter bits per key (~1% FPR). */
  public static final int DEFAULT_BLOOM_FILTER_BITS_PER_KEY = 10;

  /** Default LRU block cache size: 8 MB. */
  public static final long DEFAULT_BLOCK_CACHE_SIZE = 8L * 1024 * 1024;

  /** Default WAL block size: 32 KB. */
  public static final int DEFAULT_WAL_BLOCK_SIZE = 32 * 1024;

  /** Default base target file size for L1 SSTables: 2 MB. */
  private static final long BASE_TARGET_FILE_SIZE = 2L * 1024 * 1024;

  /** Compact constructor — validates all invariants. */
  public DBConfig {
    Objects.requireNonNull(dbPath, "dbPath must not be null");
    if (maxMemTableSize <= 0) {
      throw new IllegalArgumentException("maxMemTableSize must be > 0, got " + maxMemTableSize);
    }
    if (blockSize <= 0) {
      throw new IllegalArgumentException("blockSize must be > 0, got " + blockSize);
    }
    if ((blockSize & (blockSize - 1)) != 0) {
      throw new IllegalArgumentException("blockSize must be a power of 2, got " + blockSize);
    }
    if (maxLevels < 2) {
      throw new IllegalArgumentException("maxLevels must be >= 2, got " + maxLevels);
    }
    if (l0CompactionTrigger >= l0SlowdownTrigger) {
      throw new IllegalArgumentException(
          "l0CompactionTrigger ("
              + l0CompactionTrigger
              + ") must be < l0SlowdownTrigger ("
              + l0SlowdownTrigger
              + ")");
    }
    if (l0SlowdownTrigger >= l0StopTrigger) {
      throw new IllegalArgumentException(
          "l0SlowdownTrigger ("
              + l0SlowdownTrigger
              + ") must be < l0StopTrigger ("
              + l0StopTrigger
              + ")");
    }
    if (bloomFilterBitsPerKey < 1) {
      throw new IllegalArgumentException(
          "bloomFilterBitsPerKey must be >= 1, got " + bloomFilterBitsPerKey);
    }
  }

  /**
   * Creates a configuration with all default values for the given database path.
   *
   * @param dbPath path to the database directory
   * @return a new {@code DBConfig} with sensible defaults
   * @throws NullPointerException if dbPath is null
   */
  public static DBConfig defaultConfig(Path dbPath) {
    return new DBConfig(
        dbPath,
        DEFAULT_MAX_MEMTABLE_SIZE,
        DEFAULT_MAX_IMMUTABLE_MEMTABLES,
        DEFAULT_L0_COMPACTION_TRIGGER,
        DEFAULT_L0_SLOWDOWN_TRIGGER,
        DEFAULT_L0_STOP_TRIGGER,
        DEFAULT_L1_MAX_BYTES,
        DEFAULT_LEVEL_SIZE_MULTIPLIER,
        DEFAULT_MAX_LEVELS,
        DEFAULT_BLOCK_SIZE,
        DEFAULT_BLOCK_RESTART_INTERVAL,
        DEFAULT_BLOOM_FILTER_BITS_PER_KEY,
        DEFAULT_BLOCK_CACHE_SIZE,
        DEFAULT_WAL_BLOCK_SIZE,
        false,
        true);
  }

  /**
   * Creates a builder initialized with default values for the given database path.
   *
   * @param dbPath path to the database directory
   * @return a new {@code Builder} for fluent configuration
   */
  public static Builder builder(Path dbPath) {
    return new Builder(dbPath);
  }

  /**
   * Returns the maximum total size in bytes for the given level.
   *
   * <p>Formula: {@code l1MaxBytes * levelSizeMultiplier^(level - 1)}.
   *
   * @param level the level number (1-based)
   * @return the max total size in bytes for the level
   */
  public long maxBytesForLevel(int level) {
    long result = l1MaxBytes;
    for (int i = 1; i < level; i++) {
      result *= levelSizeMultiplier;
    }
    return result;
  }

  /**
   * Returns the target SSTable file size in bytes for the given level.
   *
   * <p>Base size is 2 MB for L1, doubling at each subsequent level.
   *
   * @param level the level number (1-based)
   * @return the target file size in bytes
   */
  public long targetFileSize(int level) {
    return BASE_TARGET_FILE_SIZE * (1L << (level - 1));
  }

  /** Fluent builder for constructing {@link DBConfig} with selective overrides. */
  public static final class Builder {

    private final Path dbPath;
    private long maxMemTableSize = DEFAULT_MAX_MEMTABLE_SIZE;
    private int maxImmutableMemTables = DEFAULT_MAX_IMMUTABLE_MEMTABLES;
    private int l0CompactionTrigger = DEFAULT_L0_COMPACTION_TRIGGER;
    private int l0SlowdownTrigger = DEFAULT_L0_SLOWDOWN_TRIGGER;
    private int l0StopTrigger = DEFAULT_L0_STOP_TRIGGER;
    private long l1MaxBytes = DEFAULT_L1_MAX_BYTES;
    private int levelSizeMultiplier = DEFAULT_LEVEL_SIZE_MULTIPLIER;
    private int maxLevels = DEFAULT_MAX_LEVELS;
    private int blockSize = DEFAULT_BLOCK_SIZE;
    private int blockRestartInterval = DEFAULT_BLOCK_RESTART_INTERVAL;
    private int bloomFilterBitsPerKey = DEFAULT_BLOOM_FILTER_BITS_PER_KEY;
    private long blockCacheSize = DEFAULT_BLOCK_CACHE_SIZE;
    private int walBlockSize = DEFAULT_WAL_BLOCK_SIZE;
    private boolean syncWrites = false;
    private boolean verifyChecksums = true;

    private Builder(Path dbPath) {
      this.dbPath = dbPath;
    }

    /** Sets the MemTable flush threshold in bytes. */
    public Builder maxMemTableSize(long maxMemTableSize) {
      this.maxMemTableSize = maxMemTableSize;
      return this;
    }

    /** Sets the maximum number of immutable MemTables before stalling writes. */
    public Builder maxImmutableMemTables(int maxImmutableMemTables) {
      this.maxImmutableMemTables = maxImmutableMemTables;
      return this;
    }

    /** Sets the L0 file count to trigger compaction. */
    public Builder l0CompactionTrigger(int l0CompactionTrigger) {
      this.l0CompactionTrigger = l0CompactionTrigger;
      return this;
    }

    /** Sets the L0 file count to start slowing writes. */
    public Builder l0SlowdownTrigger(int l0SlowdownTrigger) {
      this.l0SlowdownTrigger = l0SlowdownTrigger;
      return this;
    }

    /** Sets the L0 file count to stop writes. */
    public Builder l0StopTrigger(int l0StopTrigger) {
      this.l0StopTrigger = l0StopTrigger;
      return this;
    }

    /** Sets the max total size for Level 1 in bytes. */
    public Builder l1MaxBytes(long l1MaxBytes) {
      this.l1MaxBytes = l1MaxBytes;
      return this;
    }

    /** Sets the level size multiplier. */
    public Builder levelSizeMultiplier(int levelSizeMultiplier) {
      this.levelSizeMultiplier = levelSizeMultiplier;
      return this;
    }

    /** Sets the maximum number of levels. */
    public Builder maxLevels(int maxLevels) {
      this.maxLevels = maxLevels;
      return this;
    }

    /** Sets the target SSTable data block size in bytes. */
    public Builder blockSize(int blockSize) {
      this.blockSize = blockSize;
      return this;
    }

    /** Sets the prefix compression restart interval. */
    public Builder blockRestartInterval(int blockRestartInterval) {
      this.blockRestartInterval = blockRestartInterval;
      return this;
    }

    /** Sets the bloom filter bits per key. */
    public Builder bloomFilterBitsPerKey(int bloomFilterBitsPerKey) {
      this.bloomFilterBitsPerKey = bloomFilterBitsPerKey;
      return this;
    }

    /** Sets the LRU block cache size in bytes. */
    public Builder blockCacheSize(long blockCacheSize) {
      this.blockCacheSize = blockCacheSize;
      return this;
    }

    /** Sets the WAL block size in bytes. */
    public Builder walBlockSize(int walBlockSize) {
      this.walBlockSize = walBlockSize;
      return this;
    }

    /** Sets whether to fsync every write. */
    public Builder syncWrites(boolean syncWrites) {
      this.syncWrites = syncWrites;
      return this;
    }

    /** Sets whether to verify checksums on reads. */
    public Builder verifyChecksums(boolean verifyChecksums) {
      this.verifyChecksums = verifyChecksums;
      return this;
    }

    /**
     * Builds and validates the configuration.
     *
     * @return a new validated {@code DBConfig}
     * @throws NullPointerException if dbPath is null
     * @throws IllegalArgumentException if any parameter violates constraints
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
