package com.lsmtreestore.common;

import java.util.Objects;
import java.util.zip.CRC32C;

/**
 * CRC32C checksum utility for WAL records and SSTable blocks.
 *
 * <p>All methods are {@code static} and stateless. Backed by {@link java.util.zip.CRC32C}, which is
 * hardware-accelerated on modern x86-64 and ARM64 JVMs via the SSE 4.2 / ARMv8 CRC32 instructions.
 *
 * <p><b>Why masking?</b> LevelDB and RocksDB mask stored checksums because the raw CRC of a block
 * can, by construction, equal a CRC value embedded inside the block itself — causing a stored CRC
 * on disk to line up with a CRC word appearing in user data during recovery scans. Masking shifts
 * and offsets the value so that a raw-CRC collision with a mask-space word is astronomically
 * unlikely. The formula is LevelDB-compatible: {@code ((crc >>> 15) | (crc << 17)) + 0xa282ead8}.
 */
public final class Checksum {

  // LevelDB constant — chosen so mask(0) is non-zero and the mapping is a bijection over int.
  private static final int MASK_DELTA = 0xa282_ead8;

  private Checksum() {
    // Prevent instantiation — all API is static.
  }

  /**
   * Computes the CRC32C of the given byte array.
   *
   * @param data input bytes to checksum; must not be {@code null}
   * @return 32-bit CRC32C as a signed {@code int} (bit pattern matches the unsigned polynomial)
   * @throws NullPointerException if {@code data} is {@code null}
   */
  public static int compute(byte[] data) {
    Objects.requireNonNull(data, "data");
    CRC32C crc = new CRC32C();
    crc.update(data, 0, data.length);
    return (int) crc.getValue();
  }

  /**
   * Computes the CRC32C of a sub-range of the given byte array.
   *
   * @param data backing byte array; must not be {@code null}
   * @param offset zero-based starting index, inclusive
   * @param length number of bytes to hash starting at {@code offset}
   * @return 32-bit CRC32C as a signed {@code int}
   * @throws NullPointerException if {@code data} is {@code null}
   * @throws IndexOutOfBoundsException if {@code offset} or {@code length} is negative, or {@code
   *     offset + length} exceeds {@code data.length}
   */
  public static int compute(byte[] data, int offset, int length) {
    Objects.requireNonNull(data, "data");
    Objects.checkFromIndexSize(offset, length, data.length);
    CRC32C crc = new CRC32C();
    crc.update(data, offset, length);
    return (int) crc.getValue();
  }

  /**
   * Verifies a byte array against an expected CRC32C.
   *
   * @param data bytes to verify; must not be {@code null}
   * @param expectedChecksum the checksum previously computed over {@code data}
   * @return {@code true} if {@code compute(data)} equals {@code expectedChecksum}
   * @throws NullPointerException if {@code data} is {@code null}
   */
  public static boolean verify(byte[] data, int expectedChecksum) {
    return compute(data) == expectedChecksum;
  }

  /**
   * Masks a checksum before storing it on disk.
   *
   * <p>The mask is a bijection over 32-bit integers, so {@code unmask(mask(x)) == x} for every
   * {@code x}. The concrete formula matches LevelDB's {@code crc32c.h} so this store is
   * wire-compatible with tools that inspect LevelDB-style on-disk layouts.
   *
   * @param checksum the raw CRC32C value returned by {@link #compute(byte[])}
   * @return the masked checksum safe to embed next to the data it covers
   */
  public static int mask(int checksum) {
    // Rotate right by 15 then add a constant — invertible and collision-shifting.
    return ((checksum >>> 15) | (checksum << 17)) + MASK_DELTA;
  }

  /**
   * Reverses {@link #mask(int)} to recover the original CRC32C value.
   *
   * @param maskedChecksum a value previously produced by {@link #mask(int)}
   * @return the original unmasked checksum
   */
  public static int unmask(int maskedChecksum) {
    int rotated = maskedChecksum - MASK_DELTA;
    // Inverse rotate: rotate left by 15 to undo the rotate-right-by-15 in mask().
    return (rotated >>> 17) | (rotated << 15);
  }
}
