# 預設搜尋引擎（1.9.0）

## Design Read

```text
artifact: Android 設定介面與網址列搜尋流程
audience: 日常瀏覽使用者，也包含需要自訂搜尋服務的進階使用者
purpose: 清楚選擇搜尋去向，同時維持網址輸入的可預期性
visual-language: 澄境暖白／墨綠表面、玉綠狀態色、低干擾單選清單
mode: extension
visual-variance: 2
motion-intensity: 1
information-density: 5
asset-dependence: 1
brand-fidelity: 10
```

延續既有色票、字體、18dp 群組圓角與至少 48dp 操作高度。上方摘要先回答「現在用哪一個」，下方才列出選項。內建服務使用名稱與網域兩層文字，不加入品牌圖示或外部素材；避免不同商標風格破壞澄境介面一致性。

自訂模式使用可換行的網址欄位，避免長範本只顯示尾端。`{query}` 是唯一需要理解的規則，輔助文字、即時錯誤、編碼後預覽與「儲存並使用」形成同一流程。錯誤內容不會改變目前使用的搜尋引擎。

## 網址與搜尋判斷

輸入完整 HTTP／HTTPS 網址，或 `example.com/path` 這類明確網址時，仍直接開啟網址。其餘內容才視為搜尋文字，使用 UTF-8 進行網址編碼後放入搜尋範本。

內建範本：

| 選項 | 範本 |
| --- | --- |
| Google | `https://www.google.com/search?q={query}` |
| Bing | `https://www.bing.com/search?q={query}` |
| Yahoo | `https://search.yahoo.com/search?p={query}` |
| 百度 | `https://www.baidu.com/s?wd={query}` |
| Naver | `https://search.naver.com/search.naver?query={query}` |
| Wiki | `https://zh.wikipedia.org/w/index.php?search={query}` |

自訂範本限 2,048 字元、單行 HTTPS 網址，必須且只能含有一次 `{query}`。佔位符需位於路徑或查詢參數，不能放在主機名稱；網址不能包含帳號、密碼或錨點。無效或損壞的自訂設定會安全回退至 Google，不會把非網址內容當成可執行協定。

## 資料與驗證

搜尋引擎選擇及自訂範本保存在 App 本機，不加入 Google 同步。網址列建議仍只在本機比對；使用者送出搜尋後，搜尋文字、IP 位址、瀏覽器識別與適用 Cookie 會提供給選定服務。無痕會使用相同的引擎選擇，但不保存搜尋詞。

`SearchEnginesTest` 驗證所有內建網址、編碼、網址優先、自訂安全限制及損壞設定回退。`SearchEngineSettingsTest` 以實際設定畫面驗證選項、Bing 導航、搜尋記錄、無痕、自訂錯誤、保存與 Activity 重建。R8 獨立測試在最佳化 APK 上以真實觸控切換 Wiki、送出網址列搜尋及保存自訂範本。

本機連線檢查確認 Google、Bing、百度、Naver 與中文維基百科範本可得到 HTTP 回應；Yahoo 對自動化連線回傳其驗證流程，因此功能驗收不以搜尋結果內容作為發布門檻。搜尋服務可能依地區、Cookie 或反自動化規則改變頁面。
