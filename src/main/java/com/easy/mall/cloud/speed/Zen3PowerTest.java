package com.easy.mall.cloud.speed;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * 针对 AMD Zen3 (5600H) + Win11 24H2 的硬核性能测试
 * 重点：分支预测效率 + 满血主频持久性
 */
public class Zen3PowerTest {

  public static void main(String[] args) {
    System.out.println("--- AMD 5600H @ 24H2 性能实测 ---");
    System.out.println("操作系统: Windows 11 IoT LTSC 24H2 (VBS: ON)");
    System.out.println("运行环境: " + System.getProperty("java.version"));
    System.out.println("--------------------------------\n");

    // 1. 分支预测测试 (Branch Prediction Test)
    testBranchPrediction();

    // 2. 多核吞吐量测试 (Multi-core Throughput)
    testMultiCoreThroughput();
  }

  private static void testBranchPrediction() {
    int size = 65536;
    int[] data = new int[size];
    Random rnd = new Random(42); // 固定种子保证可重复性
    for (int i = 0; i < size; i++) data[i] = rnd.nextInt(256);

    System.out.println("[Step 1] 分支预测测试 (模拟复杂业务逻辑)");

    // 测试未排序（高预测失败率）
    long start = System.currentTimeMillis();
    long sum = compute(data, 100_000);
    long unsortedTime = System.currentTimeMillis() - start;
    System.out.printf("  > 随机数组 (预测压力大): %d ms%n", unsortedTime);

    // 测试排序后（极低预测失败率）
    Arrays.sort(data);
    start = System.currentTimeMillis();
    sum = compute(data, 100_000);
    long sortedTime = System.currentTimeMillis() - start;
    System.out.printf("  > 排序数组 (预测压力小): %d ms%n", sortedTime);

    double speedup = (double) unsortedTime / sortedTime;
    System.out.printf("  > 预测增益比: %.2fx %s%n", speedup, speedup < 1.5 ? "(24H2 优化显著)" : "(正常表现)");
    System.out.println();
  }

  private static long compute(int[] data, int iterations) {
    long sum = 0;
    for (int i = 0; i < iterations; i++) {
      for (int val : data) {
        // 这个 if 判断是 CPU 分支预测器的战场
        if (val >= 128) sum += val;
      }
    }
    return sum;
  }

  private static void testMultiCoreThroughput() {
    int cores = Runtime.getRuntime().availableProcessors();
    System.out.println("[Step 2] 多核并发吞吐量 (测试 3.9GHz 持久性)");
    System.out.println("  > 正在调动 " + cores + " 个逻辑线程进行密集的素数计算...");

    long start = System.currentTimeMillis();
    // 使用并行流压榨所有核心
    long count = IntStream.rangeClosed(2, 2_000_000)
        .parallel()
        .filter(Zen3PowerTest::isPrime)
        .count();

    long duration = System.currentTimeMillis() - start;
    System.out.printf("  > 计算完成，耗时: %d ms%n", duration);
    System.out.println("  > 请在任务管理器确认：此时 12 个框框是否全部顶满 3.8GHz+");
  }

  private static boolean isPrime(int n) {
    if (n <= 1) return false;
    for (int i = 2; i <= Math.sqrt(n); i++) {
      if (n % i == 0) return false;
    }
    return true;
  }
}