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


以下是除 `/execute_json_script` 外其他核心接口的详细使用示例，包含请求方式、参数说明、请求示例（基于 `curl`）及响应示例：


### 1. 服务状态检查 `/status`
- **功能**：检查服务是否正常运行  
- **请求方法**：`GET`  
- **参数**：无  

**请求示例**：  
```bash
curl http://localhost:9008/status
```

**响应示例**（文本）：  
```text
server is running
```


### 2. 获取UI层级结构 `/dump`
- **功能**：获取当前屏幕的UI层级结构（XML格式）  
- **请求方法**：`GET`  
- **参数**：无  

**请求示例**：  
```bash
curl http://localhost:9008/dump -o hierarchy.xml  # 保存为本地文件
```

**响应示例**（XML片段）：  
```xml
<hierarchy rotation="0">
  <node className="android.widget.FrameLayout" bounds="[0,0][1080,2340]">
    <node className="android.widget.LinearLayout" bounds="[0,50][1080,2290]">
      <node className="android.widget.TextView" text="登录" resourceId="com.example:id/tv_login" bounds="[400,800][680,920]"/>
      <!-- 更多嵌套节点 -->
    </node>
  </node>
</hierarchy>
```


### 3. 获取屏幕截图 `/screenshot`
- **功能**：获取当前屏幕的PNG截图  
- **请求方法**：`GET`  
- **参数**：  
  - `quality`（可选）：图片质量（0-100，默认80）  

**请求示例**（保存为图片）：  
```bash
curl http://localhost:9008/screenshot?quality=90 -o screenshot.png
```

**响应**：二进制PNG图片数据（可直接保存为图片文件）  


### 4. 检查UI是否变化 `/is_ui_change`
- **功能**：判断当前UI与上一次检查时是否变化  
- **请求方法**：`GET`  
- **参数**：无  

**请求示例**：  
```bash
curl http://localhost:9008/is_ui_change
```

**响应示例**（JSON）：  
```json
{"changed": true}  # true表示有变化，false表示无变化
```


### 5. 坐标点击 `/click`
- **功能**：模拟在指定坐标的单次点击（按下→延迟→抬起）  
- **请求方法**：`GET`  
- **参数**：  
  - `x`：横坐标（整数/浮点数）  
  - `y`：纵坐标（整数/浮点数）  

**请求示例**：  
```bash
curl http://localhost:9008/click?x=500&y=1000
```

**响应示例**（JSON）：  
```json
{"success": true, "message": "坐标点击成功", "x": 500.0, "y": 1000.0}
```


### 6. 触摸按下 `/touch_down`
- **功能**：模拟在指定坐标的触摸按下（不抬起，需配合`/touch_up`使用）  
- **请求方法**：`GET`  
- **参数**：  
  - `x`：横坐标  
  - `y`：纵坐标  

**请求示例**：  
```bash
curl http://localhost:9008/touch_down?x=300&y=800
```

**响应示例**（JSON）：  
```json
{"success": true, "message": "触摸按下成功"}
```


### 7. 触摸抬起 `/touch_up`
- **功能**：模拟在指定坐标的触摸抬起（与`/touch_down`配对使用）  
- **请求方法**：`GET`  
- **参数**：  
  - `x`：横坐标  
  - `y`：纵坐标  

**请求示例**：  
```bash
curl http://localhost:9008/touch_up?x=300&y=800
```

**响应示例**（JSON）：  
```json
{"success": true, "message": "触摸抬起成功"}
```


### 8. 触摸滑动 `/touch_move`
- **功能**：模拟触摸滑动（需在`/touch_down`之后、`/touch_up`之前调用）  
- **请求方法**：`GET`  
- **参数**：  
  - `x`：目标横坐标  
  - `y`：目标纵坐标  

**请求示例**（配合按下/抬起完成滑动）：  
```bash
# 按下→滑动→抬起（模拟从(300,800)滑动到(600,800)）
curl http://localhost:9008/touch_down?x=300&y=800
curl http://localhost:9008/touch_move?x=450&y=800  # 中间点
curl http://localhost:9008/touch_move?x=600&y=800  # 终点
curl http://localhost:9008/touch_up?x=600&y=800
```

**响应示例**（JSON）：  
```json
{"success": true, "message": "触摸滑动成功"}
```


### 9. 查找单个UI元素 `/find_element`
- **功能**：按属性查找单个UI元素（如文本、资源ID等）  
- **请求方法**：`GET`  
- **参数**：  
  - `type`：查找类型（如`text`、`resourceId`、`className`）  
  - `value`：查找值（与`type`对应，如文本内容、资源ID）  
  - `timeout`（可选）：超时时间（毫秒，默认5000）  

**请求示例**（查找文本为“登录”的元素）：  
```bash
curl http://localhost:9008/find_element?type=text&value=登录&timeout=3000
```

**响应示例**（JSON）：  
```json
{
  "className": "android.widget.Button",
  "resourceId": "com.example:id/btn_login",
  "text": "登录",
  "bounds": "[400,800][680,920]",  # 元素位置（左、上、右、下）
  "enabled": true,
  "visible": true
}
```


### 10. 查找多个UI元素 `/find_elements`
- **功能**：按属性查找多个符合条件的UI元素  
- **请求方法**：`GET`  
- **参数**：同 `/find_element`  

**请求示例**（查找所有文本框）：  
```bash
curl http://localhost:9008/find_elements?type=className&value=android.widget.EditText
```

**响应示例**（JSON数组）：  
```json
[
  {
    "className": "android.widget.EditText",
    "resourceId": "com.example:id/et_username",
    "bounds": "[300,500][780,600]",
    "text": ""
  },
  {
    "className": "android.widget.EditText",
    "resourceId": "com.example:id/et_password",
    "bounds": "[300,650][780,750]",
    "text": ""
  }
]
```


### 11. 文本输入 `/input`
- **功能**：查找输入框并执行文本输入（支持清空原有内容）  
- **请求方法**：`POST`  
- **请求体**（JSON）：  
  - `type`：查找输入框的类型（如`resourceId`）  
  - `value`：查找输入框的值（如资源ID）  
  - `text`：要输入的文本  
  - `clear`（可选）：是否清空原有内容（默认`true`）  

**请求示例**（向用户名输入框输入文本）：  
```bash
curl -X POST http://localhost:9008/input \
  -H "Content-Type: application/json" \
  -d '{"type":"resourceId", "value":"com.example:id/et_username", "text":"test_user", "clear":true}'
```

**响应示例**（JSON）：  
```json
{
  "success": true,
  "message": "输入成功",
  "input_text": "test_user",
  "actual_text": "test_user"
}
```

以下是结合你提供的 `HierarchyTest.java` 代码，在保留原有内容基础上补充完整的 `/execute_json_script` 接口文档（含 `click` 动作详细说明）：


### 12. 批量脚本示例（`/execute_json_script`）
`execute_json_script` 是一个支持批量执行多步骤操作的核心接口，允许通过 JSON 格式的脚本定义一系列连续动作（如点击、输入、滑动等），适用于复杂场景的自动化操作。以下是详细介绍：


### **核心功能**
- 接收一个包含多个动作的 JSON 脚本，按顺序执行所有动作。
- 支持动作间共享元素缓存（例如，前一个动作查找的元素可被后续动作复用）。
- 返回每个动作的执行结果及整体执行状态，便于调试和流程控制。


### **请求方式与参数**
- **请求方法**：`POST`  
- **请求体**：JSON 数组，每个元素为一个动作对象，包含以下字段：  
  - `type`：动作类型（必填，支持 `click`/`find_and_click`/`find_and_input`/`swipe_sequence`）。  
  - `params`：动作参数（必填，根据 `type` 不同而变化，详见下方动作说明）。  


### **支持的动作类型及参数**
#### 1. 坐标点击（`type: "click"`）
- **功能**：直接按指定坐标执行点击（模拟真实点击流程：按下→50ms延迟→抬起）。  
- **`params` 字段**：  
  - `x`：横坐标（数字，必填，单位：像素）。  
  - `y`：纵坐标（数字，必填，单位：像素）。  
- **代码逻辑说明**：  
  从 `HierarchyTest.java` 可知，`click` 动作通过 `touchController` 执行底层触摸事件：  
  ```java
  // 执行点击（按下→延迟→抬起）
  boolean clickSuccess = touchController.touchDown(x, y);
  SystemClock.sleep(50); // 模拟按下时长
  clickSuccess &= touchController.touchUp(x, y);
  ```  


#### 2. 查找并点击（`type: "find_and_click"`）
- **功能**：先按属性查找元素，再点击元素中心。  
- **`params` 字段**：  
  - `type`：查找类型（字符串，必填，如 `text`/`resourceId`/`className`）。  
  - `value`：查找值（字符串，必填，与 `type` 对应，如文本内容、资源 ID）。  
  - `timeout`：超时时间（数字，可选，默认 5000 毫秒，超过时间未找到元素则失败）。  


#### 3. 查找并输入（`type: "find_and_input"`）
- **功能**：先按属性查找输入框，再输入文本（默认清空原有内容）。  
- **`params` 字段**：  
  - `type`：查找类型（字符串，必填，如 `resourceId`）。  
  - `value`：查找值（字符串，必填，如输入框的资源 ID）。  
  - `text`：要输入的文本（字符串，必填）。  
  - `clear`：是否清空原有内容（布尔值，可选，默认 `true`）。  
  - `timeout`：超时时间（数字，可选，默认 5000 毫秒）。  


#### 4. 滑动序列（`type: "swipe_sequence"`）
- **功能**：按指定起点和多步滑动路径执行滑动（按下→分步移动→抬起）。  
- **`params` 字段**：  
  - `startX`：滑动起点横坐标（数字，必填）。  
  - `startY`：滑动起点纵坐标（数字，必填）。  
  - `steps`：滑动步骤数组（数组，必填，至少 1 步，每步为 `{x: 数字, y: 数字}`）。  
  - `duration`：总滑动时长（数字，可选，默认 500 毫秒，分配到每步的间隔时间）。  


### **请求示例（curl）**
包含 `click` 动作与其他动作的组合示例：
```bash
curl -X POST http://localhost:9008/execute_json_script \
  -H "Content-Type: application/json" \
  -d '[
    {
      "type": "click",
      "params": {
        "x": 300,  # 左上角菜单按钮坐标
        "y": 150
      }
    },
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
        "startX": 500,
        "startY": 1500,
        "steps": [{"x":500,"y":1000}, {"x":500,"y":500}],
        "duration": 800
      }
    },
    {
      "type": "click",
      "params": {
        "x": 900,  # 右下角确认按钮坐标
        "y": 1800
      }
    }
  ]'
```


### **响应格式**
返回 JSON 对象，包含整体执行状态和每个动作的详细结果：
```json
{
  "success": true,  // 整体是否成功（所有动作均成功为true）
  "totalActions": 5,  // 总动作数
  "results": [
    {
      "actionIndex": 0,
      "actionType": "click",
      "success": true,
      "message": "坐标点击成功",
      "x": 300.0,
      "y": 150.0
    },
    {
      "actionIndex": 1,
      "actionType": "find_and_click",
      "success": true,
      "message": "查找并点击成功",
      "element_bounds": "[600,480][800,580]",
      "click_x": 700,
      "click_y": 530
    },
    {
      "actionIndex": 2,
      "actionType": "find_and_input",
      "success": true,
      "message": "输入成功",
      "input_text": "test_user",
      "actual_text": "test_user"
    },
    {
      "actionIndex": 3,
      "actionType": "swipe_sequence",
      "success": true,
      "message": "滑动序列执行成功",
      "start": {"x": 500, "y": 1500},
      "end": {"x": 500, "y": 500},
      "step_count": 2
    },
    {
      "actionIndex": 4,
      "actionType": "click",
      "success": true,
      "message": "坐标点击成功",
      "x": 900.0,
      "y": 1800.0
    }
  ]
}
```


### **错误场景说明**
- 若 `click` 动作缺少 `x` 或 `y` 参数，响应将提示：  
  `{"success":false, "message":"click动作缺少参数x或y"}`  
- 若动作类型不存在，响应将提示：  
  `{"success":false, "message":"未知动作类型：xxx"}`  


以上内容保留了原有接口说明的结构和其他动作的信息，同时基于 `HierarchyTest.java` 中 `click` 动作的实现逻辑，补充了其详细说明、示例及错误场景，确保文档的完整性和准确性。


### **错误处理**
- **JSON 格式错误**：返回 `400` 状态码，提示“解析JSON脚本失败”。  
- **动作缺少字段**：如缺少 `type` 或 `params`，对应动作标记为失败，整体 `success` 为 `false`。  
- **单个动作失败**：不中断后续动作（可通过代码修改为“失败即停止”），整体 `success` 为 `false`。  
- **执行异常**：如参数类型错误、元素未找到等，在对应动作结果中返回错误消息。  


### **关键特性**
- **原子性**：按顺序执行动作，前一个动作的结果不强制影响后一个（可自定义中断逻辑）。  
- **元素缓存**：`find_and_click` 等查找动作会将元素存入缓存（`elementCache`），供后续动作复用（需代码中显式调用）。  
- **灵活性**：支持通过 `duration` 控制滑动速度、`timeout` 控制元素查找等待时间，适配不同场景。


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
