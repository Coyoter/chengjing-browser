# 網站功能與相容性檢查（1.14.1）

這份清單區分澄境自行拒絕／漏接的功能，以及 Android System WebView 的能力邊界。不能把「移除拒絕」說成已完成所有網站的相容性。

## 這次修正

| 使用情境 | 舊版問題 | 修正與驗證 |
| --- | --- | --- |
| 手機網站定位（1.14.1） | 直接 Android 來源未回應時，未嘗試 Google Play 服務融合定位 | 兩條路徑並行、互不阻斷，保留網站 timeout／maximumAge；測試真正 Google API、無 Google 服務備援與公開垃圾桶網頁。原手機結果尚需更新後確認 |
| 網頁視訊、拍攝、掃描 | 所有 `PermissionRequest` 一律拒絕 | 網站與 Android 分別授權相機；實測取得影像軌並錄出影音檔 |
| 網頁語音、錄音 | 同上 | 麥克風獨立授權；實測取得音訊軌，與影像一起錄製 |
| 受保護的影音 | 被同一段拒絕程式誤報成相機／麥克風不支援 | 允許 WebView 的受保護影音識別請求，可按網站封鎖；個別串流服務的裝置認證、訂閱與 DRM 等級仍由該服務決定 |
| MIDI 裝置控制 | 同一段程式一律拒絕 | 明確詢問 MIDI 權限；不冒充相機／麥克風。實體樂器相容性依手機及 WebView |
| 拍照、錄影、錄音上傳 | 只開檔案選擇器，未接 capture | 可選已有檔案或呼叫對應拍攝／錄音應用；指定照片 capture 可直接拍照；實測系統相機回傳完整照片 |
| 使用者點擊開新視窗的連結 | 啟用防跳轉後連主動點擊也被拒絕 | 主動點擊可在澄境建立分頁；無操作的彈窗仍攔截；實際點擊測試 |
| 需要第三方 Cookie 的登入／嵌入內容 | 所有分頁固定拒絕，沒有網站例外選項 | 網站資訊可只為目前來源開啟；無痕設定獨立且暫存。這不代表 Google 等供應商會允許 WebView OAuth |
| 讀屏與無障礙文字 | 分頁切換後可能讀到上一份文件的快取節點 | 網頁載入與顯示時刷新可讀取文字樹，不移動鍵盤或使用者焦點 |
| 多分頁 | 建立與還原都截斷在 20 個 | 移除 20 個上限，測試 25 個分頁保存與還原、新增第 26 個 |
| 重新開啟瀏覽器 | 所有舊分頁立即連線載入 | 先恢復分頁資料與縮圖，僅載入可見分頁；切換後才載入其他舊分頁，避免啟動時背景網站一起請求功能 |
| 已授權背景影音 | 背景分頁權限也被拒絕 | 已授權的影音請求不依賴目前選中的分頁；新的授權仍只在前景詢問，相機／麥克風的新使用需應用在前景 |
| 網站授權撤回 | 沒有媒體授權選項 | 網址列網站資訊可選詢問／允許／封鎖；撤回時重新載入已開啟的相關文件，讓既有使用停止 |
| 無痕網站權限與拍攝檔 | 未支援媒體權限 | 與一般授權隔離；關閉全部無痕分頁清除。拍攝暫存於關閉或下次啟動清理 |

一般拍攝暫存超過 24 小時後，在下次啟動或拍攝時清理。不會為選取既有照片要求整個相簿／儲存空間的廣泛讀取權限。相機與麥克風硬體宣告為非必要，避免讓沒有該硬體的裝置失去安裝資格。

## 既有流程一併回歸

本次發布沿用完整雲端驗證：一般與正式 R8 版本的基本導覽、返回／前進、分頁與縮圖保存、書籤／收藏同步、17 種語言、深淺色、搜尋、頁內尋找、Android 應用連結、圖片操作、HTTP／Blob／data 下載、原生影片下載、定位（含沒有 GMS）、密碼自動填入與 APK 安裝確認。新增的權限與相容性測試加入相同發布閘門。

登入仍留在澄境內，沒有重新加入外部瀏覽器登入跳轉。防惡意跳轉、HTTPS 驗證、使用者自己設定的網站封鎖及系統授權仍有效；不以無條件授予所有未來權限來冒充相容性。

## 目前核心仍有的限制

2026-10-10 的隔離 Android 16 測試環境探測到 `getUserMedia`、`MediaRecorder`、WebRTC、受保護影音介面、MIDI 與 Service Worker；沒有網頁通知／Push、`getDisplayMedia` 螢幕分享、Web Bluetooth、WebUSB 與通行金鑰介面。這是該測試環境的結果，不能當成每支手機、每個 WebView 版本的保證。

- 網頁通知／背景 Push、Web Bluetooth／WebUSB 沒有可直接開啟的通用 WebView 對接；這些不是把澄境的一個 `false` 改成 `true` 就能補齊。
- 網頁螢幕分享與通行金鑰尚未在澄境完成端到端支援。WebAuthn 的瀏覽器模式另有系統／服務供應商的特權應用要求，不能只開設定就宣稱任意網站可用。
- Google 或其他服務拒絕內嵌網頁登入，以及特定串流服務要求受認證裝置，仍須按網站實測；不能承諾所有登入和付費影音都保證成功。
- Chrome 擴充功能與完整 PWA 安裝／背景執行，仍非目前澄境已實作的功能。

若目標包含上述功能，後續需要另做核心與原生服務整合；本次沒有換掉網頁核心，也不把這些項目標示為完成。

## 官方依據

- [Android 網站權限請求及可授予資源](https://developer.android.com/reference/android/webkit/PermissionRequest)
- [WebView 檔案選擇與拍攝請求](https://developer.android.com/reference/android/webkit/WebChromeClient.FileChooserParams)
- [Chromium 的 WebView 網頁平台相容性說明](https://github.com/chromium/chromium/blob/main/android_webview/docs/web-platform-compatibility.md)
- [Chromium：WebView Push／通知追蹤項目](https://issues.chromium.org/issues/40388442)
- [Chromium：WebView Web Bluetooth 追蹤項目](https://issues.chromium.org/issues/40703318)
- [AndroidX WebAuthn 支援層級](https://developer.android.com/reference/androidx/webkit/WebSettingsCompat#setWebAuthenticationSupport(android.webkit.WebSettings,int))
