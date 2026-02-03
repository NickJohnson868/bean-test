package com.easy.mall.cloud.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

public final class CodeUtil {

  private static final long EPOCH = 1735689600000L;
  private static final long WORKER_ID_BITS = 10L;
  private static final long SEQUENCE_BITS = 12L;
  private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);  // 4095
  private static final long WORKER_SHIFT = SEQUENCE_BITS;
  private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
  private static final AtomicLong LAST_STATE = new AtomicLong(-1L);

  private static final RandomGenerator RNG = RandomGenerator.of("L64X128MixRandom");
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
  private static final char[] ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final ZoneId ZONE_ID = ZoneId.systemDefault();

  // TimeCode 状态: 高 48 位存 epochSecond，低 16 位存 sequence
  private static final AtomicLong TIME_STATE = new AtomicLong(0L);
  private static final AtomicReference<TimeCache> TIME_CACHE = new AtomicReference<>(new TimeCache(0L, ""));

  /**
   * 高性能 UUID v7 (趋势递增，RFC 9562 标准)
   */
  public static String uuid() {
    long ms = System.currentTimeMillis();
    long msb = (ms << 16) | (0x7L << 12) | (RNG.nextLong() & 0xFFFL);
    long lsb = (RNG.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

    char[] out = new char[36];
    formatHex(out, 0, 8, msb >> 32);
    out[8] = '-';
    formatHex(out, 9, 4, msb >> 16);
    out[13] = '-';
    formatHex(out, 14, 4, msb);
    out[18] = '-';
    formatHex(out, 19, 4, lsb >> 48);
    out[23] = '-';
    formatHex(out, 24, 12, lsb);
    return new String(out);
  }

  /**
   * 高性能 32 位无横线 UUID v7
   */
  public static String simpleUUID() {
    long ms = System.currentTimeMillis();
    long msb = (ms << 16) | (0x7L << 12) | (RNG.nextLong() & 0xFFFL);
    long lsb = (RNG.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

    char[] out = new char[32];
    formatHex(out, 0, 8, msb >> 32);
    formatHex(out, 8, 4, msb >> 16);
    formatHex(out, 12, 4, msb);
    formatHex(out, 16, 4, lsb >> 48);
    formatHex(out, 20, 12, lsb);
    return new String(out);
  }

  /**
   * 19位业务时间流水号，yyyyMMddHHmmss + workerId + sequence
   */
  public static long generateTimeLong(int workerId) {
    if (workerId < 0 || workerId > 9) throw new IllegalArgumentException("机器码范围应在0-9之间");

    while (true) {
      long currentSec = System.currentTimeMillis() / 1000;
      long state = TIME_STATE.get();
      long lastSec = state >> 16;
      int lastSeq = (int) (state & 0xFFFF);

      long nextSec = currentSec;
      int nextSeq = 0;

      if (currentSec <= lastSec) {
        nextSec = lastSec;
        nextSeq = lastSeq + 1;
        if (nextSeq > 9999) { // 单秒溢出，逻辑推秒
          nextSec = lastSec + 1;
          nextSeq = 0;
        }
      }

      if (TIME_STATE.compareAndSet(state, (nextSec << 16) | nextSeq)) {
        String timeStr = getFormattedTime(nextSec);
        // 14位日期 + 1位worker + 4位序列 = 19位
        return Long.parseLong(timeStr) * 100000L + (workerId * 10000L) + nextSeq;
      }
    }
  }

  public static String generateTimeCode(int workerId) {
    return String.valueOf(generateTimeLong(workerId));
  }

  public static long nextId(long workerId) {
    while (true) {
      long currentMs = System.currentTimeMillis();
      long state = LAST_STATE.get();

      // 状态机拆解
      long lastMs = state >> SEQUENCE_BITS;
      int lastSeq = (int) (state & MAX_SEQUENCE);

      long nextMs = currentMs;
      int nextSeq = 0;

      if (currentMs < lastMs) {
        // 发生时钟回拨：利用逻辑时间继续递增
        nextMs = lastMs;
        nextSeq = lastSeq + 1;
      } else if (currentMs == lastMs) {
        // 同一毫秒内自增
        nextSeq = lastSeq + 1;
      }

      // 检查序列号溢出
      if (nextSeq > MAX_SEQUENCE) {
        // 强制推演到下一毫秒，确保 ID 绝不重复且有序
        nextMs = lastMs + 1;
        nextSeq = 0;
      }

      // CAS 保证线程安全：一次性更新时间戳和序列号
      if (LAST_STATE.compareAndSet(state, (nextMs << SEQUENCE_BITS) | nextSeq)) {
        return ((nextMs - EPOCH) << TIMESTAMP_SHIFT)
            | (workerId << WORKER_SHIFT)
            | nextSeq;
      }
      // 竞争失败，Spin-lock 重试
    }
  }

  /**
   * 纯随机码生成
   */
  public static String randomCode(int length) {
    char[] out = new char[length];
    for (int i = 0; i < length; i++) {
      out[i] = ALPHANUMERIC[RNG.nextInt(ALPHANUMERIC.length)];
    }
    return new String(out);
  }

  public static String md5(String str) {
    return hash("MD5", str);
  }

  public static String sha1(String str) {
    return hash("SHA-1", str);
  }

  public static String sha256(String str) {
    return hash("SHA-256", str);
  }

  public static String base64(String s) {
    return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  public static String debase64(String s) {
    return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
  }

  private static String hash(String algorithm, String str) {
    if (ObjUtil.isBlank(str)) {
      return "";
    }
    try {
      MessageDigest md = MessageDigest.getInstance(algorithm);
      byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM environment error: " + algorithm + " not found", e);
    }
  }

  private static void formatHex(char[] dest, int offset, int len, long val) {
    for (int i = offset + len - 1; i >= offset; i--) {
      dest[i] = HEX_DIGITS[(int) (val & 0xF)];
      val >>= 4;
    }
  }

  private static String getFormattedTime(long second) {
    TimeCache current = TIME_CACHE.get();
    if (current.second == second) {
      return current.formatted;
    }
    LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(second), ZONE_ID);
    String formatted = ldt.format(TIME_FORMATTER);
    TIME_CACHE.set(new TimeCache(second, formatted));
    return formatted;
  }

  /**
   * 替换 Base64.decode
   */
  public static byte[] decodeBase64(String base64Data) {
    if (ObjUtil.isNull(base64Data) || base64Data.isEmpty()) {
      return new byte[0];
    }
    try {
      return Base64.getDecoder().decode(base64Data);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException("Invalid Base64 sequence", e);
    }
  }

  public static String encodeBase64(byte[] data) {
    if (ObjUtil.isNull(data) || data.length == 0) {
      return "";
    }
    return Base64.getEncoder().encodeToString(data);
  }

  private record TimeCache(long second, String formatted) {
  }
}