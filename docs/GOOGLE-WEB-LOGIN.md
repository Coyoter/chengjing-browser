# Google 網頁的原生登入：目前邊界

使用者提供的是 google.com 搜尋頁的「登入」按鈕，而不是澄境瀏覽器的書籤同步入口。

目前 App 已實作 `Identity.getAuthorizationClient`，使用 Android GMS 選帳戶、授權 `drive.appdata`、取得存取令牌與同步書籤。這個令牌供澄境呼叫 Drive API，不會替內嵌網頁建立 google.com 的網頁登入 Cookie。

依 2026-09-12 查核的官方資料：

1. [原生 Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation) 為 App 取得 Google ID token，由 App 的服務驗證使用者；不是任意 Google 網頁的登入憑證。
2. [Credential Manager 的 WebView 整合](https://developer.android.com/identity/sign-in/credential-manager-webview) 一般 App 模式需和 App 擁有的網站設定 Digital Asset Links，不能把自己宣告為 google.com 的擁有者。
3. [特權瀏覽器整合](https://developer.android.com/identity/sign-in/privileged-apps) 可代表第三方網站請求憑證；Google Password Manager 要求先提出申請並取得特權呼叫者核准。這是網站密碼金鑰等憑證的整合方向，仍取決於網站與使用者現有憑證，不能保證所有 Google 網頁都一點即登入。
4. [Chrome Custom Tabs](https://developer.chrome.com/docs/android/custom-tabs) 可以共用提供該分頁的瀏覽器狀態，WebView 不共用該狀態。僅在 Chrome／Custom Tab 登入，不會自動讓澄境的 WebView 同步登入。
5. [Google OAuth 政策](https://developers.google.com/identity/protocols/oauth2/policies) 限制在可受 App 操控的嵌入式網頁中進行 OAuth 授權。此限制針對 OAuth，不把它擴張為「所有 Google 搜尋頁登入永遠不可能」。

結論：0.1.3 保留已完成的 App 原生書籤同步登入；Google 網頁的原生一鍵登入仍未完成。沒有新增會誤導使用者以為網站已登入的按鈕，亦未代使用者提交第三方憑證權限申請。
