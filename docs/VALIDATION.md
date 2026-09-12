# 驗證紀錄 · 2026-09-12

## 已通過

- 15 個單元測試：網域邊界／私人託管網域、網址輸入、禁止整頁移除、AI 輸出限制、Chrome HTML 資料夾與特殊字元、匯出再匯入、去重、不同裝置合併、刪除不復活、快照格式與其他 App 資料拒絕。
- Android 16（API 36）模擬器實際 WebView／Compose 操作。開發版 3 組與正式簽章 release APK 的 4 組操作測試均通過。
- 實際點選元素、改選外層、按下預覽移除／儲存，前往內容已改變的第二頁仍移除；例外顯示原始網站、關閉例外恢復規則、重新載入保留、復原上一版。
- 開放 Shadow DOM 選取、取消預覽恢復、動態新增與重新插入的節點持續移除。
- 自訂 CSS／JavaScript 套用；例外重新載入後停止自訂程式碼。結構摘要不含測試表單輸入值。
- 透過真實 Android WebView 攔下 `window.open` 非使用者觸發彈窗與 JavaScript 無點擊跳轉。
- 淺色／深色介面、書籤與 Google 連線面板存在；實際開啟公開 `https://example.com/` 並讀回標題。
- 在正式 APK 中將隔離測試金鑰加密、讀回、清除；已確認儲存值不是明文，網站橋接沒有金鑰寫入方法。
- Android 原生網路取得 OpenRouter 最新模型清單，三個系列都有有效結果。
- 正式 APK 通過 Android 簽章驗證，簽章 SHA-1 為 `4B:60:AD:7B:7A:D1:00:61:27:FA:28:CE:7D:EC:DA:2D:69:D3:36:97`。
- 實際畫面：`qa/screenshots-final/`。已檢視暖白首頁、墨綠首頁、天眼框線、移除後第二頁與書籤面板。

## 未完成／未驗證

- Google 已完成原生帳戶授權、真實 Drive 讀回與兩份邏輯裝置快照合併；尚未使用兩支實體 Android 手機驗證跨機同步。
- OpenRouter 付費模型的真實 AI 推論尚未呼叫；沒有使用者提供的 API Key。已通過的是清單連線、加密與規則處理，不是模型推論。
- Chrome HTML 解析與匯入結果已通過；Android 系統檔案挑選器的手動選檔尚未實機驗證。
- 跨網域 iframe 整塊選取已實作，尚未完成 release APK 的專門操作測試；封閉 Shadow DOM／Canvas 內部不可逐一選取。
- 沒有實體 Android 手機連線；上述裝置證據來自 Android 官方模擬器。
- 網站全部權限、Chrome 全功能相容、DRM、惡意網站刻意對抗、所有廣告系統均沒有完整涵蓋。

## 可重現證據

- `qa/test-run-3.log`：debug 3 組實際操作通過。
- `qa/release-tests.log`：release 4 組實際操作通過。
- `app/build/test-results/testDebugUnitTest/`：15 個單元測試報告。
- `app/build/outputs/androidTest-results/connected/release/`：正式 APK 測試結果。
- `qa/release-build-final.log`：安裝包建置。

正式版最後另加入 `singleTask`，讓外部連結交給既有瀏覽器而非開啟重複的 Activity；該 Manifest 修改後重新建置、驗證簽章與安裝版本，沒有把它冒稱成前一輪 UI 測試的內容。


## 0.1.1 Google 真實同步驗證

2026-09-12 18:55–18:56，正式簽章的 0.1.1 release APK，Android API 36 模擬器，Google 帳戶 `coyoter@coyoter.com`。

- 使用者明確同意 Google API 資料政策後，完成 Google 品牌、Android OAuth 客戶端、自用測試名單與唯一 `drive.appdata` 範圍設定，均經 Console 寫入讀回。
- 原生 Google 帳戶選擇與同意畫面成功，沒有嵌入或輸出 access token。
- `GoogleDriveLiveTest` 通過：原生 UI 授權、首次同步、新增隔離測試書籤後自動上傳並讀回、第二份獨立裝置快照寫入真實 Drive 後由原生 App 合併、雙方的測試書籤刪除並同步，以及 Activity 重建後靜默授權與 Drive 回讀。
- 第二個裝置採不同 device ID、不同 Drive 檔案的邏輯裝置，使用正式 `BookmarkDrive` 網路程式，並非第二支實體手機。不將這項結果描述為實體雙機測試。
- 測試開始與結束的可見書籤 ID 集合相同；測試只操作自己建立的兩筆書籤。刪除紀錄留在 appDataFolder，維持正常防復活行為。
- `qa/google-live-result.log`：真實測試通過，69.896 秒。
- `qa/google-live-evidence.log`：四段實際成功事件，不含 token。
- `qa/google-live-screenshots/`：連結、合併兩筆測試書籤、重新開啟後完成同步。
- 18 個本機單元測試通過，含新增的有上限資料讀取測試；將 API 33 的 `InputStream.readNBytes` 改成適用 Android 9+ 的讀取方式。API 36 上的真實 Drive 網路驗證亦使用此新方式。
- 同步將帳戶識別在合併資料前綁定，防止上傳中斷後錯把另一個帳戶的資料混入；等待 Google 同意畫面時不會重複發起授權。
- `GoogleDriveLiveTest` / `GoogleSetupProbe` 後續預設略過，只能透過 `liveGoogle=true` / `probeGoogle=true` 明確啟用，避免一般測試觸碰真實 Google 資料。
