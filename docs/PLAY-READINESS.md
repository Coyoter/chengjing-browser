# Google Play 發行準備 — 1.2.0

截至 2026-09-13：使用者要求公開發行，地區為 Google Play 支援的所有地區。應用已建立（4974084381611231005）；尚未上傳 AAB、送審或上架。隱私政策、政府、金融、健康與應用內容聲明已儲存，繁中商店素材和新版說明已顯示「已可送審」。初始工作 7/11 完成；正式版所有 177 個可選國家／地區已指定。另完成未使用 AD_ID 聲明。資料安全表已填妥存草稿，需先完成登入詳細資料與目標對象才能正式保存。

## 完成的成品

- 天眼的網站 AI 與元件 AI 分流；新增元件 CSS／JS／HTML、編輯內部 HTML、移除元件、程式碼檢視、一鍵套用及上一份設定復原。
- Google 書籤同步可另開啟天眼網站設定同步；同一網站以較新的完整設定為準，空設定保存為刪除狀態。憑證例外、暫時例外、API Key 不加入同步。
- 每次 AI 分析前的資料同意、OpenRouter data_collection=deny 路由、網站間設定隔離；不把其他網站的程式碼交給目前網站的即時更新呼叫。
- 繁中與英文商店說明、512px 圖示、1024×500 主視覺與實際 Android 截圖位於 store/。預定全球發布。英文說明明示介面目前使用繁體中文。
- 新版本相依元件的授權資訊及 Apache／MIT 授權全文隨 App 提供。
- 公開產品頁 https://techtarian.com/chengjing-browser/ 與隱私政策 https://techtarian.com/chengjing-browser/privacy/ 已發布、REST 讀回並於瀏覽器讀取確認；保留既有網站 Header／Footer。
- Google OAuth 的品牌與政策網址已保存，發布狀態改為「實際運作中」。實際資料存取權頁只列非機密 drive.appdata；沒有新增其他 Drive 權限。

## 現在的上架關卡

使用者已在法律聲明與既有簽章匯入步驟明確回覆「都同意」，已完成建立應用程式。既有簽章加密匯入 Google 的授權仍有效，不需重複確認。

Play 加密公開金鑰尚未成功下載，已請使用者將指定 encryption_public_key.pem 保存至下載項目；取得後使用 PEPK 匯出加密 ZIP。不得改用新簽章導致側載版無法更新。審核專用 Google 帳號尚待提供，不可提交開發者私人帳密。Gemma 已提供不需 API Key 的本機 AI 路徑。

永久網站憑證例外仍依使用者要求保留，預設關閉、明確手動啟用、持續警示。此功能不等於獲 Google 安全核准，可能觸發 WebView 安全審查；需如實對審核方說明，不隱藏功能或宣稱異常憑證已通過驗證。參考 https://support.google.com/faqs/answer/7071387 。

建立後仍須在實際 Console 完成 App Signing、套件登記、資料安全表、分級與目標對象、商店素材、地區及正式發行審核。尚未把準備完成誤報為已上架。

## 目前實際阻礙

- IARC 內容分級頁明示填寫即同意其服務條款；已在該步驟提出新條款同意問題，尚待使用者回覆。此前 Developer Program／出口声明與既有私鑰匯入 Google 的「都同意」已履行並持續有效，不需重問。
- 專用 Google 審核帳號尚未提供，登入說明已備妥；不傳送私人帳密或未授權的 OpenRouter 金鑰。Gemma 提供不需要 API Key 的 AI 路徑，選用 Google 同步仍須有測試帳號。
- 公開加密金鑰下載在原頁與新頁均未成功取得檔案。Google 官方 PEPK 已就緒；仍需指定 encryption_public_key.pem。Chrome 內部下載頁曾遭工具安全審查拒絕，未繞過限制。
- 目前沒有上傳 AAB、建立發布版本、送審或公開上架。英文介紹檔已準備，但尚未在 Console 加入英文語系。

## 公開政策存取

Google Console 對 WordPress 的刪除資料網址回報 403，雖然一般 HTTP 請求及瀏覽器可讀取。為此單獨部署純靜態公開說明 https://chengjing-browser-policy.coyoter.workers.dev/ ，沒有改動科技人網站的防護規則。Google 網址檢查已接受新連結並允許進入後續資料類型步驟；Play 隱私政策連結也已更新並確認保存。公開頁包含 App 的完整隱私文字及明顯的資料刪除步驟，完成公開 DOM 與畫面檢查。來源位於 store/public-policy/，部署設定 store/wrangler.jsonc 不含帳戶密鑰。

## 1.2.0 交付驗證

- 54 項 JVM 測試零失敗，release lint 通過。
- 原生 Gemma 模型下載、SHA-256 核對、重建 Activity 保留狀態以及真實本機推理後套用 DOM 通過。
- 網址列建議與清除、網站圖示顯示與請求隱私通過；全螢幕系統列隱藏及 Android 返回退出單獨測試通過。UiAutomator 在部分連續測試的注入回傳 false；沒有把那些失敗執行當成通過。最終通過證據 qa/fullscreen-input-inspect.log。新測試保留為 BrowserEnhancementsTest，原 BrowserPolishTest 完整保留。
- APK 套件 tw.techtarian.browser，versionCode 18、1.2.0；原簽章一致。所有內附 .so 的 ELF LOAD 對齊均至少 16 KB，APK zipalign -P 16 驗證通過。
- APK、AAB、完整原始碼 ZIP 與 SHA256SUMS 已複製至 /Volumes/外接硬碟/Google Drive/安裝包 並核對本機檔案雜湊；不代表 Drive 雲端上傳狀態或實體手機驗收。
