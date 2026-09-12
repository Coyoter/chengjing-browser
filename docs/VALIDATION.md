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
- 0.1.3 已用 Android 模擬器的系統檔案挑選器完成真實 HTML 選檔、匯入、去重及取消操作；使用者實體手機的個別檔案管理器尚未驗證。
- iframe 整塊選取已通過 0.1.2 的原生操作測試；跨網域內部、封閉 Shadow DOM／Canvas 內部不可逐一選取。
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


## 0.1.2 手機操作回饋修正

- 底部天眼一次點擊直接進入選取，沒有開啟設定面板；設定移至選單，已保存規則與預覽流程維持。
- 觸控由 `SelectionWebView` 接管，`pickAt` 只做位置查詢，沒有合成或轉送網頁點擊。捲動與滑動慣性仍保留，停用系統雙擊辨識避免快速連續選取漏選。
- 原生測試在 window capture 階段安裝 touchstart / touchend / pointerdown / pointerup / mousedown / mouseup / click 處理器，點選連結、一般按鈕、POST 提交按鈕與 iframe 後，接收到的網站輸入事件數皆為 0，沒有新增分頁或改變網址。
- 額外驗證 JavaScript 新視窗、背景 location 導航、直接 `form.submit()` 的 POST 導航被攔截，原文件與選取狀態仍存在；退出天眼後按鈕恢復正常可點擊。
- 網址列 44 dp，頂部工具列上下留白相同；分頁框與網址列中心線一致。從實際畫面像素驗證「1」「12」筆畫的上下／左右中心誤差不超過 1 px，包含深色狀態。
- 新圖示採墨綠漸層、翡翠環線、暖白指南針；更新首頁標誌與 Android adaptive launcher icon。已輸出並查看安裝資源實際繪製結果。
- API 36 模擬器，release 設定的隔離 QA 套件 `tw.techtarian.browser.qa` 通過 6 組操作測試（2 組本次修正 + 4 組既有操作）。隔離套件避免測試變動既有自用版的 Google／書籤資料；不代表已操作使用者的實體手機。
- 證據：`qa/polish-release-tests.log`、`qa/polish-final-screenshots/`，正式安裝包另以原本套件名和同一簽章建立。

正式 0.1.2 APK 已驗證為 `tw.techtarian.browser`、versionCode 3，原簽章不變，並於模擬器覆蓋安裝成功。已複製至 `/Volumes/外接硬碟/Google Drive/安裝包/ChengJing-Browser-0.1.2-Android.apk`；來源與目的地的 SHA-256 完全一致，證據為 `qa/polish-copy-verification.json`。此項只代表指定本機資料夾的複製完成，不推定 Google Drive 桌面同步程序已上傳。


## 0.1.3 視覺重心與 Chrome 書籤匯入

使用者指出「1」看起來偏右後，撤回以字形外框中心作為該數字視覺驗收的依據。放大截圖的外框約 x=466–905、字形外框約 x=662–721，主要筆畫的橫向重量中心更靠右。JPEG 與放大比例使數值只適用該張圖的比較，不換算為實體手機上的精確偏移。

- 針對單一「1」以透明遮罩中實際筆畫 alpha 加權計算橫向重心，垂直排版保持原樣。以實際顯示像素驗證淺／深色的「1」重心與框中心誤差小於 1 px，兩位數排版仍通過原本的幾何置中驗證。
- 主選單加入「匯入 Chrome 書籤」，副文字說明需 Chrome 匯出的 HTML。匯入完成直接開啟書籤，重複匯入會顯示不重複新增的訊息。
- `BookmarkImportJourneyTest` 在隔離 QA 套件中建立測試 HTML，透過真正的 Android DocumentsUI 開啟檔案、按 Select、確認匯入，驗證兩筆書籤、巢狀資料夾、HTML 特殊字元、重複匯入與取消選檔。測試結束恢復原本的可見書籤 ID 集合。
- Release 設定的隔離套件於 API 36 模擬器通過 3 組測試：視覺重心／工具列、天眼操作回歸、系統檔案匯入流程。沒有使用自用套件的 Google 帳戶／書籤資料。
- 證據：`qa/import-optical-release-tests.log`、`qa/import-optical-final/`。
- Google 網頁一鍵 GMS 登入未加入；App 原生同步授權和網站登入狀態分開。詳見 `docs/GOOGLE-WEB-LOGIN.md`。

正式 0.1.3 APK 已以原本套件 `tw.techtarian.browser`、versionCode 4 及同一簽章建立並覆蓋安裝。已複製至 `/Volumes/外接硬碟/Google Drive/安裝包/ChengJing-Browser-0.1.3-Android.apk`，來源／目的地 SHA-256 一致（`qa/import-optical-copy.json`）。


## 0.1.4 憑證異常保留網頁

- 修正 `onReceivedSslError` 把任何資源憑證錯誤都轉為整頁故障的行為。
- 按使用者明確自用指示，僅 WebView 的可恢復憑證錯誤使用 `proceed()` 繼續載入，同時記錄異常；不把這種連線當成憑證驗證成功，不改動 Google Drive／OpenRouter 的原生 HTTP 用戶端。
- 網址列顯示警示圖示，點擊可見來源與原因。修正通知清空時會取消自己的 Snackbar，讓點擊後的內容能正常顯示。
- 程序內記錄主頁與異常資源關係，涵蓋 WebView 快取的略過決策。背景分頁的提示透過主執行緒更新，不等待該 WebView 掛到畫面；切換到不相關的正常網站會回到正常狀態。
- 本機自簽 TLS 測試伺服器搭配 Android API 36 模擬器，release 設定的隔離套件通過 2 組實際測試：異常主文件載入／重新整理／Activity 重建／點擊警示，以及有效 HTTPS 主頁載入異常憑證圖片、快取圖片在另一個有效網站載入後的警示、正常網站恢復正常。
- 圖片測試確認 `naturalWidth > 0`，不是只確認網頁未被替換。5 個憑證警示資料測試也通過。
- 證據：`qa/certificate-release-final.log`、`qa/certificate-final-screenshots/`、單元測試 `CertificateWarningsTest`。
- 測試只使用隔離 QA 套件與本機自簽憑證；沒有更改手機／電腦的系統信任根。測試伺服器與 adb reverse 在測試後關閉。
- TLS 實際連線測試後續需明確帶 `tlsFixture=true` 才會執行，避免一般測試依賴本機伺服器。

可重現：執行 `python3 scripts/test-tls-server.py`，設定 `adb reverse tcp:18743 tcp:18743`，再以 `-PqaInstall=true -Pandroid.testInstrumentationRunnerArguments.tlsFixture=true` 執行 `CertificateContinuationTest`。測試完成後關閉伺服器並移除該 reverse。

[Android 官方 API 文件](https://developer.android.com/reference/android/webkit/WebViewClient#onReceivedSslError(android.webkit.WebView,android.webkit.SslErrorHandler,android.net.http.SslError)) 說明此回呼僅處理可恢復憑證錯誤，官方一般建議取消；本版繼續載入是使用者特別要求的自用行為，不將它描述為 Chrome 的標準安全策略。

正式 0.1.4 APK 已以原本套件與簽章覆蓋安裝並讀回 versionCode 5；已複製到 `/Volumes/外接硬碟/Google Drive/安裝包/ChengJing-Browser-0.1.4-Android.apk`，來源／目的地 SHA-256 一致（`qa/certificate-copy.json`）。


## 0.1.5 書籤層級、閱讀收藏與瀏覽操作

- 書籤依既有 folder 路徑建立顯示樹，不重寫書籤 ID、同步快照或刪除記錄。以 2,000 筆測試書籤驗證根層收合、巢狀導覽、延遲列表與跨資料夾搜尋。真實 DocumentsUI 匯入測試涵蓋巢狀路徑、去重與取消。
- 收藏使用獨立本機儲存，保存章節網址、頁內位置與命名；驗證更新同一筆、從下一章更新、重開 App 後仍連到同一筆收藏、重新開啟收藏恢復捲動，且書籤資料未改變。恢復位置會等待 WebView 掛入畫面，若使用者已開始操作則取消延遲捲動。
- 網址列第一次觸控進入時全選，直接輸入取代原網址，包含下方位置。設定上下配置後重建 Activity 仍保留；天眼按鈕直接進入選取。
- 彈窗／自動跳轉只更新小點和紀錄，不發出 Snackbar；實際觸控原本通知後方的測試按鈕仍可成功。主選單可查看本分頁計數與記錄。
- User-Agent 實際於 navigator.userAgent 讀回 Chrome 手機、原始 WebView 與自訂三種模式，確認儲存。觀察到測試裝置原始 UA 有 WebView 標記，且 Chrome 與 WebView 引擎版本不同；這些是可能影響網站呈現的因素，未取得使用者私密網站，不能判定其根因已修復。未強制更改網頁配色或注入全域排版 CSS。
- 下拉刷新透過原生 SwipeRefreshLayout 包住 WebView。以真正穿過父容器的觸控驗證頂端刷新、兩種網址列位置、短拉取消、從中段到頂端不刷新、天眼未儲存草稿不受影響。以 performance.timeOrigin 確認是否真的重載；完成／失敗／手動停止會結束刷新指示器。
- 下拉測試第一次的中段情境在 JS 已捲動、原生畫面尚未反映時過早送出手勢。改為等原生 WebView 確實可向上捲動後再送出，兩項測試通過，證據 `qa/pull-refresh-final.log`。
- 本次 QA 使用 API 36 模擬器上的隔離套件 `tw.techtarian.browser.qa`，不清除自用版帳戶與書籤；不代表使用者實體手機已完成驗收。收藏目前不納入 Google 同步。


最終回歸：31 項單元測試與 11 組 release 設定的 Android 畫面操作全部通過（`qa/v015-final-regression.log`）。涵蓋本次 7 項功能、Chrome HTML 匯入，以及原本天眼觸控／跳轉隔離、數字視覺置中與主題；未操作使用者實體手機。畫面存於 `qa/v015-final-screenshots/`。

刷新畫面補驗：`PullRefreshTest` 再以 Android 原生輸入事件持續拉動並截圖，2 組通過（`qa/pull-refresh-visual.log`）。已查看 `qa/v015-final-screenshots/ChengJing-0.1.5-QA/12-pull-refresh-bottom (1).png`，確認翡翠色刷新箭頭位於網頁區，上方天眼與下方網址列保持原位。

正式 0.1.5 APK 已驗證 package `tw.techtarian.browser`、versionCode 6、原本簽章不變，並完成模擬器覆蓋安裝與版本讀回。已複製至 `/Volumes/外接硬碟/Google Drive/安裝包/ChengJing-Browser-0.1.5-Android.apk`，來源／目的地 SHA-256 同為 `c5646b4247da23a296852ed781ed838f0b111be13c1a89709b914be054ac27bb`（`qa/v015-copy-verification.json`）。此項只驗證指定本機資料夾，未確認 Google Drive 桌面程式的雲端上傳狀態。


## 0.1.6 圖示留白

- 僅修改 Android 自適應圖示前景：圓環與指南針以中心等比例縮至 78%，背景與首頁品牌圖保持既有尺寸。
- 使用既有 `BrowserPolishTest#compactSymmetricChromeAndBrand`，在 API 36 模擬器的隔離 QA 套件執行，1 組通過（`qa/icon-spacing-release.log`）。
- 從 Android PackageManager 取得已安裝 APK 圖示並以系統 Drawable 繪製；已查看實際圖像 `design/icon-adaptive-preview.png`，確認圓環四周明顯留白及中心位置。不同手機可使用不同外框遮罩；未操作使用者實體手機。
- 正式 APK 使用原本簽章與套件 `tw.techtarian.browser`，versionCode 7／0.1.6；模擬器覆蓋安裝與版本讀回成功。
- 已複製到 `/Volumes/外接硬碟/Google Drive/安裝包/ChengJing-Browser-0.1.6-Android.apk`，來源／目的地 SHA-256 同為 `14fdd25204e5c020febcdfb24a5c13c692daa29f0df8a049e8256072254b3843`（`qa/icon-spacing-copy.json`）。僅確認指定本機資料夾複製，未確認 Google Drive 雲端上傳。


## 0.1.7 快速收藏、匯入入口與例外切換

- 天眼右側改為快速收藏／更新收藏進度，維持原觸控位置；例外切換移到主選單。Chrome 書籤匯入僅保留在書籤頁右上角。
- 確認的缺陷：原先 `exception()` 先 `stopEye()`，接著對所有分頁 `configure()`，最後才重載同網域。即使沒有正在選取，舊文件仍在卸載前重套兩次；不相關網域的現有頁面也收到套用要求。
- 以本機延遲資源網頁記錄卸載前的 configure 次數，原始 0.1.6 controller 在同一測試下讀到 2，預期不重寫待卸載文件的檢查失敗（`qa/exception-baseline.log`）。這是重複改寫缺陷的重現，並非宣稱已重現使用者實體手機的整個瀏覽器失聯。
- 修正分離「下一份文件的設定」與「目前頁面的套用」；例外切換先結束原生選取、停止舊載入與刷新指示，僅重新載入同網域。設定沒變時不重新註冊文件腳本；未進入天眼時一般導覽不再向舊文件多送一次停用與套用。其他網域現有 DOM 不受此次切換影響，但後續導航仍會使用最新設定。
- 官方 `addDocumentStartJavaScript` 說明腳本在後續載入的新文件開始時執行，且執行期間會阻擋該文件載入；因此不能把更新未來文件設定與重新操作即將卸載的 DOM 當成同一件事。[Android 官方文件](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set))。
- `ExceptionRecoveryTest` 使用隔離 QA 套件與僅測試期間存在的本機 HTTP 伺服器。修正版兩組測試通過（`qa/exception-fixed.log`）：六次例外切換、選取中快速切換、連結、網址輸入、新分頁、公開 Example Domain 與真實 Google 搜尋；快速收藏新增及更新同一筆、書籤匯入入口位置也通過。後續回歸另加入同網域背景分頁的同步切換，並確認其他網域的頁面不重載、不重套。
- 已查看 `qa/exception-screenshots/02-quick-favorite.png`，星號位於天眼右側；測試確認再次點擊只更新原收藏的閱讀位置。實體手機的原失聯症狀仍待使用者更新後確認，不能把模擬器通過視為該手機已完成驗收。

最終 API 36／release 隔離套件共 8 組操作回歸全部通過（`qa/exception-final-regression.log`）：例外與同網域／其他網域分頁、快速收藏、Chrome 真實 HTML 檔案匯入、原有選取／動態元件與自訂程式，以及上下網址列的下拉刷新。

正式 0.1.7 APK 已以原本套件與簽章在模擬器覆蓋安裝，讀回 versionCode 8。已複製到 `/Volumes/外接硬碟/Google Drive/安裝包/ChengJing-Browser-0.1.7-Android.apk`，來源與目的地 SHA-256 同為 `d57b0839b00b866c4df8950e988813427e8d51e627400e9e4e6367795872a65b`（`qa/v017-copy-verification.json`）。只確認本機資料夾複製完成，未確認 Google Drive 雲端上傳。
