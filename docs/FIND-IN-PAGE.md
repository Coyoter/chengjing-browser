# 頁內搜尋修正

以目前 main（085c364476015ac1d1c27d1b8f19ab49f35ef939）為基礎，不回退到舊版介面。此次使用者要求修正並更新 main，未要求發布新版本；版本號、簽章及既有 Release 不修改。

原本「尋找頁面文字」放在覆蓋整個網頁的 BrowserPanel，且没有顯示 WebView.FindListener 的結果。現在從三點選單開啟後關閉選單，暫時以頁內搜尋列代替頂端工具列，底部工具列暫時隱藏以保留閱讀空間；同一個 WebView 仍留在畫面中，可直接看到反白及捲動。關閉後原有網址列位置與工具列原樣返回。

使用 WebView.findAllAsync、setFindListener、findNext 及 clearMatches，不注入 DOM、不將全文或搜尋詞傳送到伺服器。顯示目前／總筆數、搜尋中、找不到，支援前後筆及跨頁尾循環；空字串與沒有結果時停用移動。中文組字輸入不在組字中自行送出 Enter，硬體 Enter／Shift+Enter／Escape 與鍵盤搜尋動作有對應操作。

搜尋只屬於目前分頁，關閉、切換、導向新文件、開啟其他功能面板或銷毀 WebView 前取消工作並清除反白。不以 SharedPreferences 或 rememberSaveable 保存搜尋詞，無痕沿用自己的 WebView。天眼仍有未儲存修改時，不自動丟棄那些修改來進入搜尋。

以原有 Material 配色支援深淺色。一般寬度採單列；320 dp 等窄視窗或較大的系統字體分成輸入與操作兩列，保留 48 dp 操作區，不以重疊方式硬塞按鈕。

新增 FindInPageTest，驗證真正 WebView 的中文與英文搜尋、計數、前後筆、捲動、無結果、快速變更、返回／關閉、分頁切換／關閉／導向、無痕不寫入一般偏好設定、底部網址列與 320 dp／兩倍字體。独立 R8 測試 host 另對未加 App 測試 keep 規則的實際混淆版 APK 操作搜尋。是否通過以該提交的 GitHub Actions 結果為準。

原生 API 依據：https://developer.android.com/reference/android/webkit/WebView#findAllAsync(java.lang.String)
