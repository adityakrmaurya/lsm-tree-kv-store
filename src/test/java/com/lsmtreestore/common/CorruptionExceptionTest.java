package com.lsmtreestore.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CorruptionException}.
 *
 * <p>Covers message-only and full constructors, inheritance, and the {@link
 * CorruptionException#toString()} format that surfaces the file path and offset for operator
 * diagnostics.
 */
class CorruptionExceptionTest {

  @Test
  void messageConstructor_setsMessageAndEmptyPathAndNegativeOneOffset() {
    CorruptionException ex = new CorruptionException("bad CRC");

    assertThat(ex.getMessage()).isEqualTo("bad CRC");
    assertThat(ex.path()).isEmpty();
    assertThat(ex.offset()).isEqualTo(-1L);
  }

  @Test
  void fullConstructor_setsAllFields() {
    Path file = Paths.get("/data/db/MANIFEST-0001");
    CorruptionException ex = new CorruptionException("magic mismatch", file, 4096L);

    assertThat(ex.getMessage()).isEqualTo("magic mismatch");
    assertThat(ex.path()).contains(file);
    assertThat(ex.offset()).isEqualTo(4096L);
  }

  @Test
  void fullConstructor_nullPath_returnsEmptyOptional() {
    CorruptionException ex = new CorruptionException("truncated", null, 0L);

    assertThat(ex.path()).isEmpty();
    assertThat(ex.offset()).isZero();
  }

  @Test
  void inheritsFromStoreException_andFromRuntimeException() {
    CorruptionException ex = new CorruptionException("bad");

    assertThat(ex).isInstanceOf(StoreException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  @Test
  void toString_fullConstructor_includesPathAndOffset() {
    Path file = Paths.get("/data/db/000007.sst");
    CorruptionException ex = new CorruptionException("bad block CRC", file, 8192L);

    String rendered = ex.toString();
    assertThat(rendered).contains("bad block CRC");
    assertThat(rendered).contains("000007.sst");
    assertThat(rendered).contains("8192");
  }

  @Test
  void toString_messageOnly_doesNotIncludePathOrOffsetToken() {
    CorruptionException ex = new CorruptionException("bad varint");

    assertThat(ex.toString()).contains("bad varint");
    // No path was provided, so the rendered form must not fabricate one.
    assertThat(ex.toString()).doesNotContain("path=");
  }

  @Test
  void causeConstructor_preservesMessageAndCause() {
    Throwable underlying = new java.nio.BufferUnderflowException();
    CorruptionException ex = new CorruptionException("truncated varint", underlying);

    assertThat(ex.getMessage()).isEqualTo("truncated varint");
    assertThat(ex.getCause()).isSameAs(underlying);
    assertThat(ex.path()).isEmpty();
    assertThat(ex.offset()).isEqualTo(-1L);
  }

  @Test
  void fullCauseConstructor_preservesAllFields() {
    Path file = Paths.get("/data/db/000042.sst");
    Throwable underlying = new IllegalArgumentException("bad tag");
    CorruptionException ex =
        new CorruptionException("malformed block footer", file, 16384L, underlying);

    assertThat(ex.getMessage()).isEqualTo("malformed block footer");
    assertThat(ex.getCause()).isSameAs(underlying);
    assertThat(ex.path()).contains(file);
    assertThat(ex.offset()).isEqualTo(16384L);
  }

  @Test
  void causeConstructor_nullCause_isAllowed() {
    CorruptionException ex = new CorruptionException("bad CRC", (Throwable) null);

    assertThat(ex.getMessage()).isEqualTo("bad CRC");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void fullCauseConstructor_nullPath_returnsEmptyOptional() {
    Throwable underlying = new RuntimeException("decoder failed");
    CorruptionException ex = new CorruptionException("bad varint", null, 0L, underlying);

    assertThat(ex.path()).isEmpty();
    assertThat(ex.offset()).isZero();
    assertThat(ex.getCause()).isSameAs(underlying);
  }

  @Test
  void toString_pathNullButOffsetSet_rendersOffsetOnly() {
    // Path unknown, offset recorded — forensic output should include offset but not a synthesised
    // path token. Exercises the branch where `first` stays true before the offset clause runs.
    CorruptionException ex = new CorruptionException("bad varint", null, 42L);

    String rendered = ex.toString();
    assertThat(rendered).contains("bad varint").contains("offset=42").doesNotContain("path=");
  }

  @Test
  void toString_pathSetButOffsetUnknown_rendersPathOnly() {
    // Offset unknown, path recorded — forensic output should include path but skip the offset
    // clause entirely. Exercises the `offset == UNKNOWN_OFFSET` branch after a non-null path.
    Path file = Paths.get("/data/db/MANIFEST-0002");
    CorruptionException ex = new CorruptionException("unexpected EOF", file, -1L);

    String rendered = ex.toString();
    assertThat(rendered)
        .contains("unexpected EOF")
        .contains("MANIFEST-0002")
        .doesNotContain("offset=");
  }

  @Test
  void toString_fullCauseConstructor_includesPathAndOffset() {
    Path file = Paths.get("/data/db/000101.sst");
    CorruptionException ex =
        new CorruptionException("bad block CRC", file, 8192L, new RuntimeException("x"));

    String rendered = ex.toString();
    assertThat(rendered).contains("bad block CRC").contains("000101.sst").contains("8192");
  }
}
