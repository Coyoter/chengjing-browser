# 圖片下載與系統分享（未發行）

接續 PR #2 的三項修正，未更動版本號、main、正式簽章或歷史 Release。

## 長按圖片

一般圖片的原生長按選單包含「下載圖片」。帶有連結的圖片透過 WebView.requestFocusNodeHref 分開取得圖片 src 與連結 href，保留連結開新分頁及複製功能，不會誤把連結頁當圖片下載。一般文字與編輯欄位保留 WebView 的原生選取行為；天眼選取模式不開啟這個選單。切換／關閉分頁後不使用過期的選單目標。

HTTP／HTTPS 圖片交给 Android DownloadManager，存至系統 Downloads。使用獨立檔名避免同名覆蓋，沿用圖片來源 Cookie 與目前 User-Agent，Referer 僅提供頁面來源網域，不傳私人頁面路徑／參數，HTTPS 降級至 HTTP 時不送 Referer。下載仍遵守系統連線驗證，不因網頁允許憑證例外而略過下載憑證驗證。系統通知／下載項目呈現真正完成或失敗；App 只回報「已加入佇列」。

Android 9 在使用者要求下載後才申請儲存權限；此舊式權限限 maxSdkVersion 28，不要求全檔案存取。Android 10 以上走系統下載，不新增廣泛儲存權限。

範圍限制：目前不匯出 data:、blob:、CSS 背景圖或 Canvas。遇到不支援網址會明確提示，不能聲稱已下載。需要完整頁面 Referer、特殊授權或防盜連驗證的圖片仍可能被來源伺服器拒絕。這不是略過網站安全機制的功能。

## 分享

工具列直接提供「分享目前網頁」，不是只藏在更多選單。透過 ACTION_SEND、text/plain 與 Intent.createChooser 使用 Android Sharesheet；分享目前網址與標題，不指定收件 App、不讀取安裝清單、不自動發送給任何人。首頁／非 HTTP(S) 頁面停用分享，網址內明文基本驗證帳密會移除，文章查詢參數與錨點保留。

工具列六個操作保留至少 48 dp 高度；在一般窄手機上以等寬區域排列，極窄分割視窗可水平捲動，不犧牲原有收藏或選單入口。上下工具列設定仍沿用既有行為。

## 驗證

新增 PageActionPolicyTest（JVM）與 PageActionsTest（Android）。Android 測試以合成圖片及被攔截的分享 Intent 驗證選單和整合，不對真實收件人分享、不動正式收藏、Google Drive 或簽章。長按測試注入下載回呼核對圖片 src，並不代表所有網站的網路下載已驗收。

以對應提交的 GitHub Actions 實際結果為準：測試碼存在、套件能編譯，不等於裝置操作測試通過。正式發布前仍需確認系統實際下載完成、Android 9 授權拒絕／允許、上下工具列、320 dp 畫面、系統分享面板及取消後回到原分頁。
