package com.pdzdh.calcutool;

import com.google.zxing.common.DecoderResult;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.List;

/**
 * 二维码文本编码自适应。
 *
 * <p>二维码里存的是字节流，标称编码可能是 UTF-8，也可能是 GB18030 / GBK / BIG5，
 * 甚至没有 ECI 标识位。ZXing 只能猜一次，猜错就是「锟斤拷」式乱码，
 * 而现场二维码大多是国产工具按 GB18030 生成的。</p>
 *
 * <p>做法：把字节段按几种中文编码各严格解一遍，用字符分布打分挑最合理的一份——
 * 命中中文标点、常用汉字加分；出现替换符 U+FFFD、拉丁扩展乱码、私用区字符扣分；
 * 能通过 {@link QrTerminalParser} 六字段校验的候选直接加权重，基本一票定胜负。</p>
 */
public final class QrTextDecoder {

    /** 解码结果。 */
    public static final class Decoded {
        public final String text;
        /** 最终采用的编码名，用于界面提示。 */
        public final String charset;
        /** 是否由本类重解（ZXing 原结果被中文编码纠正过）。 */
        public final boolean repaired;

        Decoded(String text, String charset, boolean repaired) {
            this.text = text == null ? "" : text;
            this.charset = charset;
            this.repaired = repaired;
        }
    }

    /** 候选编码，顺序即同分时的优先级。 */
    private static final String[] CANDIDATES = {"UTF-8", "GB18030", "BIG5"};

    private QrTextDecoder() {}

    /** 人工输入 / 分享文本：无需解码。 */
    public static Decoded ofText(String text) {
        return new Decoded(text, "文本", false);
    }

    /** 从 ZXing 解码结果里取字节段，重新判定编码。 */
    public static Decoded of(DecoderResult result) {
        String zxing = result.getText() == null ? "" : result.getText();
        byte[] bytes = concat(result.getByteSegments());
        if (bytes.length == 0) return new Decoded(zxing, "自动", false);

        boolean ascii = true;
        for (byte b : bytes) {
            if ((b & 0x80) != 0) {
                ascii = false;
                break;
            }
        }
        if (ascii) return new Decoded(zxing, "ASCII", false);

        Candidate best = new Candidate(zxing, "自动", score(zxing));
        for (String name : CANDIDATES) {
            String text = strict(bytes, name);
            if (text == null || text.isEmpty()) continue;
            int s = score(text);
            if (s > best.score) best = new Candidate(text, name, s);
        }
        return new Decoded(best.text, best.charset, !best.text.equals(zxing));
    }

    private static final class Candidate {
        final String text;
        final String charset;
        final int score;

        Candidate(String text, String charset, int score) {
            this.text = text;
            this.charset = charset;
            this.score = score;
        }
    }

    /** 严格解码：遇到非法字节直接失败，不给「近似结果」污染判断。 */
    private static String strict(byte[] bytes, String charset) {
        try {
            CharsetDecoder d = Charset.forName(charset).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return d.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 文本「像不像正常中文」的打分，越高越可信。 */
    static int score(String s) {
        if (s == null || s.isEmpty()) return -10000;
        int sc = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\uFFFD') {
                sc -= 40;                       // 解码失败的替换符
            } else if (c == '：' || c == '，' || c == '、') {
                sc += 8;                        // 中文标点，出现即强信号
            } else if (c < 0x80) {
                sc += 1;                        // ASCII
            } else if (c >= 0x4E00 && c <= 0x9FFF) {
                sc += 3;                        // 常用汉字
            } else if (c >= 0x3000 && c <= 0x303F) {
                sc += 4;                        // 中文标点区
            } else if (c >= 0xFF01 && c <= 0xFF5E) {
                sc += 2;                        // 全角符号
            } else if (c >= 0xE000 && c <= 0xF8FF) {
                sc -= 20;                       // 私用区
            } else if (c <= 0xFF) {
                sc -= 12;                       // 拉丁扩展：GBK 字节被当 Latin-1 的典型乱码
            } else {
                sc -= 2;                        // 生僻/非中文区
            }
        }
        // 能通过六字段规范校验的，几乎可以断定编码猜对了
        if (QrTerminalParser.parse(s).ok) sc += 500;
        return sc;
    }

    private static byte[] concat(List<byte[]> segments) {
        if (segments == null || segments.isEmpty()) return new byte[0];
        int total = 0;
        for (byte[] s : segments) if (s != null) total += s.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] s : segments) {
            if (s == null) continue;
            System.arraycopy(s, 0, out, pos, s.length);
            pos += s.length;
        }
        return out;
    }
}
