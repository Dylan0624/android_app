# PointCloudViewer

PointCloudViewer 是一個基於 Android 平台的應用程式，使用 Kotlin 語言開發，並運用 OpenGL ES 2.0 進行 3D 點雲渲染。此應用不僅能模擬產生大量點雲資料，還提供豐富的互動功能，例如旋轉、縮放、平移及點選，讓使用者可以深入探索點雲數據。此外，應用內建側邊抽屜介面，可讓使用者輕鬆切換顯示座標軸、網格和圖例，並透過下拉選單切換多種色彩模式。

## 功能特色

### 模擬點雲生成
- 每次更新產生 850,000 個點，透過多環（rings）的方式分佈於 3D 空間中
- 點雲資料包含座標、強度及法向量資訊

### 多種色彩模式
- **強度模式**：根據點強度，顏色從綠色平滑過渡到紅色
- **深度模式**：根據點的 Z 軸值（深度）進行顏色映射，顏色從藍色平滑過渡到紅色
- **顏色模式**：利用點的法向量數據映射至顏色（經過正規化處理）

### 互動操作
- **單指拖曳**：旋轉 3D 視圖
- **雙指拖曳**：平移視圖
- **捏合手勢**：縮放視圖
- **雙擊**：重置視圖至預設狀態
- **長按**：選取點雲中的點，並顯示該點的詳細資訊（位置、法向量、強度）

### UI 控制
- 側邊抽屜 (DrawerLayout) 提供各種控制項，包括切換座標軸、網格、圖例、色彩模式以及調整顯示點數的比例
- 自訂的圖例視圖 (LegendView) 根據當前色彩模式顯示對應的漸層條

## 專案結構

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/pointcloudviewer/
│   │   │   ├── CameraController.kt     # 處理視角變換（旋轉、縮放、平移）
│   │   │   ├── LegendView.kt 		    # 用於繪製顏色漸層圖例
│   │   │  	├── MainActivity.kt         # 主要活動與使用者互動
│   │   │   ├── PointCloudRenderer.kt   # OpenGL 渲染實作
│   │   │   └── LidarPoint.kt          # 點雲數據模型
│   │   └── res/
│   │       └── layout/
│   │           └── activity_main.xml   # 主要介面布局
├── build.gradle
└── README.md
```

### PointCloudRenderer.kt
- 負責 OpenGL 渲染，處理點雲、座標軸及網格的繪製
- 管理點雲資料更新、頂點緩衝區及點選功能
- 包含生成模擬點雲資料的邏輯，並透過 GLSurfaceView 的 `onDrawFrame` 方法不斷更新畫面

### CameraController.kt
- 處理視角變換（旋轉、縮放、平移）並維護模型矩陣
- 提供重置視圖的方法，方便使用者恢復預設狀態

### LegendView.kt
- 自定義 View，用於繪製顏色漸層圖例
- 根據選擇的色彩模式（強度或深度）顯示對應的漸層和文字標示

### MainActivity.kt
- 主活動，負責初始化 GLSurfaceView 及渲染器，並整合側邊抽屜中的各項 UI 控制
- 實作各種手勢偵測（旋轉、縮放、雙擊、長按），與點雲互動
- 管理點雲更新排程，並透過執行緒執行模擬資料生成及更新

## 系統需求
- **Android SDK**：建議最低 API 等級 15（請根據實際需求調整）
- **OpenGL ES 2.0**：必須支援 3D 圖形渲染
- **Kotlin**：專案使用 Kotlin 語言編寫
- **Android Studio**：推薦使用 Android Studio 進行開發和除錯

## 安裝與編譯

1. 複製專案：
```bash
git clone https://github.com/yourusername/PointCloudViewer.git
```

2. 在 Android Studio 中打開專案：
    - 啟動 Android Studio
    - 選擇「Open an existing Android Studio project」，並導航到專案目錄

3. 編譯專案：
    - 同步 Gradle 檔案
    - 從 Build 菜單中選擇「Make Project」進行編譯

4. 執行應用：
    - 連接實體設備或啟動模擬器
    - 按下「Run」按鈕即可在目標裝置上執行應用

## 使用方式

### 點雲瀏覽
- 啟動應用後，可看到包含模擬點雲、網格和座標軸的 3D 視圖

### 互動手勢
- **單指拖曳**：旋轉 3D 視圖
- **雙指拖曳**：平移視圖
- **捏合手勢**：縮放視圖
- **雙擊**：重置視圖到預設狀態
- **長按**：選取點雲中的點，右上角會顯示該點的詳細資訊

### UI 控制
- 點擊左上角的「☰」按鈕以打開側邊抽屜
- 可切換座標軸、網格和圖例的顯示狀態
- 使用下拉選單切換色彩模式（強度、深度、顏色）
- 透過滑桿調整顯示的點數比例
- 點擊「重置視圖」按鈕可將視圖變換重置為預設狀態

## 自訂設定

### 點雲資料
- 點雲生成邏輯位於 `PointCloudRenderer.kt` 的 `generateSimulatedPointsBatch()` 方法中
- 可根據需求修改 `maxPoints`、`pointsPerUpdate` 或調整點的生成演算法

### 色彩模式
- 目前提供三種色彩模式：強度模式、深度模式、顏色模式
- 如需新增或調整色彩模式，可修改片段著色器 (Fragment Shader) 的邏輯

### UI 與圖例
- 圖例顯示邏輯在 `LegendView.kt` 中，可調整文字、顏色及顯示位置
- 側邊抽屜中的各項 UI 控制項也可依實際需求進行擴充或修改

## 疑難排解

### 著色器編譯錯誤
- 請確保測試裝置支援 OpenGL ES 2.0
- 檢查 Logcat 輸出中的著色器錯誤訊息以進行調整

### 效能問題
- 由於應用設計最多可處理 850,000 個點，部分舊款裝置可能效能較低
- 可調整 `pointsPerUpdate` 或降低顯示點數比例，以改善效能表現

## 授權條款

本專案採用 MIT 授權條款 - 詳見 LICENSE 檔案

## 作者

[Dylan_Lin/PEGA]

## 更新記錄

### 1.0.0 (2024-02-24)
- 初始版本發布
- 基本點雲視覺化功能
- 互動式控制實作
- 效能優化