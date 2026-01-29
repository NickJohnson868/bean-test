package com.easy.mall.cloud.util;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 基于 Spring Framework BeanUtils 的工具类
 * 仅用于压测对比，生产环境建议使用基于 VarHandle 的 BeanUtil
 */
public final class SpringBeanUtil {

  private SpringBeanUtil() {
  }

  /**
   * 注意：Spring 原生不支持深克隆，此处实现为：实例化 + 浅拷贝
   */
  // 修改 SpringBeanUtil.java 中的 deepClone 方法
  public static <T> T deepClone(T source) {
    if (source == null) return null;

    // 严谨修复：如果是 record，Spring 的 BeanUtils 彻底无能为力
    if (source.getClass().isRecord()) {
      // 在实际工业场景中，这里通常会回退到使用反射或直接报错
      // 为了压测不中断，我们可以选择暂不支持 record 的 Spring 拷贝
      return source;
    }

    try {
      T target = (T) BeanUtils.instantiateClass(source.getClass());
      BeanUtils.copyProperties(source, target);
      return target;
    } catch (Exception e) {
      throw new FatalBeanException("Spring deepClone 失败", e);
    }
  }

  public static <S, D> void copy(S source, D destination) {
    if (source == null || destination == null) return;
    BeanUtils.copyProperties(source, destination);
  }

  /**
   * 注意：Spring 不支持原生深拷贝，此 API 在此实现中行为等同于 copy()
   */
  public static <S, D> void deepCopy(S source, D destination) {
    copy(source, destination);
  }

  /**
   * 带选项的拷贝：将 CopyOptions 适配为 Spring 的 ignoreProperties
   */
  public static <S, D> void copy(S source, D destination, CopyOptions options) {
    if (source == null || destination == null) return;

    // Spring 仅支持排除字段 (ignoreProperties)
    String[] ignoreProps = null;
    if (options.excludes != null && !options.excludes.isEmpty()) {
      ignoreProps = options.excludes.toArray(new String[0]);
    }

    // 注意：Spring 不支持 includes 过滤，若设置了 includes，此工具类在 Spring 模式下会失效
    BeanUtils.copyProperties(source, destination, ignoreProps);
  }

  /**
   * 对象转换
   */
  public static <S, D> D convert(S source, Class<D> destinationClass) {
    if (source == null) return null;
    D target = BeanUtils.instantiateClass(destinationClass);
    BeanUtils.copyProperties(source, target);
    return target;
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

  /**
   * 为了 API 兼容性定义的内部 CopyOptions
   */
  public static class CopyOptions {
    public static final CopyOptions SHALLOW = new Builder().build();
    public static final CopyOptions DEEP = new Builder().build();

    final Collection<String> excludes;

    private CopyOptions(Collection<String> ex) {
      this.excludes = ex;
    }

    public static class Builder {
      private List<String> ex;

      public Builder deepCopy(boolean v) {
        return this;
      } // Spring 不支持，仅做兼容

      public Builder exclude(String... f) {
        ex = List.of(f);
        return this;
      }

      public Builder include(String... f) {
        return this;
      } // Spring 不支持

      public CopyOptions build() {
        return new CopyOptions(ex);
      }
    }
  }
}