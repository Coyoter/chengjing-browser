# 天眼開發者工具設計

Design Read:
artifact: Android 原生天眼介面
 audience: 想自行調整網站的日常瀏覽者
purpose: 區分網站級需求與單一元件修改，清楚展示新增、編輯、移除及 AI 檢查
visual-language: 延伸澄境暖白／墨綠的平面工具介面
mode: extension
visual-variance: 3
motion-intensity: 2
information-density: 5
asset-dependence: 1
brand-fidelity: 10

設計系統沿用 Light／Dark 色票：背景 background、文字 onSurface、分組 surface、主色 primary；危險狀態使用 error，不為移除功能增加強烈視覺權重。標題 20sp，操作 15sp，說明 12–14sp，程式碼 12sp 等寬；4dp 基準、8／12／16／20dp 間距。分組 18dp 圓角，列高 64dp，觸控至少 48dp。沿用 Material 圖示，無新增圖片素材或裝飾動態。

網站 AI 入口不依賴選取；選取面板將網站入口與元件操作分組。元件編輯器標明 selector 與影響數量；CSS 是作用於所選元件的屬性，JS 以 element 取得所選元件，HTML 可新增至元件內或編輯內部 HTML。網站編輯器保留全站 CSS／JS 並加入 HTML。保存到網域後重新載入，上一份設定可完整復原；不承諾能撤銷已發出的網路請求。
