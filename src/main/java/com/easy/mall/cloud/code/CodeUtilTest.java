package com.easy.mall.cloud.code;

import com.easy.mall.cloud.util.CodeUtil;
import org.apache.commons.codec.digest.DigestUtils;
import java.util.Objects;

public class CodeUtilTest {

    public static void main(String[] args) {
        String testStr = "Hello, Easy Mall 2026! 程序员万岁";

        // 1. 结果一致性校验
        String mySha1 = CodeUtil.sha1(testStr);
        String apacheSha1 = DigestUtils.sha1Hex(testStr);

        System.out.println("My SHA-1    : " + mySha1);
        System.out.println("Apache SHA-1: " + apacheSha1);

        if (Objects.equals(mySha1, apacheSha1)) {
            System.out.println("✅ 结果完全一致！你的实现是标准的 SHA-1 Hex。");
        } else {
            System.err.println("❌ 结果不匹配！请检查编码或 Hex 转换逻辑。");
        }

        // 2. 硬核性能压测 (Warm up + 100万次迭代)
        int iterations = 1_000_000;
        
        // 预热 JVM
        for (int i = 0; i < 10000; i++) {
            CodeUtil.sha1(testStr);
            DigestUtils.sha1Hex(testStr);
        }

        // 测试你的实现
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            CodeUtil.sha1(testStr);
        }
        long myTime = System.nanoTime() - start;

        // 测试 Apache 实现
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            DigestUtils.sha1Hex(testStr);
        }
        long apacheTime = System.nanoTime() - start;

        System.out.println("--- 性能比拼 (1,000,000次) ---");
        System.out.printf("My Implementation (HexFormat): %d ms\n", myTime / 1_000_000);
        System.out.printf("Apache (DigestUtils):          %d ms\n", apacheTime / 1_000_000);
    }
}