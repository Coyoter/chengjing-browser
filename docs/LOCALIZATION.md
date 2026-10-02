# 多語系

設定／外觀中的語言選單提供「跟隨手機語言」與 17 個語言原名。使用既有澄境配色、圓角和文字層級；浮動選單使用 surfaceContainerHighest、細邊框與陰影區分後方卡片。選取行有底色及勾選標記，長文字可正常顯示，選單有捲動高度限制。

## 語言對應與保存

`AppLanguagePolicy` 接受 zh-TW、zh-CN、en、ja、ko、fr、de、es、pt、ar、th、ru、hi、id、vi、bn、ur。中文依 Hant/Hans 字體和 TW/HK/MO 地區辨識；其他支援語言的地區變體對應到基礎語言，印尼文舊代碼 in 也可對應 id。手機語言列表第一個可支援的項目優先，皆不支援時採 en。

`AppLanguages` 保存 browser-v1 的 app-language（預設 system）和 language-applied。跟隨手機模式在系統語言變更與回到前景時重新解析；Android 13 以上同步原生應用程式語言，較舊版本使用 AppCompat。locale/layoutDirection 設定變更由 Activity 處理，避免切換時重建 WebView 和遺失分頁。語言偏好不納入 Google 同步。

## 資源與文字

- 英文使用 values/strings.xml，繁簡中文使用 values-b+zh+Hant 與 values-b+zh+Hans，印尼文使用 Android 相容名稱 values-in；其他使用對應 values-xx。
- `bt` 在呈現時讀取目前語言資源，使用 %1$s 等位置參數。`BrowserCaption` 保留資源 ID，讓長時間存在的 AI 模型及同步狀態也能隨語言切換。
- `localization/source-zh-TW.json` 保存完整繁體中文來源，`BrowserTextSource` 只供沒有 Android Context 的 JVM 驗證回退。新增來源後可執行 `python3 scripts/update-localization-source.py` 更新該對照表；必須同步更新 17 組 XML。
- 每個語言的 i18n/<tag>/practice.html 和 privacy.txt 隨安裝檔提供。長隱私政策使用資產文字檔，避免超過 Android 編譯字串長度限制。首頁語錄由各語言 home_quotes 資源讀取。
- 天眼的應用提示與 AI 回覆語言依所選介面語言提供。語言變更只更新頁面提示，不重新套用規則或清除未儲存預覽。網站內容、網址、協定鍵值、使用者命名與自訂程式碼維持原內容；網址和程式碼固定由左至右顯示。
- AAB 停用 language split，所有語言離線可用。執行期不使用翻譯服務，不新增翻譯 API 或密鑰需求。

翻譯以繁體中文來源製作，尚未經所有語言母語者逐項審校。

## 驗證

`python3 scripts/verify-localization.py` 驗證 17 組鍵值、位置參數、89 則語錄、練習頁與隱私政策、Android 字串長度及遺漏的中文 UI 字串。AppLanguagePolicyTest 驗證語言匹配、英文回退與 RTL。LanguageSettingsTest 驗證 17 種選項、分頁保存、語言保存、系統模式、深淺色下拉選單和長期狀態更新；FirstLanguageLaunchTest 在清除 QA 資料後另行執行。

測試專用 BrowserTestRunner 讓舊回歸預設使用 zh-TW；首次啟動驗證以 testLocale=system 執行。獨立 R8 測試主機驗證正式最佳化版本的英文、韓文切換與程序重啟。所有測試只操作 .qa 套件，不清除正式使用者資料。
