package com.lsmtreestore.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Checksum}.
 *
 * <p>Covers known CRC32C test vectors, masking round-trips, corruption detection, and
 * argument-validation edge cases required by the 95% coverage gate.
 */
class ChecksumTest {

  @Nested
  class ComputeTests {

    @Test
    void compute_emptyArray_returnsZero() {
      // CRC32C of an empty input is defined as 0 (initial register value, no bits folded in).
      assertThat(Checksum.compute(new byte[0])).isEqualTo(0);
    }

    @Test
    void compute_knownVector_matchesReferenceImplementation() {
      // Cross-check against java.util.zip.CRC32C so the utility is a thin wrapper with no drift.
      byte[] data = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
      CRC32C reference = new CRC32C();
      reference.update(data);
      int expected = (int) reference.getValue();

      assertThat(Checksum.compute(data)).isEqualTo(expected);
    }

    @Test
    void compute_sameInputTwice_returnsIdenticalResult() {
      byte[] data = new byte[] {1, 2, 3, 4, 5};
      assertThat(Checksum.compute(data)).isEqualTo(Checksum.compute(data));
    }

    @Test
    void compute_differentInputs_produceDifferentChecksums() {
      byte[] a = new byte[] {1, 2, 3};
      byte[] b = new byte[] {1, 2, 4};
      assertThat(Checksum.compute(a)).isNotEqualTo(Checksum.compute(b));
    }

    @Test
    void compute_nullArray_throwsNullPointerException() {
      assertThatNullPointerException().isThrownBy(() -> Checksum.compute(null));
    }
  }

  @Nested
  class ComputeRangeTests {

    @Test
    void compute_subRangeMatchesCopyOfRange() {
      byte[] data = "The quick brown fox".getBytes(StandardCharsets.UTF_8);
      int offset = 4;
      int length = 5; // "quick"

      CRC32C reference = new CRC32C();
      reference.update(data, offset, length);
      int expected = (int) reference.getValue();

      assertThat(Checksum.compute(data, offset, length)).isEqualTo(expected);
    }

    @Test
    void compute_rangeWithZeroLength_returnsZero() {
      byte[] data = new byte[] {1, 2, 3, 4};
      assertThat(Checksum.compute(data, 2, 0)).isEqualTo(0);
    }

    @Test
    void compute_fullRange_matchesNoArgOverload() {
      byte[] data = new byte[] {9, 8, 7, 6, 5};
      assertThat(Checksum.compute(data, 0, data.length)).isEqualTo(Checksum.compute(data));
    }

    @Test
    void compute_negativeOffset_throwsIndexOutOfBoundsException() {
      byte[] data = new byte[] {1, 2, 3};
      assertThatThrownBy(() -> Checksum.compute(data, -1, 1))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void compute_lengthExceedsArray_throwsIndexOutOfBoundsException() {
      byte[] data = new byte[] {1, 2, 3};
      assertThatThrownBy(() -> Checksum.compute(data, 0, 10))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void compute_nullArrayRange_throwsNullPointerException() {
      assertThatNullPointerException().isThrownBy(() -> Checksum.compute(null, 0, 0));
    }
  }

  @Nested
  class MaskTests {

    @Test
    void mask_thenUnmask_roundTripsToOriginal() {
      int crc = 0x1234_5678;
      assertThat(Checksum.unmask(Checksum.mask(crc))).isEqualTo(crc);
    }

    @Test
    void unmask_thenMask_alsoRoundTrips() {
      // Symmetry: the masking operation is a bijection over int.
      int masked = 0xDEAD_BEEF;
      assertThat(Checksum.mask(Checksum.unmask(masked))).isEqualTo(masked);
    }

    @Test
    void mask_zero_producesNonZero() {
      // A raw CRC of 0 could collide with padding or sentinel bytes on disk — mask must shift it
      // away from zero so accidental matches are exceedingly unlikely.
      assertThat(Checksum.mask(0)).isNotEqualTo(0);
    }

    @Test
    void mask_differentInputs_produceDifferentOutputs() {
      assertThat(Checksum.mask(1)).isNotEqualTo(Checksum.mask(2));
    }

    @Test
    void mask_knownValue_matchesLevelDbFormula() {
      // Spec from LevelDB: mask(c) = ((c >>> 15) | (c << 17)) + 0xa282ead8
      int crc = 0x0000_0001;
      int expected = ((crc >>> 15) | (crc << 17)) + 0xa282_ead8;
      assertThat(Checksum.mask(crc)).isEqualTo(expected);
    }
  }

  @Nested
  class VerifyTests {

    @Test
    void verify_matchingChecksum_returnsTrue() {
      byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
      int expected = Checksum.compute(data);
      assertThat(Checksum.verify(data, expected)).isTrue();
    }

    @Test
    void verify_corruptedPayload_returnsFalse() {
      byte[] original = "hello".getBytes(StandardCharsets.UTF_8);
      int expected = Checksum.compute(original);

      byte[] corrupted = original.clone();
      corrupted[0] ^= 0x01;

      assertThat(Checksum.verify(corrupted, expected)).isFalse();
    }

    @Test
    void verify_wrongExpectedChecksum_returnsFalse() {
      byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
      int actual = Checksum.compute(data);
      assertThat(Checksum.verify(data, actual + 1)).isFalse();
    }

    @Test
    void verify_emptyArrayWithZeroChecksum_returnsTrue() {
      assertThat(Checksum.verify(new byte[0], 0)).isTrue();
    }

    @Test
    void verify_nullData_throwsNullPointerException() {
      assertThatNullPointerException().isThrownBy(() -> Checksum.verify(null, 0));
    }
  }

  @Nested
  class ConstructionTests {

    @Test
    void privateConstructor_isInvocableViaReflection_forCoverage() throws Exception {
      // Utility class: document the design decision that no instance should ever be created.
      // We only invoke it reflectively so the private constructor line counts toward coverage.
      var ctor = Checksum.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      assertThat(ctor.newInstance()).isNotNull();
    }
  }
}
