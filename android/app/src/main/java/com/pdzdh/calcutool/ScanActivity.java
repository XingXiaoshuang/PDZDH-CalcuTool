package com.pdzdh.calcutool;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 相机扫码：读取配电终端二维码，按表 5 校验后展示字段与内容。 */
public class ScanActivity extends Activity implements Camera.PreviewCallback {

    private static final int REQ_CAMERA = 4101;

    private SurfaceView previewView;
    private TextView torchBtn;
    private TextView statusText;

    private Camera camera;
    private Camera.Size previewSize;
    private boolean torchOn;

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean paused;
    private ExecutorService worker;
    private final QRCodeReader reader = new QRCodeReader();
    private Map<DecodeHintType, Object> hints;
    private float density;
    /** 由其它应用分享进来的文本（微信/记事本等），只在首次展示时校验一次。 */
    private String sharedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        worker = Executors.newSingleThreadExecutor();

        hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        List<com.google.zxing.BarcodeFormat> formats = new ArrayList<>();
        formats.add(com.google.zxing.BarcodeFormat.QR_CODE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);

        sharedText = textFrom(getIntent());
        setContentView(buildUi());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        sharedText = textFrom(intent);
        consumeSharedText();
    }

    /** 分享进来的内容立即校验；首次启动时由 onResume 触发。 */
    private void consumeSharedText() {
        if (sharedText == null) return;
        final String text = sharedText;
        sharedText = null;
        paused = true;
        getWindow().getDecorView().postDelayed(
                () -> showResultDialog(QrTerminalParser.parse(text)), 320);
    }

    /** 其它应用通过「分享 → 扫描终端二维码」传入的内容。 */
    private static String textFrom(android.content.Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (!android.content.Intent.ACTION_SEND.equals(action)) return null;
        CharSequence cs = intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT);
        return cs == null ? null : cs.toString();
    }

    private float dp(float v) { return v * density; }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        previewView = new SurfaceView(this);
        root.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // the camera surface only exists once surfaceCreated() has fired
        previewView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) { startCamera(); }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {}
            @Override public void surfaceDestroyed(SurfaceHolder holder) { releaseCamera(); }
        });

        root.addView(new ScanOverlayView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding((int) dp(10), (int) dp(38), (int) dp(10), (int) dp(8));

        TextView back = pill("←", 18);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams((int) dp(40), (int) dp(36)));

        TextView title = new TextView(this);
        title.setText("扫描终端二维码");
        title.setTextColor(Pal.TEXT_PRIMARY);
        title.setTextSize(15);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart((int) dp(10));
        top.addView(title, tp);

        torchBtn = pill("⚡", 16);
        torchBtn.setOnClickListener(v -> toggleTorch());
        top.addView(torchBtn, new LinearLayout.LayoutParams((int) dp(40), (int) dp(36)));
        root.addView(top, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        // bottom hint + manual input
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding((int) dp(20), (int) dp(10), (int) dp(20), (int) dp(28));

        statusText = new TextView(this);
        statusText.setText("终端类型 / 厂商代码 / 终端型号 / ID号 / 硬件版本 / 生产日期");
        statusText.setTextColor(Pal.TEXT_MUTED);
        statusText.setTextSize(10);
        statusText.setTypeface(Typeface.MONOSPACE);
        statusText.setGravity(Gravity.CENTER);
        bottom.addView(statusText);

        TextView manual = new TextView(this);
        manual.setText("手动输入 / 粘贴内容");
        manual.setTextColor(Pal.ACCENT_PURPLE);
        manual.setTextSize(12);
        manual.setGravity(Gravity.CENTER);
        manual.setPadding((int) dp(14), (int) dp(10), (int) dp(14), (int) dp(10));
        manual.setOnClickListener(v -> showManualInput());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = (int) dp(6);
        bottom.addView(manual, mp);
        root.addView(bottom, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));
        return root;
    }

    private TextView pill(String text, float size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Pal.TEXT_PRIMARY);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(Pal.BG_SURFACE, 18, Pal.BORDER_MID, 1));
        return t;
    }

    private GradientDrawable shape(int fill, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, (int) dp(strokeDp)), stroke);
        return d;
    }

    // ── 相机生命周期 ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        paused = false;
        consumeSharedText();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grant) {
        super.onRequestPermissionsResult(req, perms, grant);
        if (req != REQ_CAMERA) return;
        if (grant.length > 0 && grant[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            statusText.setText("未获得相机权限，可点下方「手动输入」继续");
            statusText.setTextColor(Pal.ACCENT_RED);
        }
    }

    private void startCamera() {
        if (camera != null) return;
        if (previewView == null || !previewView.getHolder().getSurface().isValid()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            camera = Camera.open(findBackCamera());
            Camera.Parameters p = camera.getParameters();
            previewSize = choosePreviewSize(p.getSupportedPreviewSizes());
            p.setPreviewSize(previewSize.width, previewSize.height);
            p.setPreviewFormat(ImageFormat.NV21);
            List<String> modes = p.getSupportedFocusModes();
            if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            camera.setParameters(p);
            camera.setDisplayOrientation(displayOrientation());
            camera.setPreviewDisplay(previewView.getHolder());
            camera.startPreview();

            int bufSize = previewSize.width * previewSize.height * 3 / 2;
            for (int i = 0; i < 3; i++) camera.addCallbackBuffer(new byte[bufSize]);
            camera.setPreviewCallbackWithBuffer(this);
        } catch (Exception e) {
            releaseCamera();
            statusText.setText("相机不可用：" + e.getClass().getSimpleName() + "，可点「手动输入」");
            statusText.setTextColor(Pal.ACCENT_RED);
        }
    }

    private void releaseCamera() {
        if (camera == null) return;
        try {
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
            camera.release();
        } catch (Exception ignored) {
        }
        camera = null;
    }

    private static int findBackCamera() {
        int count = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return 0;
    }

    private int displayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(findBackCamera(), info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation * 90;
        return (info.orientation - degrees + 360) % 360;
    }

    private static Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        Camera.Size best = sizes.get(0);
        long target = 1280L * 720L;
        long bestDelta = Long.MAX_VALUE;
        for (Camera.Size s : sizes) {
            long delta = Math.abs((long) s.width * s.height - target);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = s;
            }
        }
        return best;
    }

    private void toggleTorch() {
        if (camera == null) return;
        try {
            Camera.Parameters p = camera.getParameters();
            List<String> modes = p.getSupportedFlashModes();
            if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                Toast.makeText(this, "此设备不支持常亮补光", Toast.LENGTH_SHORT).show();
                return;
            }
            torchOn = !torchOn;
            p.setFlashMode(torchOn ? Camera.Parameters.FLASH_MODE_TORCH
                    : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(p);
            torchBtn.setTextColor(torchOn ? Pal.ACCENT_AMBER : Pal.TEXT_PRIMARY);
        } catch (Exception ignored) {
        }
    }

    // ── 解码 ────────────────────────────────────────────────────────────────────

    @Override
    public void onPreviewFrame(final byte[] data, Camera cam) {
        if (data == null) return;
        if (paused || busy.get()) {
            try {
                cam.addCallbackBuffer(data);
            } catch (Exception ignored) {
            }
            return;
        }
        final byte[] frame = data.clone();
        final int w = previewSize.width;
        final int h = previewSize.height;
        busy.set(true);
        worker.execute(() -> {
            try {
                int side = (int) (Math.min(w, h) * 0.74);
                int left = (w - side) / 2;
                int top = (h - side) / 2;
                PlanarYUVLuminanceSource src =
                        new PlanarYUVLuminanceSource(frame, w, h, left, top, side, side, false);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(src));
                Result r = reader.decode(bitmap, hints);
                onDecoded(r.getText());
            } catch (NotFoundException | ChecksumException | FormatException ignored) {
                // not a QR frame yet
            } catch (RuntimeException ignored) {
            } finally {
                reader.reset();
                busy.set(false);
                if (camera != null) {
                    try {
                        camera.addCallbackBuffer(frame);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void onDecoded(final String text) {
        runOnUiThread(() -> {
            if (isFinishing()) return;
            paused = true;
            if (camera != null) {
                try {
                    camera.setPreviewCallbackWithBuffer(null);
                } catch (Exception ignored) {
                }
            }
            showResultDialog(QrTerminalParser.parse(text));
        });
    }

    // ── 弹窗 ────────────────────────────────────────────────────────────────────

    private void showResultDialog(final QrTerminalParser.Result r) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(Pal.BG_SURFACE, 20, Pal.BORDER_SUBTLE, 1));
        card.setPadding((int) dp(18), (int) dp(16), (int) dp(18), (int) dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("终端二维码");
        title.setTextColor(Pal.TEXT_PRIMARY);
        title.setTextSize(15);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = new TextView(this);
        state.setText(r.ok ? "✓ 校验通过" : "✕ 校验未通过");
        state.setTextColor(r.ok ? Pal.ACCENT_TEAL : Pal.ACCENT_RED);
        state.setTextSize(11);
        state.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        head.addView(state);
        card.addView(head);

        TextView rule = new TextView(this);
        rule.setText("仅接受中文逗号「，」与中文冒号「：」，不允许空格与英文标点");
        rule.setTextColor(Pal.TEXT_MUTED);
        rule.setTextSize(9.5f);
        rule.setPadding(0, (int) dp(4), 0, (int) dp(10));
        card.addView(rule);

        if (r.ok) {
            card.addView(tableHeader());
            int index = 1;
            for (Map.Entry<String, String> e : r.values.entrySet()) {
                card.addView(tableRow(index++, e.getKey(), e.getValue()));
            }
        } else {
            TextView err = new TextView(this);
            err.setText(r.error);
            err.setTextColor(Pal.ACCENT_RED);
            err.setTextSize(12);
            err.setLineSpacing(0, 1.25f);
            err.setPadding((int) dp(12), (int) dp(10), (int) dp(12), (int) dp(10));
            err.setBackground(shape(0x1AFF6E6E, 10, 0x55FF6E6E, 1));
            card.addView(err);

            TextView rawLabel = new TextView(this);
            rawLabel.setText("原始内容");
            rawLabel.setTextColor(Pal.TEXT_MUTED);
            rawLabel.setTextSize(9.5f);
            rawLabel.setTypeface(Typeface.MONOSPACE);
            rawLabel.setPadding(0, (int) dp(12), 0, (int) dp(4));
            card.addView(rawLabel);
            card.addView(rawView(r));
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(0, (int) dp(16), 0, 0);

        TextView rescan = new TextView(this);
        rescan.setText("重新扫描");
        rescan.setTextColor(Pal.TEXT_PRIMARY);
        rescan.setTextSize(13);
        rescan.setGravity(Gravity.CENTER);
        rescan.setPadding(0, (int) dp(11), 0, (int) dp(11));
        rescan.setBackground(shape(Pal.BG_ELEVATED, 12, Pal.BORDER_MID, 1));
        rescan.setOnClickListener(v -> {
            dialog.dismiss();
            resumeScanning();
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd((int) dp(8));
        footer.addView(rescan, lp1);

        TextView action = new TextView(this);
        action.setText(r.ok ? "复制内容" : "关闭");
        action.setTextColor(r.ok ? 0xFF14111E : Pal.TEXT_PRIMARY);
        action.setTextSize(13);
        action.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        action.setGravity(Gravity.CENTER);
        action.setPadding(0, (int) dp(11), 0, (int) dp(11));
        action.setBackground(shape(r.ok ? Pal.ACCENT_PURPLE : Pal.BG_ELEVATED, 12,
                r.ok ? 0 : Pal.BORDER_MID, r.ok ? 0 : 1));
        action.setOnClickListener(v -> {
            if (r.ok) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("terminal", r.raw));
                Toast.makeText(this, "已复制二维码内容", Toast.LENGTH_SHORT).show();
            } else {
                dialog.dismiss();
                resumeScanning();
            }
        });
        footer.addView(action, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(footer);

        dialog.setContentView(card);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setOnDismissListener(d -> resumeScanning());
        dialog.show();
    }

    private View tableHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) dp(2), 0, (int) dp(4));
        row.addView(cell("#", 22, Pal.TEXT_MUTED, 9.5f, false));
        row.addView(cell("代码名称", 86, Pal.TEXT_MUTED, 9.5f, false));
        row.addView(cell("内容", 0, Pal.TEXT_MUTED, 9.5f, false));
        return row;
    }

    private View tableRow(int index, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) dp(8), 0, (int) dp(8));
        row.addView(cell(String.valueOf(index), 22, Pal.TEXT_MUTED, 10.5f, true));
        row.addView(cell(label, 86, Pal.TEXT_SECOND, 11.5f, false));
        row.addView(cell(value, 0, Pal.TEXT_PRIMARY, 11.5f, true));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        View line = new View(this);
        line.setBackgroundColor(0x0DFFFFFF);
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return wrap;
    }

    private TextView cell(String text, int widthDp, int color, float size, boolean mono) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(size);
        if (mono) t.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams p = widthDp > 0
                ? new LinearLayout.LayoutParams((int) dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        t.setLayoutParams(p);
        return t;
    }

    private View rawView(QrTerminalParser.Result r) {
        TextView t = new TextView(this);
        t.setTextSize(10.5f);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(Pal.TEXT_SECOND);
        t.setPadding((int) dp(12), (int) dp(10), (int) dp(12), (int) dp(10));
        t.setBackground(shape(Pal.BG_ELEVATED, 10, Pal.BORDER_SUBTLE, 1));
        String raw = r.raw.isEmpty() ? "（空）" : r.raw;
        if (r.errorIndex > 0 && r.errorIndex <= raw.length()) {
            SpannableString ss = new SpannableString(raw);
            ss.setSpan(new BackgroundColorSpan(0x99FF6E6E), r.errorIndex - 1, r.errorIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setText(ss);
        } else {
            t.setText(raw);
        }
        return t;
    }

    private void resumeScanning() {
        paused = false;
        if (camera != null) {
            try {
                camera.setPreviewCallbackWithBuffer(this);
            } catch (Exception ignored) {
            }
        }
    }

    // ── 手动输入（相机不可用或二维码破损时的兜底） ───────────────────────────────

    private void showManualInput() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(Pal.BG_SURFACE, 20, Pal.BORDER_SUBTLE, 1));
        card.setPadding((int) dp(18), (int) dp(16), (int) dp(18), (int) dp(14));

        TextView title = new TextView(this);
        title.setText("手动输入二维码内容");
        title.setTextColor(Pal.TEXT_PRIMARY);
        title.setTextSize(15);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        card.addView(title);

        TextView hint = new TextView(this);
        hint.setText("示例：类型：D30，厂商：XX集团，型号：DTU3001，ID：D30XXXXXX130201302080112，"
                + "硬件版本：HV20.11，生产日期：2013年2月8日");
        hint.setTextColor(Pal.TEXT_MUTED);
        hint.setTextSize(9.5f);
        hint.setPadding(0, (int) dp(6), 0, (int) dp(8));
        card.addView(hint);

        final EditText input = new EditText(this);
        input.setTextSize(12);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextColor(Pal.TEXT_PRIMARY);
        input.setBackground(shape(Pal.BG_ELEVATED, 10, Pal.BORDER_SUBTLE, 1));
        input.setPadding((int) dp(10), (int) dp(10), (int) dp(10), (int) dp(10));
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        card.addView(input);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(0, (int) dp(14), 0, 0);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(Pal.TEXT_PRIMARY);
        cancel.setTextSize(13);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, (int) dp(11), 0, (int) dp(11));
        cancel.setBackground(shape(Pal.BG_ELEVATED, 12, Pal.BORDER_MID, 1));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd((int) dp(8));
        footer.addView(cancel, lp1);

        TextView ok = new TextView(this);
        ok.setText("校验");
        ok.setTextColor(0xFF14111E);
        ok.setTextSize(13);
        ok.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(0, (int) dp(11), 0, (int) dp(11));
        ok.setBackground(shape(Pal.ACCENT_PURPLE, 12, 0, 0));
        ok.setOnClickListener(v -> {
            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
            dialog.dismiss();
            showResultDialog(QrTerminalParser.parse(input.getText().toString()));
        });
        footer.addView(ok, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(footer);

        dialog.setContentView(card);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
    }
}
