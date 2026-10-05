# 系統安裝、密碼填入與 Google 登入

## APK 安裝

1.13.0 宣告 `REQUEST_INSTALL_PACKAGES`。設定／瀏覽可開啟「允許這個來源」頁面；下載清單中由使用者開啟 APK 時，以唯讀 content URI 和暫時授權交給 Android 安裝確認。未授權時先詢問是否開啟設定，返回後再顯示安裝確認。沒有靜默安裝、背景安裝或內建自動更新程序；裝置管理政策仍可能禁止安裝。

[Android 安裝來源 API](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()) 要求宣告權限並取得使用者信任。瀏覽器屬於 [Google Play 允許的用途](https://support.google.com/googleplay/android-developer/answer/12085295)，但之後提交 Play 的 AAB 前，仍須填寫權限申報並在商店說明記載「使用者下載及安裝 APK」功能。GitHub Release 不等於 Play 審核或上架。

## 系統密碼服務

一般分頁明確啟用 Android Autofill，並以原生外殼補交 Compose AndroidView 遺漏的 WebView 表單結構；網頁的真實網域、表單及 autocomplete 資料由 WebView 提供給使用者選擇的系統服務。澄境不建立密碼資料庫、不用 JS 擷取帳號密碼，也不把密碼加入書籤／天眼 Google Drive 同步。切換或關閉目前分頁時取消原填入工作階段，無痕分頁維持排除自動填入。

設定／瀏覽的「密碼與自動填入」顯示目前選擇的服務，並開啟 Android 的服務選擇頁面。[WebView 官方說明](https://developer.android.com/reference/android/webkit/WebView#onProvideAutofillVirtualStructure(android.view.ViewStructure,int)) 定義網域及表單資料的傳遞方式；實際是否提供建議、保存及填入，由網站標記、WebView 版本與服務供應商決定。部分供應商會限定受信任的瀏覽器，不能宣稱所有密碼服務都保證相容。

- Google 密碼管理員提供帳戶內的密碼保存、填入與跨裝置存取；服務須由使用者在手機設定啟用。[Google 說明](https://support.google.com/accounts/answer/6208650)
- Samsung Pass 的官方產品頁明列網站自動填入僅支援 Samsung Internet；澄境不能繞過這項限制。[三星說明](https://www.samsung.com/us/apps/samsung-pass/)
- 小米的內建服務若公開 Android Autofill，可從同一系統入口選擇；未找到可承諾 HyperOS 私有密碼庫直接存取或同步的公開 SDK。[小米既有瀏覽器隱私說明](https://trust.mi.com/pdf/MIUI_Privacy_White_Paper_EN_June_2021.pdf) 描述小米瀏覽器的保存資料，但不構成第三方存取 API。

自建跨裝置密碼庫技術上可行，但需要端到端加密、主密碼／恢復機制、鎖定與衝突處理，不能把明文密碼放入現有書籤同步。本次已有 Android 系統介接路徑，採服務供應商的同步，不額外建立獨立密碼庫。各家真實服務及小米／三星實機仍需另行確認。

## Google 登入

澄境自己的 Google Drive 同步原本即使用 GMS AuthorizationClient 原生授權；它授予澄境所需的 Drive 權限，無法替任意網站產生 Google 或網站登入 Cookie。Google 的 OAuth 用戶端／重新導向綁定網站的登入流程；[Google 登入政策](https://developers.google.com/identity/protocols/oauth2/policies) 也限制可注入程式碼及讀取 Cookie 的內嵌授權環境。因此不以 AccountManager 私有令牌、Cookie 匯入、偽造 Chrome 或 JavaScript 橋接模擬網站登入。

1.13.0 在有可用 GMS 與獨立瀏覽器時，提供「Google 登入協助」。一般分頁由使用者動作進入確切的 accounts.google.com HTTPS 登入／授權入口時，可確認在系統瀏覽器重新開啟原網站，再點選 Google 登入。採 Custom Tabs、明確指定獨立瀏覽器套件；使用該瀏覽器自己的 Google 登入狀態，網站登入也在該瀏覽器完成。原澄境分頁保留，沒有跨瀏覽器搬移 OAuth state、登入 Cookie 或密碼。

沒有 GMS、沒有支援的獨立瀏覽器、功能關閉或無痕分頁時，維持既有網頁流程。關閉協助提示會留在原頁。這是登入協助，不是「GMS 帳號選擇器讓澄境內所有網站直接登入」；個別網站仍可能拒絕 WebView OAuth。

## 驗證

SystemIntegrationTest 在隔離 QA 套件確認宣告權限、真實 APK 的系統確認畫面、實際 Android AutofillService 對網頁欄位的填入，以及無痕排除。測試服務僅提供本機測試網域的假帳密，從不使用使用者帳號。GoogleLoginPolicyTest 驗證確切 HTTPS Google 入口、仿冒網域／連接埠／登出排除，以及交由原網站重新開始登入的邊界。一般既有回歸與正式 R8 最佳化版檢查也必須通過。
