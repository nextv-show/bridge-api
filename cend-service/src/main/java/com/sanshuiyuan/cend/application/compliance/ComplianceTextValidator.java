package com.sanshuiyuan.cend.application.compliance;

import com.sanshuiyuan.cend.common.ComplianceException;
import org.springframework.stereotype.Component;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 合规文案校验器（金融监管铁律，脚手架公共层，103/104/105 共享）。
 *
 * <p>词表（[ASSUMPTION-Q4] 内置常量）：ROI/回报率/收益率/收益/分红/理财/投资/保本/固收/保息/收益凭证/投资合同。
 * 归一化（全角→半角、转小写、去空白）后做子串匹配，挡住「ＲＯＩ」「收 益」等变体。
 *
 * <p>关键设计：合法免责声明在「否定语境」下必然出现 投资/理财/收益 等词
 * （如「不构成投资建议或理财产品」「不构成任何收益保证」）。因此扫描前先按
 * <b>长度降序</b>剥离一份「合规短语白名单」，再对剩余文本扫描违禁词——既放行合规声明，
 * 又不给运营留下「把违禁词藏进伪声明」的口子（白名单是精确短语，非整段豁免）。
 */
@Component
public class ComplianceTextValidator {

    /** 违禁词（原文用于告警展示）。 */
    private static final List<String> BANNED = List.of(
            "ROI", "回报率", "收益率", "收益", "分红", "理财", "投资", "保本", "固收", "保息",
            "收益凭证", "投资合同");

    /** 合规短语白名单（扫描前剥离；归一化后比对，长度降序剥离避免残留）。 */
    private static final List<String> ALLOWED_PHRASES = List.of(
            "不构成投资建议或理财产品",
            "不构成任何收益保证",
            "不代表本金回报或利息约定",
            "不构成投资建议",
            "不构成理财产品",
            "不构成投资");

    private static final List<String> ALLOWED_NORMALIZED = ALLOWED_PHRASES.stream()
            .map(ComplianceTextValidator::normalize)
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    /** 归一化违禁词 → 原文，用于命中告警。 */
    private static final Map<String, String> BANNED_NORMALIZED = new java.util.LinkedHashMap<>();

    static {
        for (String w : BANNED) {
            BANNED_NORMALIZED.put(normalize(w), w);
        }
    }

    /** 递归扫描对象图所有字符串字段，命中即抛 {@link ComplianceException}。 */
    public void assertCompliant(Object root) {
        List<String> hits = findViolations(root);
        if (!hits.isEmpty()) {
            throw new ComplianceException(hits);
        }
    }

    /** 递归扫描对象图，返回去重后的命中违禁词列表（合规则为空）。 */
    public List<String> findViolations(Object root) {
        List<String> texts = new ArrayList<>();
        collectStrings(root, texts, new IdentityHashMap<>());
        Set<String> hits = new LinkedHashSet<>();
        for (String t : texts) {
            hits.addAll(scanText(t));
        }
        return new ArrayList<>(hits);
    }

    /** 扫描单段文本：归一化 → 剥离合规白名单短语 → 子串匹配违禁词。 */
    public List<String> scanText(String text) {
        String norm = normalize(text);
        for (String phrase : ALLOWED_NORMALIZED) {
            if (!phrase.isEmpty()) {
                norm = norm.replace(phrase, "");
            }
        }
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, String> e : BANNED_NORMALIZED.entrySet()) {
            if (norm.contains(e.getKey())) {
                hits.add(e.getValue());
            }
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private void collectStrings(Object obj, List<String> out, IdentityHashMap<Object, Boolean> seen) {
        if (obj == null) {
            return;
        }
        if (obj instanceof String s) {
            out.add(s);
            return;
        }
        if (obj instanceof Number || obj instanceof Boolean || obj instanceof Character || obj instanceof Enum<?>) {
            return;
        }
        if (seen.put(obj, Boolean.TRUE) != null) {
            return;
        }
        if (obj instanceof Collection<?> coll) {
            for (Object item : coll) {
                collectStrings(item, out, seen);
            }
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                collectStrings(v, out, seen);
            }
            return;
        }
        if (obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                collectStrings(java.lang.reflect.Array.get(obj, i), out, seen);
            }
            return;
        }
        if (obj.getClass().isRecord()) {
            for (RecordComponent rc : obj.getClass().getRecordComponents()) {
                try {
                    collectStrings(rc.getAccessor().invoke(obj), out, seen);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("合规扫描读取记录字段失败: " + rc.getName(), e);
                }
            }
        }
        // 其它普通对象类型不递归（DTO 图由 record + 集合 + 字符串构成）。
    }

    /** 全角→半角、转小写、去空白。 */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '　') {
                c = ' ';
            } else if (c >= '！' && c <= '～') {
                c = (char) (c - 0xFEE0);
            }
            sb.append(c);
        }
        return sb.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
