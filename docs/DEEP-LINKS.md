# 開啟其他 App（1.7.0）

1.6.0 及以前，WebView 會直接攔截所有非 HTTP/HTTPS 網址。1.7.0 新增由使用者主動點擊的外部 App 連結：

- App 自訂網址格式，例如 `vnd.youtube:`、`youtube://`、`line://`；實際可用格式由接收 App 決定。
- Android `intent://` 連結，保留網址與指定 App 套件；沒有對應 App 時，使用有效的 HTTP/HTTPS `browser_fallback_url`，否則留在目前頁面並提示。
- 一般 HTTP/HTTPS App Links：只有 Android 已指定的非瀏覽器 App 才接手；普通網頁、沒有指定處理 App、停用「開啟支援的連結」時，留在澄境。YouTube 等 App 需已安裝並允許處理對應網址，澄境不改使用者的系統預設。
- 點擊所產生的伺服器重新導向可在 10 秒內延續授權；點擊新視窗有一次、3 秒內的起始導航授權。完成頁面導航就清除授權。
- 無使用者動作的 JavaScript 跳轉、子框架、從網址列輸入網址後的自動重新導向，均不取得開啟 App 的授權。天眼選取時也不跳轉。
- 無痕瀏覽跳到其他 App 前會提示，取消則保留原頁；對方 App 的記錄不受澄境無痕模式控制。一般分頁不多加確認步驟。

只建立 `ACTION_VIEW` / `CATEGORY_BROWSABLE` 的新 Intent；不轉送網站提供的 component、selector、action、旗標、權限授予或 extras。瀏覽器內部、檔案與內容網址不能藉由 intent 包裝轉交 App；不加入自動安裝或開啟商店的流程。不掃描／保存整份已安裝 App 清單，僅宣告 HTTP/HTTPS 連結處理程式的查詢需求。

Android 11 起同時使用 `FLAG_ACTIVITY_REQUIRE_NON_BROWSER` 與 `FLAG_ACTIVITY_REQUIRE_DEFAULT`，避免一般網頁轉去另一個瀏覽器或彈出無關選擇器。Android 9/10 以系統預設處理程式及一般瀏覽器排除判斷；無法開啟時保留網頁。功能範圍為網頁中的點擊導航，不改澄境接收外部 HTTP/HTTPS 網址的原有行為。

## 驗證

`ExternalLinkPolicyTest` 覆蓋點擊／重新導向授權、逾時、撤銷、彈窗授權與禁用格式。`ExternalLinksTest` 使用本機 HTTP 測試頁和獨立的測試接收 App，驗證跨 App 實際收到網址、一般網址與 App Links 分流、缺少 App 的備援、無痕提示、沒有動作的跳轉攔截及 Intent 清理。接收 App 僅存在於 `release-smoke` 測試模組，不包含在正式瀏覽器 APK/AAB。

正式 R8 安裝包另以獨立測試 App 從實際介面測試自訂格式與 intent 連結。CI 以本次 Actions 結果為準；模擬器中的接收 App 驗證不代表已在使用者實體手機或所有第三方 App 完成驗收。

## 官方參考

- [Chrome 的 Android intent 與備援網址](https://developer.chrome.com/docs/android/intents)
- [Android 網址處理與非瀏覽器 App](https://developer.android.com/training/package-visibility/use-cases)
- [Android 查詢範圍與 host 萬用字元](https://developer.android.com/training/package-visibility/declaring)
