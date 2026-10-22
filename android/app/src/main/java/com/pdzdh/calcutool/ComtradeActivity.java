package com.pdzdh.calcutool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pdzdh.calcutool.comtrade.ComtradeCase;
import com.pdzdh.calcutool.comtrade.PhasorChart;
import com.pdzdh.calcutool.comtrade.SpectrumView;
import com.pdzdh.calcutool.comtrade.WaveMath;
import com.pdzdh.calcutool.comtrade.WaveformView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

/** 录波分析：读取 COMTRADE cfg/dat，绘制波形并给出有效值、谐波、向量等分析。 */
public class ComtradeActivity extends Activity implements WaveformView.Listener {

    private static final int REQ_CFG = 5101;
    private static final int REQ_DAT = 5102;
    private static final int HARMONIC_ORDER = 19;

    private ComtradeCase data;
    private WaveformView chart;
    private LinearLayout chipRow;
    private LinearLayout analysisBox;
    private TextView titleView, infoView;
    private View toolsRow, tabsRow;
    private ScrollView panelScroll;
    private View chartPlaceholder;
    private TextView btnPrimary, btnProportional;
    private TextView cursorSummaryView;

    private int mode = MODE_VALUES;
    private static final int MODE_VALUES = 0, MODE_HARM = 1, MODE_PHASOR = 2, MODE_SMALL = 3;

    private final TextView[] tabViews = new TextView[4];
    private int u0Ch = -1, i0Ch = -1;
    private com.pdzdh.calcutool.comtrade.SmallCurrentDetector.Result smallResult;
    private Uri pendingCfgUri;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        setContentView(buildRoot());
        setEmptyState();
    }

    private float dp(float v) { return v * density; }

    // ── 布局 ─────────────────────────────────────────────────────────────────

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.BG_BASE);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        root.addView(buildHeader());
        root.addView(buildInfoBar());

        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
        HorizontalScrollView chips = new HorizontalScrollView(this);
        chips.setHorizontalScrollBarEnabled(false);
        chips.addView(chipRow);
        root.addView(chips, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        chart = new WaveformView(this);
        chart.setListener(this);
        chart.setMinimumHeight((int) dp(150));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cp.topMargin = (int) dp(2);
        root.addView(chart, cp);

        chartPlaceholder = buildPlaceholder();
        root.addView(chartPlaceholder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        toolsRow = buildTools();
        root.addView(toolsRow);
        tabsRow = buildTabs();
        root.addView(tabsRow);

        panelScroll = new ScrollView(this);
        analysisBox = new LinearLayout(this);
        analysisBox.setOrientation(LinearLayout.VERTICAL);
        analysisBox.setPadding((int) dp(14), (int) dp(10), (int) dp(14), (int) dp(14));
        panelScroll.addView(analysisBox);
        root.addView(panelScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(186)));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Pal.BG_SURFACE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) dp(10), (int) dp(10), (int) dp(12), (int) dp(10));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(18);
        back.setTextColor(Pal.TEXT_PRIMARY);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams((int) dp(38), (int) dp(34)));

        titleView = new TextView(this);
        titleView.setText("录波分析");
        titleView.setTextColor(Pal.TEXT_PRIMARY);
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart((int) dp(6));
        row.addView(titleView, tp);

        btnPrimary = headerToggle("一次值");
        btnPrimary.setOnClickListener(v -> {
            boolean nowPrimary = !chart.isPrimary();
            chart.setPrimary(nowPrimary);
            updateHeaderButtons();
            refreshAnalysis();
        });
        row.addView(btnPrimary, headerToggleParams());

        btnProportional = headerToggle("比例");
        btnProportional.setOnClickListener(v -> {
            chart.setProportional(!chart.isProportional());
            updateHeaderButtons();
        });
        row.addView(btnProportional, headerToggleParams());

        TextView open = new TextView(this);
        open.setText("打开 CFG");
        open.setTextSize(12);
        open.setTextColor(0xFF14111E);
        open.setGravity(Gravity.CENTER);
        open.setPadding((int) dp(12), (int) dp(7), (int) dp(12), (int) dp(7));
        open.setBackground(shape(Pal.ACCENT_PURPLE, 16, 0, 0));
        open.setOnClickListener(v -> pickCfg());
        row.addView(open);

        header.addView(row);
        View line = new View(this);
        line.setBackgroundColor(Pal.BORDER_SUBTLE);
        header.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) density)));
        return header;
    }

    private View buildPlaceholder() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView t = new TextView(this);
        t.setText("尚未打开录波文件");
        t.setTextColor(Pal.TEXT_MUTED);
        t.setTextSize(11);
        t.setTypeface(Typeface.MONOSPACE);
        box.addView(t);
        return box;
    }

    private TextView headerToggle(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(10);
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) dp(8), (int) dp(6), (int) dp(8), (int) dp(6));
        t.setTextColor(Pal.ACCENT_TEAL);
        t.setBackground(shape(0x1F53DAAF, 14, 0x5553DAAF, 1));
        return t;
    }

    private LinearLayout.LayoutParams headerToggleParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMarginEnd((int) dp(6));
        return p;
    }

    private void updateHeaderButtons() {
        if (btnPrimary != null) {
            boolean primary = chart.isPrimary();
            btnPrimary.setText(primary ? "一次值" : "二次值");
            btnPrimary.setTextColor(primary ? Pal.ACCENT_TEAL : Pal.ACCENT_AMBER);
            btnPrimary.setBackground(shape(primary ? 0x1F53DAAF : 0x1FFFB955, 14,
                    primary ? 0x5553DAAF : 0x55FFB955, 1));
        }
        if (btnProportional != null) {
            boolean prop = chart.isProportional();
            btnProportional.setText(prop ? "保持比例" : "填充绘制");
        }
    }

    private View buildInfoBar() {
        infoView = new TextView(this);
        infoView.setText("");
        infoView.setTextColor(Pal.TEXT_MUTED);
        infoView.setTextSize(8.8f);
        infoView.setTypeface(Typeface.MONOSPACE);
        infoView.setPadding((int) dp(14), (int) dp(6), (int) dp(14), (int) dp(4));
        infoView.setSingleLine(false);
        return infoView;
    }

    private View buildTools() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) dp(12), (int) dp(2), (int) dp(12), (int) dp(6));

        row.addView(tool("−", v -> chart.zoom(1.6)));
        row.addView(tool("+", v -> chart.zoom(0.62)));
        row.addView(tool("全览", v -> chart.reset()));
        row.addView(tool("T0", v -> {
            chart.setShowT0(!chart.showT0());
            Toast.makeText(this, chart.showT0() ? "显示 0 时刻标线" : "隐藏 0 时刻标线",
                    Toast.LENGTH_SHORT).show();
        }));
        TextView hint = new TextView(this);
        hint.setText("拖动平移 · 捏合缩放 · 双击复位");
        hint.setTextColor(Pal.TEXT_MUTED);
        hint.setTextSize(8);
        hint.setTypeface(Typeface.MONOSPACE);
        hint.setGravity(Gravity.END);
        row.addView(hint, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View tool(String label, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextColor(Pal.TEXT_PRIMARY);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) dp(11), (int) dp(6), (int) dp(11), (int) dp(6));
        t.setBackground(shape(Pal.BG_ELEVATED, 10, Pal.BORDER_SUBTLE, 1));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMarginEnd((int) dp(6));
        t.setLayoutParams(p);
        return t;
    }

    private View buildTabs() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding((int) dp(12), (int) dp(2), (int) dp(12), (int) dp(2));
        String[] names = {"数值", "谐波", "向量", "小电流"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setTextSize(10.5f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, (int) dp(7), 0, (int) dp(7));
            t.setOnClickListener(v -> {
                mode = idx;
                refreshTabs();
                applyPanelHeight();
                refreshAnalysis();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMarginEnd((int) dp(6));
            row.addView(t, p);
            tabViews[i] = t;
        }
        refreshTabs();
        return row;
    }

    /** 各分析页需要的高度不同：向量图更高，数值表更矮。 */
    private void applyPanelHeight() {
        if (panelScroll == null || data == null) return;
        int h = mode == MODE_SMALL ? 386 : mode == MODE_PHASOR ? 330 : mode == MODE_HARM ? 280 : 214;
        panelScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(h)));
    }

    private void refreshTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            TextView t = tabViews[i];
            if (t == null) continue;
            boolean on = mode == i;
            t.setTextColor(on ? 0xFF14111E : Pal.TEXT_SECOND);
            t.setTypeface(Typeface.create(Typeface.MONOSPACE,
                    on ? Typeface.BOLD : Typeface.NORMAL));
            t.setBackground(shape(on ? Pal.ACCENT_TEAL : Pal.BG_SURFACE, 10,
                    on ? 0 : Pal.BORDER_SUBTLE, on ? 0 : 1));
        }
    }

    // ── 空状态 ───────────────────────────────────────────────────────────────

    private void setEmptyState() {
        if (chart != null) chart.setVisibility(View.GONE);
        if (chartPlaceholder != null) chartPlaceholder.setVisibility(View.VISIBLE);
        if (toolsRow != null) toolsRow.setVisibility(View.GONE);
        if (tabsRow != null) tabsRow.setVisibility(View.GONE);
        if (panelScroll != null) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            panelScroll.setLayoutParams(p);
        }
        titleView.setText("录波分析");
        infoView.setText("COMTRADE 1991 / 1999 / 2013 · 二进制与 ASCII 均支持");
        chipRow.removeAllViews();
        analysisBox.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) dp(16), (int) dp(16), (int) dp(16), (int) dp(16));
        card.setBackground(shape(Pal.BG_SURFACE, 18, Pal.BORDER_SUBTLE, 1));

        TextView t = new TextView(this);
        t.setText("打开录波文件");
        t.setTextColor(Pal.TEXT_PRIMARY);
        t.setTextSize(14);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        card.addView(t);

        TextView d = new TextView(this);
        d.setText("选择 .cfg 文件，自动读取同目录同名的 .dat 数据文件；"
                + "支持 ASCII / BINARY / BINARY32 / FLOAT32。");
        d.setTextColor(Pal.TEXT_SECOND);
        d.setTextSize(11);
        d.setLineSpacing(0, 1.3f);
        d.setPadding(0, (int) dp(8), 0, (int) dp(14));
        card.addView(d);

        card.addView(bigButton("选择 CFG 文件", Pal.ACCENT_PURPLE, 0xFF14111E, v -> pickCfg()));
        card.addView(bigButton("加载示例录波", Pal.BG_ELEVATED, Pal.TEXT_PRIMARY, v -> loadSample()));

        analysisBox.addView(card);
    }

    private View bigButton(String label, int bg, int fg, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13.5f);
        t.setTextColor(fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int) dp(13), 0, (int) dp(13));
        t.setBackground(shape(bg, 12, bg == Pal.BG_ELEVATED ? Pal.BORDER_MID : 0,
                bg == Pal.BG_ELEVATED ? 1 : 0));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = (int) dp(8);
        t.setLayoutParams(p);
        return t;
    }

    // ── 文件读取 ─────────────────────────────────────────────────────────────

    private void pickCfg() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_CFG);
    }

    private void pickDat() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_DAT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent intent) {
        super.onActivityResult(req, res, intent);
        if (res != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        if (req == REQ_CFG) {
            pendingCfgUri = uri;
            Uri dat = findSibling(uri, ".dat");
            if (dat == null) {
                new AlertDialog.Builder(this)
                        .setTitle("未找到同名数据文件")
                        .setMessage("没有在 cfg 同目录找到同名 .dat。\n请手动选择对应的数据文件。")
                        .setPositiveButton("选择 .dat", (d, w) -> pickDat())
                        .setNegativeButton("取消", null)
                        .show();
                return;
            }
            loadUri(uri, dat);
        } else if (req == REQ_DAT) {
            if (pendingCfgUri == null) {
                Toast.makeText(this, "请先选择 cfg 文件", Toast.LENGTH_SHORT).show();
                return;
            }
            loadUri(pendingCfgUri, uri);
        }
    }

    private void loadUri(final Uri cfgUri, final Uri datUri) {
        final String name = displayName(cfgUri);
        Toast.makeText(this, "正在解析 " + name, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] cfg = readAll(getContentResolver().openInputStream(cfgUri));
                InputStream datStream = getContentResolver().openInputStream(datUri);
                final ComtradeCase c = ComtradeCase.load(cfg, datStream, name);
                runOnUiThread(() -> bindCase(c));
            } catch (final Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "解析失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void loadSample() {
        new Thread(() -> {
            try {
                AssetManager am = getAssets();
                String base = "sample/BAY01_0024_20240610_113114_840";
                byte[] cfg = readAll(am.open(base + ".cfg"));
                final ComtradeCase c = ComtradeCase.load(cfg, am.open(base + ".dat"),
                        "BAY01_0024");
                runOnUiThread(() -> bindCase(c));
            } catch (final Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "示例加载失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        if (in == null) throw new Exception("无法打开文件");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        String s = uri.getLastPathSegment();
        return s == null ? "COMTRADE" : s;
    }

    /** 通过 DocumentsContract 在同目录内找同名 .dat。 */
    private Uri findSibling(Uri docUri, String ext) {
        try {
            String docId = DocumentsContract.getDocumentId(docUri);
            int slash = docId.lastIndexOf('/');
            if (slash <= 0) return null;
            String parentId = docId.substring(0, slash);
            String file = docId.substring(slash + 1);
            int dot = file.lastIndexOf('.');
            String target = (dot > 0 ? file.substring(0, dot) : file) + ext;

            Uri treeDoc = DocumentsContract.buildTreeDocumentUri(docUri.getAuthority(), docId);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeDoc, parentId);
            Cursor c = getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c == null) return null;
            while (c.moveToNext()) {
                String name = c.getString(1);
                if (name != null && name.equalsIgnoreCase(target)) {
                    String id = c.getString(0);
                    c.close();
                    return DocumentsContract.buildDocumentUriUsingTree(treeDoc, id);
                }
            }
            c.close();
        } catch (Exception ignored) {
        }
        return null;
    }

    // ── 绑定数据 ─────────────────────────────────────────────────────────────

    private void bindCase(ComtradeCase c) {
        data = c;
        chart.setData(c);
        chart.setVisibility(View.VISIBLE);
        if (chartPlaceholder != null) chartPlaceholder.setVisibility(View.GONE);
        if (toolsRow != null) toolsRow.setVisibility(View.VISIBLE);
        if (tabsRow != null) tabsRow.setVisibility(View.VISIBLE);
        applyPanelHeight();
        titleView.setText(c.cfgName.replace(".cfg", ""));
        infoView.setText(String.format(Locale.US,
                "%s · %s | %dA/%dD | fs %.0fHz | %d点 | %.1fms | T0@第%d点",
                shorten(c.station, 12), shorten(c.device, 10), c.analogCount(), c.digitalCount(),
                c.sampleRate, c.sampleCount, c.duration() * 1000, c.triggerIndex + 1));

        // 通道开关
        chipRow.removeAllViews();
        for (int ch = 0; ch < c.analogCount(); ch++) {
            final int index = ch;
            ComtradeCase.AnalogChannel ac = c.analogs.get(ch);
            chipRow.addView(chip(ac.name, chart.isVisible(ch), () -> {
                chart.setVisible(index, !chart.isVisible(index));
                rebuildChips();
            }));
        }
        chipRow.addView(chip("DI 数字量", false, () -> {
            chart.setShowDigital(!chart.showDigital());
            rebuildChips();
        }));

        autoDetectZeroSeq();
        updateHeaderButtons();
        refreshAnalysis();
    }

    /** 自动挑零序电压/电流通道：优先相别 Z 且单位匹配，其次看通道名。 */
    private void autoDetectZeroSeq() {
        u0Ch = -1;
        i0Ch = -1;
        if (data == null) return;
        for (int ch = 0; ch < data.analogCount(); ch++) {
            ComtradeCase.AnalogChannel ac = data.analogs.get(ch);
            boolean z = "Z".equalsIgnoreCase(ac.phase == null ? "" : ac.phase.trim())
                    || ac.name.toUpperCase(Locale.US).contains("U0")
                    || ac.name.toUpperCase(Locale.US).contains("UZ")
                    || ac.name.contains("零序");
            if (!z) continue;
            if (u0Ch < 0 && ac.unit.contains("V")) u0Ch = ch;
            if (i0Ch < 0 && ac.unit.contains("A")) i0Ch = ch;
        }
    }

    private void rebuildChips() {
        if (data == null) return;
        chipRow.removeAllViews();
        for (int ch = 0; ch < data.analogCount(); ch++) {
            final int index = ch;
            chipRow.addView(chip(data.analogs.get(ch).name, chart.isVisible(ch), () -> {
                chart.setVisible(index, !chart.isVisible(index));
                rebuildChips();
                refreshAnalysis();
            }));
        }
        chipRow.addView(chip("DI 数字量", chart.showDigital(), () -> {
            chart.setShowDigital(!chart.showDigital());
            rebuildChips();
        }));
    }

    private View chip(String label, boolean on, Runnable click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(10);
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) dp(10), (int) dp(5), (int) dp(10), (int) dp(5));
        t.setTextColor(on ? 0xFF14111E : Pal.TEXT_SECOND);
        t.setBackground(shape(on ? Pal.ACCENT_TEAL : Pal.BG_ELEVATED, 16,
                on ? 0 : Pal.BORDER_SUBTLE, on ? 0 : 1));
        t.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMarginEnd((int) dp(6));
        t.setLayoutParams(p);
        return t;
    }

    // ── 分析 ─────────────────────────────────────────────────────────────────

    private double f0() {
        return data == null || data.lineFreq <= 0 ? 50 : data.lineFreq;
    }

    private void refreshAnalysis() {
        if (data == null) return;
        analysisBox.removeAllViews();
        if (mode == MODE_HARM) buildHarmonics();
        else if (mode == MODE_PHASOR) buildPhasors();
        else if (mode == MODE_SMALL) buildSmallCurrent();
        else buildValues();
    }

    /** 以游标为中心的整周波窗口（用于相角、相量计算）。 */
    private int[] cycleWindow(int index) {
        int cyc = WaveMath.cycleSamples(data.sampleRate, f0());
        int from = index - cyc / 2;
        from = Math.max(0, Math.min(from, Math.max(0, data.sampleCount - cyc)));
        int to = Math.min(data.sampleCount, from + cyc);
        return new int[]{from, to};
    }

    private int[] windowSamples() {
        int a = data.indexOfTime(chart.cursorA());
        int b = data.indexOfTime(chart.cursorB());
        if (b <= a) {
            a = 0;
            b = data.sampleCount;
        }
        return new int[]{a, b};
    }

    private void buildValues() {
        int[] w = windowSamples();
        double dt = Math.abs(chart.cursorB() - chart.cursorA());

        analysisBox.addView(cursorSummary(dt));

        String[] head = {"通道", "T1", "T2", "有效值", "上峰值", "下峰值"};
        analysisBox.addView(row(head, -1, Pal.TEXT_MUTED, true));

        int cyc = WaveMath.cycleSamples(data.sampleRate, f0());
        for (int ch = 0; ch < data.analogCount(); ch++) {
            if (!chart.isVisible(ch)) continue;
            ComtradeCase.AnalogChannel ac = data.analogs.get(ch);
            float[] v = data.analog[ch];
            int ia = Math.min(data.sampleCount - 1, Math.max(0, data.indexOfTime(chart.cursorA())));
            int ib = Math.min(data.sampleCount - 1, Math.max(0, data.indexOfTime(chart.cursorB())));
            double factor = chart.isPrimary() ? 1 : data.secondaryRatio(ch);
            double rms = WaveMath.rms(v, w[0], w[1]) * factor;
            double hi = WaveMath.max(v, w[0], w[1]) * factor;
            double lo = WaveMath.min(v, w[0], w[1]) * factor;
            analysisBox.addView(row(new String[]{
                    ac.name,
                    fmt(v[ia] * factor, 1),
                    fmt(v[ib] * factor, 1),
                    fmt(rms, 1),
                    fmt(hi, 1),
                    fmt(lo, 1)
            }, ch, Pal.TEXT_PRIMARY, false));
        }

        if (chart.showDigital() && data.digitalCount() > 0) {
            TextView t = new TextView(this);
            t.setText("数字量通道：" + data.digitalCount() + " 路（波形区下方）");
            t.setTextColor(Pal.TEXT_MUTED);
            t.setTextSize(9);
            t.setTypeface(Typeface.MONOSPACE);
            t.setPadding(0, (int) dp(10), 0, 0);
            analysisBox.addView(t);
        }
    }

    private View cursorSummary(double dt) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
        box.setBackground(shape(Pal.BG_SURFACE, 12, Pal.BORDER_SUBTLE, 1));

        TextView a = new TextView(this);
        double cycles = dt * f0();
        double estF = dt > 0 ? Math.max(1, Math.round(cycles)) / dt : 0;
        a.setText(String.format(Locale.US, "T1 %.2fms   T2 %.2fms   Δt %.2fms   周波 %.2f   基波 %.2fHz",
                chart.cursorA() * 1000, chart.cursorB() * 1000, dt * 1000, cycles, estF));
        a.setTextColor(Pal.TEXT_SECOND);
        a.setTextSize(9.4f);
        a.setTypeface(Typeface.MONOSPACE);
        a.setLineSpacing(0, 1.2f);
        box.addView(a);

        TextView b = new TextView(this);
        b.setText(String.format(Locale.US, "采样率 %.0fHz · 采样点 %d · 录波 %d 点",
                data.sampleRate, data.sampleCount, data.sampleCount));
        b.setTextColor(Pal.TEXT_MUTED);
        b.setTextSize(8.4f);
        b.setTypeface(Typeface.MONOSPACE);
        b.setPadding(0, (int) dp(3), 0, 0);
        box.addView(b);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = (int) dp(8);
        box.setLayoutParams(p);
        return box;
    }

    private String cursorSummaryText() {
        double dt = Math.abs(chart.cursorB() - chart.cursorA());
        double cycles = dt * f0();
        double estF = dt > 0 ? Math.max(1, Math.round(cycles)) / dt : 0;
        return String.format(Locale.US,
                "T1 %.2fms   T2 %.2fms   Δt %.2fms   周波 %.2f   基波 %.2fHz",
                chart.cursorA() * 1000, chart.cursorB() * 1000, dt * 1000, cycles, estF);
    }

    private void buildHarmonics() {
        int ch = Math.max(0, Math.min(chart.selected(), data.analogCount() - 1));
        ComtradeCase.AnalogChannel ac = data.analogs.get(ch);
        float[] v = data.analog[ch];
        int[] w = windowSamples();
        if (w[1] - w[0] < 8) {
            int cyc = WaveMath.cycleSamples(data.sampleRate, f0());
            int end = Math.min(data.sampleCount, Math.max(cyc, w[0] + cyc));
            w = new int[]{Math.max(0, end - cyc), end};
        }

        double[][] h = WaveMath.harmonics(v, w[0], w[1], data.sampleRate, f0(), HARMONIC_ORDER);
        double thd = WaveMath.thd(h, HARMONIC_ORDER);
        double factor = chart.isPrimary() ? 1 : data.secondaryRatio(ch);
        double base = h[1][0];

        double maxAbs = Math.max(Math.abs(WaveMath.max(v, w[0], w[1])),
                Math.abs(WaveMath.min(v, w[0], w[1])));
        boolean weak = base <= 0 || base < maxAbs * 0.005;

        TextView head = new TextView(this);
        head.setText(String.format(Locale.US, "%s · %s · THD %.2f%%（窗口 %.1f~%.1fms）",
                ac.name, ac.unit, thd, chart.cursorA() * 1000, chart.cursorB() * 1000));
        head.setTextColor(Pal.ACCENT_TEAL);
        head.setTextSize(10);
        head.setTypeface(Typeface.MONOSPACE);
        head.setPadding((int) dp(2), 0, 0, (int) dp(8));
        analysisBox.addView(head);

        if (weak) {
            TextView warn = new TextView(this);
            warn.setText("该时间窗内基波幅值很小（远低于通道峰值），谐波占比不具参考意义；"
                    + "可用右侧「全览」或拖动游标选择含完整周波的区间。");
            warn.setTextColor(Pal.ACCENT_AMBER);
            warn.setTextSize(9.4f);
            warn.setLineSpacing(0, 1.25f);
            warn.setPadding((int) dp(2), 0, 0, (int) dp(8));
            analysisBox.addView(warn);
        }

        double[] pct = new double[HARMONIC_ORDER + 1];
        for (int k = 1; k <= HARMONIC_ORDER; k++) {
            pct[k] = base > 0 ? h[k][0] / base * 100 : 0;
        }
        SpectrumView spectrum = new SpectrumView(this);
        spectrum.setData(pct, thd, "%");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(132));
        sp.bottomMargin = (int) dp(10);
        analysisBox.addView(spectrum, sp);

        analysisBox.addView(row(new String[]{"次", "有效值", "相位", "含量"}, -1, Pal.TEXT_MUTED, true));
        for (int k = 1; k <= HARMONIC_ORDER; k++) {
            analysisBox.addView(row(new String[]{
                    String.valueOf(k),
                    fmt(h[k][0] * factor, 3),
                    String.format(Locale.US, "%.1f°", h[k][1]),
                    String.format(Locale.US, "%.2f%%", pct[k])
            }, -1, k == 1 ? Pal.ACCENT_PURPLE : Pal.TEXT_PRIMARY, false));
        }
        TextView tip = new TextView(this);
        tip.setText("点选波形通道可切换分析对象（当前：" + ac.name + "）");
        tip.setTextColor(Pal.TEXT_MUTED);
        tip.setTextSize(9);
        tip.setPadding(0, (int) dp(8), 0, 0);
        analysisBox.addView(tip);
    }

    private void buildPhasors() {
        int n = 0;
        for (int ch = 0; ch < data.analogCount(); ch++) if (chart.isVisible(ch)) n++;
        if (n == 0) {
            TextView t = new TextView(this);
            t.setText("请先在上方选择至少一个通道");
            t.setTextColor(Pal.TEXT_MUTED);
            t.setTextSize(11);
            analysisBox.addView(t);
            return;
        }

        int cursor = data.indexOfTime(chart.cursorA());
        int[] cw = cycleWindow(cursor);

        String[] labels = new String[n];
        String[] units = new String[n];
        double[] mags = new double[n];
        double[] angs = new double[n];
        int[] colors = new int[n];
        int i = 0;
        for (int ch = 0; ch < data.analogCount() && i < n; ch++) {
            if (!chart.isVisible(ch)) continue;
            ComtradeCase.AnalogChannel ac = data.analogs.get(ch);
            double[] tone = WaveMath.tone(data.analog[ch], cw[0], cw[1], f0(), data.sampleRate);
            labels[i] = ac.name;
            units[i] = ac.unit;
            colors[i] = com.pdzdh.calcutool.comtrade.PhaseColors.of(ac, ch);
            mags[i] = tone[0] * (chart.isPrimary() ? 1 : data.secondaryRatio(ch));
            angs[i] = tone[1];
            i++;
        }

        // 电压、电流量级差太大，按单位分组各自归一化，图上才都能看清
        double maxV = 0, maxA = 0, maxOther = 0;
        for (int k = 0; k < n; k++) {
            if (units[k].contains("V")) maxV = Math.max(maxV, mags[k]);
            else if (units[k].contains("A")) maxA = Math.max(maxA, mags[k]);
            else maxOther = Math.max(maxOther, mags[k]);
        }
        double[] norms = new double[n];
        for (int k = 0; k < n; k++) {
            boolean isV = units[k].contains("V");
            boolean isA = units[k].contains("A");
            double denom = isV ? maxV : isA ? maxA : maxOther;
            double factor = isV ? 1.0 : isA ? 0.62 : 0.8;
            norms[k] = denom > 0 ? mags[k] / denom * factor : 0;
        }

        PhasorChart view = new PhasorChart(this);
        view.setData(labels, mags, norms, angs, units, colors);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(250));
        analysisBox.addView(view, p);

        analysisBox.addView(row(new String[]{"通道", "幅值", "相位", "相对角"}, -1, Pal.TEXT_MUTED, true));
        for (int k = 0; k < n; k++) {
            analysisBox.addView(row(new String[]{
                    labels[k],
                    fmt(mags[k], 2) + units[k],
                    String.format(Locale.US, "%.1f°", angs[k]),
                    String.format(Locale.US, "%.1f°", WaveMath.norm(angs[k] - angs[0]))
            }, -1, k == 0 ? Pal.ACCENT_PURPLE : Pal.TEXT_PRIMARY, false));
        }
        TextView tip = new TextView(this);
        tip.setText(String.format(Locale.US,
                "基波 %.0fHz · T1 处一个周波计算 · 电压/电流分别归一化 · 相对角以 %s 为参考",
                f0(), labels[0]));
        tip.setTextColor(Pal.TEXT_MUTED);
        tip.setTextSize(9);
        tip.setLineSpacing(0, 1.25f);
        tip.setPadding(0, (int) dp(8), 0, 0);
        analysisBox.addView(tip);
    }

    // ── 小电流接地识别 ───────────────────────────────────────────────────────

    private void buildSmallCurrent() {
        analysisBox.addView(channelSelector("零序电压通道", true));
        analysisBox.addView(channelSelector("零序电流通道", false));

        if (u0Ch < 0 || i0Ch < 0) {
            TextView t = new TextView(this);
            t.setText("请先指定零序电压与零序电流通道");
            t.setTextColor(Pal.ACCENT_AMBER);
            t.setTextSize(11);
            t.setPadding(0, (int) dp(10), 0, 0);
            analysisBox.addView(t);
            return;
        }

        smallResult = com.pdzdh.calcutool.comtrade.SmallCurrentDetector
                .analyze(data, u0Ch, i0Ch, f0());
        com.pdzdh.calcutool.comtrade.SmallCurrentDetector.Result r = smallResult;

        double fu = chart.isPrimary() ? 1 : data.secondaryRatio(u0Ch);
        double fi = chart.isPrimary() ? 1 : data.secondaryRatio(i0Ch);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int accent = r.level == 1 ? Pal.ACCENT_TEAL
                : r.level == -1 ? Pal.ACCENT_RED
                : r.level == 2 ? Pal.ACCENT_AMBER : Pal.BORDER_MID;
        card.setBackground(shape(r.level == 1 ? 0x1453DAAF
                : r.level == -1 ? 0x14FF6E6E
                : r.level == 2 ? 0x14FFB955 : 0x0FFFFFFF, 12, accent, 1));
        card.setPadding((int) dp(12), (int) dp(10), (int) dp(12), (int) dp(10));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = (int) dp(10);
        card.setLayoutParams(cp);

        TextView title = new TextView(this);
        title.setText(r.detected ? "小电流接地判别结果" : "小电流接地判别");
        title.setTextColor(Pal.TEXT_MUTED);
        title.setTextSize(8.8f);
        title.setLetterSpacing(0.12f);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        card.addView(title);

        TextView verdict = new TextView(this);
        verdict.setText(r.verdict);
        verdict.setTextColor(accent == Pal.BORDER_MID ? Pal.TEXT_PRIMARY : accent);
        verdict.setTextSize(13.5f);
        verdict.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        verdict.setLineSpacing(0, 1.2f);
        verdict.setPadding(0, (int) dp(6), 0, (int) dp(8));
        card.addView(verdict);

        String[] rows = {
                String.format(Locale.US, "突变时刻   %.2f ms（第 %d 点）", r.onsetTimeMs, r.onsetIndex + 1),
                String.format(Locale.US, "零序电压   %.2f V（有效值）", r.u0FaultRms * fu),
                String.format(Locale.US, "零序电流   %.3f A（有效值）", r.i0FaultRms * fi),
                String.format(Locale.US, "瞬时零序功率 %.3f W", r.p0 * fu * fi),
                String.format(Locale.US, "相位差 φ   %.1f°，cosφ %.2f", r.phiDeg, r.cosPhi),
                String.format(Locale.US, "10 周波后 零序电压 %.2f V（%s）",
                        r.u0SteadyRms * fu, r.steadyPresent ? "持续存在" : "已衰减"),
        };
        for (String line : rows) {
            TextView t = new TextView(this);
            t.setText(line);
            t.setTextColor(Pal.TEXT_SECOND);
            t.setTextSize(10);
            t.setTypeface(Typeface.MONOSPACE);
            t.setPadding(0, (int) dp(2), 0, (int) dp(2));
            card.addView(t);
        }
        analysisBox.addView(card);

        TextView tip = new TextView(this);
        tip.setText("判据：零序电压突变后一个周波的基波零序功率方向（负为线路流向母线 → 界内），"
                + "并结合其后 10 个周波稳态零序电压是否持续；零序通道按相别 Z / 名称自动预选，可手动更改。");
        tip.setTextColor(Pal.TEXT_MUTED);
        tip.setTextSize(9);
        tip.setLineSpacing(0, 1.3f);
        tip.setPadding(0, (int) dp(10), 0, 0);
        analysisBox.addView(tip);
    }

    /** 零序通道选择：一行横向可滚动的通道芯片。 */
    private View channelSelector(String label, final boolean voltage) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, (int) dp(4), 0, (int) dp(2));

        int current = voltage ? u0Ch : i0Ch;
        TextView title = new TextView(this);
        title.setText(label + "：" + (current >= 0 ? data.analogs.get(current).name : "未选择"));
        title.setTextColor(Pal.TEXT_SECOND);
        title.setTextSize(9.6f);
        title.setTypeface(Typeface.MONOSPACE);
        wrap.addView(title);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, (int) dp(4), 0, 0);
        for (int ch = 0; ch < data.analogCount(); ch++) {
            final int index = ch;
            ComtradeCase.AnalogChannel ac = data.analogs.get(ch);
            if (voltage && !ac.unit.contains("V")) continue;
            if (!voltage && !ac.unit.contains("A")) continue;
            boolean on = current == ch;
            TextView chip = new TextView(this);
            chip.setText(ac.name);
            chip.setTextSize(9.4f);
            chip.setTypeface(Typeface.MONOSPACE);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding((int) dp(8), (int) dp(4), (int) dp(8), (int) dp(4));
            chip.setTextColor(on ? 0xFF14111E : Pal.TEXT_SECOND);
            chip.setBackground(shape(on ? Pal.ACCENT_TEAL : Pal.BG_ELEVATED, 12,
                    on ? 0 : Pal.BORDER_SUBTLE, on ? 0 : 1));
            chip.setOnClickListener(v -> {
                if (voltage) u0Ch = index; else i0Ch = index;
                refreshAnalysis();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int) dp(4));
            chips.addView(chip, lp);
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(chips);
        wrap.addView(scroll);
        return wrap;
    }

    private View row(String[] cells, int channel, int color, boolean header) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding((int) dp(2), (int) dp(5), (int) dp(2), (int) dp(5));

        float[] weights = {1.35f, 1.05f, 1.05f, 1.05f, 1.05f, 0.95f};
        for (int i = 0; i < cells.length; i++) {
            TextView t = new TextView(this);
            t.setText(cells[i]);
            t.setTextSize(header ? 8.2f : 9.6f);
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextColor(header ? color : (i == 0 ? channelColor(channel, color) : color));
            t.setGravity(i == 0 ? Gravity.START : Gravity.END);
            t.setSingleLine(true);
            row.addView(t, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, weights[Math.min(i, weights.length - 1)]));
        }
        return row;
    }

    private int channelColor(int channel, int fallback) {
        return channel < 0 ? fallback : Pal.TEXT_SECOND;
    }

    private static String fmt(double v, int decimals) {
        double a = Math.abs(v);
        if (a >= 10000) return String.format(Locale.US, "%.0f", v);
        if (a >= 100) return String.format(Locale.US, "%.1f", v);
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private GradientDrawable shape(int fill, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, (int) dp(strokeDp)), stroke);
        return d;
    }

    // ── 波形回调 ─────────────────────────────────────────────────────────────

    @Override
    public void onSelect(int analogIndex) {
        refreshAnalysis();
    }

    @Override
    public void onCursorsChanged() {
        // 拖动过程中只刷新读数行，避免每帧重建整张表
        if (cursorSummaryView != null && mode == MODE_VALUES) {
            cursorSummaryView.setText(cursorSummaryText());
        }
    }

    @Override
    public void onCursorDragEnd() {
        refreshAnalysis();
    }

    @Override
    public void onViewChanged() {
        refreshAnalysis();
    }
}
