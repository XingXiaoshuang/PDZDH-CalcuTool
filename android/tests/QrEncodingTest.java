import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import com.pdzdh.calcutool.QrCodec;
import com.pdzdh.calcutool.QrTextDecoder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** JVM 侧验证：二维码文本编码自适应（UTF-8 / GB18030 / GBK / ASCII）。 */
public class QrEncodingTest {

    private static int pass, fail;

    public static void main(String[] args) throws Exception {
        String terminal = "类型：D30，厂商：XX集团，型号：DTU3001，"
                + "ID：D30XXXXXX130201302080112，硬件版本：HV20.11，生产日期：2013年2月8日";
        String plain = "配电终端现场巡检记录：一号环网柜，2013年2月8日，正常";

        check("UTF-8 终端码", terminal, "UTF-8", terminal);
        check("GB18030 终端码", terminal, "GB18030", terminal);
        check("GBK 终端码", terminal, "GBK", terminal);
        check("GB18030 普通中文", plain, "GB18030", plain);
        checkRepair("乱码纠正 GB18030", terminal, "GB18030", "GB18030");
        checkRepair("乱码纠正 BIG5", "配電終端：D30，廠商：XX集團，型號：DTU3001", "Big5", "BIG5");
        check("ASCII 端子编号", "DTU-3001-ABCDEF", "ISO-8859-1", "DTU-3001-ABCDEF");

        System.out.println("\n" + (fail == 0 ? "ALL PARITY OK" : "FAILED") + "  pass=" + pass + " fail=" + fail);
        if (fail > 0) System.exit(1);
    }


    /** 模拟 ZXing 猜错编码（按 UTF-8 强行解 GB18030 字节），验证兜底层能纠正。 */
    private static void checkRepair(String name, String text, String bytesCharset, String expectCharset) {
        List<byte[]> segs = new ArrayList<>();
        byte[] raw = text.getBytes(java.nio.charset.Charset.forName(bytesCharset));
        segs.add(raw);
        String wrong = new String(raw, java.nio.charset.StandardCharsets.UTF_8); // 乱码
        com.google.zxing.common.DecoderResult dr =
                new com.google.zxing.common.DecoderResult(raw, wrong, segs, null);
        QrTextDecoder.Decoded d = QrTextDecoder.of(dr);
        boolean ok = text.equals(d.text) && expectCharset.equals(d.charset);
        System.out.printf("[%s] %-16s 编码=%-9s%s%n", ok ? "OK" : "NG", name, d.charset,
                ok ? "" : "  得到：" + d.text);
        if (ok) pass++;
        else fail++;
    }

    private static void check(String name, String text, String encodeCharset, String expect)
            throws Exception {
        byte[] bytes = text.getBytes(encodeCharset);
        Map<EncodeHintType, Object> encHints = new EnumMap<>(EncodeHintType.class);
        encHints.put(EncodeHintType.CHARACTER_SET, encodeCharset);
        QRCode qr = Encoder.encode(text, ErrorCorrectionLevel.M, encHints);
        ByteMatrix matrix = qr.getMatrix();

        int scale = 6, quiet = 4;
        int w = (matrix.getWidth() + quiet * 2) * scale;
        int h = (matrix.getHeight() + quiet * 2) * scale;
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) px[i] = 0xFFFFFFFF;
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y) != 1) continue;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        px[((y + quiet) * scale + dy) * w + (x + quiet) * scale + dx] = 0xFF000000;
                    }
                }
            }
        }

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);

        QrTextDecoder.Decoded d = QrCodec.decode(new RGBLuminanceSource(w, h, px), hints);
        boolean ok = expect.equals(d.text);
        System.out.printf("[%s] %-16s 编码=%-9s 字节=%-3d %s%s%n",
                ok ? "OK" : "NG", name, d.charset, bytes.length, ok ? "" : "得到：" + d.text,
                d.repaired ? "（纠正过）" : "");
        if (ok) pass++;
        else fail++;
    }
}
