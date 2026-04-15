package com.lsmtreestore.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link DBConfig}. */
class DBConfigTest {

  private static final Path TEST_PATH = Path.of("/tmp/testdb");

  @Nested
  class DefaultConfig {

    @Test
    void defaultConfig_dbPath_matchesProvided() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.dbPath()).isEqualTo(TEST_PATH);
    }

    @Test
    void defaultConfig_maxMemTableSize_is4MB() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.maxMemTableSize()).isEqualTo(4L * 1024 * 1024);
    }

    @Test
    void defaultConfig_maxImmutableMemTables_is2() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.maxImmutableMemTables()).isEqualTo(2);
    }

    @Test
    void defaultConfig_l0CompactionTrigger_is4() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.l0CompactionTrigger()).isEqualTo(4);
    }

    @Test
    void defaultConfig_l0SlowdownTrigger_is8() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.l0SlowdownTrigger()).isEqualTo(8);
    }

    @Test
    void defaultConfig_l0StopTrigger_is12() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.l0StopTrigger()).isEqualTo(12);
    }

    @Test
    void defaultConfig_l1MaxBytes_is10MB() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.l1MaxBytes()).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void defaultConfig_levelSizeMultiplier_is10() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.levelSizeMultiplier()).isEqualTo(10);
    }

    @Test
    void defaultConfig_maxLevels_is7() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.maxLevels()).isEqualTo(7);
    }

    @Test
    void defaultConfig_blockSize_is4096() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.blockSize()).isEqualTo(4096);
    }

    @Test
    void defaultConfig_blockRestartInterval_is16() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.blockRestartInterval()).isEqualTo(16);
    }

    @Test
    void defaultConfig_bloomFilterBitsPerKey_is10() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.bloomFilterBitsPerKey()).isEqualTo(10);
    }

    @Test
    void defaultConfig_blockCacheSize_is8MB() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.blockCacheSize()).isEqualTo(8L * 1024 * 1024);
    }

    @Test
    void defaultConfig_walBlockSize_is32KB() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.walBlockSize()).isEqualTo(32 * 1024);
    }

    @Test
    void defaultConfig_syncWrites_isFalse() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.syncWrites()).isFalse();
    }

    @Test
    void defaultConfig_verifyChecksums_isTrue() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.verifyChecksums()).isTrue();
    }
  }

  @Nested
  class Builder {

    @Test
    void builder_overrideSingleField_preservesOtherDefaults() {
      DBConfig config = DBConfig.builder(TEST_PATH).maxMemTableSize(8L * 1024 * 1024).build();
      assertThat(config.maxMemTableSize()).isEqualTo(8L * 1024 * 1024);
      assertThat(config.maxLevels()).isEqualTo(7); // still default
      assertThat(config.blockSize()).isEqualTo(4096); // still default
    }

    @Test
    void builder_overrideMultipleFields() {
      DBConfig config =
          DBConfig.builder(TEST_PATH).syncWrites(true).maxLevels(5).blockSize(8192).build();
      assertThat(config.syncWrites()).isTrue();
      assertThat(config.maxLevels()).isEqualTo(5);
      assertThat(config.blockSize()).isEqualTo(8192);
    }

    @Test
    void builder_allFieldsOverridden() {
      DBConfig config =
          DBConfig.builder(TEST_PATH)
              .maxMemTableSize(8L * 1024 * 1024)
              .maxImmutableMemTables(4)
              .l0CompactionTrigger(6)
              .l0SlowdownTrigger(10)
              .l0StopTrigger(14)
              .l1MaxBytes(20L * 1024 * 1024)
              .levelSizeMultiplier(8)
              .maxLevels(5)
              .blockSize(8192)
              .blockRestartInterval(32)
              .bloomFilterBitsPerKey(12)
              .blockCacheSize(16L * 1024 * 1024)
              .walBlockSize(65536)
              .syncWrites(true)
              .verifyChecksums(false)
              .build();
      assertThat(config.dbPath()).isEqualTo(TEST_PATH);
      assertThat(config.maxMemTableSize()).isEqualTo(8L * 1024 * 1024);
      assertThat(config.maxImmutableMemTables()).isEqualTo(4);
      assertThat(config.l0CompactionTrigger()).isEqualTo(6);
      assertThat(config.l0SlowdownTrigger()).isEqualTo(10);
      assertThat(config.l0StopTrigger()).isEqualTo(14);
      assertThat(config.l1MaxBytes()).isEqualTo(20L * 1024 * 1024);
      assertThat(config.levelSizeMultiplier()).isEqualTo(8);
      assertThat(config.maxLevels()).isEqualTo(5);
      assertThat(config.blockSize()).isEqualTo(8192);
      assertThat(config.blockRestartInterval()).isEqualTo(32);
      assertThat(config.bloomFilterBitsPerKey()).isEqualTo(12);
      assertThat(config.blockCacheSize()).isEqualTo(16L * 1024 * 1024);
      assertThat(config.walBlockSize()).isEqualTo(65536);
      assertThat(config.syncWrites()).isTrue();
      assertThat(config.verifyChecksums()).isFalse();
    }
  }

  @Nested
  class Validation {

    @Test
    void validate_nullDbPath_throwsNullPointerException() {
      assertThatThrownBy(() -> DBConfig.defaultConfig(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("dbPath");
    }

    @Test
    void validate_zeroMemTableSize_throwsIllegalArgument() {
      assertThatThrownBy(() -> DBConfig.builder(TEST_PATH).maxMemTableSize(0).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxMemTableSize");
    }

    @Test
    void validate_negativeMemTableSize_throwsIllegalArgument() {
      assertThatThrownBy(() -> DBConfig.builder(TEST_PATH).maxMemTableSize(-1).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxMemTableSize");
    }

    @Test
    void validate_zeroBlockSize_throwsIllegalArgument() {
      assertThatThrownBy(() -> DBConfig.builder(TEST_PATH).blockSize(0).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("blockSize");
    }

    @Test
    void validate_blockSizeNotPowerOf2_throwsIllegalArgument() {
      assertThatThrownBy(() -> DBConfig.builder(TEST_PATH).blockSize(3000).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("blockSize")
          .hasMessageContaining("power of 2");
    }

    @Test
    void validate_maxLevelsLessThan2_throwsIllegalArgument() {
      assertThatThrownBy(() -> DBConfig.builder(TEST_PATH).maxLevels(1).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxLevels");
    }

    @Test
    void validate_l0TriggerOrdering_compactionNotLessThanSlowdown() {
      assertThatThrownBy(
              () -> DBConfig.builder(TEST_PATH).l0CompactionTrigger(8).l0SlowdownTrigger(8).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("l0CompactionTrigger");
    }

    @Test
    void validate_l0TriggerOrdering_slowdownNotLessThanStop() {
      assertThatThrownBy(
              () -> DBConfig.builder(TEST_PATH).l0SlowdownTrigger(12).l0StopTrigger(12).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("l0SlowdownTrigger");
    }

    @Test
    void validate_bloomFilterBitsPerKeyLessThan1_throwsIllegalArgument() {
      assertThatThrownBy(() -> DBConfig.builder(TEST_PATH).bloomFilterBitsPerKey(0).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("bloomFilterBitsPerKey");
    }

    @Test
    void validate_minimumValidValues_succeeds() {
      // Should not throw — these are the smallest valid values
      DBConfig config =
          DBConfig.builder(TEST_PATH)
              .maxMemTableSize(1)
              .maxLevels(2)
              .blockSize(1) // 1 is a power of 2
              .l0CompactionTrigger(1)
              .l0SlowdownTrigger(2)
              .l0StopTrigger(3)
              .bloomFilterBitsPerKey(1)
              .build();
      assertThat(config.maxMemTableSize()).isEqualTo(1);
    }
  }

  @Nested
  class HelperMethods {

    @Test
    void maxBytesForLevel_level1_returnsL1MaxBytes() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.maxBytesForLevel(1)).isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void maxBytesForLevel_level2_returnsL1TimesMultiplier() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      // L2 = 10MB * 10 = 100MB
      assertThat(config.maxBytesForLevel(2)).isEqualTo(100L * 1024 * 1024);
    }

    @Test
    void maxBytesForLevel_level3_returnsL1TimesMultiplierSquared() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      // L3 = 10MB * 10^2 = 1000MB
      assertThat(config.maxBytesForLevel(3)).isEqualTo(1000L * 1024 * 1024);
    }

    @Test
    void maxBytesForLevel_customMultiplier() {
      DBConfig config =
          DBConfig.builder(TEST_PATH).l1MaxBytes(5L * 1024 * 1024).levelSizeMultiplier(5).build();
      // L1 = 5MB, L2 = 25MB, L3 = 125MB
      assertThat(config.maxBytesForLevel(1)).isEqualTo(5L * 1024 * 1024);
      assertThat(config.maxBytesForLevel(2)).isEqualTo(25L * 1024 * 1024);
      assertThat(config.maxBytesForLevel(3)).isEqualTo(125L * 1024 * 1024);
    }

    @Test
    void targetFileSize_level1_returns2MB() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      assertThat(config.targetFileSize(1)).isEqualTo(2L * 1024 * 1024);
    }

    @Test
    void targetFileSize_higherLevels_scales() {
      DBConfig config = DBConfig.defaultConfig(TEST_PATH);
      // Each level doubles: L1=2MB, L2=4MB, L3=8MB
      assertThat(config.targetFileSize(2)).isEqualTo(4L * 1024 * 1024);
      assertThat(config.targetFileSize(3)).isEqualTo(8L * 1024 * 1024);
    }
  }
}
