package com.pdzdh.calcutool;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Single-screen UI, laid out programmatically to mirror the WinForm desktop panel. */
public class MainActivity extends Activity {

    private static final String PREFS = "pdzdh_calcutool";
    private static final String KEY_LANG = "lang_en";

    private static final String AUTHOR = "落霞孤鴻";
    private static final String WECHAT = "18372698780";

    private static final String[] PARAM_NAMES = {"Ua", "Ub", "Uc", "Ia", "Ib", "Ic"};
    private static final String[] PARAM_UNITS = {"V", "V", "V", "A", "A", "A"};
    private static final int[] PARAM_COLORS = {Pal.UA, Pal.UB, Pal.UC, Pal.UA, Pal.UB, Pal.UC};

    private final EditText[] mags = new EditText[6];
    private final EditText[] angs = new EditText[6];

    private PhasorView chart;
    private ScrollView scroll;
    private LinearLayout resultsBox;
    private TextView lblTitle, lblSubtitle, lblInput, lblMag, lblAng, lblResults, lblChart, lblRun, lblNote;
    private TextView lblAbout, lblAuthorKey, lblWechatKey;
    private TextView tagAB, tagBC, tagCA;
    private TextView langBtn;

    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        Lang.setEn(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_LANG, false));
        setContentView(buildRoot());
        scroll.requestFocus();   // keep the first input field from stealing focus and auto-scrolling
        runCalc();
    }

    private float dp(double v) { return (float) (v * density); }

    // ── layout ──────────────────────────────────────────────────────────────────

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.BG_BASE);
        // keep the footer above the navigation bar even on edge-to-edge windows
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        root.addView(buildHeader());

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setFocusableInTouchMode(true);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setPadding(0, 0, 0, (int) dp(6));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) dp(14), (int) dp(12), (int) dp(14), (int) dp(14));
        content.addView(buildInputCard());
        content.addView(buildChartCard(), margins((int) dp(12)));
        content.addView(buildLineVoltageCard(), margins((int) dp(12)));
        content.addView(buildResultCard(), margins((int) dp(12)));
        content.addView(buildAboutCard(), margins((int) dp(12)));
        scroll.addView(content);

        root.addView(scroll);
        root.addView(buildFooter());
        applyStaticTexts();
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Pal.BG_SURFACE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) dp(16), (int) dp(11), (int) dp(12), (int) dp(11));

        TextView badge = new TextView(this);
        badge.setText("Y");
        badge.setTextSize(15);
        badge.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        badge.setTextColor(Pal.ACCENT_PURPLE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(Pal.BG_ELEVATED, 10, Pal.BORDER_MID, 1));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams((int) dp(30), (int) dp(30));
        bp.setMarginEnd((int) dp(10));
        row.addView(badge, bp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        lblTitle = new TextView(this);
        lblTitle.setTextColor(Pal.TEXT_PRIMARY);
        lblTitle.setTextSize(16.5f);
        lblTitle.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titles.addView(lblTitle);

        lblSubtitle = new TextView(this);
        lblSubtitle.setText("IEC-60909 · SYMMETRICAL COMPONENTS · v1.1");
        lblSubtitle.setTextColor(Pal.TEXT_MUTED);
        lblSubtitle.setTextSize(8.6f);
        lblSubtitle.setTypeface(Typeface.MONOSPACE);
        lblSubtitle.setLetterSpacing(0.06f);
        titles.addView(lblSubtitle);

        row.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        langBtn = new TextView(this);
        langBtn.setText("EN / 中");
        langBtn.setTextSize(10.5f);
        langBtn.setTypeface(Typeface.MONOSPACE);
        langBtn.setTextColor(Pal.TEXT_PRIMARY);
        langBtn.setGravity(Gravity.CENTER);
        langBtn.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
        langBtn.setBackground(selectable(rounded(Pal.BG_ELEVATED, 20, Pal.BORDER_MID, 1),
                rounded(Pal.BG_HOVER, 20, Pal.ACCENT_PURPLE, 1)));
        langBtn.setOnClickListener(v -> toggleLang());
        row.addView(langBtn);

        header.addView(row);
        header.addView(divider());
        return header;
    }

    private View buildInputCard() {
        LinearLayout card = card();
        lblInput = sectionLabel(Pal.ACCENT_PURPLE);
        card.addView(lblInput);

        // column captions
        LinearLayout caps = new LinearLayout(this);
        caps.setOrientation(LinearLayout.HORIZONTAL);
        caps.setPadding(0, (int) dp(8), 0, (int) dp(2));
        caps.addView(space(38));
        lblMag = caption(Lang.t("magnitude"));
        lblMag.setGravity(Gravity.CENTER);
        caps.addView(lblMag, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        caps.addView(space(52));
        lblAng = caption(Lang.t("angle"));
        lblAng.setGravity(Gravity.CENTER);
        caps.addView(lblAng, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        caps.addView(space(14));
        card.addView(caps);

        String[][] defs = {
                {"100", "0"}, {"100", "-120"}, {"100", "120"},
                {"10", "0"}, {"10", "-120"}, {"10", "120"}};

        for (int i = 0; i < 6; i++) {
            card.addView(paramRow(i, defs[i][0], defs[i][1]));
        }
        return card;
    }

    private View paramRow(int idx, String defMag, String defAng) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) dp(3), 0, (int) dp(3));

        View dot = new View(this);
        dot.setBackground(rounded(PARAM_COLORS[idx], 4, 0, 0));
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams((int) dp(7), (int) dp(7));
        dp1.setMarginStart((int) dp(2));
        dp1.setMarginEnd((int) dp(9));
        row.addView(dot, dp1);

        TextView name = new TextView(this);
        name.setText(PARAM_NAMES[idx]);
        name.setTextColor(Pal.TEXT_SECOND);
        name.setTextSize(13);
        name.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams((int) dp(26),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(name, np);

        mags[idx] = numberField(defMag, Pal.BG_ELEVATED);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, (int) dp(42), 1f);
        mp.setMarginStart((int) dp(1));
        row.addView(mags[idx], mp);

        row.addView(unitLabel(PARAM_UNITS[idx]));

        TextView angleIcon = new TextView(this);
        angleIcon.setText("∠");
        angleIcon.setTextColor(Pal.TEXT_MUTED);
        angleIcon.setTextSize(12);
        angleIcon.setGravity(Gravity.CENTER);
        row.addView(angleIcon, new LinearLayout.LayoutParams((int) dp(15),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        angs[idx] = numberField(defAng, Pal.BG_INPUT_ANG);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, (int) dp(42), 1f);
        ap.setMarginStart((int) dp(2));
        row.addView(angs[idx], ap);

        TextView deg = new TextView(this);
        deg.setText("°");
        deg.setTextColor(Pal.TEXT_MUTED);
        deg.setTextSize(11);
        deg.setGravity(Gravity.CENTER);
        row.addView(deg, new LinearLayout.LayoutParams((int) dp(13),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View buildChartCard() {
        LinearLayout card = card();
        lblChart = sectionLabel(Pal.ACCENT_AMBER);
        card.addView(lblChart);

        chart = new PhasorView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(320));
        cp.topMargin = (int) dp(6);
        card.addView(chart, cp);
        return card;
    }

    private View buildLineVoltageCard() {
        LinearLayout card = card();
        card.setPadding((int) dp(10), (int) dp(9), (int) dp(10), (int) dp(9));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout a = voltageChip("Uab", Pal.UAB);
        LinearLayout b = voltageChip("Ubc", Pal.UBC);
        LinearLayout c = voltageChip("Uca", Pal.UCA);
        tagAB = (TextView) a.getChildAt(1);
        tagBC = (TextView) b.getChildAt(1);
        tagCA = (TextView) c.getChildAt(1);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd((int) dp(6));
        row.addView(a, lp);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMarginEnd((int) dp(6));
        row.addView(b, lp2);

        row.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
        return card;
    }

    private LinearLayout voltageChip(String name, int color) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setBackground(rounded(Pal.BG_ELEVATED, 10, (color & 0x00FFFFFF) | 0x55000000, 1));
        chip.setPadding((int) dp(9), (int) dp(7), (int) dp(9), (int) dp(7));

        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(color);
        t.setTextSize(10);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        chip.addView(t);

        TextView v = new TextView(this);
        v.setText("—");
        v.setTextColor(Pal.TEXT_PRIMARY);
        v.setTextSize(10.2f);
        v.setTypeface(Typeface.MONOSPACE);
        v.setLineSpacing(0, 1.15f);
        chip.addView(v);
        return chip;
    }

    private View buildResultCard() {
        LinearLayout card = card();
        lblResults = sectionLabel(Pal.ACCENT_TEAL);
        card.addView(lblResults);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setPadding(0, (int) dp(4), 0, 0);
        card.addView(resultsBox);
        return card;
    }

    private View buildAboutCard() {
        LinearLayout card = card();
        card.setPadding((int) dp(14), (int) dp(12), (int) dp(14), (int) dp(10));

        lblAbout = sectionLabel(Pal.ACCENT_PURPLE);
        card.addView(lblAbout);

        lblAuthorKey = aboutKey();
        card.addView(aboutRow(lblAuthorKey, AUTHOR, Pal.TEXT_PRIMARY, false));

        lblWechatKey = aboutKey();
        LinearLayout wx = aboutRow(lblWechatKey, WECHAT, Pal.ACCENT_TEAL, true);
        wx.setOnClickListener(v -> copyWechat());
        card.addView(wx);

        TextView ver = new TextView(this);
        ver.setText("v" + appVersion() + "  ·  build " + (Lang.isEn() ? "android" : "安卓"));
        ver.setTextColor(Pal.TEXT_MUTED);
        ver.setTextSize(9);
        ver.setTypeface(Typeface.MONOSPACE);
        ver.setPadding((int) dp(1), (int) dp(8), 0, 0);
        card.addView(ver);
        return card;
    }

    private TextView aboutKey() {
        TextView t = new TextView(this);
        t.setTextColor(Pal.TEXT_SECOND);
        t.setTextSize(11.6f);
        return t;
    }

    private LinearLayout aboutRow(TextView key, String value, int valueColor, boolean copyable) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) dp(1), (int) dp(7), (int) dp(1), (int) dp(7));

        row.addView(key, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(copyable ? value + "  ⧉" : value);
        v.setTextColor(valueColor);
        v.setTextSize(12);
        v.setTypeface(Typeface.MONOSPACE);
        v.setGravity(Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void copyWechat() {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("wechat", WECHAT));
        Toast.makeText(this, Lang.t("copied") + "  " + WECHAT, Toast.LENGTH_SHORT).show();
    }

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.1";
        }
    }

    private View buildFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setBackgroundColor(Pal.BG_BASE);
        footer.addView(divider());

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding((int) dp(14), (int) dp(8), (int) dp(14), (int) dp(10));

        lblNote = new TextView(this);
        lblNote.setText("// IEC-60909 · Symmetrical Components Method");
        lblNote.setTextColor(Pal.TEXT_MUTED);
        lblNote.setTextSize(8.8f);
        lblNote.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC));
        lblNote.setPadding((int) dp(2), 0, 0, (int) dp(7));
        inner.addView(lblNote);

        lblRun = new TextView(this);
        lblRun.setGravity(Gravity.CENTER);
        lblRun.setTextColor(0xFF14111E);
        lblRun.setTextSize(15);
        lblRun.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        lblRun.setPadding(0, (int) dp(14), 0, (int) dp(14));
        lblRun.setBackground(ripple(rounded(Pal.ACCENT_PURPLE, 14, 0, 0)));
        lblRun.setOnClickListener(v -> runCalc());
        inner.addView(lblRun);

        footer.addView(inner);
        return footer;
    }

    private void applyStaticTexts() {
        lblTitle.setText(Lang.t("title"));
        if (lblInput != null) {
            lblInput.setText(Lang.t("input"));
            lblResults.setText(Lang.t("results"));
            lblChart.setText(Lang.t("chart"));
            lblRun.setText("▶   " + Lang.t("run"));
        }
        if (lblAbout != null) {
            lblAbout.setText(Lang.t("about"));
            lblAuthorKey.setText(Lang.t("author"));
            lblWechatKey.setText(Lang.t("wechat"));
        }
        if (lblMag != null) {
            lblMag.setText(Lang.t("magnitude"));
            lblAng.setText(Lang.t("angle"));
        }
        langBtn.setText(Lang.isEn() ? "EN / 中" : "中 / EN");
    }

    // ── language ────────────────────────────────────────────────────────────────

    private void toggleLang() {
        Lang.setEn(!Lang.isEn());
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit().putBoolean(KEY_LANG, Lang.isEn()).apply();
        applyStaticTexts();
        runCalc();
    }

    // ── calculation & rendering ─────────────────────────────────────────────────

    private void runCalc() {
        double[] m = new double[6];
        double[] a = new double[6];
        for (int i = 0; i < 6; i++) {
            m[i] = parse(mags[i]);
            a[i] = parse(angs[i]);
        }

        final PhasorCalc.Result r;
        try {
            r = PhasorCalc.compute(m, a);
        } catch (RuntimeException ex) {
            Toast.makeText(this, Lang.isEn() ? "Input error, please check the values."
                    : "输入格式错误，请检查各参数值。", Toast.LENGTH_SHORT).show();
            return;
        }

        chart.setResult(r);
        fillResults(r);
        updateChips(r);
    }

    private void updateChartOnly() {
        double[] m = new double[6];
        double[] a = new double[6];
        for (int i = 0; i < 6; i++) {
            m[i] = parse(mags[i]);
            a[i] = parse(angs[i]);
        }
        chart.setResult(PhasorCalc.compute(m, a));
    }

    private void fillResults(PhasorCalc.Result r) {
        resultsBox.removeAllViews();

        addGroup(Lang.t("vseq"));
        addSeq(Lang.t("pos3"), r.v1, r.baseAngle, "V");
        addSeq(Lang.t("pos1"), r.v1.div(3), r.baseAngle, "V");
        addSeq(Lang.t("neg3"), r.v2, r.baseAngle, "V");
        addSeq(Lang.t("neg1"), r.v2.div(3), r.baseAngle, "V");
        addSeq(Lang.t("zero3"), r.v0, r.baseAngle, "V");
        addSeq(Lang.t("zero1"), r.v0.div(3), r.baseAngle, "V");

        addGroup(Lang.t("iseq"));
        addSeq(Lang.t("pos3i"), r.i1, r.baseAngle, "A");
        addSeq(Lang.t("pos1i"), r.i1.div(3), r.baseAngle, "A");
        addSeq(Lang.t("neg3i"), r.i2, r.baseAngle, "A");
        addSeq(Lang.t("neg1i"), r.i2.div(3), r.baseAngle, "A");
        addSeq(Lang.t("zero3i"), r.i0, r.baseAngle, "A");
        addSeq(Lang.t("zero1i"), r.i0.div(3), r.baseAngle, "A");

        addGroup(Lang.t("unbal"));
        addRow(Lang.t("vUnbal"), num(r.vuf, 2), "—", "%");
        addRow(Lang.t("vZero"), num(r.v0uf, 2), "—", "%");
        addRow(Lang.t("iUnbal"), num(r.iuf, 2), "—", "%");
        addRow(Lang.t("iZero"), num(r.i0uf, 2), "—", "%");

        addGroup(Lang.t("power"));
        addRow(Lang.t("pAct"), num(r.st.re, 2), "—", "W");
        addRow(Lang.t("pReact"), num(r.st.im, 2), "—", "var");
        addRow(Lang.t("pApp"), num(r.st.mag(), 2), "—", "VA");
        addRow(Lang.t("pPf"), num(r.pfT, 4), "—", "");

        addGroup(Lang.t("powerPh"));
        addRow("Pa / PFa", num(r.sa.re, 2), num(r.pfA, 4), "W");
        addRow("Pb / PFb", num(r.sb.re, 2), num(r.pfB, 4), "W");
        addRow("Pc / PFc", num(r.sc.re, 2), num(r.pfC, 4), "W");
        addRow("Qa", num(r.sa.im, 2), "—", "var");
        addRow("Qb", num(r.sb.im, 2), "—", "var");
        addRow("Qc", num(r.sc.im, 2), "—", "var");
        addRow("Sa", num(r.sa.mag(), 2), "—", "VA");
        addRow("Sb", num(r.sb.mag(), 2), "—", "VA");
        addRow("Sc", num(r.sc.mag(), 2), "—", "VA");
    }

    private void updateChips(PhasorCalc.Result r) {
        tagAB.setText(voltageText(r.uab, r.baseAngle));
        tagBC.setText(voltageText(r.ubc, r.baseAngle));
        tagCA.setText(voltageText(r.uca, r.baseAngle));
    }

    /** Two lines so the value never clips inside the narrow chip. */
    private static String voltageText(Complex c, double base) {
        return String.format(Locale.US, "%.2f V%n∠ %.2f°", c.mag(),
                PhasorCalc.displayAngle(c, base));
    }

    private static String num(double v, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    private void addGroup(String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Pal.TEXT_MUTED);
        t.setTextSize(8.8f);
        t.setLetterSpacing(0.14f);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.setPadding((int) dp(1), (int) dp(11), 0, (int) dp(3));
        resultsBox.addView(t);
    }

    private void addSeq(String label, Complex v, double base, String unit) {
        addRow(label, num(v.mag(), 3),
                String.format(Locale.US, "%.2f°", PhasorCalc.displayAngle(v, base)), unit);
    }

    private void addRow(String label, String value, String angle, String unit) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) dp(1), (int) dp(6), 0, (int) dp(6));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Pal.TEXT_SECOND);
        l.setTextSize(11.6f);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Pal.TEXT_PRIMARY);
        v.setTextSize(12);
        v.setTypeface(Typeface.MONOSPACE);
        v.setGravity(Gravity.END);
        v.setSingleLine(true);
        row.addView(v, new LinearLayout.LayoutParams((int) dp(74),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView a = new TextView(this);
        a.setText(angle);
        a.setTextColor(Pal.TEXT_SECOND);
        a.setTextSize(11);
        a.setTypeface(Typeface.MONOSPACE);
        a.setGravity(Gravity.END);
        a.setSingleLine(true);
        row.addView(a, new LinearLayout.LayoutParams((int) dp(56),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView u = new TextView(this);
        u.setText(unit);
        u.setTextColor(Pal.TEXT_MUTED);
        u.setTextSize(9.4f);
        u.setTypeface(Typeface.MONOSPACE);
        u.setGravity(Gravity.END);
        row.addView(u, new LinearLayout.LayoutParams((int) dp(30),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        resultsBox.addView(row);
        resultsBox.addView(softDivider());
    }

    // ── widgets ────────────────────────────────────────────────────────────────

    private EditText numberField(String value, int bgColor) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextSize(13);
        e.setTypeface(Typeface.MONOSPACE);
        e.setTextColor(Pal.TEXT_PRIMARY);
        e.setHintTextColor(Pal.TEXT_MUTED);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setPadding((int) dp(3), 0, (int) dp(3), 0);
        e.setSelectAllOnFocus(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setBackground(selectable(rounded(bgColor, 9, Pal.BORDER_SUBTLE, 1),
                rounded(bgColor, 9, Pal.ACCENT_PURPLE, 1)));
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateChartOnly(); }
        });
        e.setOnEditorActionListener((v, actionId, event) -> {
            android.view.inputmethod.InputMethodManager im =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
            v.clearFocus();
            return false;
        });
        return e;
    }

    private TextView unitLabel(String unit) {
        TextView t = new TextView(this);
        t.setText(unit);
        t.setTextColor(Pal.TEXT_MUTED);
        t.setTextSize(9);
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams((int) dp(14),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(Pal.BG_SURFACE, 18, Pal.BORDER_SUBTLE, 1));
        card.setPadding((int) dp(14), (int) dp(13), (int) dp(14), (int) dp(13));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private TextView sectionLabel(int color) {
        TextView t = new TextView(this);
        t.setTextColor(color);
        t.setTextSize(9.2f);
        t.setLetterSpacing(0.16f);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        return t;
    }

    private TextView caption(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Pal.TEXT_MUTED);
        t.setTextSize(8.4f);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    private View space(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams((int) dp(widthDp),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return v;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Pal.BORDER_SUBTLE);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) (density))));
        return v;
    }

    private View softDivider() {
        View v = new View(this);
        v.setBackgroundColor(0x0DFFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        v.setLayoutParams(p);
        return v;
    }

    /** Top margin only; returned params are applied through addView(view, params). */
    private LinearLayout.LayoutParams margins(int v) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, v, 0, 0);
        return p;
    }

    private GradientDrawable rounded(int fillColor, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, (int) dp(strokeDp)), strokeColor);
        return d;
    }

    private StateListDrawable selectable(android.graphics.drawable.Drawable normal,
                                         android.graphics.drawable.Drawable pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{android.R.attr.state_focused}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    private RippleDrawable ripple(GradientDrawable content) {
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                content, null);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static double parse(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.")) return 0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_LANG, Lang.isEn()).apply();
    }
}
