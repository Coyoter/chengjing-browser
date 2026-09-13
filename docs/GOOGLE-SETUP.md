# Google Drive 登入設定

2026-09-13：OAuth 已切換為正式環境（UI 顯示「實際運作中」），品牌首頁與隱私政策已設定，唯一範圍 drive.appdata 列在「非機密範圍」。下列保留初期測試環境紀錄；測試名單限制已不代表目前發布狀態。最終 Play App Signing 簽章仍須核對 Android OAuth 用戶端。

Google Cloud 設定已完成並讀回確認（2026-09-12）：

- 獨立專案：`chengjing-browser-private`，Google Drive API 已啟用。
- Google 授權品牌：澄境瀏覽器。使用者已於本次對話同意 Google API 服務使用者資料政策，表單已提交。
- Android 套件：`tw.techtarian.browser`
- 自用發行簽章 SHA-1：`4B:60:AD:7B:7A:D1:00:61:27:FA:28:CE:7D:EC:DA:2D:69:D3:36:97`
- Android OAuth 客戶端：`782775870942-5vhh7eo398ultdnk3tcqcc6c4eaecol9.apps.googleusercontent.com`
- 唯一 Drive 授權：`https://www.googleapis.com/auth/drive.appdata`
- OAuth 維持外部測試模式；測試名單只有 `coyoter@coyoter.com`。沒有上架 App 或公開原始碼。

Android 使用 `Identity.getAuthorizationClient`，透過套件與簽章識別。App 不需要嵌入 Android client ID 或 client secret；Google Play services 管理帳戶授權與 access token。發行 APK 可沿用同一個 OAuth 客戶端。另一張 debug 簽章沒有登記，登入驗證請使用 release APK。

這個專案和澄境筆記完全分開，使用獨立的應用程式隱藏空間。每個裝置只寫入自己的書籤快照，再合併其他裝置的快照。刪除紀錄會保留，以免舊裝置把書籤救活。只有寫入後讀回一致才顯示「已同步」。不會上傳網域規則、API Key、瀏覽紀錄、Cookie 或密碼。

## 使用

安裝最新版自用 APK → 選單 → Google 書籤同步 → 使用 Google 帳戶連結 → 選取 `coyoter@coyoter.com` → 在 Google 畫面允許應用程式專用資料存取。首次會合併本機書籤。App 開啟以及書籤異動後同步；暫時離線會保留本機內容。

如需使用另一個 Google 帳戶，需先把該帳戶加入此專案的測試使用者清單。自用測試模式下，Google 可能再次要求授權，App 會顯示重新連結入口。

## 驗證

Android release APK 已到達真實的 Google 帳戶選擇與單一 `drive.appdata` 同意畫面。真實 Drive 回讀結果記錄於 [VALIDATION.md](VALIDATION.md)，不要只因為顯示授權畫面便推定同步成功。

`GoogleSetupProbe` 與 `GoogleDriveLiveTest` 是需要明確啟用的驗證程序，預設測試會略過，避免一般回歸測試啟動 Google 帳戶授權或寫入真實雲端。

```sh
adb shell am instrument -w -e liveGoogle true -e class tw.techtarian.browser.GoogleDriveLiveTest tw.techtarian.browser.test/androidx.test.runner.AndroidJUnitRunner
```

實作依據：[Android AuthorizationClient](https://developer.android.com/identity/authorization)、[Drive 應用程式專用資料](https://developers.google.com/workspace/drive/api/guides/appdata)、[about.get 可用最小權限讀取帳戶識別](https://developers.google.com/workspace/drive/api/reference/rest/v3/about/get)。
