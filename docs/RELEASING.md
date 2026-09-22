# Android 簽章與發布

新版原始碼為 1.8.1（versionCode 30）。合併不等於 APK/AAB 已發布：只有 Android release 工作流程完整成功，且 GitHub Release 附件通過雜湊驗證，才是完成。

## 一次性設定

在此 repository 的 Actions Secrets 設定 CHENGJING_BROWSER_KEYSTORE_BASE64（原有 signing/browser.jks 的 Base64）及 CHENGJING_BROWSER_STORE_PASSWORD（原有 signing/password 的內容）。使用 gh secret set 的標準輸入直接提交，不把值放在命令列參數、聊天內容、repo 或 Actions 產物。Base64 本身不是加密，必須放在 Secrets；不可上傳為一般檔案。

沿用既有 alias chengjing-browser 與相同 store/key password。建置會核對 docs/ANDROID-REGISTRATION.md 所記錄的原始 SHA-256 憑證，拒絕使用新金鑰或 debug 簽章代替。私鑰只在簽章步驟解碼到暫存位置，完成後刪除；QA 檢查不接收簽章秘密。

## 雲端發布

main 的版本檔或 release workflow 更新會觸發發布流程。也可手動執行 Android release，填入與目前 main 一致的 version。檢查包含 QA 單元測試、lint、APK／測試編譯及隔離模擬器回歸；全部成功且簽章設定存在後，才執行正式 release 單元測試、lint、APK/AAB 建置、簽章和 APK zip 對齊驗證。

scripts/publish-release.py 將 Tag 指向建置的精確提交，先建立 Draft，上傳五個附件並比對遠端 SHA-256，再發布為 Latest。不移動既有 Tag、不覆蓋附件；若 main 已前進或同名附件內容不同，停止並保留 Draft 供檢查。

這只發布 GitHub Release，不會提交 Google Play，也不自動部署公開隱私政策。

## 本機建置

python3 scripts/build-private.py 可沿用本機 signing/browser.jks 與 signing/password，同時產生 APK、AAB、Source.zip、BUILD.json、SHA256SUMS 到 release/<version>/。原有檔案不覆蓋。需 Java、Android SDK 36／build-tools 36.0.0，以及乾淨的已提交原始碼。

scripts/publish-release.py 只處理已建置且版本、提交、雜湊相符的套件；本機直接執行發布腳本不會替代裝置回歸驗收，日常發布應使用有檢查閘門的 Android release workflow。
