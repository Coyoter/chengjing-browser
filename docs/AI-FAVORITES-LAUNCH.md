# AI 設定、收藏同步與啟動畫面修正

此文件描述 `fix/ai-favorites-splash-20260915` 的未發行原始碼變更。既有 `v1.2.0` APK/AAB、歷史 Release、版本號及正式簽章不因本修正自動改變。

## AI 設定

`BrowserStore.aiProvider` 現在使用 Compose 可觀察狀態，同時保存 SharedPreferences。`AiProviderPanel` 與 `SettingsPanel` 讀取同一個值，選取 OpenRouter 後會立即組成 `openrouter-api-key` 欄位；切回 Gemma 會移除欄位。不需要切換其他分頁，也不會清除已儲存金鑰。

## 收藏與閱讀進度

沿用 `favorites-v1` 的 `items` 以及每筆收藏原有的 ID。新增 `deleted` 欄位，舊紀錄沒有此欄位時視為未刪除。Google Drive 的收藏資料使用 `chengjing-browser-favorites-v1` 獨立命名空間與每裝置快照，舊版的書籤同步不會讀寫它。

同步包含自訂名稱、頁面標題、目前網址、捲動位置、閱讀百分比、更新時間與刪除狀態。收藏可以指向新的章節；同步不只合併網址，也不使用最大閱讀百分比取代使用者最新儲存的位置。這不是離線網頁正文下載功能。

較新的整筆紀錄勝出；相同時間的衝突採固定排序，同時間刪除勝過修改。刪除保留 tombstone，避免較舊的離線快照將收藏復活。重新命名也更新時間並觸發同步。每次本機修改的時間至少大於目前已知時間；跨裝置時鐘仍可能影響尚未互相同步的衝突順序。

下載完成後才重新讀取本機收藏，再合併、上傳、讀回核對。網路請求進行中產生的本機變更不會被回讀快照覆蓋，完成時會偵測差異並安排下一輪同步。遠端合併本身不觸發無限上傳循環。帳戶綁定、原有 `drive.appdata` 權限及網站設定的選擇性同步保持不變。

所有需要收藏同步的裝置都必須更新。既有舊版 App 不會上傳收藏的新增、進度或刪除。收藏與進度會在重新從收藏開啟時套用；不強制改動使用者當下正在閱讀的頁面。此版仍以使用者儲存的閱讀位置為準，不增加逐次捲動就上傳的行為。

## 啟動畫面

參考 `Coyoter/chengjing-notes` 的 `LaunchSurface.kt`，以原生 Canvas 畫出澄境字標、瀏覽器副標、英文標識、低對比弧線和動態圓點。支援深淺主題及關閉動畫的系統設定，沒有固定顯示秒數、網路請求或另一個 Splash Activity。

Android 12 以上的系統啟動畫面使用內縮留白的指南針圖形及對應深淺背景。原生首幀顯示後才建立第一個 WebView；在背景關閉初始化中的 Activity，不會將空分頁寫回原本保存的分頁。

## 自動驗證

`Android checks` 使用唯讀 repository 權限與固定 SHA 的 GitHub 官方 Actions，不讀取正式簽章或 Google 帳戶秘密。

- `testDebugUnitTest`：包括新的 FavoriteSyncTest（快照、衝突、倒退閱讀、刪除、舊資料與時間邊界）。
- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug`：檢查 QA app 與測試 APK。
- 隔離模擬器執行 AiProviderSettingsTest、FavoriteSyncStoreTest、BrowserLaunchSurfaceTest，並輸出深淺色／橫直向原生啟動畫面預覽。

CI 產生的是 `.qa` 套件的測試版 APK，不是可覆蓋正式版的發行簽章 APK。實際通過或失敗以該次 GitHub Actions 的結果為準。

## 正式發布前

仍需以兩個已登入同一 Google 帳戶的裝置驗證收藏新增、跨章節進度、重新命名、離線刪除及重新連線合併。自動回歸測試不會使用真實使用者的 Google Drive、API Key 或正式收藏。

App 內隱私說明已更新同步範圍。`store/privacy.html` 及 `store/public-policy/index.html` 的公開政策來源與實際網站尚未在此修正中部署；正式發行前須同步更新收藏欄位、刪除紀錄與停止同步的說明。既有簽章金鑰保持在原本的安全位置，不放入 Git repository。
