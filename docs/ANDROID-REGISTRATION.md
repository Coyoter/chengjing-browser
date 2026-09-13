# Android 開發者套件登記

2026-09-13 最新目標改為準備 Google Play 發行。下列是當日查詢到的登記狀態，尚未代替澄境瀏覽器完成新登記或上架。

核對日期：2026-09-13。這是當次官方規定與帳戶清單的觀察，後續可能更新。

開發者身分驗證與應用程式所有權登記是兩個步驟。Android 以套件名稱及 APK 簽章憑證，把應用程式對應到已驗證帳戶；不是依 APK 檔名，也不是網站 HTTPS 憑證。

當次 Play Console 顯示六個既有套件已註冊，包括澄境筆記。澄境瀏覽器未出現在清單，本次只讀取狀態，沒有提交登記或建立商店應用程式。

- 套件名稱：`tw.techtarian.browser`
- 顯示名稱：澄境瀏覽器
- APK 簽章憑證 SHA-256：`AB:9D:27:CE:97:22:E3:4F:94:65:56:EE:01:54:14:05:4B:AE:28:93:FF:DD:57:FF:38:12:6F:F4:C5:D0:4C:F0`
- 指紋已從既有發行 APK 的憑證讀取；它不是每一版 APK 檔案的 SHA-256 校驗碼。私鑰仍保存在本機 signing 目錄。

現有 Play 開發者可在「Android 開發人員驗證 → 套件名稱 → 註冊套件名稱」登記自行散布的 APK。Google 可能要求把它提供的識別片段加入 `assets/adi-registration.properties`，再用現有金鑰簽署證明 APK；官方允許使用空白證明專案，並非一定要交付實際應用。這個流程不會把應用上架，亦不需要把私鑰上傳作為所有權證明。

9 月 30 日有兩個不同範圍：Google Play 上的套件須完成登記；首波安裝驗證針對巴西、印尼、新加坡、泰國的指定商店。依 2026-07-15 更新的 FAQ，直接側載 APK 尚不在該次執行範圍，全球擴展規劃在 2027 年。ADB 開發安裝流程另有保留。

官方來源：
- [Play 套件登記](https://support.google.com/googleplay/android-developer/answer/16984799?hl=zh-Hant)
- [Android 套件所有權證明流程](https://support.google.com/android-developer-console/answer/16640821?hl=en)
- [最新範圍與時程](https://developer.android.com/developer-verification)
- [FAQ：直接側載、ADB 與登記範圍](https://developer.android.com/developer-verification/guides/faq)
