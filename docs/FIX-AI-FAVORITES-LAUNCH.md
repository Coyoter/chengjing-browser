# AI 設定、收藏同步與啟動畫面修正

本分支只處理使用者提出的三項問題，未變更發行版本號、main、既有 Release 或簽章設定。

## AI 設定

`BrowserStore.aiProvider` 現在由同一份 Compose 可觀察狀態提供。供應商切換會使讀取它的設定父層重新組合，不再僅刷新 RadioButton 所在子層。Key 仍由既有 Android Keystore 邏輯保存；切換供應商不會刪除已儲存的 Key。

## 收藏與閱讀位置

使用獨立 `chengjing-browser-favorites-v1` Drive 集合，沿用現有 drive.appdata 授權與帳戶綁定，避免舊版本的書籤同步覆盖新資料。收藏保留既有 UUID，自訂名稱、頁面標題、網址、捲動位置與閱讀比例一起同步。衝突以最新修改時間為準，不採最高閱讀百分比；同時間刪除優先，其餘平手有固定排序，讓裝置收斂。

既有 favorites-v1/items 陣列原地相容讀取，不會清空或重新分配識別碼。刪除以 tombstone 保留，改名會更新時間。寫入後回讀驗證才回報同步成功；上傳期間的本機異動會保留並安排下一輪。收藏與書籤的舊資料不會因單純的空遠端快照被刪除。

尚未透過兩個真實 Google 帳戶或兩支實體手機驗收；測試資料須為合成資料，不可把正式帳戶當測試環境。舊版不會交換收藏資料，兩端都需更新。

## 啟動畫面

參考澄境筆記 `android/.../LaunchSurface.kt`（e2a2171a6938f47441867e478e65fb535bffa2ed）：米白／墨綠背景、偏左字標、細弧線與輕微呼吸點。使用原生第一幀，之後才恢復分頁並顯示 Compose；不新增專用 Splash Activity，不設定固定等待秒數、不等待網路，並尊重關閉動畫的系統設定。Android 12 以上移除預設的大型啟動圖示，避免雙重品牌畫面。啟動尚未完成就暫停時，不會用空分頁覆寫原有紀錄。

## 驗證範圍

新增 FavoriteSyncTest（JVM）、FavoriteStoreSyncTest（隔離 SharedPreferences）及 AiSettingsRegressionTest（QA 應用）。GitHub Actions 執行單元測試、lint、QA debug APK 與測試 APK 編譯；沒有執行模擬器／真機測試。請以對應 commit 的 Actions 結果為準，檔案存在不代表測試通過。

合併／正式發行前仍需：檢查 Actions 成功結果、在 QA 裝置執行新增 Android 測試、確認不同系統版本／深淺色啟動畫面、以合成資料驗證真實 Drive 雙裝置往返、同步更新網站上的公開隱私政策。此次只更新 App 內的 privacy.txt，沒有操作科技人網站。沒有建立正式簽章 APK/AAB，也沒有發布 Release。
