# 澄境瀏覽器：Android 設計

Design Read:
artifact: Android 原生瀏覽器
audience: 想排除干擾廣告的一般成年使用者
purpose: 正常瀏覽；看見結構、預覽移除、按網域儲存與復原
visual-language: 澄境暖紙與墨綠；原生工具列與清楚層級
mode: extension
visual-variance: 3
motion-intensity: 2
information-density: 5
asset-dependence: 1
brand-fidelity: 9

palette: 沿用澄境 #f1eee7、#fbfaf6、#1d2925、#147a64；深色 #0f1513、#1a2420、#f2efe7、#69dfc0。
typography: Android 系統字體；展示 36、頁標 24、區標 18、正文 15–16、輔助 12–13 sp。正文行距至少 1.45。
spacing: 4 dp 基準；8、12、16、24、32 dp。
shape: 工具觸控面至少 48 dp；網址列視覺高度 44 dp、22 dp 圓角；面板 24 dp 圓角。
depth: 背景色與留白建立層級；只在面板使用原生浮層。
motion: 原生面板與進度條；尊重系統動畫縮放。
asset plan: Material 向量圖示；澄境家族的墨綠底／翡翠環線／暖白指南針品牌符號，不借用 Chrome 標誌。

天眼採「瀏覽 → 看見邊界 → 點選 → 調整父層 → 預覽移除 → 儲存網域規則」。例外是本次開啟期間的網域暫停，不刪掉既存規則。自訂 CSS / JavaScript 由使用者明確儲存；AI 只提出有界的結構規則。

0.1.2：底部天眼直接進入選取，設定移至選單。選取的點擊由原生瀏覽器接管，頁面與 iframe 不收到觸控事件；捲動保留。分頁數字以實際筆畫邊界置中，網址列的上下留白對稱。

0.1.3：根據實體手機截圖修正單一「1」的視覺重心；原本外框幾何置中的證據不足以證明這個字形看起來置中。匯入 Chrome 書籤入口直接出現在主選單。
