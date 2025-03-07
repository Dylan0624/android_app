# StellarTest

StellarTest 是一個 Android 應用程式，主要用於接收並處理 UDP 數據封包，並顯示接收速率與數據資訊。該應用程式適用於高流量 UDP 數據接收，並使用 Kotlin 協程進行高效能數據處理。

## 功能特點
- **UDP 數據接收**
  - 監聽 7000 端口，並使用 `DatagramSocket` 接收數據。
  - 使用 64MB 大緩衝區來應對高吞吐量數據流。
  - 自動丟棄過多的數據，防止記憶體溢位 (OOM)。

- **數據包處理**
  - 使用 `ConcurrentLinkedQueue` 來緩存 UDP 數據包。
  - 透過 Kotlin 協程並行處理數據，提升性能。
  - 每秒計算當前接收速率，並顯示統計數據。

- **日誌顯示**
  - 在 UI 中即時顯示數據處理過程與錯誤資訊。
  - 自動限制日誌行數，避免記憶體溢位。

## 環境需求
- Android Studio (建議使用最新版本)
- 最低支援 Android 5.0 (API 21)
- 需要 `INTERNET` 權限

## 安裝與執行
### 1. 下載專案
```sh
git clone https://github.com/Dylan0624/android_app.git
cd android_app
```

### 2. 開啟 Android Studio
- 用 Android Studio 開啟專案。
- 連接 Android 設備或啟動模擬器。

### 3. 執行應用程式
- 在 Android Studio 中點擊 `Run` 按鈕，或使用快捷鍵 `Shift + F10`。

## 程式架構
主要類別與功能：

| 類別 | 功能 |
|------|------|
| `MainActivity.kt` | 應用主界面，負責 UI 更新與按鈕控制 |
| `DatagramSocket` | 監聽 UDP 數據，並處理緩衝區 |
| `ConcurrentLinkedQueue` | 緩存 UDP 數據，避免 UI 線程阻塞 |
| `CoroutineScope` | 使用協程處理 UDP 數據，提高效能 |
| `Handler` | 定期觸發垃圾回收，防止記憶體溢位 |

## 使用方式
1. 啟動應用後，點擊 **「啟動 UDP 監聽」** 按鈕。
2. 應用開始監聽 UDP 7000 端口，並在 UI 上顯示接收速率與數據統計。
3. 若要停止監聽，點擊 **「停止 UDP 監聽」** 按鈕。

## 設定
如果需要更改 UDP 監聽端口，請修改 `startUdpReceiver` 方法中的 `val port = 7000`。

## 許可權
請確保應用程式擁有 `INTERNET` 權限，在 `AndroidManifest.xml` 中確認：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```
