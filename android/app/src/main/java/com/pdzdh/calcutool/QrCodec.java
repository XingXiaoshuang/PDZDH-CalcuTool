package com.pdzdh.calcutool;

import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.detector.Detector;

import java.util.Map;

/**
 * QR 解码入口：绕开 {@code QRCodeReader} 的「解码即出字符串」，
 * 直接拿 {@link DecoderResult} 的字节段交给 {@link QrTextDecoder} 处理中文编码。
 *
 * <p>二值化用两套算法依次尝试：{@link HybridBinarizer} 适合屏拍/均匀光照，
 * {@link GlobalHistogramBinarizer} 在局部过曝或大块阴影时更稳。</p>
 */
public final class QrCodec {

    private QrCodec() {}

    /**
     * 深度模式：两套二值化都试，适合相册图片（只解一次，值得多花时间）。
     *
     * @throws com.google.zxing.NotFoundException      画面里没有可识别的二维码
     * @throws com.google.zxing.ChecksumException     纠错失败
     * @throws com.google.zxing.FormatException       格式非法
     */
    public static QrTextDecoder.Decoded decode(LuminanceSource source,
                                               Map<DecodeHintType, Object> hints)
            throws Exception {
        return decode(source, hints, true);
    }

    /**
     * @param deep true = Hybrid + Global 两套二值化依次尝试；false = 只用 Hybrid（相机实时帧，保帧率）
     */
    public static QrTextDecoder.Decoded decode(LuminanceSource source,
                                               Map<DecodeHintType, Object> hints,
                                               boolean deep)
            throws Exception {
        Binarizer[] binarizers = deep
                ? new Binarizer[]{new HybridBinarizer(source), new GlobalHistogramBinarizer(source)}
                : new Binarizer[]{new HybridBinarizer(source)};
        Exception last = null;
        for (Binarizer binarizer : binarizers) {
            try {
                BinaryBitmap image = new BinaryBitmap(binarizer);
                BitMatrix matrix = image.getBlackMatrix();
                DetectorResult detected = new Detector(matrix).detect(hints);
                DecoderResult result = new Decoder().decode(detected.getBits(), hints);
                return QrTextDecoder.of(result);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last == null ? new IllegalStateException("QR 解码失败") : last;
    }
}
