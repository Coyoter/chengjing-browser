# 澄境瀏覽器 · Android

以 Android Chromium WebView 瀏覽網站，透過「天眼」調整元件、加入 CSS 樣式或 JavaScript 頁面內容，再保存同網域設定。介面提供淺色、深色、跟隨系統與上下網址列配置。

目前原始碼版本為 **1.4.3（versionCode 24）**：正式版啟用 R8 混淆、程式碼與資源縮減；保留 AI 設定即時更新、收藏與閱讀進度同步、澄境風格啟動畫面、圖片長按下載，以及三點選單內的 Android 原生分享；第一層工具列已還原 1.2.0 設計。**程式碼合併不代表安裝檔已發布**；正式 APK/AAB 是否可下載，以 GitHub Releases 與對應 Android release 工作流程的成功結果為準。舊版附件不會被覆蓋。[1.4.3 更新說明](docs/releases/1.4.3.md) · [簽章與發布流程](docs/RELEASING.md) · [R8 驗證](docs/R8-RELEASE.md)

## 安裝與使用

需要 Android 9 或以上。正式 APK 必須沿用既有簽章才可覆蓋相同簽章的安裝；Google Play 的安裝簽章由 Play App Signing 設定決定。AAB 是 Play Console 上傳套件，不是直接安裝檔。CI 產生的 `.qa` APK 不是正式版的覆蓋安裝檔。

- 打開網站後點「天眼」，直接進入元件選取。點選元件後可新增 CSS／JS／HTML、編輯內部 HTML、移除元件或請 AI 檢查。網站 AI 入口不依賴選取。
- 點選元件後，可向外選一層、預覽移除並儲存。選取期間網站不會收到觸控，也不能因此跳轉或開新視窗。
- 元件規則、自訂 CSS／JS 按可註冊網域與子網域套用。可從選單暫時顯示原始網站，再恢復設定。
- 網址列的連線圖示可查看 HTTPS 狀態及設定憑證例外。**預設驗證；只有手動允許的網站才略過可恢復的憑證錯誤，例外持續保存，直到使用者關閉。** 憑證更換及程式重開不會自動取消例外。開啟期間網址列持續顯示警示。此設定不加入 Google 同步，也不改變 Google／OpenRouter 原生 API 的驗證。
- 星號用於快速收藏與更新閱讀進度；書籤則提供資料夾、搜尋、網址編輯，以及 Chrome 匯出的 HTML 檔案匯入／匯出。
- Google 同步僅要求 `drive.appdata`，用於隱藏的書籤、收藏、閱讀進度及選用的天眼網站設定。收藏的自訂名稱、目前網址、頁面標題、捲動位置、百分比與刪除狀態會一起同步。天眼同步仍預設關閉；開啟後同步元件規則、自訂 CSS／JS／HTML 與網站行為設定。歷史、搜尋詞、憑證例外與 API Key 不同步。所有需要收藏同步的裝置均須更新。
- OpenRouter 為選用功能。金鑰由 Android Keystore 保護；每次分析前說明送出的結構與問題，需勾選同意，再按分析。網站 AI 可建議完整網站樣式與程式碼；元件 AI 的可套用修改嚴格限於所選選擇器。查看變更後可一鍵保存套用，並復原上一份設定。
- 圖片長按可加入系統下載佇列；目前支援 HTTP／HTTPS 圖片，不含 data:/blob:、Canvas 與 CSS 背景匯出。三點選單的「分享」會開啟 Android 分享面板，傳遞目前網址與標題。
- 設定 → 瀏覽提供天眼練習場，以及隱私、資料用途與刪除方式。練習場包含移除、恢復、新增 CSS 和新增 JavaScript 範例。

## 目前限制

這是使用 Android 系統 WebView 的瀏覽器，沒有內建自己的 Chromium；更新內核需由手機更新 WebView 提供者。網站相容性不等於完整 Chrome，沒有 Chrome 密碼同步、擴充功能商店，也不保證所有網站登入與 DRM 功能都相同。網站相機、麥克風與定位目前不開放。

天眼能處理可存取的 DOM／CSS 結構。跨來源 iframe 可整塊選取，無法任意存取其內部；封閉 Shadow DOM、Canvas 內部也不是一般元件。網站結構改版後可能需要重新選取。自訂程式碼只影響使用者自己的頁面，不修改網站伺服器；JavaScript 可能發出網路請求，請只使用了解用途的程式碼。

憑證例外降低網站身分驗證保障，也無法修復真正無法建立 TLS 連線的伺服器。Google 對這類 WebView 略過行為有明確安全審核要求；不能把使用者同意視為已獲 Google 認可。公開發行前的未完成事項及已確認風險見 [Google Play 檢查](docs/PLAY-READINESS.md)。

收藏同步仍需以兩個真實裝置、同一 Google 帳戶驗收。隔離模擬器中的資料合併與 UI 回歸測試不等於已完成真實 Google Drive 端到端驗證；對外商店發行前也須更新公開隱私政策。

## 開發與完整原始碼

需要 Android SDK 36、build-tools 36.0.0、Java 17 與 Gradle wrapper。保留 AGP 8.13.0 與 Kotlin 2.4.0，固定使用符合 Kotlin 2.4 最低要求的 R8 9.1.29。

```sh
./gradlew -PqaInstall=true :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 scripts/build-private.py
```

正式建置輸出到 `release/<version>/`，包含 APK、AAB、Source.zip、BUILD.json、SHA256SUMS 與 R8.zip。R8 診斷包保留該次 mapping、設定與最佳化統計；AAB 內也包含對應 mapping 與 r8.json。`signing/` 存放本機發行簽章，不納入版本控制或原始碼壓縮檔。請保留它，既有 APK 更新需要相同簽章。不要公開或傳送私鑰與密碼；API Key 與 Google access token 不放進原始碼。雲端正式建置使用專用 Actions Secrets，不使用 QA 簽章替代。

[驗證紀錄](docs/VALIDATION.md) · [特殊元件限制](docs/SPECIAL-ELEMENTS.md) · [Android 套件登記](docs/ANDROID-REGISTRATION.md) · [同步與啟動畫面修正](docs/AI-FAVORITES-LAUNCH.md) · [圖片與分享](docs/IMAGE-DOWNLOAD-SHARE.md)

## 1.2.0 歷史驗證紀錄

以下是原有 1.2.0 的紀錄，不代表後續版本的新測試結果。

Google OAuth 已切換為正式環境，僅使用非機密的 drive.appdata 範圍；最終 Play App Signing 簽章仍須對應 Android OAuth 用戶端。OpenRouter／DeepSeek 已用合成頁面結構完成網站與元件範圍的真實呼叫。Google Drive 網站設定也完成兩份合成裝置快照的真實寫入、讀回及刪除合併測試。測試是在隔離 Android 模擬器完成，尚未代替使用者的實體手機驗收。

1.2.0 加入選用 Gemma 4 E2B 本機 AI（另下載約 2.59 GB）、影片沉浸式全螢幕、儲存連結小圖示，以及網址列本機歷史建議。54 項 JVM 測試、release lint、簽章與原生函式庫 16 KB 對齊檢查通過。本機模型完成原生下載、SHA-256 驗證及真實分析套用；全螢幕的 Android 視窗與返回退出、圖示及搜尋建議完成隔離模擬器測試，未代表實體手機影片解碼驗收。

## 1.4.0 瀏覽工具

三點選單加入下載、歷史記錄與無痕分頁；一般／無痕雙分頁集與卡片預覽保持第一層工具列不變。無痕需支援 WebView 資料隔離與清理；主動保存的檔案、書籤及收藏仍保留，詳見更新說明。

## 1.4.1 分頁快照

一般分頁快照改存 App 內部、排除系統備份，重啟可還原，關閉對應分頁才刪除。無痕仍不儲存快照。舊版未曾保存的圖片需在升級後重新瀏覽頁面才能產生；不改分頁卡片與第一層工具列外觀。

## 1.4.3 分頁選單

分頁一覽底部僅保留滿寬的「新增分頁」。關閉所有分頁移至右上角三點選單，仍須確認且僅關閉目前選取的一般／無痕分頁集。第一層工具列、分頁卡片、快照保存與 R8 最佳化不變。
