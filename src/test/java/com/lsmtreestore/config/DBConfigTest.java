package com.lsmtreestore.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link DBConfig} — the record carrying all tunable storage-engine parameters.
 *
 * <p>Covers default values, builder overrides, validation rules from issue #12, and helper methods
 * for per-level sizing.
 */
@SuppressWarnings("AbbreviationAsWordInName")
class DBConfigTest {

  private static final long MB = 1024L * 1024L;

  @Nested
  class DefaultConfigTests {

    @Test
    void defaultConfig_allDocumentedDefaults_areUsed(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      assertThat(cfg.dbPath()).isEqualTo(tmp);
      assertThat(cfg.maxMemTableSize()).isEqualTo(4 * MB);
      assertThat(cfg.maxImmutableMemTables()).isEqualTo(2);
      assertThat(cfg.l0CompactionTrigger()).isEqualTo(4);
      assertThat(cfg.l0SlowdownTrigger()).isEqualTo(8);
      assertThat(cfg.l0StopTrigger()).isEqualTo(12);
      assertThat(cfg.l1MaxBytes()).isEqualTo(10 * MB);
      assertThat(cfg.levelSizeMultiplier()).isEqualTo(10);
      assertThat(cfg.maxLevels()).isEqualTo(7);
      assertThat(cfg.blockSize()).isEqualTo(4096);
      assertThat(cfg.blockRestartInterval()).isEqualTo(16);
      assertThat(cfg.bloomFilterBitsPerKey()).isEqualTo(10);
      assertThat(cfg.blockCacheSize()).isEqualTo(8 * MB);
      assertThat(cfg.walBlockSize()).isEqualTo(32 * 1024);
      assertThat(cfg.syncWrites()).isFalse();
      assertThat(cfg.verifyChecksums()).isTrue();
    }

    @Test
    void defaultConfig_nullPath_throwsNullPointerException() {
      assertThatNullPointerException().isThrownBy(() -> DBConfig.defaultConfig(null));
    }
  }

  @Nested
  class BuilderTests {

    @Test
    void builder_requiresNonNullPath_throwsNullPointerException() {
      assertThatNullPointerException().isThrownBy(() -> DBConfig.builder(null));
    }

    @Test
    void builder_noOverrides_matchesDefaultConfig(@TempDir Path tmp) {
      DBConfig built = DBConfig.builder(tmp).build();
      DBConfig defaulted = DBConfig.defaultConfig(tmp);

      assertThat(built).isEqualTo(defaulted);
    }

    @Test
    void builder_overridesSubsetOfFields_retainsDefaultsElsewhere(@TempDir Path tmp) {
      DBConfig cfg =
          DBConfig.builder(tmp)
              .maxMemTableSize(8 * MB)
              .syncWrites(true)
              .blockCacheSize(16 * MB)
              .build();

      assertThat(cfg.maxMemTableSize()).isEqualTo(8 * MB);
      assertThat(cfg.syncWrites()).isTrue();
      assertThat(cfg.blockCacheSize()).isEqualTo(16 * MB);
      // Unchanged fields keep defaults.
      assertThat(cfg.maxImmutableMemTables()).isEqualTo(2);
      assertThat(cfg.blockSize()).isEqualTo(4096);
      assertThat(cfg.verifyChecksums()).isTrue();
    }

    @Test
    void builder_overridesEveryField_allCustomValuesPersist(@TempDir Path tmp) {
      DBConfig cfg =
          DBConfig.builder(tmp)
              .maxMemTableSize(2 * MB)
              .maxImmutableMemTables(3)
              .l0CompactionTrigger(2)
              .l0SlowdownTrigger(5)
              .l0StopTrigger(9)
              .l1MaxBytes(20 * MB)
              .levelSizeMultiplier(8)
              .maxLevels(5)
              .blockSize(8192)
              .blockRestartInterval(32)
              .bloomFilterBitsPerKey(12)
              .blockCacheSize(4 * MB)
              .walBlockSize(16 * 1024)
              .syncWrites(true)
              .verifyChecksums(false)
              .build();

      assertThat(cfg.maxMemTableSize()).isEqualTo(2 * MB);
      assertThat(cfg.maxImmutableMemTables()).isEqualTo(3);
      assertThat(cfg.l0CompactionTrigger()).isEqualTo(2);
      assertThat(cfg.l0SlowdownTrigger()).isEqualTo(5);
      assertThat(cfg.l0StopTrigger()).isEqualTo(9);
      assertThat(cfg.l1MaxBytes()).isEqualTo(20 * MB);
      assertThat(cfg.levelSizeMultiplier()).isEqualTo(8);
      assertThat(cfg.maxLevels()).isEqualTo(5);
      assertThat(cfg.blockSize()).isEqualTo(8192);
      assertThat(cfg.blockRestartInterval()).isEqualTo(32);
      assertThat(cfg.bloomFilterBitsPerKey()).isEqualTo(12);
      assertThat(cfg.blockCacheSize()).isEqualTo(4 * MB);
      assertThat(cfg.walBlockSize()).isEqualTo(16 * 1024);
      assertThat(cfg.syncWrites()).isTrue();
      assertThat(cfg.verifyChecksums()).isFalse();
    }

    @Test
    void builder_dbPathOverride_replacesPath(@TempDir Path tmp) {
      Path other = Paths.get("/tmp/other-db");
      DBConfig cfg = DBConfig.builder(tmp).dbPath(other).build();

      assertThat(cfg.dbPath()).isEqualTo(other);
    }

    @Test
    void builder_dbPathOverride_nullPath_throwsNullPointerException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp);
      assertThatNullPointerException().isThrownBy(() -> b.dbPath(null));
    }
  }

  @Nested
  class ValidationTests {

    @Test
    void record_nullDbPath_throwsNullPointerException() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new DBConfig(
                      null, 4 * MB, 2, 4, 8, 12, 10 * MB, 10, 7, 4096, 16, 10, 8 * MB, 32 * 1024,
                      false, true));
    }

    @Test
    void record_maxMemTableSize_zero_throwsIllegalArgumentException(@TempDir Path tmp) {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () ->
                  new DBConfig(
                      tmp, 0L, 2, 4, 8, 12, 10 * MB, 10, 7, 4096, 16, 10, 8 * MB, 32 * 1024, false,
                      true))
          .withMessageContaining("maxMemTableSize");
    }

    @Test
    void record_maxMemTableSize_negative_throwsIllegalArgumentException(@TempDir Path tmp) {
      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(
              () ->
                  new DBConfig(
                      tmp, -1L, 2, 4, 8, 12, 10 * MB, 10, 7, 4096, 16, 10, 8 * MB, 32 * 1024, false,
                      true))
          .withMessageContaining("maxMemTableSize");
    }

    @Test
    void builder_blockSize_notPowerOfTwo_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).blockSize(5000);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("blockSize")
          .withMessageContaining("power of 2");
    }

    @Test
    void builder_blockSize_zero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).blockSize(0);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("blockSize");
    }

    @Test
    void builder_blockSize_negative_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).blockSize(-4096);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("blockSize");
    }

    @Test
    void builder_maxLevels_below2_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).maxLevels(1);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("maxLevels");
    }

    @Test
    void builder_l0Triggers_nonStrictlyIncreasing_throwsIllegalArgumentException(
        @TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).l0CompactionTrigger(8).l0SlowdownTrigger(8);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("l0");
    }

    @Test
    void builder_l0SlowdownAboveStop_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).l0SlowdownTrigger(20).l0StopTrigger(15);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("l0");
    }

    @Test
    void builder_bloomFilterBitsPerKey_zero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).bloomFilterBitsPerKey(0);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("bloomFilterBitsPerKey");
    }

    @Test
    void builder_maxImmutableMemTables_zero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).maxImmutableMemTables(0);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("maxImmutableMemTables");
    }

    @Test
    void builder_l0CompactionTrigger_zero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).l0CompactionTrigger(0);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("l0CompactionTrigger");
    }

    @Test
    void builder_l1MaxBytes_zero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).l1MaxBytes(0L);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("l1MaxBytes");
    }

    @Test
    void builder_levelSizeMultiplier_belowTwo_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).levelSizeMultiplier(1);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("levelSizeMultiplier");
    }

    @Test
    void builder_blockRestartInterval_zero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).blockRestartInterval(0);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("blockRestartInterval");
    }

    @Test
    void builder_blockCacheSize_negative_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).blockCacheSize(-1L);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("blockCacheSize");
    }

    @Test
    void builder_walBlockSize_zero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig.Builder b = DBConfig.builder(tmp).walBlockSize(0);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(b::build)
          .withMessageContaining("walBlockSize");
    }

    @Test
    void builder_blockCacheSize_zero_isAllowed(@TempDir Path tmp) {
      // A zero-size block cache is a valid way to disable the cache entirely.
      DBConfig cfg = DBConfig.builder(tmp).blockCacheSize(0L).build();

      assertThat(cfg.blockCacheSize()).isZero();
    }
  }

  @Nested
  class HelperMethodTests {

    @Test
    void maxBytesForLevel_levelOne_returnsL1MaxBytes(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      assertThat(cfg.maxBytesForLevel(1)).isEqualTo(10 * MB);
    }

    @Test
    void maxBytesForLevel_higherLevels_scaleByMultiplier(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      // L2 = 10MB * 10 = 100MB, L3 = 10MB * 100 = 1000MB
      assertThat(cfg.maxBytesForLevel(2)).isEqualTo(100L * MB);
      assertThat(cfg.maxBytesForLevel(3)).isEqualTo(1000L * MB);
    }

    @Test
    void maxBytesForLevel_customMultiplier_usesConfiguredBase(@TempDir Path tmp) {
      DBConfig cfg =
          DBConfig.builder(tmp).l1MaxBytes(5L * MB).levelSizeMultiplier(4).maxLevels(4).build();

      assertThat(cfg.maxBytesForLevel(1)).isEqualTo(5L * MB);
      assertThat(cfg.maxBytesForLevel(2)).isEqualTo(20L * MB);
      assertThat(cfg.maxBytesForLevel(3)).isEqualTo(80L * MB);
    }

    @Test
    void maxBytesForLevel_levelZero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> cfg.maxBytesForLevel(0))
          .withMessageContaining("level");
    }

    @Test
    void maxBytesForLevel_levelAboveMax_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> cfg.maxBytesForLevel(cfg.maxLevels() + 1))
          .withMessageContaining("level");
    }

    @Test
    void targetFileSize_levelOne_returnsL1BaseSize(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      // L1 target file size is 2MB per LevelDB convention.
      assertThat(cfg.targetFileSize(1)).isEqualTo(2L * MB);
    }

    @Test
    void targetFileSize_higherLevels_scaleByMultiplier(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      assertThat(cfg.targetFileSize(2)).isEqualTo(2L * MB * 10L);
      assertThat(cfg.targetFileSize(3)).isEqualTo(2L * MB * 100L);
    }

    @Test
    void targetFileSize_levelZero_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> cfg.targetFileSize(0))
          .withMessageContaining("level");
    }

    @Test
    void targetFileSize_levelAboveMax_throwsIllegalArgumentException(@TempDir Path tmp) {
      DBConfig cfg = DBConfig.defaultConfig(tmp);

      assertThatExceptionOfType(IllegalArgumentException.class)
          .isThrownBy(() -> cfg.targetFileSize(cfg.maxLevels() + 1))
          .withMessageContaining("level");
    }
  }
}
