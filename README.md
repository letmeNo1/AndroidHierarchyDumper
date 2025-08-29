# AndroidHierarchyDumper

## 项目介绍
AndroidHierarchyDumper 是一个基于 Android 平台的 UI 自动化工具，主要用于通过网络接口与设备交互，支持 UI 层级获取、元素查找、触控操作、文本输入等功能。该工具依赖 UiAutomator 框架，可通过 HTTP 接口执行单步操作或批量 JSON 脚本，适用于 UI 测试、自动化操作、屏幕分析等场景，是 Nico 系列工程的依赖组件。


## 功能说明
- **UI 信息获取**：获取当前屏幕 UI 层级结构（XML 格式）、检查 UI 变化状态、截取屏幕截图。
- **元素操作**：按文本、资源 ID、类名等属性查找单个或多个 UI 元素，并返回元素详细信息（位置、尺寸等）。
- **触控交互**：支持坐标点击、触摸按下/抬起/滑动，以及基于元素的点击操作。
- **文本输入**：查找输入框元素并执行文本输入（支持清空原有内容）。
- **批量脚本**：通过 JSON 脚本批量执行多步动作（如点击、输入、滑动序列），减少网络请求次数。


## 环境要求
- JDK 1.8 及以上
- Android SDK（API 23 及以上，对应 Android 6.0+）
- Gradle 8.12（项目已包含 wrapper，无需手动安装）
- Android 设备或模拟器（开启 USB 调试模式）


## 构建与安装

### 构建 APK
在项目根目录执行以下命令构建 APK：
```shell
./gradlew clean assemble assembleAndroidTest
```

### 输出文件
构建完成后，APK 文件位于：
```
./app/build/outputs/apk/
├── app-debug.apk           # 主应用 APK
└── app-debug-androidTest.apk  # 测试组件 APK（核心功能实现）
```
- 应用包名：`nico.dump_hierarchy`


### 安装步骤
1. 连接 Android 设备（确保已开启 USB 调试）
2. 通过 ADB 安装 APK：
   ```shell
   adb install app/build/outputs/apk/app-debug.apk
   adb install app/build/outputs/apk/app-debug-androidTest.apk
   ```


## 使用方法

### 启动服务
1. 设备上启动应用（默认启动后自动开启后台服务）
2. 通过 ADB 转发网络端口（默认使用 9008 端口）：
   ```shell
   adb forward tcp:9008 tcp:9008
   ```
3. 验证端口转发状态：
   ```shell
   adb forward --list
   ```


### 核心接口说明
所有接口通过 HTTP 协议交互，支持 GET/POST 方法，请求参数为键值对或 JSON，响应格式为文本、JSON 或二进制数据。

| 接口路径           | 请求方法 | 功能描述                 | 参数说明                                                                 | 返回结果示例                                                                 |
|--------------------|----------|--------------------------|--------------------------------------------------------------------------|------------------------------------------------------------------------------|
| `/status`          | GET      | 检查服务状态             | 无                                                                       | `server is running`                                                         |
| `/dump`            | GET      | 获取 UI 层级结构         | 无                                                                       | UI 层级 XML 数据（包含元素位置、属性等）                                     |
| `/screenshot`      | GET      | 获取屏幕截图             | `quality`（可选，0-100，默认 80，图片质量）                              | 二进制 PNG 图片数据                                                          |
| `/is_ui_change`    | GET      | 检查 UI 是否变化         | 无                                                                       | `{"changed": true}`（`true` 表示有变化）                                    |
| `/click`           | GET      | 坐标点击                 | `x`（横坐标）、`y`（纵坐标）                                             | `{"success": true, "message": "坐标点击成功", "x": 500, "y": 1000}`         |
| `/touch_down`      | GET      | 触摸按下                 | `x`（横坐标）、`y`（纵坐标）                                             | `{"success": true, "message": "触摸按下成功"}`                              |
| `/touch_up`        | GET      | 触摸抬起                 | `x`（横坐标）、`y`（纵坐标）                                             | `{"success": true, "message": "触摸抬起成功"}`                              |
| `/touch_move`      | GET      | 触摸滑动                 | `x`（目标横坐标）、`y`（目标纵坐标）                                     | `{"success": true, "message": "触摸滑动成功"}`                              |
| `/find_element`    | GET      | 查找单个元素             | `type`（查找类型：text/resourceId/className 等）、`value`（查找值）、`timeout`（超时时间，默认 5000ms） | 元素信息 JSON（如 `{"className": "android.widget.Button", "bounds": "[100,200][300,400]"}`） |
| `/find_elements`   | GET      | 查找多个元素             | 同 `/find_element`                                                       | 元素列表 JSON（如 `[{"className": "android.widget.TextView"}, ...]`）        |
| `/input`           | POST     | 文本输入                 | 请求体：`{"type": "查找类型", "value": "查找值", "text": "输入内容", "clear": true}`（`clear` 可选，默认 true） | `{"success": true, "message": "输入成功", "actual_text": "输入内容"}`       |
| `/execute_json_script` | POST | 批量执行动作             | 请求体：JSON 数组（包含多个动作，见下方示例）                            | 执行结果 JSON（包含总状态和每个动作的详细结果）                              |


### 批量脚本示例（`/execute_json_script`）
请求体为 JSON 数组，支持动作类型：`click`、`find_and_click`、`find_and_input`、`swipe_sequence`。

```json
[
  {
    "type": "find_and_click",
    "params": {
      "type": "text",
      "value": "登录",
      "timeout": 3000
    }
  },
  {
    "type": "find_and_input",
    "params": {
      "type": "resourceId",
      "value": "com.example:id/et_username",
      "text": "test_user",
      "clear": true
    }
  },
  {
    "type": "swipe_sequence",
    "params": {
      "startX": 300,
      "startY": 1500,
      "steps": [{"x": 400, "y": 1500}, {"x": 800, "y": 1500}],
      "duration": 800
    }
  }
]
```

响应示例：
```json
{
  "success": true,
  "totalActions": 3,
  "results": [
    {
      "actionIndex": 0,
      "actionType": "find_and_click",
      "success": true,
      "message": "查找并点击成功",
      "element_bounds": "[100,200][300,400]",
      "click_x": 200,
      "click_y": 300
    },
    // ... 其他动作结果
  ]
}
```


## 权限说明
应用需要以下权限（已在 `AndroidManifest.xml` 中声明）：
- `android.permission.INTERNET`：用于网络接口通信
- `android.permission.READ_SECURE_SETTINGS`：用于获取部分受保护的 UI 信息（可能需要手动授予）


## 依赖说明
- **UI 自动化**：`androidx.test.uiautomator:uiautomator:2.2.0`（核心 UI 元素操作）
- **AndroidX 组件**：`appcompat:1.6.1`、`constraintlayout:2.1.4` 等
- **JSON 解析**：`com.google.code.gson:gson:2.8.9`（处理 JSON 序列化/反序列化）
- **Material Design**：`com.google.android.material:material:1.9.0`（基础 UI 组件）


## 注意事项
1. **兼容性**：最低支持 Android 6.0（API 23），推荐在 Android 10+ 设备上使用。
2. **元素查找**：`type` 参数支持 UiAutomator 所有查找类型（如 `text`、`resourceId`、`desc`、`className` 等）。
3. **批量脚本执行**：动作按数组顺序执行，前序动作失败不会中断后续执行（可修改代码中的 `continue` 为 `break` 停止）。
4. **权限授予**：`READ_SECURE_SETTINGS` 权限可能需要通过 `adb shell pm grant nico.dump_hierarchy android.permission.READ_SECURE_SETTINGS` 手动授予。


## 开发相关
- **核心功能实现**：`app/src/androidTest/java/nico/dump_hierarchy/HierarchyTest.java`（处理网络请求和动作执行）
- **触控控制**：`app/src/androidTest/java/nico/dump_hierarchy/TouchController.java`（封装触摸事件注入逻辑）
- **布局资源**：`app/src/main/res/`（包含应用图标、主题配置等基础资源）
- **构建配置**：`app/build.gradle`（依赖管理和编译配置）
