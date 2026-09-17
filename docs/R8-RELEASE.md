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
