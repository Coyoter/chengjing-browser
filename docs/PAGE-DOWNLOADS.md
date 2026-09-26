# 網頁檔案下載（1.10.0）

舊版在 DownloadListener 直接拒絕非 HTTP／HTTPS 網址，因此原生影音選單遇到 Blob URL 也會顯示不支援。一般影片檔原本就可以使用系統下載；新增的是暫存與內嵌來源。

## 來源與儲存

- HTTP／HTTPS：保留 Android DownloadManager 佇列、通知、Cookie 與來源網站資訊。
- Blob：在建立它的 WebView 讀取，使用隨機工作識別碼；每次最多 192 KiB 的 Blob.slice／FileReader 資料，拉取後立即寫入輸出串流，不建立整部影片的 base64 字串或第二份 app 檔案快取。單檔最多 2 GiB，同時最多兩項。
- Data：逐步解碼百分比、UTF-8 與 base64，避免把整個網址再次注入 JavaScript；網址長度上限為 48 × 1024 × 1024 個字元。
- Android 10 以上先建立 MediaStore Downloads 的 IS_PENDING 輸出，寫入完成後才公開。Android 9 使用既有儲存權限與隱藏 .part 檔案，再登錄到系統下載。
- 完成檔案沿用現有下載目錄與記錄，兼容舊圖片下載。實際 MIME、一般檔頭及建議檔名共同決定可辨識的儲存名稱。MediaStore 自動解決重名，不覆蓋既有檔案。

## 工作生命週期與隱私

不新增可由網頁任意呼叫的原生 JS interface。讀取只在使用者確認下載後開始，綁定分頁識別與文件世代；換其他分頁或開啟下載清單可以繼續，同一文件的 hash／history URL 改變不會中止。

關閉或重載來源頁會取消；Activity 銷毀也會中止。清理先關閉輸出再刪除未完成檔案。啟動時恢復 pending registry：未公開輸出刪除並記錄中斷，已公開但未及寫入清單的輸出補回下載記錄。Blob 來源已失效、來源不允許讀取、MediaSource／分段串流或受保護影片，不宣稱成功下載。

無痕匯出只需一次確認，文件與記錄保存在公開下載資料夾；新暫存下載不記錄無痕來源網址。沒有讀取 Cookie、表單或其他檔案的額外 JS bridge；Blob 從原網頁本身讀取，Data 從下載要求的內容解碼。

## 驗證

`DownloadFormatTest` 驗證格式辨識、檔名、來源邊界及 Data 解碼。`PageDownloadsTest` 透過實際網頁下載連結、影音原生選單測試寫入與進度；132 MiB MP4 檢查完整 SHA-256 與 MediaMetadataRetriever，並測試取消、來源關閉、無痕與中斷清理。

R8 獨立測試在不額外保留 App 類別的最佳化 APK 中下載 Blob MP4、Data PDF 與 HTTP PDF，再由另一個 App 接收 ACTION_VIEW，驗證 MIME、URI 讀取授權及實際檔案雜湊。

參考：[Android DownloadManager.Request](https://developer.android.com/reference/android/app/DownloadManager.Request)、[MediaStore 共用儲存](https://developer.android.com/training/data-storage/shared/media)、[Blob URL](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/blob)、[Blob.slice](https://developer.mozilla.org/en-US/docs/Web/API/Blob/slice)。
