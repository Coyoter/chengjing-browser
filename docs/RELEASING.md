# Android 簽章與發布

新版原始碼為 1.14.1（versionCode 45）。合併不等於 APK/AAB 已發布：只有 Android release 工作流程完整成功，且 GitHub Release 附件通過雜湊驗證，才是完成。

## 一次性設定

在此 repository 的 Actions Secrets 設定 CHENGJING_BROWSER_KEYSTORE_BASE64（原有 signing/browser.jks 的 Base64）及 CHENGJING_BROWSER_STORE_PASSWORD（原有 signing/password 的內容）。使用 gh secret set 的標準輸入直接提交，不把值放在命令列參數、聊天內容、repo 或 Actions 產物。Base64 本身不是加密，必須放在 Secrets；不可上傳為一般檔案。

沿用既有 alias chengjing-browser 與相同 store/key password。建置會核對 docs/ANDROID-REGISTRATION.md 所記錄的原始 SHA-256 憑證，拒絕使用新金鑰或 debug 簽章代替。私鑰只在簽章步驟解碼到暫存位置，完成後刪除；QA 檢查不接收簽章秘密。

## 雲端發布

main 的版本檔或 release workflow 更新會觸發發布流程。也可手動執行 Android release，填入與目前 main 一致的 version。檢查包含 QA 單元測試、lint、APK／測試編譯及隔離模擬器回歸；全部成功且簽章設定存在後，才執行正式 release 單元測試、lint、APK/AAB 建置、簽章和 APK zip 對齊驗證。

雲端 QA 模擬器使用 `-gpu swangle -feature -Vulkan,-HardwareDecoder`，以 ANGLE／SwiftShader 軟體繪圖及 Android 軟體影片解碼執行測試。2026-10-02 的 Linux runner 在原生影片測試出現繪圖 color buffer 錯誤後整台模擬器失聯，因此依 [Android 官方的網頁繪圖相容性建議](https://developer.android.com/studio/run/emulator-troubleshooting?hl=en) 關閉 Vulkan，並改用 [目前支援的 ANGLE／SwiftShader 模式](https://developer.android.com/studio/run/emulator-acceleration)。這是針對測試環境的調整；影片播放、原生三點選單、下載確認及檔案內容核對仍完整執行，正式 APK 的影片及繪圖設定維持相同。[Android 模擬器的解碼設定](https://android.googlesource.com/platform/external/qemu/+/ba29194f97e72ffe770bd56e4e5c5c620598004b/android/data/advancedFeatures.ini)。

QA 編譯使用同一個 Gradle 程序執行 Kotlin，完成後釋放編譯記憶體；模擬器回歸階段將 Gradle 堆積限制為 1 GB。這只限制雲端測試工具，不改變正式 APK 的記憶體設定。驗證附件保留主機記憶體取樣與 kernel 記錄，以便區分應用斷言、模擬器錯誤及主機終止程序。

scripts/publish-release.py 將 Tag 指向建置的精確提交，先建立 Draft，上傳六個附件並比對遠端 SHA-256，再發布為 Latest。不移動既有 Tag、不覆蓋附件；若 main 已前進或同名附件內容不同，停止並保留 Draft 供檢查。

這只發布 GitHub Release，不會提交 Google Play，也不自動部署公開隱私政策。

## 本機建置

python3 scripts/build-private.py 可沿用本機 signing/browser.jks 與 signing/password，同時產生 APK、AAB、Source.zip、BUILD.json、SHA256SUMS 到 release/<version>/。原有檔案不覆蓋。需 Java、Android SDK 36／build-tools 36.0.0，以及乾淨的已提交原始碼。

scripts/publish-release.py 只處理已建置且版本、提交、雜湊相符的套件；本機直接執行發布腳本不會替代裝置回歸驗收，日常發布應使用有檢查閘門的 Android release workflow。

Linux 一般回歸與獨立 R8 正式版檢查均使用 Android 16（API 36）。直接 SwiftShader GLES 在原生影片解碼時失聯，改成 ANGLE／SwiftShader 後影片可正常播放；測試以實際影片畫面範圍尋找原生選單按鈕，兼容不同 WebView 的無障礙父節點。完整保留影片下載斷言，並保存 guest logcat 與主機診斷。

原生影片三點選單下載以獨立 instrumentation 呼叫先執行，之後再執行其他回歸；完整保留原測試的播放、選單和下載檔案斷言，獨立保存報告，避免繼承前面大量 Blob 與多次 WebView 建立的程序狀態。
