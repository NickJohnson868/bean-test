package com.easy.mall.cloud.util;

import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;

/**
 * ObjUtil - 核心通用工具类
 * 1. 零依赖：仅使用 JDK 21 原生能力。
 * 2. 极致性能：利用 JDK 21 Compact Strings 和 JVM Intrinsics 优化，避免产生临时对象。
 * 3. 编译器友好：方法短小精悍，利于 JIT (C2) 执行内联优化和逃逸分析。
 * 4. 零 Optional：拒绝使用 Optional 包装，直接操作原语和对象引用。
 */
public final class ObjUtil {

  // --- [ 常量区 ] ---
  private static final int PARALLEL_THRESHOLD = 100_000;

  public static final String SPACE = " ";
  public static final char SPACE_CHAR = ' ';
  public static final String COLON = ":";
  private static final Integer INT_TRUE = 1;
  private static final String STR_TRUE = "true";

  // 以1开头, 后面跟10位数
  public static final String MOBILE_REGEXP = "^1[3-9]\\d{9}$";
  // 自然数
  public static final String NATURAL_NUM = "^[1-9]\\d*$";
  // 字段名, 数字字母下划线
  public static final String FIELD_REGEXP = "([a-zA-Z0-9_]+)";
  // 由简单的字母数字拼接而成的字符串 不含有下划线, 大写字母
  public static final String SIMPLE_CHAR_REGEXP = "([a-z0-9]+)";
  // UPC码
  public static final String UPC_REGEXP = "^(\\d{13})$";
  // 邮箱正则
  public static final String EMAIL_REGEXP = "[\\w!#$%&'*+/=?^_`{|}~-]+(?:\\.[\\w!#$%&'*+/=?^_`{|}~-]+)*@(?:[\\w](?:[\\w-]*[\\w])?\\.)+[\\w](?:[\\w-]*[\\w])?";
  // http协议正则
  public static final String HTTP_PROTOCOL_REGEXP = "^((http[s]{0,1})://)";

  /**
   * 判断对象是否为 null
   */
  public static boolean isNull(Object obj) {
    return obj == null;
  }

  /**
   * 判断对象是否不为 null
   */
  public static boolean isNotNull(Object obj) {
    return obj != null;
  }

  /**
   * 只要参数中有一个为 null，即返回 true
   */
  public static boolean isAnyNull(Object... objs) {
    if (objs == null) return true;
    for (Object obj : objs) if (obj == null) return true;
    return false;
  }

  /**
   * 所有参数都为 null，才返回 true
   */
  public static boolean isAllNull(Object... objs) {
    if (objs == null) return true;
    for (Object obj : objs) if (obj != null) return false;
    return true;
  }

  /**
   * 只要参数中有一个不为 null，即返回 true
   */
  public static boolean isAnyNotNull(Object... objs) {
    if (objs == null) return false;
    for (Object obj : objs) if (obj != null) return true;
    return false;
  }

  /**
   * 所有参数都不为 null，才返回 true
   */
  public static boolean isAllNotNull(Object... objs) {
    if (objs == null) return false;
    for (Object obj : objs) if (obj == null) return false;
    return true;
  }

  /**
   * 判断字符串是否为 null、空串或仅含空格
   */
  public static boolean isBlank(String str) {
    return str == null || str.isBlank();
  }

  /**
   * 判断字符串是否不为 null 且包含非空格字符
   */
  public static boolean isNotBlank(String str) {
    return str != null && !str.isBlank();
  }

  public static boolean isAnyBlank(String... strs) {
    if (strs == null) return true;
    for (String str : strs) if (isBlank(str)) return true;
    return false;
  }

  public static boolean isAllBlank(String... strs) {
    if (strs == null) return true;
    for (String str : strs) if (isNotBlank(str)) return false;
    return true;
  }

  /**
   * 仅判断字符串是否为 null 或长度为 0 (不关注空格)
   */
  public static boolean isEmpty(String str) {
    return str == null || str.isEmpty();
  }

  /**
   * 仅判断字符串是否不为 null 且长度大于 0
   */
  public static boolean isNotEmpty(String str) {
    return str != null && !str.isEmpty();
  }

  public static boolean isEmpty(Collection<?> coll) {
    return coll == null || coll.isEmpty();
  }

  public static boolean isNotEmpty(Collection<?> coll) {
    return coll != null && !coll.isEmpty();
  }

  public static boolean isEmpty(Map<?, ?> map) {
    return map == null || map.isEmpty();
  }

  public static boolean isNotEmpty(Map<?, ?> map) {
    return map != null && !map.isEmpty();
  }

  public static boolean isEmpty(Object[] array) {
    return array == null || array.length == 0;
  }

  public static boolean isNotEmpty(Object[] array) {
    return array != null && array.length > 0;
  }

  /**
   * 统一判空逻辑
   */
  public static boolean isEmpty(Object obj) {
    if (obj == null) return true;
    return switch (obj) {
      case String s -> s.isEmpty();
      case Collection<?> c -> c.isEmpty();
      case Map<?, ?> m -> m.isEmpty();
      case Object[] a -> a.length == 0;
      default -> false;
    };
  }

  /**
   * 统一判非空逻辑
   */
  public static boolean isNotEmpty(Object obj) {
    return !isEmpty(obj);
  }

  /**
   * 判断一个数字是否为true（等于1就是true）
   */
  public static boolean isTrue(Integer num) {
    return INT_TRUE.equals(num);
  }

  public static boolean isTrue(Boolean bool) {
    return Boolean.TRUE.equals(bool);
  }

  public static boolean isTrue(String str) {
    return STR_TRUE.equalsIgnoreCase(str);
  }

  public static boolean isFalse(Integer num) {
    return !isTrue(num);
  }

  public static boolean isFalse(Boolean bool) {
    return !isTrue(bool);
  }

  public static boolean isFalse(String str) {
    return !isTrue(str);
  }

  /**
   * 是否是手机号
   *
   * @param value 输入值
   * @return 匹配结果
   */
  public static boolean isMobile(String value) {
    return isMatching(MOBILE_REGEXP, value);
  }

  /**
   * 是否为UPC
   */
  public static boolean isUpc(String value) {
    return isMatching(UPC_REGEXP, value);
  }

  /**
   * 是否符合字段规则
   *
   * @param value 输入值
   * @return 匹配结果
   */
  public static boolean isField(String value) {
    return isMatching(FIELD_REGEXP, value);
  }

  /**
   * 是否是邮箱
   *
   * @param value 输入值
   * @return 匹配结果
   */
  public static boolean isEmail(String value) {
    return isMatching(EMAIL_REGEXP, value);
  }

  /**
   * 是否是由简单的字母数字拼接而成的字符串
   *
   * @param value 输入值
   * @return 匹配结果
   */
  public static boolean isSimpleChar(String value) {
    return isMatching(SIMPLE_CHAR_REGEXP, value);
  }

  /**
   * 是否是HTTP协议
   *
   * @param value 输入值
   * @return 匹配结果
   */
  public static boolean isHttpProtocol(String value) {
    return isFind(HTTP_PROTOCOL_REGEXP, value);
  }

  public static boolean isMatching(String regexp, String value) {
    if (ObjUtil.isBlank(value)) return false;
    return Pattern.matches(regexp, value);
  }

  public static boolean isFind(String regexp, String value) {
    if (ObjUtil.isBlank(value)) return false;
    Pattern pattern = Pattern.compile(regexp);
    return pattern.matcher(value).find();
  }

  public static boolean isNaturalNumber(String str, boolean zero) {
    if (ObjUtil.isBlank(str)) return false;
    if (zero && str.equals("0")) return true;
    return str.matches(NATURAL_NUM);
  }

  public static boolean isNaturalNumber(String str) {
    return isNaturalNumber(str, true);
  }

  public static int size(Collection<?> collection) {
    return collection == null ? 0 : collection.size();
  }

  public static int size(Map<?, ?> map) {
    return map == null ? 0 : map.size();
  }

  public static int size(Object[] array) {
    return array == null ? 0 : array.length;
  }

  public static boolean equals(Object a, Object b) {
    return Objects.equals(a, b);
  }

  public static boolean notEquals(Object a, Object b) {
    return !Objects.equals(a, b);
  }

  public static <T, S> boolean anyNotEquals(Collection<T> collection, Function<? super T, ? extends S> mapper, S expected) {
    if (ObjUtil.isEmpty(collection)) return false;
    for (T t : collection) if (ObjUtil.notEquals(mapper.apply(t), expected)) return true;
    return false;
  }

  public static <T> T defaultIfNull(final T object, final T defaultValue) {
    return object == null ? defaultValue : object;
  }

  public static <T> List<T> emptyListIfNull(final List<T> list) {
    return list == null ? Collections.emptyList() : list;
  }

  public static <K, V> Map<K, V> emptyMapIfNull(final Map<K, V> map) {
    return map == null ? Collections.emptyMap() : map;
  }

  public static <T> Set<T> emptySetIfNull(final Set<T> set) {
    return set == null ? Collections.emptySet() : set;
  }

  public static String defaultIfBlank(final String object, final String defaultValue) {
    return (object == null || object.isBlank()) ? defaultValue : object;
  }

  /**
   * 移除前后空白字符，使用 JDK 11+ strip() 以支持 Unicode 空格
   */
  public static String trim(String str) {
    return (str == null) ? null : str.strip();
  }

  public static String trimToEmpty(String str) {
    return (str == null || str.isBlank()) ? "" : str.strip();
  }

  public static String trimToNull(String str) {
    if (str == null) return null;
    String stripped = str.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  public static String removePrefix(String str, String prefix) {
    if (str == null || prefix == null || !str.startsWith(prefix)) return str;
    return str.substring(prefix.length());
  }

  public static String removeSuffix(String str, String suffix) {
    if (str == null || suffix == null || !str.endsWith(suffix)) return str;
    return str.substring(0, str.length() - suffix.length());
  }

  public static String addPrefixIfNot(String str, String prefix) {
    if (str == null || prefix == null || str.startsWith(prefix)) return str;
    return prefix + str;
  }

  /**
   * 提取两个字符串之间的内容
   */
  public static String subBetween(String str, String before, String after) {
    if (str == null || before == null || after == null) return null;
    int start = str.indexOf(before);
    if (start == -1) return null;
    int end = str.indexOf(after, start + before.length());
    if (end == -1) return null;
    return str.substring(start + before.length(), end);
  }

  /**
   * 检查字符串中是否包含空格
   */
  public static boolean containsSpace(String str) {
    return str != null && str.indexOf(SPACE_CHAR) != -1;
  }

  /**
   * 检查字符串中是否包含指定字符
   */
  public static boolean contains(String str, char searchChar) {
    return str != null && str.indexOf(searchChar) != -1;
  }

  public static <T> List<T> emptyList() {
    return Collections.emptyList();
  }

  public static <K, V> Map<K, V> emptyMap() {
    return Collections.emptyMap();
  }

  /**
   * 通用集合转数组 (零反射实现)
   */
  public static <T> T[] toArray(Collection<T> collection, IntFunction<T[]> generator) {
    if (collection == null || collection.isEmpty()) return generator.apply(0);
    return collection.toArray(generator);
  }

  public static Object[] toArray(Iterable<?> iterable) {
    if (iterable == null) return new Object[0];
    // 如果本身就是 Collection，直接利用底层优化
    if (iterable instanceof Collection<?> coll) return coll.toArray();
    // 纯 Iterable，需要动态扩容中转
    List<Object> list = new ArrayList<>();
    for (Object obj : iterable) list.add(obj);
    return list.toArray();
  }

  /**
   * 集合交集判断。
   * 逻辑：Collections.disjoint 返回 true 表示“不相交”，取反即为“包含任意元素”。
   */
  public static boolean containsAny(Collection<?> coll1, Collection<?> coll2) {
    if (isEmpty(coll1) || isEmpty(coll2)) return false;
    return !Collections.disjoint(coll1, coll2);
  }

  public static boolean contains(Collection<?> collection, Object target) {
    if (collection == null || collection.isEmpty()) return false;
    return collection.contains(target);
  }

  public static <T> T getFirst(List<T> list) {
    if (isEmpty(list)) return null;
    return list.getFirst();
  }

  /**
   * 判断集合中是否有任意一个元素符合指定条件
   */
  public static <T> boolean anyMatch(Collection<T> collection, Predicate<? super T> predicate) {
    if (isEmpty(collection) || predicate == null) return false;
    for (T element : collection) if (predicate.test(element)) return true;
    return false;
  }

  /**
   * 判断集合中是否所有元素都符合指定条件
   * 注意：空集合将返回 true (符合数学中的空虚真理)
   */
  public static <T> boolean allMatch(Collection<T> collection, Predicate<? super T> predicate) {
    if (isEmpty(collection)) return true;
    if (predicate == null) return false;
    for (T element : collection) if (!predicate.test(element)) return false;
    return true;
  }

  /**
   * 判断集合中是否没有任何元素符合指定条件
   */
  public static <T> boolean noneMatch(Collection<T> collection, Predicate<? super T> predicate) {
    return !anyMatch(collection, predicate);
  }

  /**
   * 合并多个 List 为一个新的 ArrayList
   */
  @SafeVarargs
  public static <T> List<T> mergeLists(List<T>... lists) {
    if (ObjUtil.isEmpty(lists)) return new ArrayList<>(0);
    int totalSize = 0;
    for (List<T> list : lists) if (ObjUtil.isNotEmpty(list)) totalSize += list.size();
    List<T> result = new ArrayList<>(totalSize);
    for (List<T> list : lists) if (ObjUtil.isNotEmpty(list)) result.addAll(list);
    return result;
  }

  /**
   * 合并多个 Set 为一个新的 HashSet
   */
  @SafeVarargs
  public static <T> Set<T> mergeSets(Set<T>... sets) {
    if (ObjUtil.isEmpty(sets)) return new HashSet<>(0);
    int totalSize = 0;
    for (Set<T> set : sets) if (ObjUtil.isNotEmpty(set)) totalSize += set.size();
    // 计算初始容量：(预期元素数 / 0.75) + 1
    int initialCapacity = (int) (totalSize / 0.75f) + 1;
    Set<T> result = new HashSet<>(initialCapacity);
    for (Set<T> set : sets) if (ObjUtil.isNotEmpty(set)) result.addAll(set);
    return result;
  }

  /**
   * 合并多个 Map
   */
  @SafeVarargs
  public static <K, V> Map<K, V> mergeMaps(Map<K, V>... maps) {
    if (ObjUtil.isEmpty(maps)) return new HashMap<>(0);
    int totalSize = 0;
    for (Map<K, V> m : maps) if (ObjUtil.isNotEmpty(m)) totalSize += m.size();
    int initialCapacity = (int) (totalSize / 0.75f) + 1;
    Map<K, V> result = new HashMap<>(initialCapacity);
    for (Map<K, V> m : maps) if (ObjUtil.isNotEmpty(m)) result.putAll(m);
    return result;
  }

  public static <T> T ternary(Boolean bool, T t1, T t2) {
    return ObjUtil.isTrue(bool) ? t1 : t2;
  }

  public static <T> T nullTernary(Object obj, T t1, T t2) {
    return isNull(obj) ? t1 : t2;
  }

  /**
   * 创建 Map 构建器，利用 JDK 19+ 预分配容量，防止 Resize
   */
  public static <K, V> MapBuilder<K, V> tBuilder(int size) {
    return new MapBuilder<>(size);
  }

  public static MapBuilder<String, Object> builder(int size) {
    return new MapBuilder<>(size);
  }

  public static class MapBuilder<K, V> {
    private final Map<K, V> map;

    public MapBuilder(int size) {
      this.map = HashMap.newHashMap(size);
    }

    public MapBuilder<K, V> put(K key, V value) {
      map.put(key, value);
      return this;
    }

    public Map<K, V> build() {
      return this.map;
    }
  }

  public static <T> Set<T> asSet(T[] array) {
    if (isEmpty(array)) return Collections.emptySet();

    if (array[0] instanceof Enum) {
      // 1. 获取 Class 对象，这里必须强转为原始 Class
      Class clazz = array[0].getClass();
      // 2. 使用原始类型的 EnumSet，绕过捕获限制
      EnumSet enumSet = EnumSet.noneOf(clazz);
      // 3. 这里的 Collections.addAll 接收 Collection 和 T[]，原始类型可以直接通过
      Collections.addAll(enumSet, (Enum[]) array);
      return (Set<T>) enumSet;
    }

    // 非枚举逻辑...
    Set<T> set = HashSet.newHashSet(array.length);
    Collections.addAll(set, array);
    return set;
  }

  /**
   * 高效过滤工具：基于 for 循环，规避 Stream 开销
   */
  public static <T> List<T> filter(Collection<T> collection, Predicate<? super T> predicate) {
    if (isEmpty(collection)) return Collections.emptyList();
    // 预分配容量，减少 ArrayList 扩容频率
    List<T> result = new ArrayList<>(collection.size());
    for (T item : collection) {
      if (item != null && predicate.test(item)) {
        result.add(item);
      }
    }
    return result;
  }

  /**
   * 扁平化集合 (去重、去 null)
   */
  public static <T> List<T> flat(Collection<? extends Collection<T>> collections) {
    if (isEmpty(collections)) return Collections.emptyList();
    // 预估容量：假设子集合平均大小为 2-3
    List<T> result = new ArrayList<>(collections.size() * 2);
    Set<T> seen = new HashSet<>(HashMap.newHashMap(collections.size() * 2).size());
    for (Collection<T> sub : collections) {
      if (sub == null) continue;
      for (T item : sub) {
        if (item != null && seen.add(item)) {
          result.add(item);
        }
      }
    }
    return result;
  }

  /**
   * 扁平化 + 映射
   */
  public static <T, S> List<S> flat(Collection<T> collection, Function<? super T, ? extends Collection<S>> mapper) {
    if (isEmpty(collection)) return Collections.emptyList();
    List<S> result = new ArrayList<>(collection.size() * 2);
    Set<S> seen = new HashSet<>(HashMap.newHashMap(collection.size() * 2).size());
    for (T item : collection) {
      if (item == null) continue;
      Collection<S> mapped = mapper.apply(item);
      if (isEmpty(mapped)) continue;
      for (S s : mapped) {
        if (s != null && seen.add(s)) {
          result.add(s);
        }
      }
    }
    return result;
  }

  /**
   * 去重
   */
  public static <T> List<T> distinct(Collection<T> collection) {
    if (isEmpty(collection)) return Collections.emptyList();
    Set<T> seen = new HashSet<>(HashMap.newHashMap(collection.size()).size());
    List<T> result = new ArrayList<>(collection.size());
    for (T item : collection) {
      if (item != null && seen.add(item)) {
        result.add(item);
      }
    }
    return result;
  }

  /**
   * 提取字段 (去重、去 null)
   */
  public static <T, S> List<S> convert(Collection<T> collection, Function<? super T, ? extends S> mapper) {
    if (isEmpty(collection)) return Collections.emptyList();

    List<S> result = new ArrayList<>(collection.size());
    Set<S> seen = new HashSet<>(HashMap.newHashMap(collection.size()).size());

    for (T item : collection) {
      if (item == null) continue;
      S val = mapper.apply(item);
      if (val != null && seen.add(val)) {
        result.add(val);
      }
    }
    return result;
  }

  /**
   * 按条件提取字段 (去重、去 null)
   */
  public static <T, S> List<S> convert(Collection<T> collection, Function<? super T, ? extends S> mapper, Predicate<? super T> predicate) {
    if (isEmpty(collection)) return Collections.emptyList();

    List<S> result = new ArrayList<>(collection.size());
    Set<S> seen = new HashSet<>(HashMap.newHashMap(collection.size()).size());

    for (T item : collection) {
      if (item != null && predicate.test(item)) {
        S val = mapper.apply(item);
        if (val != null && seen.add(val)) {
          result.add(val);
        }
      }
    }
    return result;
  }

  /**
   * 提取到 Set
   */
  public static <T, S> Set<S> convertToSet(Collection<T> collection, Function<? super T, ? extends S> mapper) {
    if (isEmpty(collection)) return Set.of();

    Set<S> result = new HashSet<>(HashMap.newHashMap(collection.size()).size());
    for (T item : collection) {
      if (item == null) continue;
      S val = mapper.apply(item);
      if (val != null) result.add(val);
    }
    return result;
  }

  /**
   * 按条件提取到 Set
   */
  public static <T, S> Set<S> convertToSet(Collection<T> collection, Function<? super T, ? extends S> mapper, Predicate<? super T> predicate) {
    if (isEmpty(collection)) return Set.of();

    Set<S> result = new HashSet<>(HashMap.newHashMap(collection.size()).size());
    for (T item : collection) {
      if (item != null && predicate.test(item)) {
        S val = mapper.apply(item);
        if (val != null) result.add(val);
      }
    }
    return result;
  }

  /**
   * List 转 Map (V 为原元素，冲突取先到)
   */
  public static <K, V> Map<K, V> convertToMap(List<V> list, Function<? super V, ? extends K> keyMapper) {
    if (isEmpty(list)) return Map.of();

    Map<K, V> map = HashMap.newHashMap(list.size());
    for (V v : list) {
      if (v == null) continue;
      K key = keyMapper.apply(v);
      if (key != null) map.putIfAbsent(key, v);
    }
    return map;
  }

  /**
   * List 转 Map (R 为 Value，冲突取先到)
   */
  public static <K, V, R> Map<K, R> convertToMap(List<V> list, Function<? super V, ? extends K> keyMapper, Function<? super V, ? extends R> valueMapper) {
    if (isEmpty(list)) return Map.of();

    Map<K, R> map = HashMap.newHashMap(list.size());
    for (V v : list) {
      if (v == null) continue;
      K key = keyMapper.apply(v);
      R val = valueMapper.apply(v);
      if (key != null && val != null) map.putIfAbsent(key, val);
    }
    return map;
  }

  /**
   * List 转 Map (带自定义 mergeFunction)
   */
  public static <K, V, R> Map<K, R> convertToMap(List<V> list, Function<? super V, ? extends K> keyMapper, Function<? super V, ? extends R> valueMapper, BinaryOperator<R> mergeFunction) {
    if (isEmpty(list)) return Map.of();

    Map<K, R> map = HashMap.newHashMap(list.size());
    for (V v : list) {
      if (v == null) continue;
      K key = keyMapper.apply(v);
      R val = valueMapper.apply(v);
      if (key != null && val != null) map.merge(key, val, mergeFunction);
    }
    return map;
  }

  /**
   * 分组 (值为原元素列表)
   */
  public static <K, V> Map<K, List<V>> groupBy(List<V> list, Function<? super V, ? extends K> keyMapper) {
    if (isEmpty(list)) return Map.of();

    Map<K, List<V>> map = HashMap.newHashMap(list.size() / 2);
    for (V v : list) {
      if (v == null) continue;
      K key = keyMapper.apply(v);
      if (key != null) {
        map.computeIfAbsent(key, i -> new ArrayList<>()).add(v);
      }
    }
    return map;
  }

  /**
   * 分组并映射值列表
   */
  public static <K, V, R> Map<K, List<R>> groupBy(List<V> list, Function<? super V, ? extends K> keyMapper, Function<? super V, ? extends R> valueMapper) {
    if (isEmpty(list)) return Map.of();

    Map<K, List<R>> map = HashMap.newHashMap(list.size() / 2);
    for (V v : list) {
      if (v == null) continue;
      K key = keyMapper.apply(v);
      R val = valueMapper.apply(v);
      if (key != null) {
        map.computeIfAbsent(key, i -> new ArrayList<>()).add(val);
      }
    }
    return map;
  }

  /**
   * 分组求和工具
   */
  public static <T, K> Map<K, Integer> groupBySum(Collection<T> collection,
                                                  Function<? super T, ? extends K> keyMapper,
                                                  ToIntFunction<? super T> valueMapper) {
    if (isEmpty(collection)) return HashMap.newHashMap(0);
    Map<K, Integer> result = HashMap.newHashMap(collection.size());
    for (T item : collection) {
      if (item == null) continue;
      K key = keyMapper.apply(item);
      if (key == null) continue;

      int val = valueMapper.applyAsInt(item);
      result.merge(key, val, Integer::sum);
    }
    return result;
  }

  /**
   * 分区
   */
  public static <T> Map<Boolean, List<T>> partition(Collection<T> collection, Predicate<? super T> predicate) {
    Map<Boolean, List<T>> result = new HashMap<>(4);
    result.put(true, new ArrayList<>());
    result.put(false, new ArrayList<>());

    if (isEmpty(collection)) return result;

    for (T item : collection) {
      if (item != null) {
        result.get(predicate.test(item)).add(item);
      }
    }
    return result;
  }
}