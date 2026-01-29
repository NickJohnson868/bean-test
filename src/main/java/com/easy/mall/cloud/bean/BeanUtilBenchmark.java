package com.easy.mall.cloud.bean;

import com.easy.mall.cloud.model.SourcePojo;
import com.easy.mall.cloud.model.TargetPojo;
import com.easy.mall.cloud.util.BeanUtil;
import com.easy.mall.cloud.util.UnsafeBeanUtil;
import org.springframework.beans.BeanUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

/**
 * Bean 工具类性能压测程序
 * 环境要求：JDK 21+ (需启用虚拟线程)
 */
public class BeanUtilBenchmark {

  private static final int THREAD_COUNT = 200;       // 虚拟线程数
  private static final int OPS_PER_THREAD = 50000;   // 每个线程执行次数
  private static final int WARMUP_ITERATIONS = 50000; // 预热次数

  public static void main(String[] args) throws InterruptedException {
    printSystemInfo();

    System.out.println("=== 硬件环境准备就绪，准备压测 ===");
    System.out.println("线程模式: Virtual Threads");
    System.out.println("总操作量: " + (THREAD_COUNT * OPS_PER_THREAD) + " 次转换\n");
    // 准备测试数据
    SourcePojo source = new SourcePojo("测试用户", 18, "上海市浦东新区", 99.9);
    // 1. 预热 (Warm-up)
    System.out.print("JIT 预热中... ");
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      UnsafeBeanUtil.convert(source, TargetPojo.class);
      BeanUtil.convert(source, TargetPojo.class);
    }
    System.out.println("Done.\n");
    // 1. Unsafe 方案
    runTest("UnsafeBeanUtil (Memory Offset)", () -> {
      TargetPojo target = new TargetPojo();
      UnsafeBeanUtil.copy(source, target);
    });

    // 2. JDK 21 VarHandle 方案
    runTest("BeanUtil (VarHandle/Invoke)", () -> {
      TargetPojo target = new TargetPojo();
      BeanUtil.copy(source, target);
    });

    // 3. Spring 方案
    runTest("Spring BeanUtils (Reflection)", () -> {
      TargetPojo target = new TargetPojo();
      BeanUtils.copyProperties(source, target);
    });
  }

  /**
   * 打印详尽的 CPU 和运行时信息
   */
  private static void printSystemInfo() {
    String osName = System.getProperty("os.name");
    String osArch = System.getProperty("os.arch");
    int processors = Runtime.getRuntime().availableProcessors();
    String javaVersion = System.getProperty("java.version");
    String vmName = System.getProperty("java.vm.name");

    System.out.println("--------------------------------------------------");
    System.out.println("硬件与运行时信息:");
    System.out.println("  操作系统: " + osName + " (" + osArch + ")");
    System.out.println("  CPU 核心数 (Logical): " + processors);
    System.out.println("  Java 版本: " + javaVersion);
    System.out.println("  JVM 实现: " + vmName);

    // 尝试获取更具体的 CPU 型号 (仅部分系统环境支持)
    String cpuModel = System.getenv("PROCESSOR_IDENTIFIER");
    if (cpuModel != null) {
      System.out.println("  CPU 型号: " + cpuModel);
    }
    System.out.println("--------------------------------------------------");
  }

  private static void runTest(String label, Runnable task) throws InterruptedException {
    LongAdder counter = new LongAdder();
    CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

    long start = System.nanoTime();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < THREAD_COUNT; i++) {
        executor.submit(() -> {
          try {
            for (int j = 0; j < OPS_PER_THREAD; j++) {
              task.run();
              counter.increment();
            }
          } finally {
            latch.countDown();
          }
        });
      }
      latch.await();
    }

    long durationNs = System.nanoTime() - start;
    double durationMs = durationNs / 1_000_000.0;
    long totalOps = counter.sum();
    double qps = (totalOps / (durationNs / 1_000_000_000.0));

    System.out.printf("[%s] 结果报告:\n", label);
    System.out.printf(" - 总耗时: %.2f ms\n", durationMs);
    System.out.printf(" - 平均单次耗时: %.4f us\n", (durationMs * 1000) / totalOps);
    System.out.printf(" - 吞吐量 (QPS): %,.0f ops/s\n\n", qps);
  }
}