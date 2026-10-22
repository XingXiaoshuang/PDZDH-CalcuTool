package com.pdzdh.calcutool.comtrade;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * COMTRADE 配置与数据装载（IEEE C37.111-1991/1999/2013）。
 *
 * <p>cfg 可能是 GB18030（国内装置常见）或 UTF-8，这里先按 UTF-8 严格解码，
 * 失败再回退 GB18030；dat 支持 ASCII / BINARY / BINARY32 / FLOAT32。</p>
 */
public final class ComtradeCase {

    public static final int TYPE_ASCII = 0;
    public static final int TYPE_BINARY = 1;
    public static final int TYPE_BINARY32 = 2;
    public static final int TYPE_FLOAT32 = 3;

    public static final class AnalogChannel {
        public int index;
        public String name = "";
        public String phase = "";
        public String unit = "";
        public double a = 1, b;
        public double min, max;
        public double primary = 1, secondary = 1;
        public char ps = 'S';

        /** 原始值换算到一次值（PS=P 时按一次系数，PS=S 时再乘变比）。 */
        public double scale(double raw) {
            double base = a * raw + b;
            if (ps == 'S' && secondary != 0) return base * primary / secondary;
            return base;
        }

        public double scaleSecondary(double raw) {
            double base = a * raw + b;
            if (ps == 'P' && primary != 0) return base * secondary / primary;
            return base;
        }
    }

    public static final class DigitalChannel {
        public int index;
        public String name = "";
        public String phase = "";
        public int normalState;
    }

    // ── cfg ──────────────────────────────────────────────────────────────────
    public String station = "";
    public String device = "";
    public String revision = "1991";
    public final List<AnalogChannel> analogs = new ArrayList<>();
    public final List<DigitalChannel> digitals = new ArrayList<>();
    public int dataType = TYPE_BINARY;
    public double timeMult = 1.0;
    public double sampleRate = 1000;
    /** 线路额定频率（cfg 的 lf 行），默认 50Hz。 */
    public double lineFreq = 50;
    public int declaredSamples;
    public String startTime = "";
    public String triggerTime = "";
    public String cfgName = "";

    // ── dat ──────────────────────────────────────────────────────────────────
    /** [通道][采样点]，已换算为一次值。 */
    public float[][] analog;
    /** 相对触发时刻的秒数，0 为录波 0 时刻。 */
    public double[] time;
    public int sampleCount;
    public double[] sampleRatePerSample;
    /** [采样点] 数字量字，按通道顺序，第 i 个通道 = (word[i/16] >> (i%16)) & 1。 */
    public boolean[][] digital;
    public int digitalWords;

    public double triggerOffsetSec;
    public int triggerIndex;

    private ComtradeCase() {}

    public int analogCount() { return analogs.size(); }

    public int digitalCount() { return digitals.size(); }

    public double duration() {
        return sampleCount > 1 ? time[sampleCount - 1] - time[0] : 0;
    }

    /** 一次值 -> 二次值 的换算系数。 */
    public double secondaryRatio(int ch) {
        if (ch < 0 || ch >= analogs.size()) return 1;
        AnalogChannel c = analogs.get(ch);
        if (c.primary > 0 && c.secondary > 0) return c.secondary / c.primary;
        return 1;
    }

    public boolean digitalAt(int ch, int sample) {
        if (digital == null || ch >= digitals.size() || sample < 0 || sample >= sampleCount) {
            return false;
        }
        return digital[sample][ch];
    }

    public double valueAt(int ch, int sample) {
        return (analog == null) ? 0 : analog[ch][sample];
    }

    /** 由时间（秒，相对触发）找最近采样点。 */
    public int indexOfTime(double t) {
        if (sampleCount == 0) return 0;
        int lo = 0, hi = sampleCount - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (time[mid] < t) lo = mid + 1; else hi = mid;
        }
        if (lo > 0 && Math.abs(time[lo - 1] - t) < Math.abs(time[lo] - t)) return lo - 1;
        return lo;
    }

    // ── 装载 ─────────────────────────────────────────────────────────────────

    public static ComtradeCase load(byte[] cfgBytes, InputStream datStream, String name)
            throws IOException {
        ComtradeCase c = parseCfg(cfgBytes, name);
        c.readDat(datStream);
        return c;
    }

    /** cfg 解码：优先 UTF-8，失败回退 GB18030。 */
    public static String decode(byte[] bytes) {
        try {
            return Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

    public static ComtradeCase parseCfg(byte[] cfgBytes, String name) throws IOException {
        String text = decode(cfgBytes);
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> ls = new ArrayList<>();
        for (String l : lines) {
            if (l.trim().isEmpty()) continue;
            ls.add(l.trim());
        }
        if (ls.size() < 3) throw new IOException("cfg 内容不完整");

        ComtradeCase c = new ComtradeCase();
        c.cfgName = name;

        String[] head = split(ls.get(0));
        c.station = head.length > 0 ? head[0] : "";
        c.device = head.length > 1 ? head[1] : "";
        c.revision = head.length > 2 ? head[2] : "1991";

        String[] counts = split(ls.get(1));
        int analogCount = 0, digitalCount = 0;
        for (String part : counts) {
            String p = part.toUpperCase();
            if (p.endsWith("A") && isNumber(p.substring(0, p.length() - 1))) {
                analogCount = Integer.parseInt(p.substring(0, p.length() - 1));
            } else if (p.endsWith("D") && isNumber(p.substring(0, p.length() - 1))) {
                digitalCount = Integer.parseInt(p.substring(0, p.length() - 1));
            }
        }
        if (analogCount == 0 && counts.length >= 2 && isNumber(counts[0])) {
            analogCount = Integer.parseInt(counts[0]);
            digitalCount = counts.length > 1 && isNumber(counts[1]) ? Integer.parseInt(counts[1]) : 0;
        }

        int p = 2;
        for (int i = 0; i < analogCount && p < ls.size(); i++, p++) {
            String[] f = split(ls.get(p));
            AnalogChannel ch = new AnalogChannel();
            ch.index = i + 1;
            if (f.length > 1) ch.name = f[1];
            if (f.length > 2) ch.phase = f[2];
            if (f.length > 4) ch.unit = f[4];
            ch.a = num(f, 5, 1);
            ch.b = num(f, 6, 0);
            ch.min = num(f, 8, -32767);
            ch.max = num(f, 9, 32767);
            ch.primary = num(f, 10, 1);
            ch.secondary = num(f, 11, 1);
            if (f.length > 12 && !f[12].isEmpty()) ch.ps = Character.toUpperCase(f[12].charAt(0));
            c.analogs.add(ch);
        }
        for (int i = 0; i < digitalCount && p < ls.size(); i++, p++) {
            String[] f = split(ls.get(p));
            DigitalChannel ch = new DigitalChannel();
            ch.index = i + 1;
            if (f.length > 1) ch.name = f[1];
            if (f.length > 2) ch.phase = f[2];
            if (f.length > 4) ch.normalState = (int) num(f, 4, 0);
            c.digitals.add(ch);
        }

        // lf（线路频率）→ nrates → 采样率段
        if (p < ls.size() && isNumber(ls.get(p))) {
            c.lineFreq = num(split(ls.get(p)), 0, 50);
            p++;
        }
        if (p < ls.size() && ls.get(p).equalsIgnoreCase("nrates")) p++;
        int rates = 0;
        if (p < ls.size() && isNumber(ls.get(p))) {
            rates = (int) num(split(ls.get(p)), 0, 1);
            p++;
        }
        if (rates <= 0) {
            String[] f = split(ls.get(p));
            c.sampleRate = num(f, 0, 1000);
            c.declaredSamples = (int) num(f, 1, 0);
            p++;
        } else {
            for (int i = 0; i < rates && p < ls.size(); i++, p++) {
                String[] f = split(ls.get(p));
                double sr = num(f, 0, 1000);
                int end = (int) num(f, 1, 0);
                if (i == 0) c.sampleRate = sr;
                c.declaredSamples = end;
            }
        }

        if (p < ls.size()) c.startTime = ls.get(p++);
        if (p < ls.size()) c.triggerTime = ls.get(p++);

        if (p < ls.size()) {
            String ft = ls.get(p++).toUpperCase();
            if (ft.startsWith("ASCII")) c.dataType = TYPE_ASCII;
            else if (ft.startsWith("BINARY32")) c.dataType = TYPE_BINARY32;
            else if (ft.startsWith("FLOAT32")) c.dataType = TYPE_FLOAT32;
            else c.dataType = TYPE_BINARY;
        }
        if (p < ls.size() && isNumber(ls.get(p))) c.timeMult = num(split(ls.get(p)), 0, 1);
        if (c.timeMult <= 0) c.timeMult = 1;

        c.triggerOffsetSec = parseTimeDiff(c.startTime, c.triggerTime);
        return c;
    }

    private void readDat(InputStream in) throws IOException {
        switch (dataType) {
            case TYPE_ASCII: readAscii(in); break;
            case TYPE_BINARY32: readBinary(in, 4); break;
            case TYPE_FLOAT32: readBinary(in, 4); break;
            default: readBinary(in, 2); break;
        }
    }

    private void readAscii(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "US-ASCII"));
        List<float[]> rows = new ArrayList<>();
        List<Long> stamps = new ArrayList<>();
        List<Boolean[]> digits = new ArrayList<>();
        int nA = analogs.size(), nD = digitals.size();
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] f = line.split(",");
            if (f.length < 2 + nA) continue;
            float[] vals = new float[nA];
            for (int i = 0; i < nA; i++) {
                double raw = num(f, 2 + i, 0);
                vals[i] = (float) analogs.get(i).scale(raw);
            }
            rows.add(vals);
            stamps.add((long) (num(f, 1, 0) * timeMult));
            if (nD > 0 && f.length > 2 + nA) {
                Boolean[] bits = new Boolean[nD];
                String st = f[2 + nA].trim();
                long word = 0;
                try {
                    word = Long.parseLong(st);
                } catch (NumberFormatException ignored) {
                }
                for (int i = 0; i < nD; i++) bits[i] = ((word >> i) & 1L) == 1L;
                digits.add(bits);
            }
        }
        finish(rows, stamps, digits);
    }

    private void readBinary(InputStream in, int width) throws IOException {
        int nA = analogs.size(), nD = digitals.size();
        int words = (nD + 15) / 16;
        int recSize = 8 + nA * width + words * 2;
        byte[] buf = new byte[recSize];

        List<float[]> rows = new ArrayList<>();
        List<Long> stamps = new ArrayList<>();
        List<Boolean[]> digits = new ArrayList<>();

        while (true) {
            int read = readFully(in, buf);
            if (read < recSize) break;
            long ts = (buf[4] & 0xFFL) | ((buf[5] & 0xFFL) << 8) | ((buf[6] & 0xFFL) << 16)
                    | ((buf[7] & 0xFFL) << 24);
            float[] vals = new float[nA];
            int off = 8;
            for (int i = 0; i < nA; i++, off += width) {
                double raw;
                if (dataType == TYPE_FLOAT32) {
                    int bits = (buf[off] & 0xFF) | ((buf[off + 1] & 0xFF) << 8)
                            | ((buf[off + 2] & 0xFF) << 16) | ((buf[off + 3] & 0xFF) << 24);
                    raw = Float.intBitsToFloat(bits);
                } else if (width == 4) {
                    raw = (buf[off] & 0xFF) | ((buf[off + 1] & 0xFF) << 8)
                            | ((buf[off + 2] & 0xFF) << 16) | ((buf[off + 3] & 0xFF) << 24);
                } else {
                    raw = (short) ((buf[off] & 0xFF) | ((buf[off + 1] & 0xFF) << 8));
                }
                vals[i] = (float) analogs.get(i).scale(raw);
            }
            rows.add(vals);
            stamps.add((long) (ts * timeMult));
            if (nD > 0) {
                Boolean[] bits = new Boolean[nD];
                for (int w = 0; w < words; w++) {
                    int word = (buf[off] & 0xFF) | ((buf[off + 1] & 0xFF) << 8);
                    off += 2;
                    for (int b = 0; b < 16; b++) {
                        int idx = w * 16 + b;
                        if (idx < nD) bits[idx] = ((word >> b) & 1) == 1;
                    }
                }
                digits.add(bits);
            }
        }
        finish(rows, stamps, digits);
    }

    private void finish(List<float[]> rows, List<Long> stamps, List<Boolean[]> digits) {
        sampleCount = rows.size();
        int nA = analogs.size(), nD = digitals.size();
        analog = new float[nA][sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            float[] v = rows.get(i);
            for (int ch = 0; ch < nA; ch++) analog[ch][i] = ch < v.length ? v[ch] : 0;
        }
        time = new double[sampleCount];
        sampleRatePerSample = new double[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            double sec = stamps.get(i) / 1_000_000.0;
            time[i] = sec - triggerOffsetSec;
        }
        if (sampleCount > 1 && (time[sampleCount - 1] - time[0]) <= 0) {
            // dat 时间戳缺失或不单调：退回按额定采样率推算
            double dt = sampleRate > 0 ? 1.0 / sampleRate : 0.001;
            for (int i = 0; i < sampleCount; i++) time[i] = i * dt - triggerOffsetSec;
        }
        triggerIndex = indexOfTime(0);
        for (int i = 0; i < sampleCount; i++) {
            int prev = Math.max(0, i - 1);
            int next = Math.min(sampleCount - 1, i + 1);
            double dt = time[next] - time[prev];
            sampleRatePerSample[i] = dt > 0 ? (next - prev) / dt : sampleRate;
        }
        if (nD > 0) {
            digitalWords = (nD + 15) / 16;
            digital = new boolean[sampleCount][nD];
            for (int i = 0; i < sampleCount && i < digits.size(); i++) {
                Boolean[] b = digits.get(i);
                for (int ch = 0; ch < nD; ch++) digital[i][ch] = b[ch] != null && b[ch];
            }
        }
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    private static String[] split(String s) {
        String[] raw = s.split(",", -1);
        for (int i = 0; i < raw.length; i++) raw[i] = raw[i].trim();
        return raw;
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double num(String[] f, int i, double def) {
        if (f == null || i >= f.length) return def;
        try {
            String s = f[i].trim();
            return s.isEmpty() ? def : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** "dd/mm/yyyy,hh:mm:ss.ssssss" 两时刻之差（秒）。 */
    static double parseTimeDiff(String start, String trigger) {
        long a = parseStamp(start), b = parseStamp(trigger);
        if (a < 0 || b < 0) return 0;
        return (b - a) / 1_000_000.0;
    }

    /** 返回自 1970 起的微秒数，解析失败返回 -1。 */
    static long parseStamp(String s) {
        if (s == null) return -1;
        String[] parts = s.split(",");
        if (parts.length < 2) return -1;
        String[] d = parts[0].trim().split("/");
        String[] t = parts[1].trim().split(":");
        if (d.length < 3 || t.length < 3) return -1;
        try {
            int day = Integer.parseInt(d[0]);
            int month = Integer.parseInt(d[1]);
            int year = Integer.parseInt(d[2]);
            int hh = Integer.parseInt(t[0]);
            int mm = Integer.parseInt(t[1]);
            double ss = Double.parseDouble(t[2]);
            long secs = daysFromCivil(year, month, day) * 86400L + hh * 3600L + mm * 60L;
            return secs * 1_000_000L + Math.round(ss * 1_000_000.0);
        } catch (Exception e) {
            return -1;
        }
    }

    private static long daysFromCivil(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    public static ComtradeCase fromBytes(byte[] cfg, byte[] dat, String name) throws IOException {
        return load(cfg, new ByteArrayInputStream(dat), name);
    }
}
