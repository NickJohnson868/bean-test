package com.easy.mall.cloud.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.SimplePropertyPreFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 基于 FastJSON2 的 Bean 工具类
 * 修复了所有版本敏感的 API 调用，确保在 2.0.43 及前后版本均可编译通过
 */
public final class JsonBeanUtil {

  private JsonBeanUtil() {
  }

  /**
   * 深拷贝：通过二进制序列化实现，性能优于字符串
   */
  @SuppressWarnings("unchecked")
  public static <T> T deepClone(T source) {
    if (source == null) return null;
    byte[] bytes = JSON.toJSONBytes(source, JSONWriter.Feature.ReferenceDetection);
    return (T) JSON.parseObject(bytes, source.getClass(), JSONReader.Feature.SupportAutoType);
  }

  public static <S, D> void copy(S source, D destination) {
    copy(source, destination, CopyOptions.SHALLOW);
  }

  public static <S, D> void deepCopy(S source, D destination) {
    copy(source, destination, CopyOptions.DEEP);
  }

  /**
   * 核心拷贝实现
   * 修复：使用最基础的 JSONObject 桥接方案，彻底避开 copyTo 方法
   */
  public static <S, D> void copy(S source, D destination, CopyOptions options) {
    if (source == null || destination == null) return;

    SimplePropertyPreFilter filter = null;
    if (options.includes != null || options.excludes != null) {
      filter = new SimplePropertyPreFilter();
      if (options.includes != null) filter.getIncludes().addAll(options.includes);
      if (options.excludes != null) filter.getExcludes().addAll(options.excludes);
    }

    // 1. 将源对象序列化为 JSONObject
    // 这一步能自动处理 includes/excludes
    String json = JSON.toJSONString(source, filter, JSONWriter.Feature.ReferenceDetection);
    JSONObject jsonObject = JSON.parseObject(json);

    // 2. 严谨修复：手动将 JSONObject 中的属性读取到目标对象中
    // 使用 JSONReader.of(json) 配合目标实例，这是 FastJSON2 最核心的注入逻辑
    JSONReader reader = JSONReader.of(json);
    reader.readObject(destination, JSONReader.Feature.FieldBased);
  }

  /**
   * 对象转换
   * 修复：使用 parseObject(String, Class) 这种最经典稳定的 API
   */
  public static <S, D> D convert(S source, Class<D> destinationClass) {
    if (source == null) return null;
    if (source.getClass() == destinationClass) return (D) source;

    // 先转为 JSON 字符串再解析，虽然慢，但 API 兼容性最强
    String json = JSON.toJSONString(source, JSONWriter.Feature.ReferenceDetection);
    return JSON.parseObject(json, destinationClass);
  }

  /**
   * 批量转换
   */
  public static <S, D> List<D> converts(Iterable<S> sourceList, Class<D> destinationClass) {
    if (sourceList == null) return Collections.emptyList();
    List<D> result = (sourceList instanceof Collection<?> c) ? new ArrayList<>(c.size()) : new ArrayList<>();
    for (S s : sourceList) {
      result.add(convert(s, destinationClass));
    }
    return result;
  }

  public static class CopyOptions {
    public static final CopyOptions SHALLOW = new Builder().build();
    public static final CopyOptions DEEP = new Builder().deepCopy(true).build();

    final boolean deepCopy;
    final Collection<String> includes, excludes;

    private CopyOptions(boolean d, Collection<String> in, Collection<String> ex) {
      this.deepCopy = d;
      this.includes = in;
      this.excludes = ex;
    }

    public static class Builder {
      private boolean d;
      private List<String> in, ex;

      public Builder deepCopy(boolean v) {
        d = v;
        return this;
      }

      public Builder include(String... f) {
        in = List.of(f);
        return this;
      }

      public Builder exclude(String... f) {
        ex = List.of(f);
        return this;
      }

      public CopyOptions build() {
        return new CopyOptions(d, in, ex);
      }
    }
  }
}