using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Linq;
using System.Numerics;
using System.Windows.Forms;

namespace 电工计算器
{
    public partial class main : Form
    {
        // ── 调色板 ─────────────────────────────────────────────────────────────
        static readonly Color BgBase      = Color.FromArgb(13,  12,  17 ); // 最深背景
        static readonly Color BgSurface   = Color.FromArgb(22,  21,  28 ); // 面板底色
        static readonly Color BgElevated  = Color.FromArgb(30,  29,  38 ); // 卡片/输入
        static readonly Color BgHover     = Color.FromArgb(40,  38,  52 ); // Hover 状态
        static readonly Color BorderSubtle= Color.FromArgb(48,  45,  62 ); // 微弱边框
        static readonly Color BorderMid   = Color.FromArgb(68,  65,  88 ); // 中等边框
        static readonly Color TextPrimary = Color.FromArgb(236, 233, 245); // 主文字
        static readonly Color TextSecond  = Color.FromArgb(148, 142, 172); // 次要文字
        static readonly Color TextMuted   = Color.FromArgb(88,  84, 110 ); // 暗提示
        static readonly Color AccentPurple= Color.FromArgb(163, 135, 255); // Claude 紫
        static readonly Color AccentTeal  = Color.FromArgb(83,  218, 175); // 青绿
        static readonly Color AccentAmber = Color.FromArgb(255, 185, 85 ); // 琥珀
        static readonly Color AccentRed   = Color.FromArgb(255, 110, 110); // 红
        static readonly Color AccentGreen = Color.FromArgb(122, 210, 96 ); // 绿
        static readonly Color AccentPink  = Color.FromArgb(255, 130, 185); // 粉
        static readonly Color AccentCyan  = Color.FromArgb(100, 210, 255); // 青

        // 相量颜色映射
        static Color ColUa  = Color.FromArgb(255, 235, 59 ); // A 相电压 —— 明黄
        static Color ColUb  = Color.FromArgb(76,  175, 80 ); // B 相电压 —— 绿
        static Color ColUc  = Color.FromArgb(244, 67,  54 ); // C 相电压 —— 红
        static Color ColUab = Color.FromArgb(255, 152, 0  ); // 线电压 AB —— 橙
        static Color ColUbc = Color.FromArgb(139, 195, 74 ); // 线电压 BC —— 浅绿
        static Color ColUca = Color.FromArgb(233, 30,  99 ); // 线电压 CA —— 粉红
        static Color ColIa  = Color.FromArgb(255, 245, 157); // A 相电流 —— 淡黄
        static Color ColIb  = Color.FromArgb(165, 214, 167); // B 相电流 —— 淡绿
        static Color ColIc  = Color.FromArgb(239, 154, 154); // C 相电流 —— 淡红
        static Color ColPos = Color.FromArgb(33,  150, 243); // 正序 —— 蓝
        static Color ColNeg = Color.FromArgb(156, 39,  176); // 负序 —— 紫
        static Color ColZer = Color.FromArgb(158, 158, 158); // 零序 —— 灰

        // ── 控件声明 ────────────────────────────────────────────────────────────
        PictureBox polarChart;
        Label lvLabelAB, lvLabelBC, lvLabelCA;
        ListView lvResult;
        NumericUpDown vaMag, vaAng, vbMag, vbAng, vcMag, vcAng,
                      iaMag, iaAng, ibMag, ibAng, icMag, icAng;

        // 语言状态与需要更新的UI控件
        bool isEnglish = true;
        Label lblMainTitle, lblSubTitle;
        Label lblInputSec, lblResultSec, lblChartSec;
        Label lblColMag, lblColAng;
        Button btnRun;

        // ── 构造 ────────────────────────────────────────────────────────────────
        public main()
        {
            InitializeComponent();
            BuildUI();

            this.Shown += (s, e) => RunCalc();
            this.SizeChanged += (s, e) => RedrawChart();

            EventHandler liveUpdate = (s, e) => RedrawChart();
            foreach (var n in AllInputs())
            {
                n.ValueChanged += liveUpdate;
                n.KeyUp += (s, e) => liveUpdate(s, EventArgs.Empty);
            }
        }

        IEnumerable<NumericUpDown> AllInputs() =>
            new[] { vaMag, vaAng, vbMag, vbAng, vcMag, vcAng,
                    iaMag, iaAng, ibMag, ibAng, icMag, icAng };

        void RedrawChart()
        {
            if (polarChart.Width > 0 && polarChart.Height > 0)
                DrawPhasor(polarChart, GetVectors());
        }

        // ════════════════════════════════════════════════════════════════════════
        //  UI 构建
        // ════════════════════════════════════════════════════════════════════════
        void BuildUI()
        {
            Text = "三相交流分析 · Powered by Anthropic Claude Code - Opus 4.6";
            Size = new Size(1100, 700);
            MinimumSize = new Size(960, 620);
            BackColor = BgBase;
            ForeColor = TextPrimary;
            Font = new Font("Segoe UI", 9.5f);

            // ── 顶部标题栏 ──────────────────────────────────────────────────────
            var titleBar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 48,
                BackColor = BgSurface,
                Padding = new Padding(0)
            };
            DrawBottomBorder(titleBar, BorderSubtle);

            var dot1 = MakeDot(Color.FromArgb(255, 95, 86));
            var dot2 = MakeDot(Color.FromArgb(255, 189, 46));
            var dot3 = MakeDot(Color.FromArgb(40, 200, 64));
            dot1.Location = new Point(16, 16);
            dot2.Location = new Point(34, 16);
            dot3.Location = new Point(52, 16);

            lblMainTitle = new Label
            {
                Text = "三相交流分析器",
                Font = new Font("Segoe UI", 11, FontStyle.Regular),
                ForeColor = TextPrimary,
                AutoSize = true,
                Location = new Point(80, 13)
            };
            lblSubTitle = new Label
            {
                Text = "IEC-60909  ·  Symmetrical Components  ·  v1.1",
                Font = new Font("Consolas", 8.5f),
                ForeColor = TextMuted,
                AutoSize = true,
                Location = new Point(240, 16)
            };

            var btnLangToggle = new Button
            {
                Text = "EN / 中",
                Size = new Size(60, 22),
                Location = new Point(600, 13),
                BackColor = BgElevated,
                ForeColor = TextPrimary,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Consolas", 8),
                Cursor = Cursors.Hand
            };
            btnLangToggle.FlatAppearance.BorderSize = 1;
            btnLangToggle.FlatAppearance.BorderColor = BorderMid;
            btnLangToggle.Click += (s, e) =>
            {
                isEnglish = !isEnglish;
                UpdateUITexts();
                RunCalc();
                RedrawChart();
            };

            titleBar.Controls.AddRange(new Control[] { dot1, dot2, dot3, lblMainTitle, lblSubTitle, btnLangToggle });
            this.Controls.Add(titleBar);

            // ── 主体布局 ────────────────────────────────────────────────────────
            var root = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 2,
                RowCount = 1,
                Padding = new Padding(14, 10, 14, 12),
                BackColor = BgBase
            };
            root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 46));
            root.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 54));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            this.Controls.Add(root);

            // ── 左列 ────────────────────────────────────────────────────────────
            var leftCol = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 2,
                Padding = new Padding(0, 0, 8, 0)
            };
            leftCol.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            leftCol.RowStyles.Add(new RowStyle(SizeType.Absolute, 240));
            leftCol.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            root.Controls.Add(leftCol, 0, 0);

            leftCol.Controls.Add(BuildInputCard(), 0, 0);
            leftCol.Controls.Add(BuildResultCard(), 0, 1);

            // ── 右列 ────────────────────────────────────────────────────────────
            var rightCol = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 3,
                Padding = new Padding(8, 0, 0, 0)
            };
            rightCol.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            rightCol.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            rightCol.RowStyles.Add(new RowStyle(SizeType.Absolute, 70));
            rightCol.RowStyles.Add(new RowStyle(SizeType.Absolute, 46));
            root.Controls.Add(rightCol, 1, 0);

            rightCol.Controls.Add(BuildChartCard(), 0, 0);
            rightCol.Controls.Add(BuildLineVoltagePanel(), 0, 1);
            rightCol.Controls.Add(BuildBottomBar(), 0, 2);
        }

        // ── 宏观窗口小部件 ──────────────────────────────────────────────────────
        Label MakeDot(Color c)
        {
            return new Label
            {
                Size = new Size(12, 12),
                BackColor = c,
                FlatStyle = FlatStyle.Flat,
            };
        }
        // (圆角需在 Paint 中实现，这里用简单矩形占位)

        // ── 输入卡片 ─────────────────────────────────────────────────────────────
        Control BuildInputCard()
        {
            var card = MakeCard();
            var inner = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 2,
                Padding = new Padding(4)
            };
            inner.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            inner.RowStyles.Add(new RowStyle(SizeType.Absolute, 26));
            inner.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            card.Controls.Add(inner);

            inner.Controls.Add(lblInputSec = MakeSectionLabel("INPUT PARAMETERS", AccentPurple), 0, 0);

            var tbl = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 6,
                RowCount = 7,
            };
            tbl.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 32));  // dot
            tbl.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 34));  // label
            tbl.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 40));   // mag
            tbl.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 18));  // ∠
            tbl.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 40));   // ang
            tbl.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 14));  // °

            // 表头行
            tbl.Controls.Add(MakeColHeader(""), 0, 0);
            tbl.Controls.Add(MakeColHeader(""), 1, 0);
            tbl.Controls.Add(lblColMag = MakeColHeader("Magnitude"), 2, 0);
            tbl.Controls.Add(MakeColHeader(""), 3, 0);
            tbl.Controls.Add(lblColAng = MakeColHeader("Angle (°)"), 4, 0);
            tbl.Controls.Add(MakeColHeader(""), 5, 0);

            AddParamRow(tbl, 1, "Ua", ColUa,  100,    0,  out vaMag, out vaAng, "V");
            AddParamRow(tbl, 2, "Ub", ColUb,  100, -120,  out vbMag, out vbAng, "V");
            AddParamRow(tbl, 3, "Uc", ColUc,  100,  120,  out vcMag, out vcAng, "V");
            AddParamRow(tbl, 4, "Ia", ColUa,   10,    0,  out iaMag, out iaAng, "A");
            AddParamRow(tbl, 5, "Ib", ColUb,   10, -120,  out ibMag, out ibAng, "A");
            AddParamRow(tbl, 6, "Ic", ColUc,   10,  120,  out icMag, out icAng, "A");

            inner.Controls.Add(tbl, 0, 1);
            return card;
        }

        void AddParamRow(TableLayoutPanel tbl, int row, string name, Color dotColor,
            decimal defMag, decimal defAng, out NumericUpDown mag, out NumericUpDown ang, string unit)
        {
            // 颜色点
            var dot = new Panel
            {
                Width = 8, Height = 8,
                BackColor = dotColor,
                Margin = new Padding(12, 0, 0, 0),
                Anchor = AnchorStyles.None
            };
            // 标签
            var lbl = new Label
            {
                Text = name,
                Font = new Font("Consolas", 9),
                ForeColor = TextSecond,
                Dock = DockStyle.Fill,
                TextAlign = ContentAlignment.MiddleLeft
            };
            mag = MakeNumericInput(0, 10000, defMag, 2);
            var angleIcon = new Label
            {
                Text = "∠",
                ForeColor = TextMuted,
                Dock = DockStyle.Fill,
                TextAlign = ContentAlignment.MiddleCenter,
                Font = new Font("Segoe UI", 10)
            };
            ang = MakeNumericInput(-360, 360, defAng, 2);
            ang.BackColor = Color.FromArgb(25, 24, 33);
            var degLbl = new Label
            {
                Text = "°",
                ForeColor = TextMuted,
                Dock = DockStyle.Fill,
                TextAlign = ContentAlignment.MiddleLeft
            };

            tbl.Controls.Add(dot, 0, row);
            tbl.Controls.Add(lbl, 1, row);
            tbl.Controls.Add(mag, 2, row);
            tbl.Controls.Add(angleIcon, 3, row);
            tbl.Controls.Add(ang, 4, row);
            tbl.Controls.Add(degLbl, 5, row);
        }

        void UpdateUITexts()
        {
            if (isEnglish)
            {
                lblMainTitle.Text = "3-Phase AC Analyzer";
                lblInputSec.Text = "INPUT PARAMETERS";
                lblColMag.Text = "Magnitude";
                lblColAng.Text = "Angle (°)";
                lblResultSec.Text = "ANALYSIS RESULTS";
                if (lvResult.Columns.Count >= 4)
                {
                    lvResult.Columns[0].Text = "Parameter";
                    lvResult.Columns[1].Text = "Magnitude";
                    lvResult.Columns[2].Text = "Angle";
                    lvResult.Columns[3].Text = "Unit";
                }
                lblChartSec.Text = "PHASOR DIAGRAM";
                btnRun.Text = "▶  Run Analysis";
            }
            else
            {
                lblMainTitle.Text = "三相交流分析器";
                lblInputSec.Text = "输入参数";
                lblColMag.Text = "幅值";
                lblColAng.Text = "角度 (°)";
                lblResultSec.Text = "计算结果";
                if (lvResult.Columns.Count >= 4)
                {
                    lvResult.Columns[0].Text = "参数";
                    lvResult.Columns[1].Text = "幅值";
                    lvResult.Columns[2].Text = "角度";
                    lvResult.Columns[3].Text = "单位";
                }
                lblChartSec.Text = "相量图";
                btnRun.Text = "▶  开始计算";
            }
        }

        // ── 结果卡片 ─────────────────────────────────────────────────────────────
        Control BuildResultCard()
        {
            var card = MakeCard();
            card.Padding = new Padding(0);

            var inner = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 2,
                Padding = new Padding(12, 8, 12, 8)
            };
            inner.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            inner.RowStyles.Add(new RowStyle(SizeType.Absolute, 26));
            inner.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            card.Controls.Add(inner);

            inner.Controls.Add(lblResultSec = MakeSectionLabel("ANALYSIS RESULTS", AccentTeal), 0, 0);

            lvResult = new ListView
            {
                Dock = DockStyle.Fill,
                View = View.Details,
                FullRowSelect = true,
                GridLines = false,
                BackColor = BgSurface,
                ForeColor = TextPrimary,
                Font = new Font("Consolas", 9),
                HeaderStyle = ColumnHeaderStyle.Nonclickable,
                BorderStyle = BorderStyle.None,
                OwnerDraw = false
            };
            lvResult.Columns.Add("Parameter", 160);
            lvResult.Columns.Add("Magnitude", 95, HorizontalAlignment.Right);
            lvResult.Columns.Add("Angle", 90, HorizontalAlignment.Right);
            lvResult.Columns.Add("Unit", 45, HorizontalAlignment.Center);

            inner.Controls.Add(lvResult, 0, 1);
            return card;
        }

        // ── 相量图卡片 ───────────────────────────────────────────────────────────
        Control BuildChartCard()
        {
            var card = MakeCard();

            var inner = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 1,
                RowCount = 2,
                Padding = new Padding(4)
            };
            inner.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            inner.RowStyles.Add(new RowStyle(SizeType.Absolute, 26));
            inner.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            card.Controls.Add(inner);

            inner.Controls.Add(lblChartSec = MakeSectionLabel("PHASOR DIAGRAM", AccentAmber), 0, 0);

            polarChart = new PictureBox
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent,
                BorderStyle = BorderStyle.None
            };
            inner.Controls.Add(polarChart, 0, 1);
            return card;
        }

        // ── 线电压面板 ───────────────────────────────────────────────────────────
        Control BuildLineVoltagePanel()
        {
            var card = MakeCard();
            var flow = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                FlowDirection = FlowDirection.LeftToRight,
                Padding = new Padding(6, 4, 6, 4),
                WrapContents = false
            };
            card.Controls.Add(flow);

            lvLabelAB = MakeVoltageTag("Uab", ColUab);
            lvLabelBC = MakeVoltageTag("Ubc", ColUbc);
            lvLabelCA = MakeVoltageTag("Uca", ColUca);
            flow.Controls.AddRange(new Control[] { lvLabelAB, lvLabelBC, lvLabelCA });
            return card;
        }

        Label MakeVoltageTag(string name, Color accent)
        {
            return new Label
            {
                Text = $"{name}  —",
                AutoSize = true,
                ForeColor = accent,
                Font = new Font("Consolas", 10),
                Margin = new Padding(4, 8, 18, 4)
            };
        }

        // ── 底部操作栏 ───────────────────────────────────────────────────────────
        Control BuildBottomBar()
        {
            var pnl = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent,
                Padding = new Padding(2, 0, 2, 0)
            };
            btnRun = new Button
            {
                Text = "▶  Run Analysis",
                Size = new Size(148, 32),
                Location = new Point(0, 6),
                BackColor = AccentPurple,
                ForeColor = Color.FromArgb(15, 13, 22),
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnRun.FlatAppearance.BorderSize = 0;
            btnRun.FlatAppearance.MouseOverBackColor = Color.FromArgb(183, 160, 255);
            btnRun.FlatAppearance.MouseDownBackColor = Color.FromArgb(140, 110, 225);
            btnRun.Click += (s, e) => RunCalc();

            var lblStd = new Label
            {
                Text = "// IEC-60909 · Symmetrical Components Method",
                AutoSize = true,
                ForeColor = TextMuted,
                Font = new Font("Consolas", 8.5f, FontStyle.Italic),
                Location = new Point(160, 14)
            };
            pnl.Controls.AddRange(new Control[] { btnRun, lblStd });
            return pnl;
        }

        // ── 通用辅助 ─────────────────────────────────────────────────────────────
        Panel MakeCard()
        {
            return new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = BgSurface,
                BorderStyle = BorderStyle.None,
                Margin = new Padding(3),
                Padding = new Padding(12, 8, 12, 8)
            };
        }

        Label MakeSectionLabel(string text, Color accent)
        {
            return new Label
            {
                Text = text,
                AutoSize = false,
                Dock = DockStyle.Fill,
                Font = new Font("Consolas", 7.5f, FontStyle.Bold),
                ForeColor = accent,
                TextAlign = ContentAlignment.MiddleLeft
            };
        }

        Label MakeColHeader(string text)
        {
            return new Label
            {
                Text = text,
                Dock = DockStyle.Fill,
                Font = new Font("Segoe UI", 8),
                ForeColor = TextMuted,
                TextAlign = ContentAlignment.MiddleLeft
            };
        }

        NumericUpDown MakeNumericInput(decimal min, decimal max, decimal val, int decimals)
        {
            return new NumericUpDown
            {
                DecimalPlaces = decimals,
                Minimum = min,
                Maximum = max,
                Value = val,
                Dock = DockStyle.Fill,
                BackColor = BgElevated,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Consolas", 9.5f),
                Margin = new Padding(2)
            };
        }

        void DrawBottomBorder(Control ctrl, Color color)
        {
            ctrl.Paint += (s, e) =>
            {
                using (var p = new Pen(color, 1))
                {
                    e.Graphics.DrawLine(p, 0, ctrl.Height - 1, ctrl.Width, ctrl.Height - 1);
                }
            };
        }

        // ════════════════════════════════════════════════════════════════════════
        //  计算逻辑
        // ════════════════════════════════════════════════════════════════════════
        static readonly Complex A  = new Complex(-0.5,  Math.Sqrt(3) / 2);
        static readonly Complex A2 = new Complex(-0.5, -Math.Sqrt(3) / 2);

        void RunCalc()
        {
            try
            {
                var (va, bva) = ParseBase(vaMag.Text, vaAng.Text);
                Complex vb = ParseRel(vbMag.Text, vbAng.Text, bva);
                Complex vc = ParseRel(vcMag.Text, vcAng.Text, bva);
                Complex ia = ParseRel(iaMag.Text, iaAng.Text, bva);
                Complex ib = ParseRel(ibMag.Text, ibAng.Text, bva);
                Complex ic = ParseRel(icMag.Text, icAng.Text, bva);

                var (v1, v2, v0) = CalcSeq(va, vb, vc);
                var (i1, i2, i0) = CalcSeq(ia, ib, ic);

                lvResult.Items.Clear();

                // ── 序分量 ──
                AddGroup(isEnglish ? "VOLTAGE SEQUENCES" : "电压序分量");
                AddSeq(isEnglish ? "  Pos seq  3U₁" : "  正序 3U₁", v1,      bva, "V");
                AddSeq(isEnglish ? "  Pos seq  U₁"  : "  正序 U₁",  v1 / 3,  bva, "V");
                AddSeq(isEnglish ? "  Neg seq  3U₂" : "  负序 3U₂", v2,      bva, "V");
                AddSeq(isEnglish ? "  Neg seq  U₂"  : "  负序 U₂",  v2 / 3,  bva, "V");
                AddSeq(isEnglish ? "  Zero seq 3U₀" : "  零序 3U₀", v0,      bva, "V");
                AddSeq(isEnglish ? "  Zero seq U₀"  : "  零序 U₀",  v0 / 3,  bva, "V");

                AddGroup(isEnglish ? "CURRENT SEQUENCES" : "电流序分量");
                AddSeq(isEnglish ? "  Pos seq  3I₁" : "  正序 3I₁", i1,      bva, "A");
                AddSeq(isEnglish ? "  Pos seq  I₁"  : "  正序 I₁",  i1 / 3,  bva, "A");
                AddSeq(isEnglish ? "  Neg seq  3I₂" : "  负序 3I₂", i2,      bva, "A");
                AddSeq(isEnglish ? "  Neg seq  I₂"  : "  负序 I₂",  i2 / 3,  bva, "A");
                AddSeq(isEnglish ? "  Zero seq 3I₀" : "  零序 3I₀", i0,      bva, "A");
                AddSeq(isEnglish ? "  Zero seq I₀"  : "  零序 I₀",  i0 / 3,  bva, "A");

                // ── 不平衡度 ──
                double vuf  = v1.Magnitude > 0 ? v2.Magnitude / v1.Magnitude * 100 : 0;
                double v0uf = v1.Magnitude > 0 ? v0.Magnitude / v1.Magnitude * 100 : 0;
                double iuf  = i1.Magnitude > 0 ? i2.Magnitude / i1.Magnitude * 100 : 0;
                double i0uf = i1.Magnitude > 0 ? i0.Magnitude / i1.Magnitude * 100 : 0;

                AddGroup(isEnglish ? "UNBALANCE" : "不平衡度");
                Add(isEnglish ? "  Voltage unbalance" : "  电压不平衡度",  vuf.ToString("F2"),  "—", "%");
                Add(isEnglish ? "  Voltage zero ratio" : "  零序电压含有率", v0uf.ToString("F2"), "—", "%");
                Add(isEnglish ? "  Current unbalance" : "  电流不平衡度", iuf.ToString("F2"),  "—", "%");
                Add(isEnglish ? "  Current zero ratio" : "  零序电流含有率", i0uf.ToString("F2"), "—", "%");

                // ── 功率 ──
                Complex sa = va * Complex.Conjugate(ia);
                Complex sb = vb * Complex.Conjugate(ib);
                Complex sc = vc * Complex.Conjugate(ic);
                Complex sT = sa + sb + sc;
                double pfT = sT.Magnitude > 0 ? sT.Real / sT.Magnitude : 0;
                double pfA = sa.Magnitude > 0 ? sa.Real / sa.Magnitude : 0;
                double pfB = sb.Magnitude > 0 ? sb.Real / sb.Magnitude : 0;
                double pfC = sc.Magnitude > 0 ? sc.Real / sc.Magnitude : 0;

                AddGroup(isEnglish ? "POWER  (3-PHASE TOTAL)" : "三相总功率");
                Add(isEnglish ? "  Active    P" : "  有功功率 P",    sT.Real.ToString("F2"),      "—", "W");
                Add(isEnglish ? "  Reactive  Q" : "  无功功率 Q",    sT.Imaginary.ToString("F2"), "—", "var");
                Add(isEnglish ? "  Apparent  S" : "  视在功率 S",    sT.Magnitude.ToString("F2"), "—", "VA");
                Add(isEnglish ? "  Power factor PF" : "  总功率因数 PF",pfT.ToString("F4"),          "—", "");

                AddGroup(isEnglish ? "POWER  (PER PHASE)" : "分相功率");
                Add("  Pa / PFa", sa.Real.ToString("F2"), pfA.ToString("F4"), "W");
                Add("  Pb / PFb", sb.Real.ToString("F2"), pfB.ToString("F4"), "W");
                Add("  Pc / PFc", sc.Real.ToString("F2"), pfC.ToString("F4"), "W");
                Add("  Qa", sa.Imaginary.ToString("F2"), "—", "var");
                Add("  Qb", sb.Imaginary.ToString("F2"), "—", "var");
                Add("  Qc", sc.Imaginary.ToString("F2"), "—", "var");
                Add("  Sa", sa.Magnitude.ToString("F2"),  "—", "VA");
                Add("  Sb", sb.Magnitude.ToString("F2"),  "—", "VA");
                Add("  Sc", sc.Magnitude.ToString("F2"),  "—", "VA");

                // ── 线电压标签 ──
                var uab = va - vb;
                var ubc = vb - vc;
                var uca = vc - va;
                UpdateVTag(lvLabelAB, "Uab", uab, bva);
                UpdateVTag(lvLabelBC, "Ubc", ubc, bva);
                UpdateVTag(lvLabelCA, "Uca", uca, bva);

                // ── 绘图 ──
                DrawPhasor(polarChart, BuildVectors(va, vb, vc, ia, ib, ic,
                    uab, ubc, uca, v1, v2, v0, i1, i2, i0));
            }
            catch
            {
                MessageBox.Show("输入格式错误，请检查各参数值。", "Input Error",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
        }

        Dictionary<string, Complex> GetVectors()
        {
            try
            {
                var (va, bva) = ParseBase(vaMag.Text, vaAng.Text);
                Complex vb = ParseRel(vbMag.Text, vbAng.Text, bva);
                Complex vc = ParseRel(vcMag.Text, vcAng.Text, bva);
                Complex ia = ParseRel(iaMag.Text, iaAng.Text, bva);
                Complex ib = ParseRel(ibMag.Text, ibAng.Text, bva);
                Complex ic = ParseRel(icMag.Text, icAng.Text, bva);
                var (v1, v2, v0) = CalcSeq(va, vb, vc);
                var (i1, i2, i0) = CalcSeq(ia, ib, ic);
                return BuildVectors(va, vb, vc, ia, ib, ic,
                    va-vb, vb-vc, vc-va, v1, v2, v0, i1, i2, i0);
            }
            catch { return new Dictionary<string, Complex>(); }
        }

        Dictionary<string, Complex> BuildVectors(
            Complex va, Complex vb, Complex vc,
            Complex ia, Complex ib, Complex ic,
            Complex uab, Complex ubc, Complex uca,
            Complex v1, Complex v2, Complex v0,
            Complex i1, Complex i2, Complex i0)
        {
            return new Dictionary<string, Complex>
            {
                { "Ua", va }, { "Ub", vb }, { "Uc", vc },
                { "Uab", uab }, { "Ubc", ubc }, { "Uca", uca },
                { "Ia", ia }, { "Ib", ib }, { "Ic", ic },
                { "U1", v1/3 }, { "U2", v2/3 }, { "U0", v0/3 },
                { "I1", i1/3 }, { "I2", i2/3 }, { "I0", i0/3 }
            };
        }

        // ── 解析 & 序分量 ──────────────────────────────────────────────────────
        (Complex value, double baseAngle) ParseBase(string mag, string ang)
        {
            double m = double.Parse(mag), a = double.Parse(ang);
            return (Complex.FromPolarCoordinates(m, 0), a);
        }

        Complex ParseRel(string mag, string ang, double baseAng)
        {
            double m = double.Parse(mag);
            double rad = (double.Parse(ang) - baseAng) * Math.PI / 180;
            return Complex.FromPolarCoordinates(m, rad);
        }

        (Complex pos, Complex neg, Complex zero) CalcSeq(Complex va, Complex vb, Complex vc)
        {
            Complex z  = (va + vb + vc) / 3;
            Complex p  = (va + A * vb + A2 * vc) / 3;
            Complex n  = (va + A2 * vb + A * vc) / 3;
            return (p * 3, n * 3, z * 3);
        }

        // ── 结果列表辅助 ──────────────────────────────────────────────────────
        void AddGroup(string title)
        {
            var item = new ListViewItem(title);
            item.ForeColor = TextMuted;
            item.Font = new Font("Consolas", 8, FontStyle.Bold);
            item.SubItems.Add(""); item.SubItems.Add(""); item.SubItems.Add("");
            lvResult.Items.Add(item);
        }

        void AddSeq(string name, Complex seq, double baseAng, string unit)
        {
            double mag = seq.Magnitude;
            double ang = Norm(seq.Phase * 180 / Math.PI + baseAng);
            Add(name, mag.ToString("F3"), ang.ToString("F2") + "°", unit);
        }

        void Add(string name, string v1, string v2, string unit)
        {
            var item = new ListViewItem(name);
            item.SubItems.Add(v1); item.SubItems.Add(v2); item.SubItems.Add(unit);
            lvResult.Items.Add(item);
        }

        void UpdateVTag(Label lbl, string name, Complex val, double baseAng)
        {
            double mag = val.Magnitude;
            double ang = Norm(val.Phase * 180 / Math.PI + baseAng);
            lbl.Text = $"{name}  {mag:F2} V ∠ {ang:F2}°";
        }

        double Norm(double d)
        {
            d %= 360;
            return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
        }

        // ════════════════════════════════════════════════════════════════════════
        //  相量图绘制
        // ════════════════════════════════════════════════════════════════════════
        void DrawPhasor(PictureBox pb, Dictionary<string, Complex> vecs)
        {
            if (vecs.Count == 0) return;
            int w = pb.Width, h = pb.Height;
            if (w < 10 || h < 10) return;

            var bmp = new Bitmap(w, h);
            using (var g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

                // 背景
                g.Clear(BgSurface);

                int cx = w / 2, cy = h / 2;
                double radius = Math.Min(w, h) / 2.0 * 0.82;

                // 同心参考圆
                using (var circlePen = new Pen(Color.FromArgb(30, 255, 255, 255), 0.5f))
                {
                    circlePen.DashStyle = DashStyle.Dash;
                    for (int i = 1; i <= 4; i++)
                    {
                        double r = radius / 4 * i;
                        g.DrawEllipse(circlePen, (float)(cx-r), (float)(cy-r), (float)(r*2), (float)(r*2));
                    }
                }

                // 轴线
                using (var axisPen = new Pen(Color.FromArgb(45, 255, 255, 255), 0.5f))
                {
                    g.DrawLine(axisPen, cx, 0, cx, h);
                    g.DrawLine(axisPen, 0, cy, w, cy);
                }

                // 角度刻度（30° 间隔）
                using (var tickPen = new Pen(Color.FromArgb(22, 255, 255, 255), 0.5f))
                {
                    for (int deg = 0; deg < 360; deg += 30)
                    {
                        double rad = deg * Math.PI / 180;
                        g.DrawLine(tickPen, cx, cy,
                            (float)(cx + radius * Math.Cos(rad)),
                            (float)(cy - radius * Math.Sin(rad)));
                    }
                }

                // 标尺标签（0°, 90°, 180°, 270°）
                Font axisFont = new Font("Consolas", 7.5f);
                Brush axisClr = new SolidBrush(TextMuted);
                void DrawAxisLabel(string text, double r, double deg)
                {
                    double rad = deg * Math.PI / 180;
                    float x = (float)(cx + r * Math.Cos(rad)) - 8;
                    float y = (float)(cy - r * Math.Sin(rad)) - 7;
                    g.DrawString(text, axisFont, axisClr, x, y);
                }
                double outerR = radius + 10;
                DrawAxisLabel("0°",   outerR,   0);
                DrawAxisLabel("90°",  outerR,  90);
                DrawAxisLabel("180°", outerR, 180);
                DrawAxisLabel("270°", outerR, 270);

                // 缩放
                double maxMag = vecs.Max(v => v.Key.StartsWith("I") ? v.Value.Magnitude * 10 : v.Value.Magnitude);
                double scale  = maxMag == 0 ? 1 : radius / maxMag;

                // 相量颜色映射
            Color GetColor(string name)
            {
                if (name == "Ua") return ColUa;
                if (name == "Ub") return ColUb;
                if (name == "Uc") return ColUc;
                if (name == "Ia") return ColIa;
                if (name == "Ib") return ColIb;
                if (name == "Ic") return ColIc;
                if (name == "Uab") return ColUab;
                if (name == "Ubc") return ColUbc;
                if (name == "Uca") return ColUca;
                if (name.EndsWith("1")) return ColPos;
                if (name.EndsWith("2")) return ColNeg;
                if (name.EndsWith("0")) return ColZer;
                return TextPrimary;
            }

                float lineW(string n) => n.StartsWith("U") && n.Length == 2 ? 2.0f :
                                         n.StartsWith("U") && n.Length == 3 ? 1.5f : 1.0f;

                // 绘制相量
                var labelPts = new List<(PointF pos, string text, Color col)>();
                foreach (var kv in vecs)
                {
                    string name = kv.Key;
                    Complex ph  = kv.Value;
                    double mag  = ph.Magnitude;
                    double ang  = ph.Phase;
                    if (name.StartsWith("I")) mag *= 10;

                    float ex = (float)(cx + scale * mag * Math.Cos(ang));
                    float ey = (float)(cy - scale * mag * Math.Sin(ang));

                    Color col = GetColor(name);
                    using (var pen = new Pen(col, lineW(name)))
                    {
                        pen.CustomEndCap = new AdjustableArrowCap(3.5f, 3.5f, true);
                        g.DrawLine(pen, cx, cy, ex, ey);
                    }

                    // 端点高亮圆
                    g.FillEllipse(new SolidBrush(Color.FromArgb(180, col)), ex - 2.5f, ey - 2.5f, 5, 5);

                    labelPts.Add((new PointF(ex + 5, ey - 8), name, col));
                }

                // 标签（简单避让）
                var placedLabels = new List<RectangleF>();
                Font labelFont = new Font("Consolas", 8.5f, FontStyle.Bold);
                foreach (var (pt, text, col) in labelPts)
                {
                    var sz = g.MeasureString(text, labelFont);
                    var rect = new RectangleF(pt.X, pt.Y, sz.Width, sz.Height);
                    // 微调避免重叠
                    int attempts = 0;
                    while (placedLabels.Any(r => r.IntersectsWith(rect)) && attempts < 6)
                    {
                        rect.Offset(0, -10);
                        attempts++;
                    }
                    placedLabels.Add(rect);

                    // 文字阴影
                    g.DrawString(text, labelFont, new SolidBrush(Color.FromArgb(80, 0, 0, 0)),
                        rect.X + 1, rect.Y + 1);
                    g.DrawString(text, labelFont, new SolidBrush(col), rect.X, rect.Y);
                }

                // 图例（右下角）
                DrawLegend(g, w, h);
            }

            pb.Image = bmp;
        }

        void DrawLegend(Graphics g, int w, int h)
        {
            var entries = new (Color col, string text)[]
            {
                (ColUa,  isEnglish ? "Phase Ua (V)" : "A相电压 (V)"),
                (ColUb,  isEnglish ? "Phase Ub (V)" : "B相电压 (V)"),
                (ColUc,  isEnglish ? "Phase Uc (V)" : "C相电压 (V)"),
                (ColIa,  isEnglish ? "Phase Ia (A)" : "A相电流 (A)"),
                (ColIb,  isEnglish ? "Phase Ib (A)" : "B相电流 (A)"),
                (ColIc,  isEnglish ? "Phase Ic (A)" : "C相电流 (A)"),
                (ColUab, isEnglish ? "Line Uab" : "线电压 Uab"),
                (ColUbc, isEnglish ? "Line Ubc" : "线电压 Ubc"),
                (ColUca, isEnglish ? "Line Uca" : "线电压 Uca"),
                (ColPos, isEnglish ? "Positive seq" : "正序"),
                (ColNeg, isEnglish ? "Negative seq" : "负序"),
                (ColZer, isEnglish ? "Zero seq"     : "零序"),
            };

            int lx = w - 130, ly = h - entries.Length * 14 - 10;
            Font lf = new Font("Segoe UI", 7.5f);

            using (var bgBrush = new SolidBrush(Color.FromArgb(140, BgBase)))
            {
                g.FillRectangle(bgBrush, lx - 4, ly - 4, 128, entries.Length * 14 + 8);
            }

            for (int i = 0; i < entries.Length; i++)
            {
                float ry = ly + i * 14;
                using (var linePen = new Pen(entries[i].col, 2.0f))
                {
                    g.DrawLine(linePen, lx, ry + 5, lx + 12, ry + 5);
                }
                using (var textBrush = new SolidBrush(Color.FromArgb(220, TextPrimary)))
                {
                    g.DrawString(entries[i].text, lf, textBrush, lx + 16, ry);
                }
            }
        }
    }
}