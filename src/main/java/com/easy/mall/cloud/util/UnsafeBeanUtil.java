package com.easy.mall.cloud.util;

import sun.misc.Unsafe;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业级 Unsafe Bean 工具类 (2026 修复增强版)
 * 修复了：原生类型兼容性、内存可见性屏障、集合兼容逻辑、以及被遗漏的便捷 API
 */
public final class UnsafeBeanUtil {

  private static final Unsafe UNSAFE;
  private static final Map<Class<?>, ClassMetadata> METADATA_CACHE = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_MAP = new IdentityHashMap<>();
  private static final Set<Class<?>> IMMUTABLE_TYPES = Collections.newSetFromMap(new IdentityHashMap<>());

  static {
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      UNSAFE = (Unsafe) f.get(null);

      // 初始化基本类型映射
      PRIMITIVE_WRAPPER_MAP.put(int.class, Integer.class);
      PRIMITIVE_WRAPPER_MAP.put(long.class, Long.class);
      PRIMITIVE_WRAPPER_MAP.put(double.class, Double.class);
      PRIMITIVE_WRAPPER_MAP.put(float.class, Float.class);
      PRIMITIVE_WRAPPER_MAP.put(boolean.class, Boolean.class);
      PRIMITIVE_WRAPPER_MAP.put(char.class, Character.class);
      PRIMITIVE_WRAPPER_MAP.put(byte.class, Byte.class);
      PRIMITIVE_WRAPPER_MAP.put(short.class, Short.class);

      // 初始化不可变类型集合
      IMMUTABLE_TYPES.addAll(PRIMITIVE_WRAPPER_MAP.values());
      IMMUTABLE_TYPES.addAll(Arrays.asList(
          String.class, BigDecimal.class, BigInteger.class,
          Class.class, java.time.LocalDate.class, java.time.LocalDateTime.class,
          java.time.ZonedDateTime.class, java.time.Instant.class, java.time.Duration.class
      ));
    } catch (Exception e) {
      throw new ExceptionInInitializerError("Unsafe 初始化失败: " + e.getMessage());
    }
  }

  private UnsafeBeanUtil() {
  }

  // --- 核心业务 API (补全版) ---

  /**
   * 浅拷贝：将 source 的字段值复制到 destination 中
   */
  public static <S, D> void copy(S source, D destination) {
    copy(source, destination, CopyOptions.SHALLOW);
  }

  /**
   * 深拷贝：递归克隆 source 的字段到 destination
   */
  public static <S, D> void deepCopy(S source, D destination) {
    copy(source, destination, CopyOptions.DEEP);
  }

  /**
   * 对象转换：实例化目标类并进行浅拷贝
   */
  public static <S, D> D convert(S source, Class<D> destClass) {
    if (source == null) return null;
    D dest = createInstance(destClass);
    copy(source, dest, CopyOptions.SHALLOW);
    return dest;
  }

  /**
   * 批量转换
   */
  public static <S, D> List<D> converts(Iterable<S> sourceList, Class<D> destClass) {
    if (sourceList == null) return Collections.emptyList();
    List<D> result = (sourceList instanceof Collection<?> c) ? new ArrayList<>(c.size()) : new ArrayList<>();
    for (S s : sourceList) result.add(convert(s, destClass));
    return result;
  }

  /**
   * 深度克隆：生成一个完全独立的副本
   */
  public static <T> T deepClone(T source) {
    if (source == null) return null;
    return (T) deepCopyInternal(source, new IdentityHashMap<>());
  }

  /**
   * 核心拷贝逻辑：支持 CopyOptions 配置
   */
  public static void copy(Object source, Object dest, CopyOptions options) {
    if (source == null || dest == null) return;

    ClassMetadata srcMeta = getMetadata(source.getClass());
    ClassMetadata destMeta = getMetadata(dest.getClass());
    IdentityHashMap<Object, Object> seen = options.deepCopy ? new IdentityHashMap<>() : null;

    if (seen != null) seen.put(source, dest);

    for (FieldOffset srcFo : srcMeta.offsets) {
      if (!options.shouldCopy(srcFo.name)) continue;

      FieldOffset destFo = destMeta.nameToOffset.get(srcFo.name);
      // 修复：使用更严谨的 isAssignable 进行类型兼容性检查
      if (destFo != null && isAssignable(srcFo.type, destFo.type)) {
        Object value = getValue(source, srcFo);
        if (value == null) {
          if (!options.ignoreNulls) setValue(dest, destFo, null);
        } else {
          Object valToSet = options.deepCopy ? deepCopyInternal(value, seen) : value;
          setValue(dest, destFo, valToSet);
        }
      }
    }
    // 关键修复：加入内存屏障，确保在多线程环境下写入对其他线程立即可见
    UNSAFE.storeFence();
  }

  // --- 内部实现逻辑 ---

  private static Object deepCopyInternal(Object value, IdentityHashMap<Object, Object> seen) {
    if (value == null || isImmutable(value.getClass())) return value;

    Object existed = seen.get(value);
    if (existed != null) return existed;

    Class<?> clazz = value.getClass();

    if (clazz.isArray()) {
      int len = Array.getLength(value);
      Object copy = Array.newInstance(clazz.getComponentType(), len);
      seen.put(value, copy);
      for (int i = 0; i < len; i++) {
        Array.set(copy, i, deepCopyInternal(Array.get(value, i), seen));
      }
      return copy;
    }

    if (clazz.isRecord()) {
      return deepCopyRecord(value, seen);
    }

    if (value instanceof Collection<?> col) {
      Collection<Object> copy = createSafeCollection(value, col.size());
      seen.put(value, copy);
      for (Object o : col) copy.add(deepCopyInternal(o, seen));
      return copy;
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> copy = createSafeMap(value, map.size());
      seen.put(value, copy);
      map.forEach((k, v) -> copy.put(deepCopyInternal(k, seen), deepCopyInternal(v, seen)));
      return copy;
    }

    Object target = createInstance(clazz);
    seen.put(value, target);
    ClassMetadata meta = getMetadata(clazz);
    for (FieldOffset fo : meta.offsets) {
      Object fVal = getValue(value, fo);
      if (fVal != null) {
        setValue(target, fo, deepCopyInternal(fVal, seen));
      }
    }
    return target;
  }

  private static Object deepCopyRecord(Object source, IdentityHashMap<Object, Object> seen) {
    Class<?> clazz = source.getClass();
    RecordComponent[] components = clazz.getRecordComponents();
    Object[] args = new Object[components.length];
    Class<?>[] argTypes = new Class<?>[components.length];

    ClassMetadata meta = getMetadata(clazz);
    for (int i = 0; i < components.length; i++) {
      argTypes[i] = components[i].getType();
      FieldOffset fo = meta.nameToOffset.get(components[i].getName());
      args[i] = deepCopyInternal(getValue(source, fo), seen);
    }

    try {
      Constructor<?> ctor = clazz.getDeclaredConstructor(argTypes);
      ctor.setAccessible(true);
      Object dest = ctor.newInstance(args);
      seen.put(source, dest);
      return dest;
    } catch (Exception e) {
      throw new RuntimeException("Record 深拷贝失败: " + clazz.getName(), e);
    }
  }

  private static Object getValue(Object obj, FieldOffset fo) {
    if (fo.accessor != null) {
      try {
        return fo.accessor.invoke(obj);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    if (!fo.isPrimitive) return UNSAFE.getObject(obj, fo.offset);
    if (fo.type == int.class) return UNSAFE.getInt(obj, fo.offset);
    if (fo.type == long.class) return UNSAFE.getLong(obj, fo.offset);
    if (fo.type == boolean.class) return UNSAFE.getBoolean(obj, fo.offset);
    if (fo.type == double.class) return UNSAFE.getDouble(obj, fo.offset);
    if (fo.type == float.class) return UNSAFE.getFloat(obj, fo.offset);
    if (fo.type == byte.class) return UNSAFE.getByte(obj, fo.offset);
    if (fo.type == char.class) return UNSAFE.getChar(obj, fo.offset);
    if (fo.type == short.class) return UNSAFE.getShort(obj, fo.offset);
    return null;
  }

  private static void setValue(Object obj, FieldOffset fo, Object val) {
    if (!fo.isPrimitive) {
      UNSAFE.putObject(obj, fo.offset, val);
      return;
    }
    if (val == null) return;
    if (fo.type == int.class) UNSAFE.putInt(obj, fo.offset, ((Number) val).intValue());
    else if (fo.type == long.class) UNSAFE.putLong(obj, fo.offset, ((Number) val).longValue());
    else if (fo.type == boolean.class) UNSAFE.putBoolean(obj, fo.offset, (boolean) val);
    else if (fo.type == double.class) UNSAFE.putDouble(obj, fo.offset, ((Number) val).doubleValue());
    else if (fo.type == float.class) UNSAFE.putFloat(obj, fo.offset, ((Number) val).floatValue());
    else if (fo.type == byte.class) UNSAFE.putByte(obj, fo.offset, ((Number) val).byteValue());
    else if (fo.type == char.class) UNSAFE.putChar(obj, fo.offset, (char) val);
    else if (fo.type == short.class) UNSAFE.putShort(obj, fo.offset, ((Number) val).shortValue());
  }

  private static ClassMetadata getMetadata(Class<?> clazz) {
    return METADATA_CACHE.computeIfAbsent(clazz, k -> {
      List<FieldOffset> list = new ArrayList<>();
      Map<String, FieldOffset> map = new HashMap<>();

      if (k.isRecord()) {
        for (RecordComponent rc : k.getRecordComponents()) {
          Method accessor = rc.getAccessor();
          accessor.setAccessible(true);
          FieldOffset fo = new FieldOffset(rc.getName(), -1, rc.getType(), rc.getType().isPrimitive(), accessor);
          list.add(fo);
          map.put(rc.getName(), fo);
        }
      } else {
        for (Class<?> curr = k; curr != null && curr != Object.class; curr = curr.getSuperclass()) {
          for (Field f : curr.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod)) continue;
            try {
              long offset = UNSAFE.objectFieldOffset(f);
              FieldOffset fo = new FieldOffset(f.getName(), offset, f.getType(), f.getType().isPrimitive(), null);
              list.add(fo);
              map.putIfAbsent(f.getName(), fo);
            } catch (Exception ignored) {
            }
          }
        }
      }
      return new ClassMetadata(list.toArray(new FieldOffset[0]), Collections.unmodifiableMap(map));
    });
  }

  private static boolean isAssignable(Class<?> src, Class<?> dst) {
    if (dst.isAssignableFrom(src)) return true;
    Class<?> sW = PRIMITIVE_WRAPPER_MAP.getOrDefault(src, src);
    Class<?> dW = PRIMITIVE_WRAPPER_MAP.getOrDefault(dst, dst);
    return sW.equals(dW);
  }

  private static boolean isImmutable(Class<?> c) {
    return c.isEnum() || c.isPrimitive() || IMMUTABLE_TYPES.contains(c) || c.getPackageName().startsWith("java.time");
  }

  private static <T> T createInstance(Class<T> clazz) {
    try {
      return (T) UNSAFE.allocateInstance(clazz);
    } catch (Exception e) {
      throw new RuntimeException("实例化失败: " + clazz, e);
    }
  }

  private static Collection<Object> createSafeCollection(Object source, int size) {
    if (source instanceof Set) return new LinkedHashSet<>(size);
    if (source instanceof Deque) return new ArrayDeque<>(size);
    return new ArrayList<>(size);
  }

  private static Map<Object, Object> createSafeMap(Object source, int size) {
    if (source instanceof SortedMap) return new TreeMap<>();
    return new LinkedHashMap<>(size);
  }

  // --- 内部数据结构 ---

  private record FieldOffset(String name, long offset, Class<?> type, boolean isPrimitive, Method accessor) {
  }

  private record ClassMetadata(FieldOffset[] offsets, Map<String, FieldOffset> nameToOffset) {
  }

  public static class CopyOptions {
    public static final CopyOptions SHALLOW = new Builder().build();
    public static final CopyOptions DEEP = new Builder().deepCopy(true).build();

    final boolean deepCopy, ignoreNulls;
    final Set<String> includes, excludes;

    private CopyOptions(boolean d, boolean i, Set<String> in, Set<String> ex) {
      this.deepCopy = d;
      this.ignoreNulls = i;
      this.includes = in;
      this.excludes = ex;
    }

    public boolean shouldCopy(String f) {
      if (excludes != null && excludes.contains(f)) return false;
      return includes == null || includes.isEmpty() || includes.contains(f);
    }

    public static class Builder {
      private boolean d, i;
      private Set<String> in, ex;

      public Builder deepCopy(boolean v) {
        d = v;
        return this;
      }

      public Builder ignoreNulls(boolean v) {
        i = v;
        return this;
      }

      public Builder include(String... f) {
        in = new HashSet<>(Arrays.asList(f));
        return this;
      }

      public Builder exclude(String... f) {
        ex = new HashSet<>(Arrays.asList(f));
        return this;
      }

      public CopyOptions build() {
        return new CopyOptions(d, i, in, ex);
      }
    }
  }
}