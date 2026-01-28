package com.easy.mall.cloud.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 基于 JDK 21 的高性能 Bean 工具类
 */
public final class BeanUtil {

  private static final Map<Class<?>, ConstructorAccess> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();
  private static final Map<ClassPair, List<BiConsumer<Object, Object>>> FAST_COPY_CACHE = new ConcurrentHashMap<>();
  private static final Map<ClassPair, List<VarHandleCopier>> ROBUST_COPY_CACHE = new ConcurrentHashMap<>();
  private static final Map<Class<?>, FieldAccess[]> CLASS_ACCESS_CACHE = new ConcurrentHashMap<>();

  private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_MAP = new HashMap<>();
  private static final Set<Class<?>> WRAPPER_TYPES = new HashSet<>();

  static {
    PRIMITIVE_WRAPPER_MAP.put(int.class, Integer.class);
    PRIMITIVE_WRAPPER_MAP.put(long.class, Long.class);
    PRIMITIVE_WRAPPER_MAP.put(double.class, Double.class);
    PRIMITIVE_WRAPPER_MAP.put(float.class, Float.class);
    PRIMITIVE_WRAPPER_MAP.put(boolean.class, Boolean.class);
    PRIMITIVE_WRAPPER_MAP.put(char.class, Character.class);
    PRIMITIVE_WRAPPER_MAP.put(byte.class, Byte.class);
    PRIMITIVE_WRAPPER_MAP.put(short.class, Short.class);
    WRAPPER_TYPES.addAll(PRIMITIVE_WRAPPER_MAP.values());
    WRAPPER_TYPES.addAll(Set.of(String.class, BigDecimal.class, BigInteger.class, Class.class));
  }

  @SuppressWarnings("unchecked")
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

  public static <S, D> void copy(S source, D destination, CopyOptions options) {
    if (source == null || destination == null) return;
    if (isSimpleCopy(options)) {
      List<BiConsumer<Object, Object>> chain = getFastCopyChain(source.getClass(), destination.getClass());
      for (BiConsumer<Object, Object> task : chain) task.accept(source, destination);
    } else {
      copyRobust(source, destination, options);
    }
  }

  public static <S, D> D convert(S source, Class<D> destinationClass) {
    if (source == null) return null;
    D destination = newInstance(destinationClass);
    copy(source, destination);
    return destination;
  }

  public static <S, D> List<D> converts(Iterable<S> sourceList, Class<D> destinationClass) {
    if (sourceList == null) return Collections.emptyList();
    List<D> result = (sourceList instanceof Collection<?> c) ? new ArrayList<>(c.size()) : new ArrayList<>();
    for (S s : sourceList) result.add(convert(s, destinationClass));
    return result;
  }

  private static void copyRobust(Object source, Object destination, CopyOptions options) {
    List<VarHandleCopier> copiers = getRobustCopiers(source.getClass(), destination.getClass());
    IdentityHashMap<Object, Object> seen = options.deepCopy ? new IdentityHashMap<>() : null;
    if (seen != null) seen.put(source, destination);
    for (VarHandleCopier copier : copiers) {
      if (!options.shouldCopy(copier.name)) continue;
      Object val = copier.sourceHandle.get(source);
      if (val == null) {
        if (!options.ignoreNulls) copier.destHandle.set(destination, null);
      } else {
        Object toSet = options.deepCopy ? deepCopyInternal(val, seen) : val;
        copier.destHandle.set(destination, toSet);
      }
    }
  }

  private static List<VarHandleCopier> getRobustCopiers(Class<?> srcClass, Class<?> dstClass) {
    return ROBUST_COPY_CACHE.computeIfAbsent(new ClassPair(srcClass, dstClass), i -> {
      List<VarHandleCopier> plan = new ArrayList<>();
      Map<String, FieldAccess> dstMap = getAccessMap(dstClass);
      for (FieldAccess srcAcc : getAccessorsCached(srcClass)) {
        FieldAccess dstAcc = dstMap.get(srcAcc.name);
        if (dstAcc != null && isAssignable(srcAcc.type, dstAcc.type)) {
          plan.add(new VarHandleCopier(srcAcc.name, srcAcc.handle, dstAcc.handle));
        }
      }
      return plan;
    });
  }

  private static Object deepCopyInternal(Object value, IdentityHashMap<Object, Object> seen) {
    if (value == null || isEffectivelyImmutable(value.getClass())) return value;
    if (value instanceof Date d) return d.clone();
    Object existed = seen.get(value);
    if (existed != null) return existed;

    Class<?> clazz = value.getClass();
    if (clazz.isArray()) {
      int len = Array.getLength(value);
      Object copy = Array.newInstance(clazz.getComponentType(), len);
      seen.put(value, copy);
      for (int i = 0; i < len; i++) Array.set(copy, i, deepCopyInternal(Array.get(value, i), seen));
      return copy;
    }
    if (value instanceof Collection<?> col) {
      Collection<Object> copy = createCollectionInstance(clazz, col.size());
      seen.put(value, copy);
      for (Object o : col) copy.add(deepCopyInternal(o, seen));
      return copy;
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> copy = createMapInstance(clazz, map.size());
      seen.put(value, copy);
      map.forEach((k, v) -> copy.put(deepCopyInternal(k, seen), deepCopyInternal(v, seen)));
      return copy;
    }
    if (clazz.isRecord()) {
      RecordComponent[] components = clazz.getRecordComponents();
      Object[] args = new Object[components.length];
      for (int i = 0; i < components.length; i++) {
        try {
          args[i] = deepCopyInternal(components[i].getAccessor().invoke(value), seen);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      Object copy = newRecordInstance(clazz, args);
      seen.put(value, copy);
      return copy;
    }
    Object target = newInstance(clazz);
    seen.put(value, target);
    for (FieldAccess acc : getAccessorsCached(clazz)) {
      Object fVal = acc.handle.get(value);
      if (fVal != null) acc.handle.set(target, deepCopyInternal(fVal, seen));
    }
    return target;
  }

  private static List<BiConsumer<Object, Object>> getFastCopyChain(Class<?> srcClass, Class<?> dstClass) {
    return FAST_COPY_CACHE.computeIfAbsent(new ClassPair(srcClass, dstClass), i -> {
      List<BiConsumer<Object, Object>> chain = new ArrayList<>();
      Map<String, FieldAccess> dstMap = getAccessMap(dstClass);
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      for (FieldAccess srcAcc : getAccessorsCached(srcClass)) {
        FieldAccess dstAcc = dstMap.get(srcAcc.name);
        if (dstAcc != null && isAssignable(srcAcc.type, dstAcc.type)) {
          try {
            MethodHandle getter = lookup.unreflectGetter(srcAcc.field);
            MethodHandle setter = lookup.unreflectSetter(dstAcc.field);
            MethodHandle pipe = MethodHandles.filterArguments(setter, 1, getter)
                .asType(MethodType.methodType(void.class, Object.class, Object.class));
            chain.add((s, d) -> {
              try {
                pipe.invokeExact(d, s);
              } catch (Throwable e) {
                throw new RuntimeException(e);
              }
            });
          } catch (Exception ignored) {
          }
        }
      }
      return chain;
    });
  }

  private static FieldAccess[] getAccessorsCached(Class<?> clazz) {
    return CLASS_ACCESS_CACHE.computeIfAbsent(clazz, c -> {
      List<FieldAccess> list = new ArrayList<>();
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      for (Class<?> curr = c; curr != null && curr != Object.class; curr = curr.getSuperclass()) {
        for (Field f : curr.getDeclaredFields()) {
          int mod = f.getModifiers();
          if (!Modifier.isStatic(mod) && !Modifier.isFinal(mod)) {
            try {
              f.setAccessible(true);
              VarHandle vh = MethodHandles.privateLookupIn(curr, lookup).unreflectVarHandle(f);
              list.add(new FieldAccess(f.getName(), vh, f.getType(), f));
            } catch (Exception ignored) {
            }
          }
        }
      }
      return list.toArray(new FieldAccess[0]);
    });
  }

  private static boolean isSimpleCopy(CopyOptions options) {
    return !options.deepCopy && !options.ignoreNulls && options.includes == null && options.excludes == null;
  }

  private static Map<String, FieldAccess> getAccessMap(Class<?> clazz) {
    Map<String, FieldAccess> map = new HashMap<>();
    for (FieldAccess acc : getAccessorsCached(clazz)) map.put(acc.name, acc);
    return map;
  }

  @SuppressWarnings("unchecked")
  private static <T> T newInstance(Class<T> clazz) {
    if (clazz.getName().startsWith("java.util.ImmutableCollections")) return null;
    ConstructorAccess access = CONSTRUCTOR_CACHE.computeIfAbsent(clazz, c -> {
      try {
        MethodHandle mh = MethodHandles.privateLookupIn(c, MethodHandles.lookup()).findConstructor(c, MethodType.methodType(void.class));
        return new ConstructorAccess(mh, null);
      } catch (Exception e) {
        return new ConstructorAccess(null, null);
      }
    });
    if (access.mh == null) throw new RuntimeException("No default constructor: " + clazz.getName());
    try {
      return (T) access.mh.invoke();
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private static Object newRecordInstance(Class<?> clazz, Object[] args) {
    ConstructorAccess access = CONSTRUCTOR_CACHE.computeIfAbsent(clazz, c -> {
      try {
        Class<?>[] argTypes = Arrays.stream(c.getRecordComponents()).map(RecordComponent::getType).toArray(Class[]::new);
        MethodHandle mh = MethodHandles.lookup().findConstructor(c, MethodType.methodType(void.class, argTypes));
        return new ConstructorAccess(mh, argTypes);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    try {
      return access.mh.invokeWithArguments(args);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <C extends Collection<Object>> C createCollectionInstance(Class<?> type, int size) {
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.getName().startsWith("java.util.")) {
      return (C) (Set.class.isAssignableFrom(type) ? new LinkedHashSet<>(size) : new ArrayList<>(size));
    }
    try {
      return (C) newInstance(type);
    } catch (Exception e) {
      return (C) (Set.class.isAssignableFrom(type) ? new LinkedHashSet<>(size) : new ArrayList<>(size));
    }
  }

  @SuppressWarnings("unchecked")
  private static <M extends Map<Object, Object>> M createMapInstance(Class<?> type, int size) {
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.getName().startsWith("java.util.")) {
      return (M) (SortedMap.class.isAssignableFrom(type) ? new TreeMap<>() : new LinkedHashMap<>(size));
    }
    try {
      return (M) newInstance(type);
    } catch (Exception e) {
      return (M) new LinkedHashMap<>(size);
    }
  }

  private static boolean isAssignable(Class<?> src, Class<?> dst) {
    if (dst.isAssignableFrom(src)) return true;
    Class<?> sW = PRIMITIVE_WRAPPER_MAP.getOrDefault(src, src);
    Class<?> dW = PRIMITIVE_WRAPPER_MAP.getOrDefault(dst, dst);
    return sW.equals(dW);
  }

  private static boolean isEffectivelyImmutable(Class<?> c) {
    return WRAPPER_TYPES.contains(c) || c.isEnum() || c.getPackageName().startsWith("java.time");
  }

  private record FieldAccess(String name, VarHandle handle, Class<?> type, Field field) {
  }

  private record ConstructorAccess(MethodHandle mh, Class<?>[] argTypes) {
  }

  private record VarHandleCopier(String name, VarHandle sourceHandle, VarHandle destHandle) {
  }

  private record ClassPair(Class<?> src, Class<?> dst) {
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

    boolean shouldCopy(String f) {
      if (excludes != null && excludes.contains(f)) return false;
      return includes == null || includes.isEmpty() || includes.contains(f);
    }

    public static class Builder {
      private final Set<String> in = new HashSet<>();
      private final Set<String> ex = new HashSet<>();
      private boolean d, i;

      public Builder deepCopy(boolean v) {
        d = v;
        return this;
      }

      public Builder ignoreNulls(boolean v) {
        i = v;
        return this;
      }

      public Builder include(String... f) {
        Collections.addAll(in, f);
        return this;
      }

      public Builder exclude(String... f) {
        Collections.addAll(ex, f);
        return this;
      }

      public CopyOptions build() {
        return new CopyOptions(d, i, in.isEmpty() ? null : in, ex.isEmpty() ? null : ex);
      }
    }
  }
}