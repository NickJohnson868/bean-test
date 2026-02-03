package com.easy.mall.cloud.bean;

import com.easy.mall.cloud.util.StreamUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class StreamBenchmark {

  // 真正的热机阈值，触发 C2 编译
  private static final int WARMUP_ITERATIONS = 20_000;

  public static void main(String[] args) throws InterruptedException {
    int[] sizes = {100, 1000, 10000, 100000, 1000000};

    System.out.println("Benchmark Start (Unit: us)");
    System.out.println("JVM Warmup starting (C2 Threshold: " + WARMUP_ITERATIONS + ")...");

    // 全局预热：确保所有实现都进入最佳状态
    warmup();

    System.out.println("Size\t\tMethod\t\tFor\t\tStream\t\tParallel");
    System.out.println("-------------------------------------------------------------------------");

    for (int size : sizes) {
      runBenchmark(size);
      TimeUnit.MILLISECONDS.sleep(500);
    }
  }

  private static void warmup() {
    List<Long> warmData = new ArrayList<>(100);
    for (long i = 0; i < 100; i++) warmData.add(i);
    List<List<Long>> warmFlat = List.of(warmData, warmData);

    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      // 循环调用，强制编译器“热起来”
      StreamUtil.transToMapFor(warmData, v -> v);
      StreamUtil.transToMapStream(warmData, v -> v);
      StreamUtil.transToMapParallel(warmData, v -> v);
      StreamUtil.flatFor(warmFlat);
      StreamUtil.flatStream(warmFlat);
      StreamUtil.flatParallel(warmFlat);
    }
  }

  private static void runBenchmark(int size) {
    List<Long> data = new ArrayList<>(size);
    for (long i = 0; i < size; i++) data.add(i);
    List<List<Long>> flatData = List.of(data, data, data);

    // --- transToMap 测试 ---
    long t1 = System.nanoTime() / 1000;
    StreamUtil.transToMapFor(data, v -> v);
    long t2 = System.nanoTime() / 1000;
    StreamUtil.transToMapStream(data, v -> v);
    long t3 = System.nanoTime() / 1000;
    StreamUtil.transToMapParallel(data, v -> v);
    long t4 = System.nanoTime() / 1000;

    System.out.printf("%d\ttransToMap\t%d\t\t%d\t\t%d\n", size, (t2 - t1), (t3 - t2), (t4 - t3));

    // --- flat 测试 ---
    t1 = System.nanoTime() / 1000;
    StreamUtil.flatFor(flatData);
    t2 = System.nanoTime() / 1000;
    StreamUtil.flatStream(flatData);
    t3 = System.nanoTime() / 1000;
    StreamUtil.flatParallel(flatData);
    t4 = System.nanoTime() / 1000;

    System.out.printf("%d\tflat      \t%d\t\t%d\t\t%d\n", size, (t2 - t1), (t3 - t2), (t4 - t3));
    System.out.println("-------------------------------------------------------------------------");
  }
}