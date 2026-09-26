# Google 同步恢復與網站定位

## 已確認的原因

舊版 Drive 傳輸只設定 40 秒整體請求時間，讀取仍使用 OkHttp 預設的 10 秒。讀取／整體逾時後直接結束該次同步，沒有應用層重試；例外的 localizedMessage 與 toString 直接出現在主要狀態及紅字。重試開始和停止同步時也沒有清除舊 details。每次 Activity resume 均可能跑完整同步，無資料變更仍上傳各裝置快照。

截圖只能確認 InterruptedIOException timeout，無法僅憑截圖判定手機當時是網路波動、Google 回應延遲或特定請求超時。這次針對已確認的傳輸與狀態處理缺口修正，沒有宣稱辨識了使用者手機上的單一網路原因。

定位原先未宣告手機定位權限，WebChromeClient 也一律拒絕網站請求並顯示「此版本尚未開放網站定位」。

## 修正範圍

- 連線 15 秒、讀／寫 30 秒、單次 call 60 秒。GET 與相同內容的 PATCH 最多嘗試三次，等待 1、2 秒加少量隨機值；遵守 Retry-After，超過 30 秒的伺服器等待交由下次同步。403 僅在明確限流原因時重試，401、權限拒絕、憑證錯誤及格式問題不盲目重送。
- 新建快照的 POST 不重播，避免回覆丟失時建立重複檔案。下一次完整同步仍先列出雲端快照，辨識自己的檔案後才決定建立／更新。取消會停止正在傳輸的請求與重試等待。
- 自動 resume 最快相隔 30 秒；近期已成功時在 60 秒內不重跑。「立即同步」及本機編輯觸發的同步不受此冷卻限制。已讀回且內容一致的快照不再上傳；變更仍需上傳後回讀驗證，不提前更新最後成功時間。
- 主要提示使用繁體中文，暫時連線問題不當成帳戶故障顯示紅字；真正需要處理的授權、權限、資料驗證問題仍清楚標示。保留帳戶綁定、刪除記錄、同步期間的本機編輯與上次完成時間。
- 定位權限按完整來源（協定、主機、連接埠）區分，只接收目前前景分頁的安全來源請求；HTTPS 與本機 loopback 可用。拒絕跨來源、過時文件、憑證異常與已開啟憑證例外的來源。未新增相機、麥克風或背景定位權限。
- 網站同意後才請求 Android 精確／概略定位，兩者任一獲准即可；Android 拒絕、定位服務關閉、分頁關閉或換頁時不提供座標。網站資訊可修改站點授權，修改後重新載入相同來源的相關分頁，以終止舊的定位追蹤。
- 一般與無痕授權分開。定位決策不進 Google 同步；無痕決策只在記憶體，關閉最後一個無痕分頁或結束 Activity 即清除。App 不自行讀取或保存座標，WebView 在取得同意後交給請求網站。

## 驗證

DriveTransportTest 以實際本機 HTTP 伺服器重現讀取超時、限流、伺服器失敗、不可重播的建立、取消及權限分類。SyncRecoveryTest 用同一套 Drive REST 路徑及快照格式驗證完整同步恢復、內容回讀、避免重複上傳、最後成功時間與帳戶隔離。沒有使用真實 Google 帳戶或讀寫使用者雲端內容。

WebsiteLocationTest 使用真正 WebView 的 navigator.geolocation、網站確認對話框、Android 權限對話框和模擬 GPS 座標，驗證拒絕／允許、座標回傳、撤回、來源隔離及無痕清除。獨立 R8 測試宿主另外操作不可偵錯的最佳化 APK，確認授權與座標回傳在正式組態仍可運作。這是 Android 模擬器驗證，不等同使用者原手機的衛星收訊或 Google 帳戶實測。

## 參考

- [Google Drive 錯誤與重試指引](https://developers.google.com/workspace/drive/api/guides/handle-errors)
- [OkHttp 4.12.0 請求逾時設定](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/OkHttpClient.kt)
- [Android 定位權限](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [WebChromeClient 定位回呼](https://developer.android.com/reference/android/webkit/WebChromeClient#onGeolocationPermissionsShowPrompt(java.lang.String,%20android.webkit.GeolocationPermissions.Callback))
