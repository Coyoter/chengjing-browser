# Google Play 發行準備 — 1.2.0

截至 2026-09-13：使用者要求公開發行，地區為 Google Play 支援的所有地區。應用已建立（4974084381611231005）；尚未上傳 AAB、送審或上架。隱私政策、政府、金融、健康與應用內容聲明已儲存，商店素材和說明仍在草稿。

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
