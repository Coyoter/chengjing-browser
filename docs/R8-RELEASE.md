# 1.4.2：Play 程式碼最佳化與混淆

## 問題與實作

使用者的 Play Console 截圖對 22 (1.4.1) 顯示「模糊化 0%」，並標示 25% 門檻與 2027 年 2 月期限。1.4.1 的 release 明確設定 isMinifyEnabled=false；這不是圖片變模糊，也不是單純缺少除錯符號。

正式 release 現在開啟 minification、最佳化、混淆及資源縮減，使用 proguard-android-optimize.txt 與窄範圍的 JNI／JavaScript 保留規則。不改 Kotlin 應用程式原始碼、資料格式、畫面與原始簽章。既有 AGP 8.13.0 保持不變；Kotlin 2.4 依官方相容表需要 R8 9.1.29 以上，因此在 pluginManagement/buildscript 固定 R8 9.1.29，避免只開啟舊版 shrinker 卻丟失 Kotlin metadata。

依據：
- https://developer.android.com/build/kotlin-support
- https://r8.googlesource.com/r8/+/refs/heads/main/README.md
- https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- https://developer.android.com/topic/performance/vitals/code-optimization

## 保留規則不是全域排除

LiteRT-LM 的原生程式會按名稱找 JNI 類別、例外、getter 和 callback。規則僅保留這些動態邊界，尤其 onMessage/onNext/onDone/onError，不保留整個瀏覽器或整個 Google／AndroidX 套件。實際 0.17.0 AAR 的類別簽名與既有 APK 的 JNI 符號已用來核對必要方法。Google 同步與 AndroidX 依賴沿用各自 consumer 規則。

## 實際產物驗證

scripts/r8_verification.py 讀取實際 AAB 的 BUNDLE-METADATA/com.android.tools/r8.json 與內嵌 proguard.map，檢查開關、R8 版本、資源縮減、DEX 雜湊、mapping 一致性、app 類別實際改名及未保護比例。報告的百分比來自該次 R8 編譯器的 noObfuscation/noOptimization/noShrinking 統計，不是偽造或自行上傳的 Play 評分。

r8-checks 在無正式 Secrets 的 runner 建置 .qa 套件的非 debuggable release，並透過獨立 release-smoke host 操作該 APK。host 不依賴 :app、不加入 app 的 androidTest 保留規則，不把測試參照的類別留在正式碼中。測試包括 release 真正改名、JNI 載入／呼叫／原生例外、動態 getter、AI 欄位、WebView 天眼訊息、歷史、下載入口、同步入口與無痕分頁。JNI 冒煙測試沒有下載 2.59 GB 模型，不等於完成整個模型推論驗收；同步入口測試不會登入或讀取真實 Google Drive。

原本的 80 項 JVM、37 項 Android 回歸與三段跨程序快照測試仍保留。結果以精確提交的 Actions 報告為準，這份說明不預先宣稱全部成功。

## 發行與 Play Console

main 的發布同時等待既有回歸與 R8 release 驗證；正式簽章套件還會重新檢查實際 AAB。每個新 Release 多附一份 R8.zip（mapping、設定、移除清單、r8.json 與驗證摘要），BUILD.json 記錄該次統計。AAB 內含該版本 mapping，利於 Play 反混淆崩潰紀錄。不可拿其他版本 mapping 替換。

GitHub Release 不會替你提交 Google Play。需上傳 1.4.2 / 23 AAB，再由 Play 重算該版本資訊；不能以本機驗證宣稱既有 22 (1.4.1) 的警告已被後台移除。SHA-1/256 憑證不會因混淆而更換。

## 1.8.0 圖片操作回歸

獨立 R8 host 另檢查圖片的長按選單、預覽雙擊後的畫面像素變化、下載清單，以及另一個 UID 實際讀取剪貼簿／系統分享圖片的 MIME、大小與 SHA-256。不增加應用程式測試保留規則，FileProvider 只開放圖片操作的暫存目錄。完整流程現有 11 個獨立案例，結果以當次 Actions 為準。

## 1.8.1 外觀回歸

新增第 12 個獨立案例：手機淺色／App 深色、手機深色／App 淺色、跟隨系統，均檢查長按選單實際像素及分頁保留；也驗證外部網址不再被舊面板遮住。不依賴 App 內部類別，也不增加正式碼保留規則。

## 1.9.0 搜尋引擎回歸

新增第 13 個獨立案例：在最佳化 APK 的設定畫面讀取全部搜尋引擎選項，切換 Wiki 後從網址列送出搜尋，再保存自訂 HTTPS 範本並截圖。測試 host 不依賴 App 類別，也不為這項功能增加 R8 保留規則。

第 14 個案例以真實 WebView 元件新增 HTML 規則，再從已儲存規則開啟編輯、進入 AI 修訂頁、返回並更新 HTML，確認正式版顯示更新後內容。分頁預覽案例等待 Chromium 實際呈現圖片後再擷取預覽，不以原生 accessibility idle 代替已繪製畫面。

1.9.1 增加三個返回案例（共 17 項）：由獨立來源 App 開啟連結並連續 Back 返回、不留空分頁；冷啟動與實際程序停止後的外部分頁仍能正確返回；一般自行新增的頁面在根頁 Back 離開後仍保留。來源 App 僅存在測試 host，不包含於正式 APK／AAB。
