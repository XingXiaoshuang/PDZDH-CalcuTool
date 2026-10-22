import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import com.pdzdh.calcutool.QrCodec;
import com.pdzdh.calcutool.QrTextDecoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/** 模拟「相册选图」：把二维码写成 PNG，再按图片解码路径（正片 + 反相）识别。 */
public class QrImageTest {

    private static final String TEXT =
            "类型：D30，厂商：XX集团，型号：DTU3001，ID：D30XXXXXX130201302080112，"
                    + "硬件版本：HV20.11，生产日期：2013年2月8日";

    public static void main(String[] args) throws Exception {
        File dir = new File("build/tests/qrimg");
        dir.mkdirs();
        File normal = new File(dir, "qr-gb18030.png");
        File dark = new File(dir, "qr-dark.png");
        render(normal, false);
        render(dark, true);

        check("普通白底图", normal);
        check("黑底反相图", dark);
        System.out.println(ok ? "\nIMAGE PATH OK" : "\nIMAGE PATH FAILED");
        if (!ok) System.exit(1);
    }

    private static void render(File file, boolean invert) throws Exception {
        Map<com.google.zxing.EncodeHintType, Object> hints =
                new EnumMap<>(com.google.zxing.EncodeHintType.class);
        hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "GB18030");
        QRCode qr = Encoder.encode(TEXT, ErrorCorrectionLevel.M, hints);
        ByteMatrix m = qr.getMatrix();

        int scale = 8, quiet = 4;
        int w = (m.getWidth() + quiet * 2) * scale;
        int h = (m.getHeight() + quiet * 2) * scale;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int bg = invert ? 0x000000 : 0xFFFFFF;
        int fg = invert ? 0xFFFFFF : 0x000000;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) img.setRGB(x, y, bg);
        }
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (m.get(x, y) != 1) continue;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        img.setRGB((x + quiet) * scale + dx, (y + quiet) * scale + dy, fg);
                    }
                }
            }
        }
        ImageIO.write(img, "png", file);
    }

    private static boolean ok = true;

    private static void check(String name, File file) throws Exception {
        BufferedImage img = ImageIO.read(file);
        int w = img.getWidth(), h = img.getHeight();
        int[] px = new int[w * h];
        img.getRGB(0, 0, w, h, px, 0, w);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);

        QrTextDecoder.Decoded d = null;
        try {
            d = QrCodec.decode(new RGBLuminanceSource(w, h, px), hints);
        } catch (Exception e) {
            int[] inv = new int[px.length];
            for (int i = 0; i < px.length; i++) inv[i] = 0xFF000000 | (~px[i] & 0xFFFFFF);
            try {
                d = QrCodec.decode(new RGBLuminanceSource(w, h, inv), hints);
            } catch (Exception e2) {
                System.out.printf("[NG] %-12s 未识别 %s%n", name, e2.getClass().getSimpleName());
                ok = false;
                return;
            }
        }
        boolean pass = TEXT.equals(d.text);
        if (!pass) ok = false;
        System.out.printf("[%s] %-12s %dx%d 编码=%s%s%n", pass ? "OK" : "NG", name, w, h,
                d.charset, pass ? "" : "  得到：" + d.text);
    }
}
