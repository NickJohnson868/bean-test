package com.easy.mall.cloud.util;

import sun.misc.Unsafe;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业级 Unsafe Bean 工具类 (2026 最终严谨版)
 * 修复：针对 JDK 内部不可变集合 (List.of, Collections.unmodifiableXXX) 的深拷贝支持
 */
public final class UnsafeBeanUtil {

  private static final Unsafe UNSAFE;
  private static final Map<Class<?>, ClassMetadata> METADATA_CACHE = new ConcurrentHashMap<>();
  private static final Set<Class<?>> IMMUTABLE_TYPES = Collections.newSetFromMap(new IdentityHashMap<>());

  static {
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      UNSAFE = (Unsafe) f.get(null);

      IMMUTABLE_TYPES.addAll(Arrays.asList(
          String.class, Integer.class, Long.class, Double.class, Float.class,
          Boolean.class, Character.class, Byte.class, Short.class,
          java.math.BigDecimal.class, java.math.BigInteger.class,
          Class.class, java.time.LocalDate.class, java.time.LocalDateTime.class,
          java.time.ZonedDateTime.class, java.time.Duration.class, java.time.Instant.class
      ));
    } catch (Exception e) {
      throw new ExceptionInInitializerError("Unsafe 初始化关键错误: " + e.getMessage());
    }
  }

  private UnsafeBeanUtil() {
  }

  public static <S, D> List<D> converts(Iterable<S> sourceList, Class<D> destClass) {
    if (sourceList == null) return Collections.emptyList();
    List<D> result = (sourceList instanceof Collection<?> c) ? new ArrayList<>(c.size()) : new ArrayList<>();
    for (S s : sourceList) {
      result.add(convert(s, destClass));
    }
    return result;
  }

  public static <S, D> D convert(S source, Class<D> destClass) {
    if (source == null) return null;
    D dest = createInstance(destClass);
    copy(source, dest, CopyOptions.SHALLOW);
    return dest;
  }

  public static <T> T deepClone(T source) {
    if (source == null) return null;
    return (T) deepCopyInternal(source, new IdentityHashMap<>());
  }

  public static <S, D> void copy(S source, D destination) {
    copy(source, destination, CopyOptions.SHALLOW);
  }

  public static <S, D> void deepCopy(S source, D destination) {
    copy(source, destination, CopyOptions.DEEP);
  }

  public static void copy(Object source, Object dest, CopyOptions options) {
    if (source == null || dest == null) return;
    ClassMetadata srcMeta = getMetadata(source.getClass());
    ClassMetadata destMeta = getMetadata(dest.getClass());

    IdentityHashMap<Object, Object> seen = options.deepCopy ? new IdentityHashMap<>() : null;
    if (seen != null) seen.put(source, dest);

    for (FieldOffset srcFo : srcMeta.offsets) {
      if (!options.shouldCopy(srcFo.name)) continue;
      FieldOffset destFo = destMeta.nameToOffset.get(srcFo.name);
      if (destFo != null && isStrictlyCompatible(srcFo.type, destFo.type)) {
        Object value = getValue(source, srcFo);
        if (value == null) {
          if (!options.ignoreNulls) setValue(dest, destFo, null);
        } else {
          Object valToSet = options.deepCopy ? deepCopyInternal(value, seen) : value;
          setValue(dest, destFo, valToSet);
        }
      }
    }
  }

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
      // 严谨点：不再尝试使用 Unsafe 克隆集合类本身，而是强制创建可变的容器
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
      setValue(target, fo, deepCopyInternal(getValue(value, fo), seen));
    }
    return target;
  }

  private static Object getValue(Object obj, FieldOffset fo) {
    if (fo.accessor != null) {
      try {
        return fo.accessor.invoke(obj);
      } catch (Exception e) {
        throw new RuntimeException("读取字段失败: " + fo.name, e);
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

  private static <T> T createInstance(Class<T> clazz) {
    try {
      return (T) UNSAFE.allocateInstance(clazz);
    } catch (Exception e) {
      throw new RuntimeException("实例化失败: " + clazz.getName(), e);
    }
  }

  private static Object deepCopyRecord(Object source, IdentityHashMap<Object, Object> seen) {
    try {
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
      Constructor<?> ctor = clazz.getDeclaredConstructor(argTypes);
      ctor.setAccessible(true);
      Object dest = ctor.newInstance(args);
      seen.put(source, dest);
      return dest;
    } catch (Exception e) {
      throw new RuntimeException("Record 深拷贝失败", e);
    }
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
            if (Modifier.isStatic(f.getModifiers())) continue;
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

  private static boolean isImmutable(Class<?> c) {
    return c.isEnum() || IMMUTABLE_TYPES.contains(c) || c.isPrimitive();
  }

  private static boolean isStrictlyCompatible(Class<?> s, Class<?> d) {
    return d.isAssignableFrom(s) || s == d;
  }

  // 严谨处理集合：避免实例化 JDK 内部不可变集合类
  private static Collection<Object> createSafeCollection(Object source, int size) {
    Class<?> t = source.getClass();
    String className = t.getName();
    // 如果是 JDK 内部不可变集合或接口/抽象类，使用标准实现
    if (className.contains("ImmutableCollections") || className.contains("Collections$")
        || Modifier.isAbstract(t.getModifiers()) || t.isInterface()) {
      return (source instanceof Set) ? new LinkedHashSet<>(size) : new ArrayList<>(size);
    }
    return createInstance((Class<Collection<Object>>) t);
  }

  private static Map<Object, Object> createSafeMap(Object source, int size) {
    Class<?> t = source.getClass();
    String className = t.getName();
    if (className.contains("ImmutableCollections") || className.contains("Collections$")
        || Modifier.isAbstract(t.getModifiers()) || t.isInterface()) {
      return (source instanceof SortedMap) ? new TreeMap<>() : new LinkedHashMap<>(size);
    }
    return createInstance((Class<Map<Object, Object>>) t);
  }

  private record FieldOffset(String name, long offset, Class<?> type, boolean isPrimitive, Method accessor) {
  }

  private static class ClassMetadata {
    final FieldOffset[] offsets;
    final Map<String, FieldOffset> nameToOffset;

    ClassMetadata(FieldOffset[] offsets, Map<String, FieldOffset> nameToOffset) {
      this.offsets = offsets;
      this.nameToOffset = nameToOffset;
    }
  }

  public static class CopyOptions {
    public static final CopyOptions SHALLOW = new CopyOptions(false, false);
    public static final CopyOptions DEEP = new CopyOptions(true, false);
    final boolean deepCopy, ignoreNulls;

    public CopyOptions(boolean d, boolean i) {
      this.deepCopy = d;
      this.ignoreNulls = i;
    }

    public boolean shouldCopy(String f) {
      return true;
    }
  }
}