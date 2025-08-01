package com.laker.postman.model;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 支持 pm.expect(xxx) 断言的链式断言对象
 */
public class Expectation {
    private final Object actual;
    public final Expectation to = this;
    public final Expectation be = this;
    public final Expectation have = this;

    public Expectation(Object actual) {
        this.actual = actual;
    }

    public void include(Object expected) {
        if (actual == null || expected == null || !actual.toString().contains(expected.toString())) {
            throw new AssertionError("include断言失败: 期望包含=" + expected + ", 实际=" + actual);
        }
    }

    public void eql(Object expected) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            throw new AssertionError("eql断言失败: 期望=" + expected + ", 实际=" + actual);
        }
    }

    public void property(String property) {
        if (actual instanceof Map) {
            if (!((Map<?, ?>) actual).containsKey(property)) {
                throw new AssertionError("property断言失败: 不存在属性=" + property);
            }
        } else {
            throw new AssertionError("property断言失败: actual不是Map类型");
        }
    }

    public void match(String regex) {
        if (actual == null || !Pattern.compile(regex).matcher(actual.toString()).find()) {
            throw new AssertionError("match断言失败: 正则=" + regex + ", 实际=" + actual);
        }
    }

    public void match(Pattern pattern) {
        if (actual == null || !pattern.matcher(actual.toString()).find()) {
            throw new AssertionError("match断言失败: pattern=" + pattern + ", 实际=" + actual);
        }
    }

    // Handle JavaScript RegExp objects
    public void match(Object jsRegExp) {
        if (jsRegExp != null) {
            try {
                // Convert JavaScript RegExp to string and extract the pattern part
                String regExpStr = jsRegExp.toString();
                // JS RegExp toString format is typically /pattern/flags
                if (regExpStr.startsWith("/") && regExpStr.lastIndexOf("/") > 0) {
                    String patternStr = regExpStr.substring(1, regExpStr.lastIndexOf("/"));
                    // Create a Java Pattern (ignoring flags for simplicity)
                    Pattern pattern = Pattern.compile(patternStr);
                    match(pattern);
                    return;
                }
            } catch (Exception e) {
                // Fall through to the error below
            }
        }
        throw new AssertionError("Match assertion failed: invalid JavaScript RegExp object=" + jsRegExp + ", actual=" + actual);
    }

    public void below(Number max) {
        if (!(actual instanceof Number) || ((Number) actual).doubleValue() >= max.doubleValue()) {
            throw new AssertionError("below断言失败: 期望小于=" + max + ", 实际=" + actual);
        }
    }
}