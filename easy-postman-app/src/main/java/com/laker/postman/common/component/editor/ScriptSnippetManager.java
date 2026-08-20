package com.laker.postman.common.component.editor;

import com.laker.postman.plugin.api.ScriptCompletionItem;
import com.laker.postman.plugin.api.ScriptCompletionKind;
import com.laker.postman.plugin.host.PluginAccess;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.UtilityClass;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ShorthandCompletion;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * 脚本 API 自动补全管理器
 * 提供全面的 JavaScript 内置对象、Postman API 和第三方库的自动补全支持
 *
 * <p>包含以下内容：</p>
 * <ul>
 *     <li>JavaScript 内置对象：String, Array, Object, Math, Date, JSON, Promise, RegExp 等</li>
 *     <li>Postman 核心 API：pm, pm.test, pm.expect, pm.request, pm.response 等</li>
 *     <li>第三方库：CryptoJS, Lodash, Moment.js, Cheerio, xml2Json 等</li>
 *     <li>Chai 断言库：完整的 BDD/TDD 断言链</li>
 * </ul>
 *
 */
@UtilityClass
@Slf4j
public class ScriptSnippetManager {

    /**
     * 创建自动补全提供器
     */
    public static CompletionProvider createCompletionProvider() {
        // 使用自定义 Provider 来实现自动激活和点号分隔的补全
        DefaultCompletionProvider provider = new DefaultCompletionProvider() {
            @Override
            public boolean isAutoActivateOkay(JTextComponent tc) {
                Document doc = tc.getDocument();
                int caret = tc.getCaretPosition();

                if (caret <= 0) {
                    return false;
                }

                try {
                    char ch = doc.getText(caret - 1, 1).charAt(0);
                    return isCompletionTriggerChar(ch);
                } catch (BadLocationException e) {
                    return false;
                }
            }

            @Override
            protected boolean isValidChar(char ch) {
                return isCompletionTriggerChar(ch);
            }

            @Override
            public String getAlreadyEnteredText(JTextComponent comp) {
                int caret = comp.getCaretPosition();
                if (caret == 0) {
                    return "";
                }

                try {
                    Document doc = comp.getDocument();
                    int start = caret - 1;
                    while (start >= 0) {
                        char ch = doc.getText(start, 1).charAt(0);
                        if (!isValidChar(ch)) {
                            break;
                        }
                        start--;
                    }
                    start++;
                    return doc.getText(start, caret - start);
                } catch (BadLocationException e) {
                    return "";
                }
            }
        };

        // 添加所有 API 补全提示
        addAllCompletions(provider);

        return provider;
    }

    private static boolean isCompletionTriggerChar(char ch) {
        // 点号也是有效字符，这样 pm. 会被当作一个整体
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.';
    }

    /**
     * 添加所有 API 补全提示（主入口）
     */
    private static void addAllCompletions(DefaultCompletionProvider provider) {
        // ===== JavaScript 核心 =====
        addJavaScriptKeywords(provider);
        addJavaScriptGlobalObjects(provider);
        addStringMethods(provider);
        addArrayMethods(provider);
        addObjectMethods(provider);
        addMathMethods(provider);
        addDateMethods(provider);
        addJsonMethods(provider);
        addPromiseMethods(provider);
        addRegExpMethods(provider);
        addNumberMethods(provider);
        addConsoleMethods(provider);
        addEncodingFunctions(provider);

        // ===== Postman 核心 API =====
        addPostmanCore(provider);
        addPmCoreMethods(provider);
        addPmEnvironment(provider);
        addPmGlobals(provider);
        addPmVariables(provider);
        addPmIterationData(provider);
        addPmRequest(provider);
        addPmResponse(provider);
        addPmCookies(provider);
        addPmInfo(provider);
        addPmSendRequest(provider);
        addPmDataStoreApis(provider);

        // ===== Chai 断言库 =====
        addChaiAssertions(provider);

        // ===== 第三方库 =====
        addCryptoJS(provider);
        addLodash(provider);
        addMoment(provider);
        addCheerio(provider);
        addXml2Json(provider);

        for (var contributor : PluginAccess.getScriptCompletionContributors()) {
            // 内建补全先注册，插件补全后追加。
            // 这样宿主负责“基础语言 + 核心 pm”，插件只补自己的增量能力。
            try {
                contributor.contribute(item -> addPluginCompletion(provider, item));
            } catch (Throwable t) {
                log.warn("Plugin script completion contributor failed. Skipping contributor.", t);
            }
        }
    }

    private static void addPluginCompletion(DefaultCompletionProvider provider, ScriptCompletionItem item) {
        if (item == null || item.inputText() == null || item.inputText().isBlank()) {
            return;
        }
        if (item.kind() == ScriptCompletionKind.SHORTHAND) {
            provider.addCompletion(new ShorthandCompletion(
                    provider,
                    item.inputText(),
                    item.replacementText() == null ? "" : item.replacementText(),
                    item.shortDescription()
            ));
            return;
        }
        provider.addCompletion(new BasicCompletion(provider, item.inputText(), item.shortDescription()));
    }

    // ========================================
    // JavaScript 核心对象和方法
    // ========================================

    /**
     * 添加 JavaScript 关键字
     */
    private static void addJavaScriptKeywords(DefaultCompletionProvider provider) {
        // 关键字
        String[] keywords = {
                "function", "var", "let", "const", "if", "else", "for", "while", "do",
                "switch", "case", "default", "break", "continue", "return", "try", "catch",
                "finally", "throw", "new", "this", "typeof", "instanceof", "in", "of",
                "true", "false", "null", "undefined", "class", "extends", "super",
                "async", "await", "yield", "import", "export", "from", "as", "delete", "void"
        };

        for (String keyword : keywords) {
            provider.addCompletion(new BasicCompletion(provider, keyword, "JavaScript keyword: " + keyword));
        }
    }

    /**
     * 添加 JavaScript 全局对象
     */
    private static void addJavaScriptGlobalObjects(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "String", "String constructor"));
        provider.addCompletion(new BasicCompletion(provider, "Array", "Array constructor"));
        provider.addCompletion(new BasicCompletion(provider, "Object", "Object constructor"));
        provider.addCompletion(new BasicCompletion(provider, "Math", "Math object - mathematical functions"));
        provider.addCompletion(new BasicCompletion(provider, "Date", "Date constructor"));
        provider.addCompletion(new BasicCompletion(provider, "JSON", "JSON object - parse and stringify"));
        provider.addCompletion(new BasicCompletion(provider, "RegExp", "RegExp constructor"));
        provider.addCompletion(new BasicCompletion(provider, "Number", "Number constructor"));
        provider.addCompletion(new BasicCompletion(provider, "Boolean", "Boolean constructor"));
        provider.addCompletion(new BasicCompletion(provider, "Promise", "Promise constructor (ES6)"));
        provider.addCompletion(new BasicCompletion(provider, "Map", "Map constructor (ES6)"));
        provider.addCompletion(new BasicCompletion(provider, "Set", "Set constructor (ES6)"));
        provider.addCompletion(new BasicCompletion(provider, "WeakMap", "WeakMap constructor (ES6)"));
        provider.addCompletion(new BasicCompletion(provider, "WeakSet", "WeakSet constructor (ES6)"));
        provider.addCompletion(new BasicCompletion(provider, "Symbol", "Symbol constructor (ES6)"));
        provider.addCompletion(new BasicCompletion(provider, "Error", "Error constructor"));
        provider.addCompletion(new BasicCompletion(provider, "parseInt", "Parse string to integer"));
        provider.addCompletion(new BasicCompletion(provider, "parseFloat", "Parse string to float"));
        provider.addCompletion(new BasicCompletion(provider, "isNaN", "Check if value is NaN"));
        provider.addCompletion(new BasicCompletion(provider, "isFinite", "Check if value is finite"));
    }

    /**
     * 添加 String 方法
     */
    private static void addStringMethods(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "charAt", "String.prototype.charAt() - get char at index"));
        provider.addCompletion(new BasicCompletion(provider, "charCodeAt", "String.prototype.charCodeAt() - get char code"));
        provider.addCompletion(new BasicCompletion(provider, "concat", "String.prototype.concat() - concatenate strings"));
        provider.addCompletion(new BasicCompletion(provider, "includes", "String.prototype.includes() - check if contains substring"));
        provider.addCompletion(new BasicCompletion(provider, "indexOf", "String.prototype.indexOf() - find first index"));
        provider.addCompletion(new BasicCompletion(provider, "lastIndexOf", "String.prototype.lastIndexOf() - find last index"));
        provider.addCompletion(new BasicCompletion(provider, "match", "String.prototype.match() - match regex"));
        provider.addCompletion(new BasicCompletion(provider, "matchAll", "String.prototype.matchAll() - match all regex"));
        provider.addCompletion(new BasicCompletion(provider, "replace", "String.prototype.replace() - replace substring"));
        provider.addCompletion(new BasicCompletion(provider, "replaceAll", "String.prototype.replaceAll() - replace all occurrences"));
        provider.addCompletion(new BasicCompletion(provider, "search", "String.prototype.search() - search regex"));
        provider.addCompletion(new BasicCompletion(provider, "slice", "String.prototype.slice() - extract substring"));
        provider.addCompletion(new BasicCompletion(provider, "split", "String.prototype.split() - split into array"));
        provider.addCompletion(new BasicCompletion(provider, "substring", "String.prototype.substring() - extract substring"));
        provider.addCompletion(new BasicCompletion(provider, "substr", "String.prototype.substr() - extract substring (deprecated)"));
        provider.addCompletion(new BasicCompletion(provider, "toLowerCase", "String.prototype.toLowerCase() - convert to lowercase"));
        provider.addCompletion(new BasicCompletion(provider, "toUpperCase", "String.prototype.toUpperCase() - convert to uppercase"));
        provider.addCompletion(new BasicCompletion(provider, "trim", "String.prototype.trim() - remove whitespace"));
        provider.addCompletion(new BasicCompletion(provider, "trimStart", "String.prototype.trimStart() - remove leading whitespace"));
        provider.addCompletion(new BasicCompletion(provider, "trimEnd", "String.prototype.trimEnd() - remove trailing whitespace"));
        provider.addCompletion(new BasicCompletion(provider, "padStart", "String.prototype.padStart() - pad from start"));
        provider.addCompletion(new BasicCompletion(provider, "padEnd", "String.prototype.padEnd() - pad from end"));
        provider.addCompletion(new BasicCompletion(provider, "repeat", "String.prototype.repeat() - repeat string"));
        provider.addCompletion(new BasicCompletion(provider, "startsWith", "String.prototype.startsWith() - check if starts with"));
        provider.addCompletion(new BasicCompletion(provider, "endsWith", "String.prototype.endsWith() - check if ends with"));
    }

    /**
     * 添加 Array 方法
     */
    private static void addArrayMethods(DefaultCompletionProvider provider) {
        // 遍历方法
        provider.addCompletion(new BasicCompletion(provider, "forEach", "Array.prototype.forEach() - iterate array"));
        provider.addCompletion(new BasicCompletion(provider, "map", "Array.prototype.map() - transform array"));
        provider.addCompletion(new BasicCompletion(provider, "filter", "Array.prototype.filter() - filter array"));
        provider.addCompletion(new BasicCompletion(provider, "reduce", "Array.prototype.reduce() - reduce to single value"));
        provider.addCompletion(new BasicCompletion(provider, "reduceRight", "Array.prototype.reduceRight() - reduce from right"));
        provider.addCompletion(new BasicCompletion(provider, "find", "Array.prototype.find() - find first element"));
        provider.addCompletion(new BasicCompletion(provider, "findIndex", "Array.prototype.findIndex() - find first index"));
        provider.addCompletion(new BasicCompletion(provider, "some", "Array.prototype.some() - test if any passes"));
        provider.addCompletion(new BasicCompletion(provider, "every", "Array.prototype.every() - test if all pass"));

        // 查找方法
        provider.addCompletion(new BasicCompletion(provider, "includes", "Array.prototype.includes() - check if contains"));
        provider.addCompletion(new BasicCompletion(provider, "indexOf", "Array.prototype.indexOf() - find first index"));
        provider.addCompletion(new BasicCompletion(provider, "lastIndexOf", "Array.prototype.lastIndexOf() - find last index"));

        // 修改方法
        provider.addCompletion(new BasicCompletion(provider, "push", "Array.prototype.push() - add to end"));
        provider.addCompletion(new BasicCompletion(provider, "pop", "Array.prototype.pop() - remove from end"));
        provider.addCompletion(new BasicCompletion(provider, "shift", "Array.prototype.shift() - remove from start"));
        provider.addCompletion(new BasicCompletion(provider, "unshift", "Array.prototype.unshift() - add to start"));
        provider.addCompletion(new BasicCompletion(provider, "splice", "Array.prototype.splice() - add/remove elements"));
        provider.addCompletion(new BasicCompletion(provider, "slice", "Array.prototype.slice() - extract section"));
        provider.addCompletion(new BasicCompletion(provider, "concat", "Array.prototype.concat() - merge arrays"));
        provider.addCompletion(new BasicCompletion(provider, "reverse", "Array.prototype.reverse() - reverse array"));
        provider.addCompletion(new BasicCompletion(provider, "sort", "Array.prototype.sort() - sort array"));
        provider.addCompletion(new BasicCompletion(provider, "fill", "Array.prototype.fill() - fill with value"));
        provider.addCompletion(new BasicCompletion(provider, "copyWithin", "Array.prototype.copyWithin() - copy within array"));

        // 转换方法
        provider.addCompletion(new BasicCompletion(provider, "join", "Array.prototype.join() - join to string"));
        provider.addCompletion(new BasicCompletion(provider, "toString", "Array.prototype.toString() - convert to string"));
        provider.addCompletion(new BasicCompletion(provider, "flat", "Array.prototype.flat() - flatten array"));
        provider.addCompletion(new BasicCompletion(provider, "flatMap", "Array.prototype.flatMap() - map and flatten"));

        // 静态方法
        provider.addCompletion(new BasicCompletion(provider, "Array.isArray", "Array.isArray() - check if array"));
        provider.addCompletion(new BasicCompletion(provider, "Array.from", "Array.from() - create array from iterable"));
        provider.addCompletion(new BasicCompletion(provider, "Array.of", "Array.of() - create array from arguments"));
    }

    /**
     * 添加 Object 方法
     */
    private static void addObjectMethods(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "Object.keys", "Object.keys() - get object keys"));
        provider.addCompletion(new BasicCompletion(provider, "Object.values", "Object.values() - get object values"));
        provider.addCompletion(new BasicCompletion(provider, "Object.entries", "Object.entries() - get key-value pairs"));
        provider.addCompletion(new BasicCompletion(provider, "Object.assign", "Object.assign() - merge objects"));
        provider.addCompletion(new BasicCompletion(provider, "Object.create", "Object.create() - create object with prototype"));
        provider.addCompletion(new BasicCompletion(provider, "Object.freeze", "Object.freeze() - freeze object"));
        provider.addCompletion(new BasicCompletion(provider, "Object.seal", "Object.seal() - seal object"));
        provider.addCompletion(new BasicCompletion(provider, "Object.defineProperty", "Object.defineProperty() - define property"));
        provider.addCompletion(new BasicCompletion(provider, "Object.getOwnPropertyNames", "Get own property names"));
        provider.addCompletion(new BasicCompletion(provider, "Object.getPrototypeOf", "Get prototype of object"));
        provider.addCompletion(new BasicCompletion(provider, "Object.setPrototypeOf", "Set prototype of object"));
        provider.addCompletion(new BasicCompletion(provider, "hasOwnProperty", "Check if has own property"));
        provider.addCompletion(new BasicCompletion(provider, "toString", "Convert to string"));
        provider.addCompletion(new BasicCompletion(provider, "valueOf", "Get primitive value"));
    }

    /**
     * 添加 Math 方法
     */
    private static void addMathMethods(DefaultCompletionProvider provider) {
        // 常用方法
        provider.addCompletion(new BasicCompletion(provider, "Math.random", "Math.random() - random number [0,1)"));
        provider.addCompletion(new BasicCompletion(provider, "Math.floor", "Math.floor() - round down"));
        provider.addCompletion(new BasicCompletion(provider, "Math.ceil", "Math.ceil() - round up"));
        provider.addCompletion(new BasicCompletion(provider, "Math.round", "Math.round() - round to nearest"));
        provider.addCompletion(new BasicCompletion(provider, "Math.abs", "Math.abs() - absolute value"));
        provider.addCompletion(new BasicCompletion(provider, "Math.min", "Math.min() - minimum value"));
        provider.addCompletion(new BasicCompletion(provider, "Math.max", "Math.max() - maximum value"));
        provider.addCompletion(new BasicCompletion(provider, "Math.pow", "Math.pow() - power"));
        provider.addCompletion(new BasicCompletion(provider, "Math.sqrt", "Math.sqrt() - square root"));
        provider.addCompletion(new BasicCompletion(provider, "Math.cbrt", "Math.cbrt() - cube root"));
        provider.addCompletion(new BasicCompletion(provider, "Math.sign", "Math.sign() - sign of number"));
        provider.addCompletion(new BasicCompletion(provider, "Math.trunc", "Math.trunc() - truncate decimal"));

        // 三角函数
        provider.addCompletion(new BasicCompletion(provider, "Math.sin", "Math.sin() - sine"));
        provider.addCompletion(new BasicCompletion(provider, "Math.cos", "Math.cos() - cosine"));
        provider.addCompletion(new BasicCompletion(provider, "Math.tan", "Math.tan() - tangent"));
        provider.addCompletion(new BasicCompletion(provider, "Math.asin", "Math.asin() - arcsine"));
        provider.addCompletion(new BasicCompletion(provider, "Math.acos", "Math.acos() - arccosine"));
        provider.addCompletion(new BasicCompletion(provider, "Math.atan", "Math.atan() - arctangent"));
        provider.addCompletion(new BasicCompletion(provider, "Math.atan2", "Math.atan2() - arctangent of quotient"));

        // 常量
        provider.addCompletion(new BasicCompletion(provider, "Math.PI", "Math.PI - π constant"));
        provider.addCompletion(new BasicCompletion(provider, "Math.E", "Math.E - Euler's number"));
        provider.addCompletion(new BasicCompletion(provider, "Math.LN2", "Math.LN2 - natural log of 2"));
        provider.addCompletion(new BasicCompletion(provider, "Math.LN10", "Math.LN10 - natural log of 10"));
        provider.addCompletion(new BasicCompletion(provider, "Math.LOG2E", "Math.LOG2E - log base 2 of E"));
        provider.addCompletion(new BasicCompletion(provider, "Math.LOG10E", "Math.LOG10E - log base 10 of E"));
    }

    /**
     * 添加 Date 方法
     */
    private static void addDateMethods(DefaultCompletionProvider provider) {
        // 静态方法
        provider.addCompletion(new BasicCompletion(provider, "Date.now", "Date.now() - current timestamp"));
        provider.addCompletion(new BasicCompletion(provider, "Date.parse", "Date.parse() - parse date string"));
        provider.addCompletion(new BasicCompletion(provider, "Date.UTC", "Date.UTC() - UTC timestamp"));

        // 实例方法 - 获取
        provider.addCompletion(new BasicCompletion(provider, "getFullYear", "Get full year"));
        provider.addCompletion(new BasicCompletion(provider, "getMonth", "Get month (0-11)"));
        provider.addCompletion(new BasicCompletion(provider, "getDate", "Get day of month (1-31)"));
        provider.addCompletion(new BasicCompletion(provider, "getDay", "Get day of week (0-6)"));
        provider.addCompletion(new BasicCompletion(provider, "getHours", "Get hours (0-23)"));
        provider.addCompletion(new BasicCompletion(provider, "getMinutes", "Get minutes (0-59)"));
        provider.addCompletion(new BasicCompletion(provider, "getSeconds", "Get seconds (0-59)"));
        provider.addCompletion(new BasicCompletion(provider, "getMilliseconds", "Get milliseconds (0-999)"));
        provider.addCompletion(new BasicCompletion(provider, "getTime", "Get timestamp"));
        provider.addCompletion(new BasicCompletion(provider, "getTimezoneOffset", "Get timezone offset"));

        // 实例方法 - 设置
        provider.addCompletion(new BasicCompletion(provider, "setFullYear", "Set full year"));
        provider.addCompletion(new BasicCompletion(provider, "setMonth", "Set month"));
        provider.addCompletion(new BasicCompletion(provider, "setDate", "Set day of month"));
        provider.addCompletion(new BasicCompletion(provider, "setHours", "Set hours"));
        provider.addCompletion(new BasicCompletion(provider, "setMinutes", "Set minutes"));
        provider.addCompletion(new BasicCompletion(provider, "setSeconds", "Set seconds"));
        provider.addCompletion(new BasicCompletion(provider, "setMilliseconds", "Set milliseconds"));
        provider.addCompletion(new BasicCompletion(provider, "setTime", "Set timestamp"));

        // 转换方法
        provider.addCompletion(new BasicCompletion(provider, "toISOString", "Convert to ISO 8601 string"));
        provider.addCompletion(new BasicCompletion(provider, "toJSON", "Convert to JSON string"));
        provider.addCompletion(new BasicCompletion(provider, "toDateString", "Convert to date string"));
        provider.addCompletion(new BasicCompletion(provider, "toTimeString", "Convert to time string"));
        provider.addCompletion(new BasicCompletion(provider, "toLocaleString", "Convert to locale string"));
        provider.addCompletion(new BasicCompletion(provider, "toLocaleDateString", "Convert to locale date string"));
        provider.addCompletion(new BasicCompletion(provider, "toLocaleTimeString", "Convert to locale time string"));
    }

    /**
     * 添加 JSON 方法
     */
    private static void addJsonMethods(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "JSON.parse",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_JSON_PARSE)));
        provider.addCompletion(new BasicCompletion(provider, "JSON.stringify",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_JSON_STRINGIFY)));
    }

    /**
     * 添加 Promise 方法
     */
    private static void addPromiseMethods(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "Promise.resolve", "Promise.resolve() - create resolved promise"));
        provider.addCompletion(new BasicCompletion(provider, "Promise.reject", "Promise.reject() - create rejected promise"));
        provider.addCompletion(new BasicCompletion(provider, "Promise.all", "Promise.all() - wait for all promises"));
        provider.addCompletion(new BasicCompletion(provider, "Promise.race", "Promise.race() - wait for first promise"));
        provider.addCompletion(new BasicCompletion(provider, "Promise.allSettled", "Promise.allSettled() - wait for all to settle"));
        provider.addCompletion(new BasicCompletion(provider, "Promise.any", "Promise.any() - wait for first resolved"));
        provider.addCompletion(new BasicCompletion(provider, "then", "Promise.then() - handle success"));
        provider.addCompletion(new BasicCompletion(provider, "catch", "Promise.catch() - handle error"));
        provider.addCompletion(new BasicCompletion(provider, "finally", "Promise.finally() - always execute"));
    }

    /**
     * 添加 RegExp 方法
     */
    private static void addRegExpMethods(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "test", "RegExp.test() - test if matches"));
        provider.addCompletion(new BasicCompletion(provider, "exec", "RegExp.exec() - execute regex"));
    }

    /**
     * 添加 Number 方法
     */
    private static void addNumberMethods(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "Number.isNaN", "Number.isNaN() - check if NaN"));
        provider.addCompletion(new BasicCompletion(provider, "Number.isFinite", "Number.isFinite() - check if finite"));
        provider.addCompletion(new BasicCompletion(provider, "Number.isInteger", "Number.isInteger() - check if integer"));
        provider.addCompletion(new BasicCompletion(provider, "Number.isSafeInteger", "Number.isSafeInteger() - check if safe integer"));
        provider.addCompletion(new BasicCompletion(provider, "Number.parseFloat", "Number.parseFloat() - parse float"));
        provider.addCompletion(new BasicCompletion(provider, "Number.parseInt", "Number.parseInt() - parse integer"));
        provider.addCompletion(new BasicCompletion(provider, "toFixed", "Number.toFixed() - format to fixed decimals"));
        provider.addCompletion(new BasicCompletion(provider, "toPrecision", "Number.toPrecision() - format to precision"));
        provider.addCompletion(new BasicCompletion(provider, "toExponential", "Number.toExponential() - format to exponential"));
    }

    /**
     * 添加 console 方法
     */
    private static void addConsoleMethods(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "console.log",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CONSOLE_LOG)));
        provider.addCompletion(new BasicCompletion(provider, "console.error", "console.error() - log error"));
        provider.addCompletion(new BasicCompletion(provider, "console.warn", "console.warn() - log warning"));
        provider.addCompletion(new BasicCompletion(provider, "console.info", "console.info() - log info"));
        provider.addCompletion(new BasicCompletion(provider, "console.debug", "console.debug() - log debug"));
        provider.addCompletion(new BasicCompletion(provider, "console.table", "console.table() - display as table"));
        provider.addCompletion(new BasicCompletion(provider, "console.time", "console.time() - start timer"));
        provider.addCompletion(new BasicCompletion(provider, "console.timeEnd", "console.timeEnd() - end timer"));
        provider.addCompletion(new BasicCompletion(provider, "console.clear", "console.clear() - clear console"));
    }

    /**
     * 添加编码/解码函数
     */
    private static void addEncodingFunctions(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "btoa",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_BTOA)));
        provider.addCompletion(new BasicCompletion(provider, "atob",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_ATOB)));
        provider.addCompletion(new BasicCompletion(provider, "encodeURIComponent",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_ENCODE_URI)));
        provider.addCompletion(new BasicCompletion(provider, "decodeURIComponent",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_DECODE_URI)));
        provider.addCompletion(new BasicCompletion(provider, "encodeURI",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_ENCODE_URI_FULL)));
        provider.addCompletion(new BasicCompletion(provider, "decodeURI",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_DECODE_URI_FULL)));
    }

    // ========================================
    // Postman 核心 API
    // ========================================

    /**
     * 添加 Postman 核心对象
     */
    private static void addPostmanCore(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM)));
        provider.addCompletion(new BasicCompletion(provider, "console",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CONSOLE)));
        provider.addCompletion(new BasicCompletion(provider, "require", "Require module"));
    }

    /**
     * 添加 pm 核心方法
     */
    private static void addPmCoreMethods(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.test",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_TEST)));
        provider.addCompletion(new BasicCompletion(provider, "pm.expect",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT)));
        provider.addCompletion(new BasicCompletion(provider, "pm.sendRequest", "pm.sendRequest() - send HTTP request"));

        // UUID 和时间戳
        provider.addCompletion(new BasicCompletion(provider, "pm.uuid",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_UUID)));
        provider.addCompletion(new BasicCompletion(provider, "pm.getTimestamp",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_GET_TIMESTAMP)));

        // Cookie
        provider.addCompletion(new BasicCompletion(provider, "pm.getResponseCookie",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_GET_RESPONSE_COOKIE)));
    }

    /**
     * 添加 pm.environment - 环境变量
     */
    private static void addPmEnvironment(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.environment",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_ENVIRONMENT)));
        provider.addCompletion(new BasicCompletion(provider, "pm.environment.set",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_ENV_SET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.environment.get",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_ENV_GET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.environment.has",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_ENV_HAS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.environment.unset",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_ENV_UNSET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.environment.clear",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_ENV_CLEAR)));
        provider.addCompletion(new BasicCompletion(provider, "pm.environment.toObject", "Convert to object"));
    }

    /**
     * 添加 pm.globals - 全局变量
     */
    private static void addPmGlobals(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.globals", "Global variables object"));
        provider.addCompletion(new BasicCompletion(provider, "pm.globals.set", "pm.globals.set() - set global variable"));
        provider.addCompletion(new BasicCompletion(provider, "pm.globals.get", "pm.globals.get() - get global variable"));
        provider.addCompletion(new BasicCompletion(provider, "pm.globals.has", "pm.globals.has() - check if exists"));
        provider.addCompletion(new BasicCompletion(provider, "pm.globals.unset", "pm.globals.unset() - delete variable"));
        provider.addCompletion(new BasicCompletion(provider, "pm.globals.clear", "pm.globals.clear() - clear all"));
        provider.addCompletion(new BasicCompletion(provider, "pm.globals.toObject", "Convert to object"));
    }

    /**
     * 添加 pm.variables - 变量（脚本级别）
     */
    private static void addPmVariables(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.variables",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_VARIABLES)));
        provider.addCompletion(new BasicCompletion(provider, "pm.variables.set",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_VAR_SET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.variables.get",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_VAR_GET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.variables.has",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_VAR_HAS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.variables.unset",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_VAR_UNSET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.variables.clear",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_VAR_CLEAR)));
        provider.addCompletion(new BasicCompletion(provider, "pm.variables.toObject",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_VAR_TO_OBJECT)));
        provider.addCompletion(new BasicCompletion(provider, "pm.variables.replaceIn", "Replace variables in string"));
    }

    /**
     * 添加 pm.iterationData - 迭代数据
     */
    private static void addPmIterationData(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.iterationData", "Iteration data object"));
        provider.addCompletion(new BasicCompletion(provider, "pm.iterationData.get", "Get iteration data value"));
        provider.addCompletion(new BasicCompletion(provider, "pm.iterationData.has", "Check if iteration data exists"));
        provider.addCompletion(new BasicCompletion(provider, "pm.iterationData.toObject", "Convert iteration data to object"));
        provider.addCompletion(new BasicCompletion(provider, "pm.iterationData.toJSON", "Convert iteration data to JSON"));
    }

    /**
     * 添加 pm.request - 请求对象
     */
    private static void addPmRequest(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.request",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.url",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_URL)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.method",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_METHOD)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.headers",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_HEADERS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.update",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_UPDATE)));

        // URL 相关
        provider.addCompletion(new BasicCompletion(provider, "pm.request.url.query",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_URL_QUERY)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.url.query.add",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_URL_QUERY_ADD)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.url.query.upsert",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_URL_QUERY_UPSERT)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.url.query.remove",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_URL_QUERY_REMOVE)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.url.query.get",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_URL_QUERY_GET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.url.query.has",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_URL_QUERY_HAS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.url.query.all",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_URL_QUERY_ALL)));

        // Headers 方法
        provider.addCompletion(new BasicCompletion(provider, "pm.request.headers.get",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_HEADERS_GET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.headers.has",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_HEADERS_HAS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.headers.add",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_HEADERS_ADD)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.headers.remove",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_HEADERS_REMOVE)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.headers.upsert",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_HEADERS_UPSERT)));

        // Postman RequestBody
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body.mode",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY_MODE)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body.raw",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY_RAW)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body.formdata",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY_FORMDATA)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body.urlencoded",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY_URLENCODED)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body.update",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY_UPDATE)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body.toString",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY_TO_STRING)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body.toJSON",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY_TO_JSON)));
        provider.addCompletion(new BasicCompletion(provider, "pm.request.body.isEmpty",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_REQUEST_BODY_IS_EMPTY)));
    }

    /**
     * 添加 pm.response - 响应对象
     */
    private static void addPmResponse(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.response",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.code",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_CODE)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.status",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_STATUS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.headers",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_HEADERS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.text",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_TEXT)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.json",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_JSON)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.responseTime",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_TIME)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.size",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_SIZE)));

        // Headers 方法
        provider.addCompletion(new BasicCompletion(provider, "pm.response.headers.get", "Get response header"));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.headers.has", "Check if header exists"));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.headers.toObject", "Convert to object"));

        // 断言链
        provider.addCompletion(new BasicCompletion(provider, "pm.response.to.have.status",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_TO_HAVE_STATUS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.to.have.header",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_RESPONSE_TO_HAVE_HEADER)));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.to.have.body", "Assert response has body"));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.to.have.jsonBody", "Assert response has JSON body"));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.to.be.ok", "Assert response is 2xx"));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.to.be.error", "Assert response is error"));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.to.be.clientError", "Assert response is 4xx"));
        provider.addCompletion(new BasicCompletion(provider, "pm.response.to.be.serverError", "Assert response is 5xx"));
    }

    /**
     * 添加 pm.cookies - Cookie 管理
     */
    private static void addPmCookies(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.cookies",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_COOKIES)));
        provider.addCompletion(new BasicCompletion(provider, "pm.cookies.get",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_COOKIES_GET)));
        provider.addCompletion(new BasicCompletion(provider, "pm.cookies.has",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_COOKIES_HAS)));
        provider.addCompletion(new BasicCompletion(provider, "pm.cookies.getAll",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_COOKIES_GET_ALL)));
        provider.addCompletion(new BasicCompletion(provider, "pm.cookies.jar",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_COOKIES_JAR)));
        provider.addCompletion(new BasicCompletion(provider, "pm.cookies.toObject",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_COOKIES_TO_OBJECT)));
    }

    /**
     * 添加 pm.info - 请求信息
     */
    private static void addPmInfo(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.info", "Request execution info"));
        provider.addCompletion(new BasicCompletion(provider, "pm.info.eventName", "Event name (prerequest/test)"));
        provider.addCompletion(new BasicCompletion(provider, "pm.info.iteration", "Current iteration number"));
        provider.addCompletion(new BasicCompletion(provider, "pm.info.iterationCount", "Total iterations"));
        provider.addCompletion(new BasicCompletion(provider, "pm.info.requestName", "Request name"));
        provider.addCompletion(new BasicCompletion(provider, "pm.info.requestId", "Request ID"));
    }

    /**
     * 添加 pm.sendRequest - 发送HTTP请求
     */
    private static void addPmSendRequest(DefaultCompletionProvider provider) {
        provider.addCompletion(new ShorthandCompletion(provider, "sendreq",
                """
                pm.sendRequest("${url}", function (err, response) {
                    if (err) {
                        console.log(err);
                    } else {
                        console.log(response.json());
                    }
                });""",
                "pm.sendRequest() template"));
    }

    /**
     * Add built-in data store APIs and plugin-provided script APIs.
     */
    private static void addPmDataStoreApis(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "pm.elasticsearch", "Elasticsearch script API"));
        provider.addCompletion(new BasicCompletion(provider, "pm.elasticsearch.request", "pm.elasticsearch.request(options)"));
        provider.addCompletion(new BasicCompletion(provider, "pm.elasticsearch.query", "pm.elasticsearch.query(options)"));
        provider.addCompletion(new ShorthandCompletion(provider, "es.query",
                """
                const resp = pm.elasticsearch.request({
                  baseUrl: "http://localhost:9200",
                  method: "GET",
                  path: "/orders/_search",
                  body: JSON.stringify({ query: { match_all: {} }, size: 1 })
                });
                pm.test("ES query success", function () {
                  pm.expect(resp.code).to.equal(200);
                });""",
                "Elasticsearch query + assert"));

        provider.addCompletion(new BasicCompletion(provider, "pm.influxdb", "InfluxDB script API"));
        provider.addCompletion(new BasicCompletion(provider, "pm.influxdb.query", "pm.influxdb.query(options)"));
        provider.addCompletion(new BasicCompletion(provider, "pm.influxdb.write", "pm.influxdb.write(options)"));
        provider.addCompletion(new BasicCompletion(provider, "pm.influxdb.request", "pm.influxdb.request(options)"));
        provider.addCompletion(new ShorthandCompletion(provider, "influx.query",
                """
                const resp = pm.influxdb.query({
                  baseUrl: "http://localhost:8086",
                  version: "v1",
                  db: "metrics",
                  username: pm.environment.get("influxUser"),
                  password: pm.environment.get("influxPassword"),
                  query: "SELECT * FROM cpu ORDER BY time DESC LIMIT 1"
                });
                pm.test("Influx query success", function () {
                  pm.expect(resp.code).to.equal(200);
                  pm.expect(resp.json).to.exist();
                });""",
                "InfluxDB query + assert"));
        provider.addCompletion(new ShorthandCompletion(provider, "influx.write",
                """
                const writeResp = pm.influxdb.write({
                  baseUrl: "http://localhost:8086",
                  version: "v1",
                  db: "metrics",
                  username: pm.environment.get("influxUser"),
                  password: pm.environment.get("influxPassword"),
                  precision: "ms",
                  lineProtocol: "api_requests,service=order count=1i " + Date.now()
                });
                pm.test("Influx write success", function () {
                  pm.expect(writeResp.code).to.equal(204);
                });""",
                "InfluxDB write + assert"));
    }

    // ========================================
    // Chai 断言库
    // ========================================

    /**
     * 添加 Chai 断言库（BDD/TDD 风格）
     */
    private static void addChaiAssertions(DefaultCompletionProvider provider) {
        // 基础断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.equal",
                "pm.expect(${value}).to.equal(${expected})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_EQUAL)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.eql",
                "pm.expect(${value}).to.eql(${expected})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_EQL)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.deep.equal",
                "pm.expect(${value}).to.deep.equal(${expected})",
                "Deep equality assertion"));

        // 包含断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.include",
                "pm.expect(${text}).to.include(\"${substring}\")",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_INCLUDE)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.contain",
                "pm.expect(${array}).to.contain(${item})",
                "Check if contains item"));

        // 属性断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.property",
                "pm.expect(${object}).to.have.property(\"${propertyName}\")",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_HAVE_PROPERTY)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.own.property",
                "pm.expect(${object}).to.have.own.property(\"${propertyName}\")",
                "Check if has own property"));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.nested.property",
                "pm.expect(${object}).to.have.nested.property(\"${path}\")",
                "Check if has nested property"));

        // 数值比较断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.above",
                "pm.expect(${value}).to.be.above(${min})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_ABOVE)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.below",
                "pm.expect(${value}).to.be.below(${max})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_BELOW)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.least",
                "pm.expect(${value}).to.be.at.least(${min})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_AT_LEAST)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.most",
                "pm.expect(${value}).to.be.at.most(${max})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_AT_MOST)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.within",
                "pm.expect(${value}).to.be.within(${min}, ${max})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_WITHIN)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.closeTo",
                "pm.expect(${actual}).to.be.closeTo(${expected}, ${delta})",
                "Check if close to value"));

        // 类型断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.a",
                "pm.expect(${value}).to.be.a(\"${type}\")",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_A)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.an",
                "pm.expect(${value}).to.be.an(\"${type}\")",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_AN)));
        // 长度断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.length",
                "pm.expect(${array}).to.have.length(${expectedLength})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_HAVE_LENGTH)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.lengthOf",
                "pm.expect(${array}).to.have.lengthOf(${expectedLength})",
                "Check length of array/string"));

        // 匹配断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.match",
                "pm.expect(${text}).to.match(/${regex}/)",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_MATCH)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.string",
                "pm.expect(${text}).to.have.string(\"${substring}\")",
                "Check if has substring"));

        // 数组/对象断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.keys",
                "pm.expect(${object}).to.have.keys([\"${key1}\", \"${key2}\"])",
                "Check if has keys"));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.all.keys",
                "pm.expect(${object}).to.have.all.keys([\"${key1}\", \"${key2}\"])",
                "Check if has all keys"));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.any.keys",
                "pm.expect(${object}).to.have.any.keys([\"${key1}\", \"${key2}\"])",
                "Check if has any keys"));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.members",
                "pm.expect(${array}).to.have.members([${item1}, ${item2}])",
                "Check if has members"));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.oneOf",
                "pm.expect(${value}).to.be.oneOf([${val1}, ${val2}])",
                "Check if one of values"));

        // 状态断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.ok",
                "pm.expect(${value}).to.be.ok",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_OK)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.exist",
                "pm.expect(${value}).to.exist",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_EXIST)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.empty",
                "pm.expect(${value}).to.be.empty",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_EMPTY)));

        // 布尔值断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.true",
                "pm.expect(${value}).to.be.true",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_TRUE)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.false",
                "pm.expect(${value}).to.be.false",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_FALSE)));

        // 特殊值断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.null",
                "pm.expect(${value}).to.be.null",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_NULL)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.undefined",
                "pm.expect(${value}).to.be.undefined",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_UNDEFINED)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.NaN",
                "pm.expect(${value}).to.be.NaN",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_TO_BE_NAN)));

        // 否定断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.not",
                "pm.expect(${value}).to.not.equal(${unexpected})",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_PM_EXPECT_NOT)));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.not.exist",
                "pm.expect(${value}).to.not.exist",
                "Assert value does not exist"));

        // 响应断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.respondTo",
                "pm.expect(${object}).to.respondTo(\"${method}\")",
                "Assert object responds to method"));

        // 满足条件
        provider.addCompletion(new ShorthandCompletion(provider, "expect.satisfy",
                "pm.expect(${value}).to.satisfy(function(val) { return val > 0; })",
                "Assert satisfies custom condition"));

        // 变化断言
        provider.addCompletion(new ShorthandCompletion(provider, "expect.change",
                "pm.expect(${fn}).to.change(${object}, \"${property}\")",
                "Assert function changes property"));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.increase",
                "pm.expect(${fn}).to.increase(${object}, \"${property}\")",
                "Assert function increases property"));
        provider.addCompletion(new ShorthandCompletion(provider, "expect.decrease",
                "pm.expect(${fn}).to.decrease(${object}, \"${property}\")",
                "Assert function decreases property"));
    }

    // ========================================
    // 第三方库
    // ========================================

    /**
     * 添加 CryptoJS 加密库
     */
    private static void addCryptoJS(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS)));

        // 加密算法
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.AES",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS_AES)));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.AES.encrypt", "AES encrypt"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.AES.decrypt", "AES decrypt"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.DES",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS_DES)));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.DES.encrypt", "DES encrypt"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.DES.decrypt", "DES decrypt"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.TripleDES", "TripleDES encryption"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.RC4", "RC4 encryption"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.Rabbit", "Rabbit encryption"));

        // 哈希算法
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.MD5",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS_MD5)));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.SHA1",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS_SHA1)));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.SHA256",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS_SHA256)));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.SHA224", "SHA-224 hash"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.SHA384", "SHA-384 hash"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.SHA512", "SHA-512 hash"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.SHA3", "SHA-3 hash"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.RIPEMD160", "RIPEMD-160 hash"));

        // HMAC
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.HmacMD5", "HMAC-MD5"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.HmacSHA1", "HMAC-SHA1"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.HmacSHA256",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS_HMAC_SHA256)));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.HmacSHA224", "HMAC-SHA224"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.HmacSHA384", "HMAC-SHA384"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.HmacSHA512", "HMAC-SHA512"));

        // 编码
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.enc",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS_ENC)));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.enc.Hex", "Hex encoding"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.enc.Base64", "Base64 encoding"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.enc.Latin1", "Latin1 encoding"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.enc.Utf8", "UTF-8 encoding"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.enc.Utf16", "UTF-16 encoding"));

        // 其他
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.lib.WordArray",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_CRYPTOJS_WORD_ARRAY)));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.pad.Pkcs7", "PKCS7 padding"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.pad.ZeroPadding", "Zero padding"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.mode.CBC", "CBC mode"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.mode.ECB", "ECB mode"));
        provider.addCompletion(new BasicCompletion(provider, "CryptoJS.mode.CFB", "CFB mode"));
    }

    /**
     * 添加 Lodash 工具库
     */
    private static void addLodash(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "_",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH)));

        // 数组方法
        provider.addCompletion(new BasicCompletion(provider, "_.chunk", "Split array into chunks"));
        provider.addCompletion(new BasicCompletion(provider, "_.compact", "Remove falsy values"));
        provider.addCompletion(new BasicCompletion(provider, "_.concat", "Concatenate arrays"));
        provider.addCompletion(new BasicCompletion(provider, "_.difference", "Array difference"));
        provider.addCompletion(new BasicCompletion(provider, "_.drop", "Drop first n elements"));
        provider.addCompletion(new BasicCompletion(provider, "_.dropRight", "Drop last n elements"));
        provider.addCompletion(new BasicCompletion(provider, "_.fill", "Fill array with value"));
        provider.addCompletion(new BasicCompletion(provider, "_.flatten", "Flatten array one level"));
        provider.addCompletion(new BasicCompletion(provider, "_.flattenDeep", "Recursively flatten array"));
        provider.addCompletion(new BasicCompletion(provider, "_.intersection", "Array intersection"));
        provider.addCompletion(new BasicCompletion(provider, "_.join", "Join array elements"));
        provider.addCompletion(new BasicCompletion(provider, "_.pull", "Remove values from array"));
        provider.addCompletion(new BasicCompletion(provider, "_.reverse", "Reverse array"));
        provider.addCompletion(new BasicCompletion(provider, "_.slice", "Slice array"));
        provider.addCompletion(new BasicCompletion(provider, "_.take", "Take first n elements"));
        provider.addCompletion(new BasicCompletion(provider, "_.union", "Array union"));
        provider.addCompletion(new BasicCompletion(provider, "_.uniq",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_UNIQ)));
        provider.addCompletion(new BasicCompletion(provider, "_.uniqBy", "Unique by iteratee"));
        provider.addCompletion(new BasicCompletion(provider, "_.without", "Filter out values"));
        provider.addCompletion(new BasicCompletion(provider, "_.zip", "Zip arrays"));
        provider.addCompletion(new BasicCompletion(provider, "_.zipObject", "Create object from arrays"));

        // 集合方法
        provider.addCompletion(new BasicCompletion(provider, "_.countBy", "Count by criterion"));
        provider.addCompletion(new BasicCompletion(provider, "_.every", "Test if all pass"));
        provider.addCompletion(new BasicCompletion(provider, "_.filter",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_FILTER)));
        provider.addCompletion(new BasicCompletion(provider, "_.find",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_FIND)));
        provider.addCompletion(new BasicCompletion(provider, "_.findLast", "Find from end"));
        provider.addCompletion(new BasicCompletion(provider, "_.forEach", "Iterate collection"));
        provider.addCompletion(new BasicCompletion(provider, "_.groupBy",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_GROUP_BY)));
        provider.addCompletion(new BasicCompletion(provider, "_.includes", "Check if contains"));
        provider.addCompletion(new BasicCompletion(provider, "_.keyBy", "Create object indexed by key"));
        provider.addCompletion(new BasicCompletion(provider, "_.map",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_MAP)));
        provider.addCompletion(new BasicCompletion(provider, "_.orderBy", "Order by criteria"));
        provider.addCompletion(new BasicCompletion(provider, "_.partition", "Partition by predicate"));
        provider.addCompletion(new BasicCompletion(provider, "_.reduce", "Reduce collection"));
        provider.addCompletion(new BasicCompletion(provider, "_.reject", "Opposite of filter"));
        provider.addCompletion(new BasicCompletion(provider, "_.sample",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_SAMPLE)));
        provider.addCompletion(new BasicCompletion(provider, "_.sampleSize", "Random sample of size n"));
        provider.addCompletion(new BasicCompletion(provider, "_.shuffle", "Shuffle collection"));
        provider.addCompletion(new BasicCompletion(provider, "_.size", "Get collection size"));
        provider.addCompletion(new BasicCompletion(provider, "_.some", "Test if any pass"));
        provider.addCompletion(new BasicCompletion(provider, "_.sortBy",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_SORT_BY)));

        // 对象方法
        provider.addCompletion(new BasicCompletion(provider, "_.assign", "Assign properties"));
        provider.addCompletion(new BasicCompletion(provider, "_.at", "Get values at paths"));
        provider.addCompletion(new BasicCompletion(provider, "_.clone", "Shallow clone"));
        provider.addCompletion(new BasicCompletion(provider, "_.cloneDeep",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_CLONE_DEEP)));
        provider.addCompletion(new BasicCompletion(provider, "_.defaults", "Assign default values"));
        provider.addCompletion(new BasicCompletion(provider, "_.findKey", "Find key by predicate"));
        provider.addCompletion(new BasicCompletion(provider, "_.get",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_GET)));
        provider.addCompletion(new BasicCompletion(provider, "_.has",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_HAS)));
        provider.addCompletion(new BasicCompletion(provider, "_.invert", "Invert object keys/values"));
        provider.addCompletion(new BasicCompletion(provider, "_.keys", "Get object keys"));
        provider.addCompletion(new BasicCompletion(provider, "_.merge",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_MERGE)));
        provider.addCompletion(new BasicCompletion(provider, "_.omit",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_OMIT)));
        provider.addCompletion(new BasicCompletion(provider, "_.pick",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_PICK)));
        provider.addCompletion(new BasicCompletion(provider, "_.set", "Set value at path"));
        provider.addCompletion(new BasicCompletion(provider, "_.toPairs", "Convert to key-value pairs"));
        provider.addCompletion(new BasicCompletion(provider, "_.values", "Get object values"));

        // 字符串方法
        provider.addCompletion(new BasicCompletion(provider, "_.camelCase", "Convert to camelCase"));
        provider.addCompletion(new BasicCompletion(provider, "_.capitalize", "Capitalize first letter"));
        provider.addCompletion(new BasicCompletion(provider, "_.escape", "Escape HTML entities"));
        provider.addCompletion(new BasicCompletion(provider, "_.kebabCase", "Convert to kebab-case"));
        provider.addCompletion(new BasicCompletion(provider, "_.lowerCase", "Convert to lower case"));
        provider.addCompletion(new BasicCompletion(provider, "_.pad", "Pad string"));
        provider.addCompletion(new BasicCompletion(provider, "_.repeat", "Repeat string"));
        provider.addCompletion(new BasicCompletion(provider, "_.replace", "Replace string"));
        provider.addCompletion(new BasicCompletion(provider, "_.snakeCase", "Convert to snake_case"));
        provider.addCompletion(new BasicCompletion(provider, "_.split", "Split string"));
        provider.addCompletion(new BasicCompletion(provider, "_.startCase", "Convert to Start Case"));
        provider.addCompletion(new BasicCompletion(provider, "_.toLower", "Convert to lowercase"));
        provider.addCompletion(new BasicCompletion(provider, "_.toUpper", "Convert to uppercase"));
        provider.addCompletion(new BasicCompletion(provider, "_.trim", "Trim whitespace"));
        provider.addCompletion(new BasicCompletion(provider, "_.truncate", "Truncate string"));
        provider.addCompletion(new BasicCompletion(provider, "_.unescape", "Unescape HTML entities"));
        provider.addCompletion(new BasicCompletion(provider, "_.upperCase", "Convert to upper case"));

        // 数学方法
        provider.addCompletion(new BasicCompletion(provider, "_.add", "Add numbers"));
        provider.addCompletion(new BasicCompletion(provider, "_.ceil", "Round up"));
        provider.addCompletion(new BasicCompletion(provider, "_.divide", "Divide numbers"));
        provider.addCompletion(new BasicCompletion(provider, "_.floor", "Round down"));
        provider.addCompletion(new BasicCompletion(provider, "_.max", "Get maximum"));
        provider.addCompletion(new BasicCompletion(provider, "_.maxBy", "Get maximum by iteratee"));
        provider.addCompletion(new BasicCompletion(provider, "_.mean", "Get mean"));
        provider.addCompletion(new BasicCompletion(provider, "_.meanBy",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_MEAN_BY)));
        provider.addCompletion(new BasicCompletion(provider, "_.min", "Get minimum"));
        provider.addCompletion(new BasicCompletion(provider, "_.minBy", "Get minimum by iteratee"));
        provider.addCompletion(new BasicCompletion(provider, "_.multiply", "Multiply numbers"));
        provider.addCompletion(new BasicCompletion(provider, "_.random",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_RANDOM)));
        provider.addCompletion(new BasicCompletion(provider, "_.round", "Round number"));
        provider.addCompletion(new BasicCompletion(provider, "_.subtract", "Subtract numbers"));
        provider.addCompletion(new BasicCompletion(provider, "_.sum", "Sum numbers"));
        provider.addCompletion(new BasicCompletion(provider, "_.sumBy",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_LODASH_SUM_BY)));

        // 实用方法
        provider.addCompletion(new BasicCompletion(provider, "_.debounce", "Debounce function"));
        provider.addCompletion(new BasicCompletion(provider, "_.delay", "Delay function execution"));
        provider.addCompletion(new BasicCompletion(provider, "_.isArray", "Check if array"));
        provider.addCompletion(new BasicCompletion(provider, "_.isBoolean", "Check if boolean"));
        provider.addCompletion(new BasicCompletion(provider, "_.isDate", "Check if date"));
        provider.addCompletion(new BasicCompletion(provider, "_.isEmpty", "Check if empty"));
        provider.addCompletion(new BasicCompletion(provider, "_.isEqual", "Deep comparison"));
        provider.addCompletion(new BasicCompletion(provider, "_.isFunction", "Check if function"));
        provider.addCompletion(new BasicCompletion(provider, "_.isNil", "Check if null/undefined"));
        provider.addCompletion(new BasicCompletion(provider, "_.isNull", "Check if null"));
        provider.addCompletion(new BasicCompletion(provider, "_.isNumber", "Check if number"));
        provider.addCompletion(new BasicCompletion(provider, "_.isObject", "Check if object"));
        provider.addCompletion(new BasicCompletion(provider, "_.isString", "Check if string"));
        provider.addCompletion(new BasicCompletion(provider, "_.isUndefined", "Check if undefined"));
        provider.addCompletion(new BasicCompletion(provider, "_.range", "Create range of numbers"));
        provider.addCompletion(new BasicCompletion(provider, "_.throttle", "Throttle function"));
        provider.addCompletion(new BasicCompletion(provider, "_.times", "Invoke function n times"));
        provider.addCompletion(new BasicCompletion(provider, "_.uniqueId", "Generate unique ID"));
    }

    /**
     * 添加 Moment.js 时间处理库
     */
    private static void addMoment(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "moment",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT)));
        provider.addCompletion(new BasicCompletion(provider, "moment()",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_CALL)));

        // 解析
        provider.addCompletion(new BasicCompletion(provider, "moment.utc", "Parse as UTC"));
        provider.addCompletion(new BasicCompletion(provider, "moment.unix", "Parse unix timestamp"));

        // 格式化
        provider.addCompletion(new BasicCompletion(provider, "format",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_FORMAT)));
        provider.addCompletion(new BasicCompletion(provider, "fromNow", "Time from now"));
        provider.addCompletion(new BasicCompletion(provider, "toNow", "Time to now"));
        provider.addCompletion(new BasicCompletion(provider, "from", "Time from date"));
        provider.addCompletion(new BasicCompletion(provider, "to", "Time to date"));
        provider.addCompletion(new BasicCompletion(provider, "calendar", "Calendar time"));

        // 获取/设置
        provider.addCompletion(new BasicCompletion(provider, "millisecond", "Get/set millisecond"));
        provider.addCompletion(new BasicCompletion(provider, "second", "Get/set second"));
        provider.addCompletion(new BasicCompletion(provider, "minute", "Get/set minute"));
        provider.addCompletion(new BasicCompletion(provider, "hour", "Get/set hour"));
        provider.addCompletion(new BasicCompletion(provider, "date", "Get/set date"));
        provider.addCompletion(new BasicCompletion(provider, "day", "Get/set day of week"));
        provider.addCompletion(new BasicCompletion(provider, "weekday", "Get/set weekday"));
        provider.addCompletion(new BasicCompletion(provider, "isoWeekday", "Get/set ISO weekday"));
        provider.addCompletion(new BasicCompletion(provider, "dayOfYear", "Get/set day of year"));
        provider.addCompletion(new BasicCompletion(provider, "week", "Get/set week"));
        provider.addCompletion(new BasicCompletion(provider, "isoWeek", "Get/set ISO week"));
        provider.addCompletion(new BasicCompletion(provider, "month", "Get/set month"));
        provider.addCompletion(new BasicCompletion(provider, "quarter", "Get/set quarter"));
        provider.addCompletion(new BasicCompletion(provider, "year", "Get/set year"));
        provider.addCompletion(new BasicCompletion(provider, "weekYear", "Get/set week year"));
        provider.addCompletion(new BasicCompletion(provider, "isoWeekYear", "Get/set ISO week year"));

        // 操作
        provider.addCompletion(new BasicCompletion(provider, "add",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_ADD)));
        provider.addCompletion(new BasicCompletion(provider, "subtract",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_SUBTRACT)));
        provider.addCompletion(new BasicCompletion(provider, "startOf",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_START_OF)));
        provider.addCompletion(new BasicCompletion(provider, "endOf",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_END_OF)));
        provider.addCompletion(new BasicCompletion(provider, "local", "Convert to local"));
        provider.addCompletion(new BasicCompletion(provider, "utc", "Convert to UTC"));
        provider.addCompletion(new BasicCompletion(provider, "utcOffset", "Get/set UTC offset"));

        // 比较
        provider.addCompletion(new BasicCompletion(provider, "isBefore",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_IS_BEFORE)));
        provider.addCompletion(new BasicCompletion(provider, "isAfter",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_IS_AFTER)));
        provider.addCompletion(new BasicCompletion(provider, "isSame",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_IS_SAME)));
        provider.addCompletion(new BasicCompletion(provider, "isSameOrBefore", "Check if same or before"));
        provider.addCompletion(new BasicCompletion(provider, "isSameOrAfter", "Check if same or after"));
        provider.addCompletion(new BasicCompletion(provider, "isBetween", "Check if between"));
        provider.addCompletion(new BasicCompletion(provider, "isDST", "Check if DST"));
        provider.addCompletion(new BasicCompletion(provider, "isLeapYear", "Check if leap year"));

        // 差异
        provider.addCompletion(new BasicCompletion(provider, "diff",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_DIFF)));

        // 转换
        provider.addCompletion(new BasicCompletion(provider, "valueOf",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_VALUE_OF)));
        provider.addCompletion(new BasicCompletion(provider, "unix",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_UNIX)));
        provider.addCompletion(new BasicCompletion(provider, "toDate", "Convert to JavaScript Date"));
        provider.addCompletion(new BasicCompletion(provider, "toArray", "Convert to array"));
        provider.addCompletion(new BasicCompletion(provider, "toJSON", "Convert to JSON"));
        provider.addCompletion(new BasicCompletion(provider, "toISOString",
                I18nUtil.getMessage(MessageKeys.AUTOCOMPLETE_MOMENT_TO_ISO_STRING)));
        provider.addCompletion(new BasicCompletion(provider, "toObject", "Convert to object"));
        provider.addCompletion(new BasicCompletion(provider, "toString", "Convert to string"));

        // 查询
        provider.addCompletion(new BasicCompletion(provider, "isValid", "Check if valid"));
        provider.addCompletion(new BasicCompletion(provider, "locale", "Get/set locale"));
        provider.addCompletion(new BasicCompletion(provider, "daysInMonth", "Get days in month"));
    }

    /**
     * 添加 Cheerio HTML/XML 解析库
     */
    private static void addCheerio(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "cheerio", "Cheerio HTML/XML parser"));
        provider.addCompletion(new BasicCompletion(provider, "cheerio.load", "Load HTML/XML"));
        provider.addCompletion(new BasicCompletion(provider, "$", "jQuery-like selector (after load)"));
        provider.addCompletion(new BasicCompletion(provider, "html", "Get/set HTML"));
        provider.addCompletion(new BasicCompletion(provider, "text", "Get/set text"));
        provider.addCompletion(new BasicCompletion(provider, "attr", "Get/set attribute"));
        provider.addCompletion(new BasicCompletion(provider, "find", "Find elements"));
        provider.addCompletion(new BasicCompletion(provider, "each", "Iterate elements"));
        provider.addCompletion(new BasicCompletion(provider, "eq", "Get element at index"));
        provider.addCompletion(new BasicCompletion(provider, "first", "Get first element"));
        provider.addCompletion(new BasicCompletion(provider, "last", "Get last element"));
        provider.addCompletion(new BasicCompletion(provider, "parent", "Get parent"));
        provider.addCompletion(new BasicCompletion(provider, "children", "Get children"));
        provider.addCompletion(new BasicCompletion(provider, "siblings", "Get siblings"));
        provider.addCompletion(new BasicCompletion(provider, "next", "Get next sibling"));
        provider.addCompletion(new BasicCompletion(provider, "prev", "Get previous sibling"));
    }

    /**
     * 添加 xml2Json 库
     */
    private static void addXml2Json(DefaultCompletionProvider provider) {
        provider.addCompletion(new BasicCompletion(provider, "xml2Json", "Convert XML to JSON"));
        provider.addCompletion(new BasicCompletion(provider, "xml2Json.parse", "Parse XML string to JSON"));
        provider.addCompletion(new BasicCompletion(provider, "xml2Json.toJson", "Convert XML to JSON object"));
    }
}
