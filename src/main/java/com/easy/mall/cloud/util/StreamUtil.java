package com.easy.mall.cloud.util;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StreamUtil {

  // --- [ flat 方法的三种实现 ] ---

  public static <T> List<T> flatFor(Collection<? extends Collection<T>> collections) {
    if (ObjUtil.isEmpty(collections)) return List.of();
    // 1. 提前计算总大小，彻底消灭 ArrayList 的扩容
    int totalSize = 0;
    for (Collection<T> sub : collections) {
      if (sub != null) totalSize += sub.size();
    }
    List<T> result = new ArrayList<>(totalSize); // 一次性到位
    for (Collection<T> sub : collections) {
      if (sub != null) result.addAll(sub);
    }
    return result;
  }

  public static <T> List<T> flatStream(Collection<? extends Collection<T>> collections) {
    if (ObjUtil.isEmpty(collections)) return List.of();
    return collections.stream()
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .toList(); // JDK 16+ 优化点
  }

  public static <T> List<T> flatParallel(Collection<? extends Collection<T>> collections) {
    if (ObjUtil.isEmpty(collections)) return List.of();
    return collections.parallelStream()
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .toList();
  }

  // --- [ transToMap 方法的三种实现 ] ---

  public static <K, V> Map<K, V> transToMapFor(List<V> list, Function<? super V, ? extends K> keyMapper) {
    if (ObjUtil.isEmpty(list)) return Map.of();
    Map<K, V> map = HashMap.newHashMap(list.size()); // JDK 19+ 预分配容量
    for (V v : list) {
      if (v != null) {
        K key = keyMapper.apply(v);
        if (key != null) map.putIfAbsent(key, v);
      }
    }
    return map;
  }

  public static <K, V> Map<K, V> transToMapStream(List<V> list, Function<? super V, ? extends K> keyMapper) {
    if (ObjUtil.isEmpty(list)) return Map.of();
    return list.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(keyMapper, v -> v, (f, i) -> f));
  }

  public static <K, V> Map<K, V> transToMapParallel(List<V> list, Function<? super V, ? extends K> keyMapper) {
    if (ObjUtil.isEmpty(list)) return Map.of();
    // 注意：toMap 在并行流下性能较差，并发场景通常用 toConcurrentMap
    return list.parallelStream()
        .filter(Objects::nonNull)
        .collect(Collectors.toConcurrentMap(keyMapper, v -> v, (f, i) -> f));
  }
}