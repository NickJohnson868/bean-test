package com.easy.mall.cloud.bean;

import com.easy.mall.cloud.util.UnsafeBeanUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

public class UnsafeBeanUtilTest {

  public static void main(String[] args) {
    try {
      System.out.println("=== 开始 UnsafeBeanUtil 严谨性测试 ===\n");
      testConvert();
      testConverts();
      testDeepCloneWithCircularReference();
      testRecordSupport();
      System.out.println("恭喜！所有测试用例通过，代码严谨性验证完毕。");
    } catch (Exception e) {
      System.err.println("测试失败：" + e);
      throw e;
    }
  }

  /**
   * 测试单对象转换 (Shallow Copy)
   */
  private static void testConvert() {
    Source s = new Source("张三", 25);
    Target t = UnsafeBeanUtil.convert(s, Target.class);

    assert t != null;
    assert "张三".equals(t.name);
    assert t.age == 25;
    System.out.println("[PASS] 单对象 convert 测试通过");
  }

  /**
   * 测试批量转换
   */
  private static void testConverts() {
    List<Source> list = Arrays.asList(new Source("A", 1), new Source("B", 2));
    List<Target> results = UnsafeBeanUtil.converts(list, Target.class);

    assert results.size() == 2;
    assert "A".equals(results.get(0).name);
    assert results.get(1).age == 2;
    System.out.println("[PASS] 批量 converts 测试通过");
  }

  /**
   * 测试深拷贝及循环引用处理
   */
  private static void testDeepCloneWithCircularReference() {
    Node n1 = new Node("Node-1");
    Node n2 = new Node("Node-2");
    n1.next = n2;
    n2.next = n1; // 构造循环引用

    Node clone = UnsafeBeanUtil.deepClone(n1);

    assert clone != n1 : "必须是新对象";
    assert clone.next != n2 : "子对象也必须是新对象";
    assert clone.next.next == clone : "循环引用关系必须保持一致";
    assert clone.name.equals(n1.name);
    System.out.println("[PASS] 深拷贝与循环引用测试通过");
  }

  /**
   * 测试现代 Java Record 支持
   */
  private static void testRecordSupport() {
    UserRecord original = new UserRecord("Admin", 99, List.of("Read", "Write"));
    UserRecord cloned = UnsafeBeanUtil.deepClone(original);

    assert cloned != original;
    assert cloned.name().equals("Admin");
    assert cloned.roles().size() == 2;
    assert cloned.roles() != original.roles() : "集合应该也被深拷贝了";
    System.out.println("[PASS] Record 类型深拷贝测试通过");
  }

  @Data
  @NoArgsConstructor
  public static class Source {
    private String name;
    private int age;

    public Source(String name, int age) {
      this.name = name;
      this.age = age;
    }
  }

  @Data
  @NoArgsConstructor
  public static class Target {
    private String name;
    private int age;
  }

  @Data
  @NoArgsConstructor
  public static class Node {
    private String name;
    private Node next;

    public Node(String name) {
      this.name = name;
    }
  }

  // JDK 14+ Record
  public record UserRecord(String name, int level, List<String> roles) {
  }
}