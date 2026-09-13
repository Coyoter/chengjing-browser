# Google Play 發行準備 — 1.1.0

截至 2026-09-13：使用者要求公開發行，地區為 Google Play 支援的所有地區。應用程式尚未在 Google Play 建立／上傳／送審。已在建立頁填入澄境瀏覽器、tw.techtarian.browser、繁體中文、免費、應用程式；尚未勾選法律聲明。

## 完成的成品

- 天眼的網站 AI 與元件 AI 分流；新增元件 CSS／JS／HTML、編輯內部 HTML、移除元件、程式碼檢視、一鍵套用及上一份設定復原。
- Google 書籤同步可另開啟天眼網站設定同步；同一網站以較新的完整設定為準，空設定保存為刪除狀態。憑證例外、暫時例外、API Key 不加入同步。
- 每次 AI 分析前的資料同意、OpenRouter data_collection=deny 路由、網站間設定隔離；不把其他網站的程式碼交給目前網站的即時更新呼叫。
- 繁中與英文商店說明、512px 圖示、1024×500 主視覺與實際 Android 截圖位於 store/。預定全球發布。英文說明明示介面目前使用繁體中文。
- 112 個相依元件的授權資訊及 Apache／MIT 授權全文隨 App 提供。
- 公開產品頁 https://techtarian.com/chengjing-browser/ 與隱私政策 https://techtarian.com/chengjing-browser/privacy/ 已發布、REST 讀回並於瀏覽器讀取確認；保留既有網站 Header／Footer。
- Google OAuth 的品牌與政策網址已保存，發布狀態改為「實際運作中」。實際資料存取權頁只列非機密 drive.appdata；沒有新增其他 Drive 權限。

## 現在的上架關卡

建立應用程式頁要求開發者作出「確認應用程式符合 Developer Program 政策的所有規定」及「接受美國出口法律」兩項聲明。依瀏覽器工具對法律承諾的確認要求，取得使用者當下明確同意前不代勾選或建立。

現有 APK 簽章需維持，才能讓原側載版直接更新。後續 Play App Signing 如要求匯入既有簽章私鑰，應使用 Google 指定的加密程序，並先取得對此私鑰及 Google 接收目的的明確授權；不偷偷改用不同簽章造成無法覆蓋更新。

永久網站憑證例外仍依使用者要求保留，預設關閉、明確手動啟用、持續警示。此功能不等於獲 Google 安全核准，可能觸發 WebView 安全審查；需如實對審核方說明，不隱藏功能或宣稱異常憑證已通過驗證。參考 https://support.google.com/faqs/answer/7071387 。

建立後仍須在實際 Console 完成 App Signing、套件登記、資料安全表、分級與目標對象、商店素材、地區及正式發行審核。尚未把準備完成誤報為已上架。
