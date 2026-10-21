package com.pdzdh.calcutool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 配电终端二维码（表 5 二维码信息结构）解析与校验。
 *
 * <p>合法内容形如：
 * {@code 类型：D30，厂商：XX集团，型号：DTU3001，ID：D30XXXXXX130201302080112，硬件版本：HV20.11，生产日期：2013年2月8日}</p>
 *
 * <p>只接受中文标点：字段分隔符为中文逗号 {@code ，}，键值分隔符为中文冒号 {@code ：}；
 * 不允许出现空格（含全角空格/制表符/换行）以及英文逗号 {@code ,} 与英文冒号 {@code :}。</p>
 */
public final class QrTerminalParser {

    /** 表 5 的代码名称，同时作为展示顺序。 */
    public static final String[] FIELD_LABELS = {
            "终端类型", "厂商代码", "终端型号", "ID号", "硬件版本", "生产日期"
    };

    /** 每个字段可接受的写法（第 0 个为规范名称）。 */
    private static final String[][] ALIASES = {
            {"类型", "终端类型"},
            {"厂商", "厂商代码"},
            {"型号", "终端型号"},
            {"ID", "ID号"},
            {"硬件版本"},
            {"生产日期"}
    };

    private static final char SEP = '，';
    private static final char COLON = '：';

    private static final Pattern DATE = Pattern.compile("^\\d{4}年\\d{1,2}月\\d{1,2}日$");
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9\\-_/]{6,40}$");
    private static final Pattern HARDWARE = Pattern.compile("^[A-Za-z0-9._\\-/]{1,24}$");

    private QrTerminalParser() {}

    public static final class Result {
        public final boolean ok;
        public final String raw;
        /** 校验失败原因（ok=false 时有效）。 */
        public final String error;
        /** 违规字符位置（1 起），0 表示不涉及位置。 */
        public final int errorIndex;
        /** 按表 5 顺序排列的 字段名 -> 内容。 */
        public final LinkedHashMap<String, String> values;

        Result(boolean ok, String raw, String error, int errorIndex,
               LinkedHashMap<String, String> values) {
            this.ok = ok;
            this.raw = raw == null ? "" : raw;
            this.error = error;
            this.errorIndex = errorIndex;
            this.values = values;
        }
    }

    public static Result parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return fail(raw, "内容为空，未读取到二维码数据", 0);
        }

        // 1. 空白与英文标点检查（逐字符，便于指出位置）
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\u00A0' || c == '\u3000' || c == '\t'
                    || c == '\n' || c == '\r') {
                return fail(raw, "不允许出现空格（第 " + (i + 1) + " 个字符附近）", i + 1);
            }
            if (c == ',') {
                return fail(raw, "不允许使用英文逗号「,」，请改用中文逗号「，」（第 "
                        + (i + 1) + " 个字符）", i + 1);
            }
            if (c == ':') {
                return fail(raw, "不允许使用英文冒号「:」，请改用中文冒号「：」（第 "
                        + (i + 1) + " 个字符）", i + 1);
            }
            if (c == ';') {
                return fail(raw, "不允许使用英文分号「;」，字段之间请用中文逗号「，」（第 "
                        + (i + 1) + " 个字符）", i + 1);
            }
        }

        // 2. 分隔与字段数量
        String[] parts = raw.split(String.valueOf(SEP), -1);
        if (parts.length != FIELD_LABELS.length) {
            return fail(raw, "字段数量应为 " + FIELD_LABELS.length + " 个，实际 "
                    + parts.length + " 个；字段之间必须用中文逗号「，」分隔", 0);
        }

        // 3. 逐项解析 键:值
        Map<String, String> keyed = new LinkedHashMap<>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                return fail(raw, "第 " + (i + 1) + " 项为空（多余的中文逗号？）", 0);
            }
            int colon = part.indexOf(COLON);
            if (colon <= 0 || colon == part.length() - 1) {
                return fail(raw, "第 " + (i + 1) + " 项格式应为「字段名：内容」，实际为「"
                        + part + "」", 0);
            }
            String key = part.substring(0, colon);
            String value = part.substring(colon + 1);
            String label = labelOf(key);
            if (label == null) {
                return fail(raw, "第 " + (i + 1) + " 项字段名「" + key
                        + "」不是有效字段名（可用：类型/厂商/型号/ID/硬件版本/生产日期，键值之间用中文冒号）", 0);
            }
            if (keyed.containsKey(label)) {
                return fail(raw, "字段「" + label + "」重复出现", 0);
            }
            keyed.put(label, value);
        }

        // 4. 必填项完整性（顺序无关）
        List<String> missing = new ArrayList<>();
        for (String label : FIELD_LABELS) {
            if (!keyed.containsKey(label)) missing.add(label);
        }
        if (!missing.isEmpty()) {
            return fail(raw, "缺少字段：" + join(missing), 0);
        }

        // 5. 取值格式
        String date = keyed.get("生产日期");
        if (!DATE.matcher(date).matches()) {
            return fail(raw, "生产日期「" + date + "」格式应为 YYYY年M月D日", 0);
        }
        String id = keyed.get("ID号");
        if (!DEVICE_ID.matcher(id).matches()) {
            return fail(raw, "ID号「" + id + "」应为 6~40 位字母、数字或 - _ / 组合", 0);
        }
        String hw = keyed.get("硬件版本");
        if (!HARDWARE.matcher(hw).matches()) {
            return fail(raw, "硬件版本「" + hw + "」应为 1~24 位字母、数字或 . - _ / 组合", 0);
        }

        // 6. 按表 5 顺序输出
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String label : FIELD_LABELS) {
            ordered.put(label, keyed.get(label));
        }
        return new Result(true, raw, null, 0, ordered);
    }

    /** 字段名（含别名）映射到表 5 代码名称；不认识时返回 null。 */
    static String labelOf(String key) {
        for (int i = 0; i < ALIASES.length; i++) {
            for (String alias : ALIASES[i]) {
                if (alias.equalsIgnoreCase(key)) return FIELD_LABELS[i];
            }
        }
        return null;
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append('、');
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static Result fail(String raw, String error, int index) {
        return new Result(false, raw, error, index, new LinkedHashMap<>());
    }
}
