package com.pdzdh.calcutool;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 配电工具箱首页：平铺展示各小工具。 */
public class HomeActivity extends Activity {

    private static final String AUTHOR = "落霞孤鴻";
    private static final String WECHAT = "18372698780";

    private float density;

    private static final class Tool {
        final String name;
        final String desc;
        final String tag;
        final int glyph;
        final Class<?> activity;

        Tool(String name, String desc, String tag, int glyph, Class<?> activity) {
            this.name = name;
            this.desc = desc;
            this.tag = tag;
            this.glyph = glyph;
            this.activity = activity;
        }
    }

    private final Tool[] tools = {
            new Tool("三相交流分析", "序分量 · 功率 · 相量图", "AC ANALYSIS",
                    ToolGlyph.PHASOR, MainActivity.class),
            new Tool("终端二维码", "扫码校验六字段规范", "TERMINAL QR",
                    ToolGlyph.QR, ScanActivity.class),
            new Tool("录波分析", "COMTRADE · 谐波 · 有效值", "COMTRADE",
                    ToolGlyph.WAVE, ComtradeActivity.class),
            new Tool("转刀割草", "绝地求生小游戏", "GAME",
                    ToolGlyph.GAME, GameActivity.class),
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        setContentView(buildRoot());
    }

    private float dp(float v) { return v * density; }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Pal.BG_BASE);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) dp(14), (int) dp(14), (int) dp(14), (int) dp(18));

        TextView caption = new TextView(this);
        caption.setText("全部工具");
        caption.setTextColor(Pal.TEXT_MUTED);
        caption.setTextSize(9.2f);
        caption.setLetterSpacing(0.16f);
        caption.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        caption.setPadding((int) dp(2), 0, 0, (int) dp(10));
        content.addView(caption);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        for (Tool t : tools) {
            View card = buildCard(t);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = (int) dp(150);
            lp.setMargins((int) dp(4), (int) dp(4), (int) dp(4), (int) dp(4));
            grid.addView(card, lp);
        }
        content.addView(grid, wrap());

        content.addView(buildAbout(), topMargin(18));

        TextView ver = new TextView(this);
        ver.setText("v1.2.0  ·  build android");
        ver.setTextColor(Pal.TEXT_MUTED);
        ver.setTextSize(9);
        ver.setTypeface(Typeface.MONOSPACE);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, (int) dp(14), 0, 0);
        content.addView(ver);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Pal.BG_SURFACE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) dp(18), (int) dp(16), (int) dp(18), (int) dp(14));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("配电工具箱");
        title.setTextColor(Pal.TEXT_PRIMARY);
        title.setTextSize(21);
        title.setLetterSpacing(0.02f);
        title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        titles.addView(title);

        TextView sub = new TextView(this);
        sub.setText("POWER DISTRIBUTION TOOLBOX");
        sub.setTextColor(Pal.TEXT_MUTED);
        sub.setTextSize(8.8f);
        sub.setLetterSpacing(0.14f);
        sub.setTypeface(Typeface.MONOSPACE);
        sub.setPadding(0, (int) dp(3), 0, 0);
        titles.addView(sub);
        row.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setText(String.valueOf(tools.length));
        badge.setTextColor(Pal.ACCENT_TEAL);
        badge.setTextSize(11);
        badge.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(shape(0x1F53DAAF, 20, 0x5553DAAF, 1));
        row.addView(badge, new LinearLayout.LayoutParams((int) dp(34), (int) dp(26)));

        header.addView(row);
        View line = new View(this);
        line.setBackgroundColor(Pal.BORDER_SUBTLE);
        header.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) density)));
        return header;
    }

    private View buildCard(final Tool tool) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) dp(14), (int) dp(13), (int) dp(14), (int) dp(12));
        card.setBackground(shape(Pal.BG_SURFACE, 18, Pal.BORDER_SUBTLE, 1));

        int[] pal = ToolGlyph.palette(tool.glyph);
        LinearLayout iconBox = new LinearLayout(this);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(shape(pal[0], 14, 0, 0));
        ToolGlyph glyph = new ToolGlyph(this, tool.glyph);
        iconBox.addView(glyph, new LinearLayout.LayoutParams((int) dp(40), (int) dp(40)));
        card.addView(iconBox, new LinearLayout.LayoutParams((int) dp(52), (int) dp(52)));

        TextView tag = new TextView(this);
        tag.setText(tool.tag);
        tag.setTextColor(Pal.TEXT_MUTED);
        tag.setTextSize(8);
        tag.setLetterSpacing(0.12f);
        tag.setTypeface(Typeface.MONOSPACE);
        tag.setPadding(0, (int) dp(11), 0, (int) dp(2));
        card.addView(tag);

        TextView name = new TextView(this);
        name.setText(tool.name);
        name.setTextColor(Pal.TEXT_PRIMARY);
        name.setTextSize(13.5f);
        name.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        card.addView(name);

        TextView desc = new TextView(this);
        desc.setText(tool.desc);
        desc.setTextColor(Pal.TEXT_SECOND);
        desc.setTextSize(9.5f);
        desc.setPadding(0, (int) dp(3), 0, 0);
        card.addView(desc);

        card.setOnClickListener(v -> startActivity(new Intent(this, tool.activity)));
        return card;
    }

    private View buildAbout() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) dp(14), (int) dp(12), (int) dp(14), (int) dp(12));
        card.setBackground(shape(Pal.BG_SURFACE, 18, Pal.BORDER_SUBTLE, 1));

        TextView title = new TextView(this);
        title.setText("关于");
        title.setTextColor(Pal.ACCENT_PURPLE);
        title.setTextSize(9.2f);
        title.setLetterSpacing(0.16f);
        title.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        card.addView(title);

        card.addView(aboutRow("作者", AUTHOR, Pal.TEXT_PRIMARY, false));

        LinearLayout wx = aboutRow("微信", WECHAT + "  ⧉", Pal.ACCENT_TEAL, true);
        wx.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("wechat", WECHAT));
            Toast.makeText(this, "微信号已复制  " + WECHAT, Toast.LENGTH_SHORT).show();
        });
        card.addView(wx);
        return card;
    }

    private LinearLayout aboutRow(String key, String value, int color, boolean bold) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) dp(1), (int) dp(7), (int) dp(1), (int) dp(7));

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextColor(Pal.TEXT_SECOND);
        k.setTextSize(11.6f);
        row.addView(k, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(12);
        v.setTypeface(Typeface.MONOSPACE);
        v.setGravity(Gravity.END);
        row.addView(v);
        return row;
    }

    private GradientDrawable shape(int fill, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, (int) dp(strokeDp)), stroke);
        return d;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int dpValue) {
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = (int) dp(dpValue);
        return p;
    }
}
