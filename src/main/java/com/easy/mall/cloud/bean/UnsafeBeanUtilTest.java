package com.easy.mall.cloud.bean;

import com.easy.mall.cloud.model.*;
import com.easy.mall.cloud.util.UnsafeBeanUtil;

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
      testCopyShallow();
      testCopyWithOptions();
      testDeepCopy();
      testPrimitiveWrapperCompatibility();
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
    assert "张三".equals(t.getName());
    assert t.getAge() == 25;
    System.out.println("[PASS] 单对象 convert 测试通过");
  }

  /**
   * 测试批量转换
   */
  private static void testConverts() {
    List<Source> list = Arrays.asList(new Source("A", 1), new Source("B", 2));
    List<Target> results = UnsafeBeanUtil.converts(list, Target.class);

    assert results.size() == 2;
    assert "A".equals(results.get(0).getName());
    assert results.get(1).getAge() == 2;
    System.out.println("[PASS] 批量 converts 测试通过");
  }

  /**
   * 测试深拷贝及循环引用处理
   */
  private static void testDeepCloneWithCircularReference() {
    Node n1 = new Node("Node-1");
    Node n2 = new Node("Node-2");
    n1.setNext(n2);
    n2.setNext(n1); // 构造循环引用

    Node clone = UnsafeBeanUtil.deepClone(n1);

    assert clone != n1 : "必须是新对象";
    assert clone.getNext() != n2 : "子对象也必须是新对象";
    assert clone.getNext().getNext() == clone : "循环引用关系必须保持一致";
    assert clone.getName().equals(n1.getName());
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

  /**
   * 1. 测试浅拷贝 (copy) - 验证引用对象是否保持一致
   */
  private static void testCopyShallow() {
    ComplexSource s = new ComplexSource("Order-001", new Node("Child"));
    ComplexTarget t = new ComplexTarget();

    UnsafeBeanUtil.copy(s, t);

    assert t.getId().equals("Order-001");
    assert t.getNode() == s.getNode() : "浅拷贝下，嵌套对象引用必须相同";
    System.out.println("[PASS] copy (Shallow) 测试通过");
  }

  /**
   * 2. 测试深拷贝 (deepCopy) - 验证嵌套对象是否生成副本
   */
  private static void testDeepCopy() {
    ComplexSource s = new ComplexSource("Order-002", new Node("Child"));
    ComplexTarget t = new ComplexTarget();

    UnsafeBeanUtil.deepCopy(s, t);

    assert t.getId().equals("Order-002");
    assert t.getNode() != s.getNode() : "深拷贝下，嵌套对象必须是新实例";
    assert t.getNode().getName().equals(s.getNode().getName());
    System.out.println("[PASS] deepCopy 测试通过");
  }

  /**
   * 3. 测试 CopyOptions - 包含(include)与排除(exclude)
   */
  private static void testCopyWithOptions() {
    Source s = new Source("Secret", 100);
    Target t = new Target();

    // 仅拷贝 age，排除 name
    UnsafeBeanUtil.copy(s, t, new UnsafeBeanUtil.CopyOptions.Builder()
        .exclude("name")
        .build());

    assert t.getName() == null : "name 字段应被排除";
    assert t.getAge() == 100 : "age 字段应被拷贝";

    // 仅包含 name
    Target t2 = new Target();
    UnsafeBeanUtil.copy(s, t2, new UnsafeBeanUtil.CopyOptions.Builder()
        .include("name")
        .build());

    assert t2.getName().equals("Secret");
    assert t2.getAge() == 0 : "age 字段不应在包含列表内";
    System.out.println("[PASS] CopyOptions (Include/Exclude) 测试通过");
  }

  /**
   * 4. 测试基本类型与包装类的兼容性 (int <-> Integer)
   */
  private static void testPrimitiveWrapperCompatibility() {
    WrapperSource s = new WrapperSource(18, true);
    PrimitiveTarget t = new PrimitiveTarget();

    UnsafeBeanUtil.copy(s, t);

    assert t.getAge() == 18;
    assert t.isActive();
    System.out.println("[PASS] 基本类型/包装类兼容性测试通过");
  }
}