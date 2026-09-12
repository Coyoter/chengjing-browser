# 澄境瀏覽器 · Android 自用版

以 Android Chromium WebView 瀏覽網站，透過天眼選取網站元件、預覽移除並保存網域規則。純自用；沒有上架、發布或建立遠端公開原始碼庫。

## 使用方式

1. 安裝 `release/ChengJing-Browser-0.1.3-Android.apk`，Android 9 或以上。
2. 開啟網站，按底部「天眼」，直接進入選取模式。
3. 點中元件。若只選到文字或小叉叉，按「選取外面一層」。
4. 按「預覽移除」，確認文章仍正常後按頂部「儲存」。按 × 會取消未儲存的預覽。
5. 底部盾牌切換「例外」，會重新載入原始網站；再按一次恢復規則。天眼面板也能復原上次儲存。

首頁附有本機練習場，可以測試移除、同網域第二頁與例外切換。天眼的規則、AI、自訂程式碼入口在選單的「天眼設定」。

0.1.2：選取期間由原生瀏覽器接管觸控，不將點擊傳入網站／iframe；瀏覽器另阻止該分頁的跳轉、新視窗和 POST 導航，離開選取後恢復正常操作。網址列視覺高度 44 dp、上下對稱，分頁數字按實際筆畫置中；App 圖示改為澄境家族的指南針。

## 首版功能

- 分頁、網址／Google 搜尋、上一頁／下一頁、下載、書籤、瀏覽紀錄、頁面文字搜尋、電腦版切換、檔案上傳與影片全螢幕。
- 淺色、深色、跟隨系統；沿用澄境的暖白與墨綠配色。
- 網頁結構邊框、父層選取、浮動／隱藏元件清單、整塊 iframe 移除、開放式 Shadow DOM、動態新增元件持續套用。
- 規則按可註冊網域及其子網域套用，使用 Public Suffix List 避免把不同 `github.io` 使用者混成一個網站。
- 可按網域開啟跳轉／彈窗防護，另外支援自訂 CSS、JavaScript 與恢復捲動。
- OpenRouter 金鑰使用 Android Keystore AES-GCM 加密。預設 DeepSeek、Gemini、GPT，支援更新模型清單及手動指定。
- AI 僅在使用者要求時取得結構摘要，回傳有範圍限制的元件規則；先驗證、預覽再儲存。AI 不會直接寫任意 JavaScript。
- 主選單「匯入 Chrome 書籤」可選取 HTML；保留資料夾、去重，完成後直接顯示書籤。支援匯出、搜尋與編輯；Google Drive 書籤同步已接入並完成真實雲端讀回。

## 目前限制

- 這是可安裝的第一版，自用瀏覽器，不等於完整 Chrome：沒有 Chrome 密碼／帳號同步、擴充功能商店、Chrome 全部網站權限與 DRM 相容性。
- 目前網站相機、麥克風、定位請求不開放。部分 Google 登入或金融網站可能拒絕 WebView；不冒充已支援。
- 規則處理可存取的 DOM / CSS 結構。跨網域 iframe 可整塊選取，無法逐一選取其內部；封閉 Shadow DOM 與 Canvas 內部也不是一般 DOM 元件。
- 元件的 ID／class／層級若真的改變，規則可能需要重選；位置型選擇器在兄弟節點重新排序時可能不準。不能保證所有網站永不失效。
- 隱藏元件不等於停止該網站所有程式或資源下載。跳轉防護是另一個網域選項，不是萬能的惡意網站沙箱；伺服器正常轉址仍保留。
- 自訂 JavaScript 在每次文件載入後執行；網站 CSP 可能限制它。例外會停止注入並重新載入，無法撤銷程式之前已送出的網路操作。
- **Google 登入與真實 Drive 書籤同步已驗證**：目前自用測試名單為 `coyoter@coyoter.com`。已驗證原生授權、真實上傳／讀回、第二份裝置快照合併、刪除與 App 重開後再次同步。第二裝置是同一模擬器內獨立的邏輯裝置快照，尚未使用兩支實體手機。詳見 [Google 設定](docs/GOOGLE-SETUP.md)。
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


## 0.1.3 補充

- 單一數字「1」按實際筆畫濃淡的橫向重心校正；不只使用字形外框。垂直位置維持，其他數字沿用原本排版。
- Chrome 書籤匯入入口在主選單，不必先進書籤頁。先使用 Chrome 的書籤匯出功能取得 HTML，再在澄境選取該檔案；某些 Android 檔案選擇器選中檔案後還需按「選取／Select」。匯入成功會直接顯示書籤。
- Google 書籤同步使用原生 GMS；這不代表 google.com、YouTube 等網頁也已登入。網頁的原生一鍵登入尚未加入，見 [Google 網頁登入調查](docs/GOOGLE-WEB-LOGIN.md)。
