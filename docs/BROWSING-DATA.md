# 刪除瀏覽資料與快取（1.6.0）

三點選單上方的「瀏覽記錄」與清單標題統一命名；瀏覽工具中重複的歷史入口改為「刪除瀏覽資料」。

## 刪除範圍

- 六種時間：過去 15 分鐘、1 小時、24 小時、7 天、4 週、不限時間。按下「刪除資料」時才固定起訖時間，包含起點及終點。
- 瀏覽記錄與網址列搜尋詞依最近一次記錄時間刪除；沿用最多 250 個不同頁面與 100 筆搜尋詞的保存方式。
- 未關閉的一般分頁依最後瀏覽時間關閉，刪除其持久快照並儲存剩餘分頁。最後一個分頁關閉後保留新的空白首頁。
- 新分頁、主動導航與切換分頁會更新最後瀏覽時間。程序重啟的自動還原不更新時間，也不重新加入瀏覽記錄。舊版沒有時間的資料標記為未知，只在「不限時間」刪除。
- Cookie、網路快取、網站儲存及 service workers 使用 `WebStorageCompat.deleteBrowsingData(WebStorage.getInstance())` 清理一般 Profile，等待完成回呼，再清除 App 自有的網站圖示快取。
- **WebView 不提供按時間刪除網站資料的公開 API。** 畫面清楚標示這一項為「不限時間」，預設不勾選；即使選了有限時間，勾選網站資料仍代表清除全部一般網站資料，可能登出網站。這與 Chrome 內建引擎的精準時間刪除不同。不支援完整清理 API 的 WebView 會停用此選項並提示更新，不以不完整清理冒充成功。
- 書籤、收藏、下載檔案、天眼規則、AI 模型及設定不屬於此操作。無痕分頁與資料保持獨立，沿用關閉無痕工作階段的清理方式。
- 刪除中停用按鈕與取消，等待非同步清理。儲存或清理失敗會顯示「刪除未完成」，不顯示成功；已完成的刪除不可復原。

## 快取稽核與修改

一般 WebView 使用預設 HTTP 快取模式，快取有效期由網站 HTTP 標頭決定；過期可重新驗證。引擎另有容量配額與淘汰機制，沒有固定天數不代表磁碟無限累積，因此不新增會週期性登出使用者的全資料刪除。

原本 `SiteIcons` 的磁碟 PNG 沒有數量、容量或期限限制，記憶體中的每網站鎖與失敗記錄也可能持續增長。1.6.0 改為：

- 磁碟最多 256 個檔案、8 MiB，超額先刪最舊檔案；圖示取得後最多保留 30 天。
- 啟動與每次成功保存後清理；讀取時檢查是否過期，不載入過期圖示。
- 記憶體圖示最多 128 筆、失敗嘗試最多 256 筆、固定 16 個鎖；清理時使先前的下載／寫入失效，避免清完又被背景工作寫回。
- 分頁快照為工作階段資料，上限 20 個分頁，每張最多 1 MB，既有關閉及啟動孤兒清理保留，不混入 HTTP／圖示快取淘汰。

## 驗證

`BrowsingDataPolicyTest` 驗證六種時間的邊界、未定時間的升級資料與格式回讀。`IconCachePolicyTest` 驗證 30 天、容量及數量淘汰。

`BrowsingDataTest` 在隔離 `.qa` App 驗證選單、勾選與取消、跨時間刪除、一般與無痕隔離、真實 Cookie/localStorage、HTTP 圖片快取前後的伺服器請求數，以及深淺色畫面。`scripts/test-browsing-data-restart.py` 在兩個獨立程序中驗證刪除後不復活與舊時間保留。R8 的獨立測試 App 也從實際介面執行刪除。

實測結果以 Release 對應的 Actions 與附件為準；模擬器不代表使用者實體手機驗收。

## 官方依據

- [WebStorageCompat 清理範圍、完成回呼與多 Profile](https://developer.android.com/reference/androidx/webkit/WebStorageCompat)
- [Chromium WebView HTTP 快取容量](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/browser/network_service/net_helpers.cc)
- [WebView 快取配額設定](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/common/aw_features.cc)
