# 澄境瀏覽器 · Android 自用版

以 Android Chromium WebView 瀏覽網站，透過天眼選取網站元件、預覽移除並保存網域規則。純自用；沒有上架、發布或建立遠端公開原始碼庫。

## 使用方式

1. 安裝 `release/ChengJing-Browser-0.1.0-Android.apk`，Android 9 或以上。
2. 開啟網站，按底部「天眼」→「開啟天眼・選取元件」。
3. 點中元件。若只選到文字或小叉叉，按「選取外面一層」。
4. 按「預覽移除」，確認文章仍正常後按頂部「儲存」。按 × 會取消未儲存的預覽。
5. 底部盾牌切換「例外」，會重新載入原始網站；再按一次恢復規則。天眼面板也能復原上次儲存。

首頁附有本機練習場，可以測試移除、同網域第二頁與例外切換。

## 首版功能

- 分頁、網址／Google 搜尋、上一頁／下一頁、下載、書籤、瀏覽紀錄、頁面文字搜尋、電腦版切換、檔案上傳與影片全螢幕。
- 淺色、深色、跟隨系統；沿用澄境的暖白與墨綠配色。
- 網頁結構邊框、父層選取、浮動／隱藏元件清單、整塊 iframe 移除、開放式 Shadow DOM、動態新增元件持續套用。
- 規則按可註冊網域及其子網域套用，使用 Public Suffix List 避免把不同 `github.io` 使用者混成一個網站。
- 可按網域開啟跳轉／彈窗防護，另外支援自訂 CSS、JavaScript 與恢復捲動。
- OpenRouter 金鑰使用 Android Keystore AES-GCM 加密。預設 DeepSeek、Gemini、GPT，支援更新模型清單及手動指定。
- AI 僅在使用者要求時取得結構摘要，回傳有範圍限制的元件規則；先驗證、預覽再儲存。AI 不會直接寫任意 JavaScript。
- Chrome 書籤 HTML 匯入與匯出、資料夾、搜尋、編輯、去重；Google Drive 書籤同步程式已接入。

## 目前限制

- 這是可安裝的第一版，自用瀏覽器，不等於完整 Chrome：沒有 Chrome 密碼／帳號同步、擴充功能商店、Chrome 全部網站權限與 DRM 相容性。
- 目前網站相機、麥克風、定位請求不開放。部分 Google 登入或金融網站可能拒絕 WebView；不冒充已支援。
- 規則處理可存取的 DOM / CSS 結構。跨網域 iframe 可整塊選取，無法逐一選取其內部；封閉 Shadow DOM 與 Canvas 內部也不是一般 DOM 元件。
- 元件的 ID／class／層級若真的改變，規則可能需要重選；位置型選擇器在兄弟節點重新排序時可能不準。不能保證所有網站永不失效。
- 隱藏元件不等於停止該網站所有程式或資源下載。跳轉防護是另一個網域選項，不是萬能的惡意網站沙箱；伺服器正常轉址仍保留。
- 自訂 JavaScript 在每次文件載入後執行；網站 CSP 可能限制它。例外會停止注入並重新載入，無法撤銷程式之前已送出的網路操作。
- **Google Cloud 專案與 Drive API 已建立；OAuth 設定與真實帳戶同步尚未完成**，請見 [Google 設定](docs/GOOGLE-SETUP.md)。不會把授權畫面或模擬資料測試當作真實同步通過。
- **OpenRouter 真實模型呼叫未驗證**：尚未輸入自用 API Key。已驗證規則解析與危險／無效輸出拒絕流程。

Chrome 書籤匯入採 [Chrome 官方 HTML 匯出](https://support.google.com/chrome/answer/96816)；亦可使用 [Google 匯出資料](https://support.google.com/chrome/answer/10248834) 中的書籤 HTML。Google Drive 只申請 [drive.appdata](https://developers.google.com/workspace/drive/api/guides/appdata)。

## 本機開發

Android SDK 36、Android Studio 內建 JDK 21、Gradle wrapper 8.14.3。使用者無須自行拼貼程式碼，完整專案在此目錄。

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
python3 scripts/build-private.py
```

`signing/` 是本機自用簽章，權限 600，不納入版本控制。請保留它，未來同一個 App 更新需要相同簽章。請不要把這個資料夾公開分享。OpenRouter 的金鑰和 Google access token 不放進原始碼。
