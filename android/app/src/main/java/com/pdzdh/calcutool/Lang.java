package com.pdzdh.calcutool;

/** Runtime EN/CN toggle, mirroring the EN / 中 button of the desktop build. */
final class Lang {
    private static boolean en = false;   // Chinese is the default UI language

    private Lang() {}

    static boolean isEn() { return en; }
    static void setEn(boolean v) { en = v; }

    /** @param key semantic key, see the switch below */
    static String t(String key) {
        if (en) {
            switch (key) {
                case "title": return "3-Phase AC Analyzer";
                case "input": return "INPUT PARAMETERS";
                case "magnitude": return "MAGNITUDE";
                case "angle": return "ANGLE (°)";
                case "results": return "ANALYSIS RESULTS";
                case "chart": return "PHASOR DIAGRAM";
                case "run": return "Run Analysis";
                case "vseq": return "VOLTAGE SEQUENCES";
                case "iseq": return "CURRENT SEQUENCES";
                case "unbal": return "UNBALANCE";
                case "power": return "POWER (3-PHASE TOTAL)";
                case "powerPh": return "POWER (PER PHASE)";
                case "pos3": return "Positive seq  3U₁";
                case "pos1": return "Positive seq  U₁";
                case "neg3": return "Negative seq  3U₂";
                case "neg1": return "Negative seq  U₂";
                case "zero3": return "Zero seq  3U₀";
                case "zero1": return "Zero seq  U₀";
                case "pos3i": return "Positive seq  3I₁";
                case "pos1i": return "Positive seq  I₁";
                case "neg3i": return "Negative seq  3I₂";
                case "neg1i": return "Negative seq  I₂";
                case "zero3i": return "Zero seq  3I₀";
                case "zero1i": return "Zero seq  I₀";
                case "vUnbal": return "Voltage unbalance";
                case "vZero": return "Voltage zero ratio";
                case "iUnbal": return "Current unbalance";
                case "iZero": return "Current zero ratio";
                case "pAct": return "Active  P";
                case "pReact": return "Reactive  Q";
                case "pApp": return "Apparent  S";
                case "pPf": return "Power factor  PF";
                case "seqLegend": return "Sequence";
                case "about": return "ABOUT";
                case "author": return "Author";
                case "wechat": return "WeChat";
                case "copied": return "WeChat ID copied";
                case "version": return "Version";
                case "qrSection": return "TERMINAL QR";
                case "qrScan": return "Scan terminal QR code";
                case "qrScanSub": return "Reads type / vendor / model / ID / hardware version / date";
                default: return key;
            }
        }
        switch (key) {
            case "title": return "三相交流分析器";
            case "input": return "输入参数";
            case "magnitude": return "幅值";
            case "angle": return "角度 (°)";
            case "results": return "计算结果";
            case "chart": return "相量图";
            case "run": return "开始计算";
            case "vseq": return "电压序分量";
            case "iseq": return "电流序分量";
            case "unbal": return "不平衡度";
            case "power": return "三相总功率";
            case "powerPh": return "分相功率";
            case "pos3": return "正序 3U₁";
            case "pos1": return "正序 U₁";
            case "neg3": return "负序 3U₂";
            case "neg1": return "负序 U₂";
            case "zero3": return "零序 3U₀";
            case "zero1": return "零序 U₀";
            case "pos3i": return "正序 3I₁";
            case "pos1i": return "正序 I₁";
            case "neg3i": return "负序 3I₂";
            case "neg1i": return "负序 I₂";
            case "zero3i": return "零序 3I₀";
            case "zero1i": return "零序 I₀";
            case "vUnbal": return "电压不平衡度";
            case "vZero": return "零序电压含有率";
            case "iUnbal": return "电流不平衡度";
            case "iZero": return "零序电流含有率";
            case "pAct": return "有功功率 P";
            case "pReact": return "无功功率 Q";
            case "pApp": return "视在功率 S";
            case "pPf": return "总功率因数 PF";
            case "seqLegend": return "序分量";
            case "about": return "关于";
            case "author": return "作者";
            case "wechat": return "微信";
            case "copied": return "微信号已复制";
            case "version": return "版本";
            case "qrSection": return "终端二维码";
            case "qrScan": return "扫描终端二维码";
            case "qrScanSub": return "读取类型 / 厂商 / 型号 / ID / 硬件版本 / 生产日期";
            default: return key;
        }
    }
}
