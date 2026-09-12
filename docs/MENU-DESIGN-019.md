# 0.1.9 選單與設定佈局

Design Read:
artifact: Android 瀏覽器選單與設定面板
audience: 澄境瀏覽器的日常使用者
purpose: 快速找到操作、清楚比較設定，避免文字長短造成不一致的尺寸
visual-language: 澄境筆記的分組設定、柔和表面層級、緊湊導覽；墨綠與暖紙色
mode: overhaul（只限選單與設定呈現）
visual-variance: 3
motion-intensity: 2
information-density: 6
asset-dependence: 1
brand-fidelity: 9

參考澄境筆記實際來源：SettingsView.tsx 的 settings-section、engine-choice-grid、engine-runtime-card；SettingsJumpNav.tsx 的分類導覽；styles.css 的 borderless 主題。移植分類、層級與一致間距，不照搬桌面多欄。

palette: 沿用既有 Light / Dark 色票；面板使用 background，分組使用 surface，選中項目使用 primaryContainer；不增加漸層、發光或卡片陰影。
typography: 固定面板標題 20 sp；分類 13 sp；操作 15 sp；輔助 12 sp；標題與輔助文字各限一行，過長顯示省略。
spacing: 4 dp 基準；頁邊 20、分組間 20、分組內 16；主選單操作列固定 64 dp，快捷入口固定 76 dp。
shape: 同一面板寬度、最大 560 dp，手機兩侧 12 dp；主選單與設定固定為可用高度的 92%，保留系統頂端空間；26 dp 圓角，分組 18 dp，所有面板皆無拖曳橫槓。
depth: 用兩層表面顏色表達分組，保留原生遮罩；不添加裝飾陰影。
motion: 原生對話面板進出；內容切換不做額外動畫。
asset plan: 既有 Material 向量與澄境品牌圖；外觀位置選項用程式繪製小型佈局示意。

主選單：固定標題／關閉；四個等寬快捷入口；目前頁面與天眼分組；底部設定及同步入口。頁面名稱只出現在單行輔助資訊，不撐高操作列。
設定：外觀／瀏覽／AI 三類等寬切換。外觀以主題與網址列位置選項呈現；瀏覽收納網站識別、同步與練習場；AI 分成金鑰與模型。
共用面板：統一寬度、邊距、標題與返回／關閉；每個面板由頂端展開，不把上一個面板的捲動高度帶到下一個。
