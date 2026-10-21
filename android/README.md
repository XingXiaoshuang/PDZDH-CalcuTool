# 三相交流分析 · Android

桌面版（WinForm / `main.cs`）的安卓移植，业务算法、界面语言与配色保持一致。
默认中文界面，可切换英文（记忆上次选择）。

| 输入与相量图 | 计算结果 | 关于 |
|---|---|---|
| ![输入](picture/screen-cn-input.png) | ![结果](picture/screen-cn-results.png) | ![关于](picture/screen-cn-about.png) |

![英文](picture/screen-en-input.png)

## 功能对照

| 桌面版 | Android 版 | 实现位置 |
|---|---|---|
| 三相电压/电流输入（幅值 + 相对角） | 同 | `MainActivity.paramRow` |
| 序分量 3U₁/U₁ · 3U₂/U₂ · 3U₀/U₀ | 同 | `PhasorCalc.sequence` |
| 不平衡度 / 零序含有率 | 同 | `PhasorCalc.compute` |
| 三相总功率 P/Q/S/PF、分相功率 | 同 | `PhasorCalc.compute` |
| 线电压 Uab/Ubc/Uca | 同（标签 + 相量） | `MainActivity.updateChips` |
| 相量图（同心圆、30° 刻度、电流 ×10 缩放、标签避让、图例） | 同 | `PhasorView` |
| EN / 中 界面切换 | 同，默认中文 | `Lang` |
| 输入变化即时刷新相量图 | 同 | `MainActivity.updateChartOnly` |

## 构建

不依赖 Gradle / AndroidX / Kotlin，纯 Framework + Java。

```bash
export JAVA_HOME=<JDK 17+>
export ANDROID_HOME=<Android SDK，需 build-tools 34.0.0 与 platform android-34>
./build.sh
# -> android/PDZDH-CalcuTool-1.1.apk
```

流水线：`aapt2 compile → aapt2 link → javac → d8 → zipalign → apksigner`。

签名：`keystore/pdzdh-release.jks`（口令 `android`，别名 `androiddebugkey`）随仓库提交，
保证升级安装签名一致；仓库内缺失时 `build.sh` 会自动生成。

编译参数注意：`javac --release 8 -parameters`。`-parameters` 是必需的，
d8 8.2.x 解析到无参数名的 `MethodParameters` 属性会抛 NPE。

## 校验

- 计算核心与桌面算法逐项对齐：3 组随机工况（幅值/相位/功率，误差 < 1e-4）交叉验证，见 `PhasorCalc`
- APK 已在 Pixel 6 / Android 14 模拟器上跑通：冷启动、输入联动、开始计算、中英切换、长列表滚动

## 图标

`tools/gen_icon.py` 生成自适应图标（108dp 矢量前景 + 5 档密度 PNG + 圆形变体）：

- 设计：深紫径向渐变底 + 三相相量（A 黄 / B 青 / C 珊瑚红，互差 120°）+ 参考圆环 + 中性点
- 与 App 内相量图同源，改设计后重跑脚本即可覆盖全部密度
- 预览：`icon/icon-512.png`

## 作者

- 落霞孤鴻
- 微信：18372698780（App 内「关于」卡片点击即复制）
