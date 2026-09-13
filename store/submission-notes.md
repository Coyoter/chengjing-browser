# 澄境瀏覽器送審資料

名稱：澄境瀏覽器 / ChengJing Browser
套件：tw.techtarian.browser
版本：1.2.0（18）
價格：免費。OpenRouter 是使用者自行連結的外部服務，可能另行收費。
發布地區：依使用者指示，Google Play 支援的所有地區。
主要語言：繁體中文；另備英文商店說明，未宣稱 App 介面已有英文翻譯。
開發者：Coyoter
公開支援：admin@techtarian.com
首頁：https://techtarian.com/chengjing-browser/
隱私及資料刪除方式：https://chengjing-browser-policy.coyoter.workers.dev/

## 審核操作方式

一般瀏覽、分頁、書籤與天眼編輯不需要登入。從設定 → 瀏覽 → 天眼練習場開啟本機示範；打開天眼，點選元件，即可查看新增、編輯、移除與 AI 檢查入口。網站 AI 入口不需要先選元件。

選用本機 Gemma 4 E2B 不需要 API Key，須下載約 2.59 GB 並有足夠裝置資源；選用 OpenRouter 需要使用者自己的金鑰與服務額度；不提供開發者的私人金鑰給審核團隊。介面會提示設定與每次分析資料同意。模型結果不會偷偷執行，按「一鍵套用建議」才保存與重載，可從天眼設定復原上一份儲存。

選用 Google 同步使用手機 GMS 原生授權，只要求 drive.appdata。書籤同步之外，可自行開啟天眼網站設定同步；沒有本應用伺服器上的獨立帳戶註冊。

網頁使用 Android System WebView；未內建或自行更新 Chromium 二進位內核。沒有 REQUIRE_INSTALL_PACKAGES 權限或 APK 自動更新器。憑證例外是手動、永久、特定 HTTPS 主機的設定，預設關閉並持續顯示警示；Google／OpenRouter 的原生 API 請求持續驗證 TLS。

## 資料安全表填寫依據

這是根據程式資料流整理的填表資料，尚未提交表單。

- 使用者選用 Google 同步：帳戶電子郵件／識別碼、書籤網址與名稱、資料夾、同步裝置識別碼；另選用天眼同步時包括網域、修改記錄與自訂程式碼。用途為登入驗證與 App 功能。不是必要才能瀏覽。
- 使用者選用 OpenRouter 雲端 AI：目前網域、有限結構、自訂程式碼、使用者需求；用途為 App 功能。可能對應網站瀏覽資訊及其他使用者產生的內容。不得因資料直接送 Google 或 OpenRouter 就誤填完全沒有收集。
- 書籤／網站資訊及程式碼的雲端副本不是短暫處理。OpenRouter 請求採 data_collection=deny，但不能把 OpenRouter 帳戶記錄一律宣稱為零保留。
- 一般開放網頁導覽依 Google 的 WebView 說明另行區分；本應用主動送往 AI 或同步的資料仍需申報。
- 對第三方的傳輸在每次 AI 分析同意及選用 Google 同步時告知；官方表單對使用者主動發起或顯著告知並同意的傳輸，另有 sharing 定義例外。依實際表單與資料流逐項核對，不把「沒有開發者伺服器」當成豁免。
- 本應用對 Google／OpenRouter 的收集傳輸使用有效 HTTPS 驗證。一般網站的 HTTP 或使用者憑證例外，不被描述為已通過安全驗證。
- 刪除方式在應用與公開政策中提供；停止同步保留資料，清除 App 資料與刪除 Drive 的隱藏應用資料是分開動作。

官方填表依據：https://support.google.com/googleplay/android-developer/answer/10787469
核心 GMS SDK 說明：https://developers.google.com/android/guides/play-data-disclosure

## 尚待實際 Console 完成

正式 App Signing 與套件登記、資料安全表送出、IARC 分級與目標對象、上傳素材、正式發行送審。簽章與 Google 同步須以最終 Play 配送簽章核對，不能直接把本機簽章測試當成 Play 配送已驗證。

1.2.0 的 Gemma 分析只在裝置處理，不傳送提示或網站結構至雲端；模型下载來自 Hugging Face，需說明網路請求。網站圖示直連網站根目錄，網址列建議完全由本機歷史產生。

Console 已儲存五類可選收集資料：電子郵件、使用者 ID、網站瀏覽資訊、其他使用者原創內容及同步裝置 ID。非暫時處理，用途為 App 功能；電子郵件與使用者 ID 另用於帳戶管理。依使用者主動選用與明確同意的傳輸例外，未列為分享。內容填妥但目標對象被登入資料前置條件阻擋，因此資料安全表僅保存草稿，尚未正式送審。
