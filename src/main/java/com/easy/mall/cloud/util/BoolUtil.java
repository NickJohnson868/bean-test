package com.easy.mall.cloud.util;

public class BoolUtil {

  private static final Integer I_TRUE = 1;

  /**
   * 判断一个数字是否为true（等于1就是true）
   *
   * @param num 输入的数字
   * @return 是否为true
   */
  public static boolean isTrue(Integer num) {
    return I_TRUE.equals(num);
  }

  public static boolean isTrue(Boolean bool) {
    return Boolean.TRUE.equals(bool);
  }

  public static boolean isTrue(String str) {
    return "true".equalsIgnoreCase(str);
  }

  public static boolean isFalse(Integer num) {
    return !isTrue(num);
  }

  public static boolean isFalse(Boolean bool) {
    return !isTrue(bool);
  }

  public static boolean isFalse(String str) {
    return !isTrue(str);
  }
}
