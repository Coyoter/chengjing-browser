# Android 直接定位與 WebView 相容性

## 問題與修正

1.11.0 加入網站與 Android 授權，但真正取得位置仍完全由 WebView 的定位供應者執行。模擬器當時可取得座標，不能據此保證小米 17 Ultra／澎湃 4 的定位供應者也正常。使用者回報同一垃圾桶查詢頁在 Chrome 正常，澄境則逾時且未顯示定位服務被呼叫。目前沒有該手機的系統記錄或實機，不能斷言單一 OEM／Google 服務的具體故障原因。

1.11.1 在支援 document-start script 與 WebMessageListener 的 WebView 中，將頂層網站的 `getCurrentPosition`、`watchPosition`、`clearWatch` 接到 Android `LocationManager`。GPS、network 與可用的 Android system fused provider 分別請求；不使用 Play Services 的 FusedLocationProviderClient。某一融合定位供應者失效，不會阻止直接 GPS／網路請求。較舊 WebView 保留原本路徑，網站資訊提供更新提示。

## 1.14.1：補上 Google Play 服務融合定位

使用者再次回報 Chrome 能定位、澄境逾時。原來的直接定位測試只證明 Android provider 收到測試座標後能交回網頁，沒有涵蓋 Google Play 服務可提供位置、直接 provider 卻沒有更新的情境；不能以先前的模擬 GPS 成功認定原手機問題已解決。

1.14.1 透過 `FusedLocationProviderClient` 增加可選的定位來源，與原有 GPS／network／系統 fused 同時工作。Google 服務缺少、停用、失效或遲遲不回應，都不取消直接來源。高精度要求不強制等待 GPS 才交出有效網路位置；回傳精度仍如實提供給網站。網站接受近期位置時，以單調時鐘檢查實際時效；`maximumAge=0` 仍拒絕歷史位置。網站 timeout 保持原設定，成功、取消、換頁、離開前景及撤回授權時移除所有來源的請求。Google 定位註冊若在取消後才完成，也會再次移除，不留背景請求。

`HybridPositionTest` 驗證來源互相備援、近期與過期位置、timeout、取消及持續定位的停止。`PlayServicesLocationTest` 使用真正的 Google API 測試模式，在直接 provider 沒有回報時將測試座標交回真正 WebView；opt-in 公開網頁測試使用原垃圾桶頁面及其 12 秒 timeout／30 秒 maximumAge，核對「定位完成」與最近五個地點。另保留停用 Google 服務的直接定位整合測試。這些是模擬器與測試座標證據，尚非使用者手機的實機接收結果。

## 授權與資料邊界

- 訊息必須是有限長度的字串，來自安全來源與目前頂層框架。以 WebView 提供的 sourceOrigin 比對目前頁面，並回讀 document-start 時建立的不可改寫文件識別與 Permissions-Policy 描述；不信任網頁自行提供的來源或允許旗標。
- 座標透過綁定原框架的 JavaScriptReplyProxy 傳回，並以文件識別、分頁與導航世代防止舊回呼送入新文件。不使用對所有框架開放的 JavascriptInterface 暴露位置。
- 取得網站與 Android 權限後才查詢系統位置。只有概略權限時不升級要求精確位置；位置精度仍受 Android 授權與系統來源限制。網站封鎖、Permissions-Policy 禁止、跨框架或過時文件不能藉訊息直接發動定位。
- 最多八項並行網站請求。單次請求預設最長 60 秒、明確設定最多 120 秒；網站較短的 timeout 保留。maximumAge 為 0 時不回傳舊快取。接受快取時以系統單調時鐘核對時效；高精度要求優先使用時效內精度較好的位置。
- 單次請求成功、逾時、取消、分頁關閉／換頁、撤回授權或 renderer 結束即移除系統 listener。持續定位在頁面／App 離開前景時暫停，回到前景且仍獲授權才恢復；clearWatch 永久移除該次要求。
- Android 權限對話框造成的暫時生命週期變更，不會先取消仍在等待授權的網站要求；尚未取得授權時不開啟系統定位。
- 不將座標寫入檔案、錯誤訊息、同步或診斷狀態。網站資訊只顯示是否已向 Android 請求、是否收到位置或失敗原因；網站仍可依其自己的政策處理已獲允許的位置。

## 驗證與實際限制

`NativeLocationTest` 會讓 WebView 原本的定位方法不再回傳結果，確認 App 本身向 Android 註冊 provider，核對 location service 中的套件登記並驗證座標。涵蓋概略權限、GPS、舊位置拒絕、持續定位的前景切換、取消、逾時、權限撤回、無痕關閉、跨框架與 Permissions-Policy。

`scripts/test-native-location.py` 限定可拋棄的 Android 模擬器，停用 Google Play Services、重新安裝獨立 QA 套件後執行上述測試，保存完整結果與系統服務診斷。正式 R8 測試也會讓 WebView 原本的定位函式失效，要求最佳化 APK 將實際位置回呼交給網站。

另以 opt-in `liveLocation=true` 檢查 `https://techtarian.com/tools/tpe-trash-can-location/`，等待真實政府資料載入，按下原網頁定位按鈕，經網站授權後用模擬 GPS 驗證「定位完成」與最近五個地點。這是原網頁與實際 Android API 的整合驗證，座標是測試座標；不是小米 17 Ultra 的真實衛星接收測試。澎湃 4 實機結果仍需更新後確認。

## 參考

- [Android LocationManager](https://developer.android.com/reference/android/location/LocationManager)
- [Google 融合定位](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient)
- [定位請求與近期位置、精度等待設定](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder)
- [WebView 的來源與框架驗證](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E,androidx.webkit.WebViewCompat.WebMessageListener))
- [Chromium 定位供應者選擇](https://github.com/chromium/chromium/blob/main/services/device/geolocation/android/java/src/org/chromium/device/geolocation/LocationProviderFactory.java)
