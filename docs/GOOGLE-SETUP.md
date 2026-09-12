# 自用版 Google Drive 登入設定

狀態：Android 程式已接入 AuthorizationClient；Google Cloud 專案 `chengjing-browser-private` 已建立，Google Drive API 已啟用。Google 品牌表單已填好，停在「我同意 Google API 服務：使用者資料政策」勾選框，等待使用者當下同意；Android OAuth 客戶端尚未登記。

這個 App 有自己的套件與簽章，不能直接冒用澄境筆記的 Android 客戶端。已使用獨立 Google Cloud 專案，避免共用澄境筆記的品牌及 appDataFolder。

- Android 套件：`tw.techtarian.browser`
- 自用發行簽章 SHA-1：`4B:60:AD:7B:7A:D1:00:61:27:FA:28:CE:7D:EC:DA:2D:69:D3:36:97`
- 唯一 Drive 授權：`https://www.googleapis.com/auth/drive.appdata`
- Android 使用 `Identity.getAuthorizationClient`。不在 App 放 client secret；Google Play services 管理授權與 token 刷新。

後續待完成：

1. 已完成：建立「澄境瀏覽器」Google Cloud 專案，啟用 Google Drive API。
2. 設定 Google Auth Platform 品牌與開發者聯絡資料。純自用可先使用測試發布狀態，加入自己的 Google 帳戶為測試使用者；以 Console 當時顯示的授權期限規則為準。
3. 建立 Android OAuth 客戶端，填入上方套件與發行簽章 SHA-1。若要測試 debug APK，另登記 debug 簽章。
4. 安裝本機發行 APK，從「Google 書籤同步」完成帳戶選擇與授權。
5. 使用隔離的測試書籤完成首次上傳、重新讀回、第二裝置合併、刪除同步、離線後再同步與取消授權測試。未完成這步不可宣稱真實雲端同步已通過。

雲端只建立 `appProperties.app=chengjing-browser-bookmarks-v1` 的書籤快照。每個裝置寫入自己的檔案，合併各檔案，避免同時寫入同一個檔案造成覆蓋。保留刪除紀錄，避免舊裝置讓書籤復活。寫入後讀回一致才顯示「已同步」。不會上傳網域規則、API Key、瀏覽紀錄、Cookie 或密碼。

實作依據：[Android AuthorizationClient](https://developer.android.com/identity/authorization)、[Drive 應用程式專用資料](https://developers.google.com/workspace/drive/api/guides/appdata)。
