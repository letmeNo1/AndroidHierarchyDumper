# AndroidHierarchyDumper

一个基于Android UI Automator的工具库，用于在Android设备上获取UI层级结构、模拟用户操作（点击、滑动等）及相关UI分析，支持通过网络接口进行交互，适用于自动化测试、UI分析等场景。


## 功能特点

-  dump当前屏幕的UI层级结构（XML/JSON格式）
-  支持屏幕截图获取
-  模拟用户触摸操作（点击、按下、抬起、滑动）
-  查找UI元素（单元素/多元素匹配）
-  监听UI变化状态
-  通过HTTP接口进行网络交互，方便跨进程/跨设备调用


## 环境要求

-  JDK 1.8+
-  Android SDK:
  -  compileSdk 33
  -  minSdk 23（支持Android 6.0及以上设备）
  -  targetSdk 32
-  Gradle 8.12（项目已包含gradle wrapper，无需手动安装）


## 构建步骤

1. 克隆项目到本地：
   ```shell
   git clone https://github.com/letmeNo1/AndroidHierarchyDumper.git
   cd AndroidHierarchyDumper
   ```

2. 执行构建命令（支持Windows/macOS/Linux）：
   ```shell
   # Linux/macOS
   ./gradlew clean assemble assembleAndroidTest

   # Windows
   gradlew.bat clean assemble assembleAndroidTest
   ```

3. 构建成功后，APK文件位于：
   ```
   ./app/build/outputs/apk/
   ├── app-debug.apk           # 主应用APK（包名：nico.dump_hierarchy）
   └── app-debug-androidTest.apk  # 测试APK（包含核心功能实现）
   ```


## 安装与启动

### 安装APK
使用`adb`工具安装构建产出的APK：
```shell
# 安装主应用
adb install ./app/build/outputs/apk/app-debug.apk

# 安装测试应用（核心功能）
adb install ./app/build/outputs/apk/app-debug-androidTest.apk
```

### 启动服务
1. 配置端口转发（将设备端口映射到本地，方便网络交互）：
   ```shell
   adb forward tcp:8000 tcp:8000
   ```
   （默认端口为8000，可通过参数自定义，见下方API说明）

2. 启动测试服务：
   ```shell
   adb shell am instrument -w -e port 8000 nico.dump_hierarchy.test/androidx.test.runner.AndroidJUnitRunner
   ```
   启动成功后，服务将在设备上的`8000`端口运行，可通过本地`8000`端口访问。


## 接口说明

服务启动后，可通过HTTP请求调用以下接口（基础地址：`http://localhost:8000`）：

| 接口路径          | 方法   | 功能描述                     | 参数说明                                  | 返回值示例                                  |
|-------------------|--------|------------------------------|-------------------------------------------|---------------------------------------------|
| `/status`         | GET    | 检查服务状态                 | 无                                        | `server is running`                         |
| `/dump`           | GET    | 获取UI层级结构               | 无                                        | UI层级XML/JSON数据                          |
| `/screenshot`     | GET    | 获取屏幕截图                 | 无                                        | 图片二进制数据                              |
| `/is_ui_change`   | GET    | 检查UI是否发生变化           | 无                                        | `{"changed": true}`                         |
| `/click`          | GET    | 模拟点击坐标                 | `x`: 横坐标；`y`: 纵坐标                  | `Click at (x, y) success`                  |
| `/touch_down`     | GET    | 模拟触摸按下                 | `x`: 横坐标；`y`: 纵坐标                  | 操作结果                                    |
| `/touch_up`       | GET    | 模拟触摸抬起                 | `x`: 横坐标；`y`: 纵坐标                  | 操作结果                                    |
| `/touch_move`     | GET    | 模拟触摸滑动                 | `x`: 目标横坐标；`y`: 目标纵坐标          | 操作结果                                    |
| `/find_element`   | GET    | 查找单个UI元素               | 元素匹配参数（如`text`、`resourceId`等）  | 元素信息JSON                                |
| `/find_elements`  | GET    | 查找多个UI元素               | 元素匹配参数（如`text`、`resourceId`等）  | 元素列表JSON                                |
| `/input`          | POST   | 输入文本                     | 请求体：`{"text": "输入内容"}`            | 输入结果                                    |


## 权限说明

项目需要以下权限（已在`AndroidManifest.xml`中声明）：
- `android.permission.READ_SECURE_SETTINGS`：读取系统安全设置（用于获取部分UI信息）
- `android.permission.INTERNET`：网络交互（支持HTTP接口）

部分设备可能需要手动授予`READ_SECURE_SETTINGS`权限：
```shell
adb shell pm grant nico.dump_hierarchy android.permission.READ_SECURE_SETTINGS
```


## 项目结构

```
AndroidHierarchyDumper/
├── app/
│   ├── src/
│   │   ├── main/                 # 主应用代码
│   │   │   ├── AndroidManifest.xml  # 权限及应用配置
│   │   │   └── java/nico/dump_hierarchy/MainActivity.java  # 主界面（空实现）
│   │   └── androidTest/          # 测试代码（核心功能）
│   │       └── java/nico/dump_hierarchy/
│   │           ├── HierarchyTest.java  # HTTP服务及接口实现
│   │           └── TouchController.java  # 触摸事件模拟工具
│   └── build.gradle              # 模块构建配置
├── build.gradle                  # 项目全局构建配置
└── gradle/                       # Gradle wrapper配置
```


## 依赖库

- AndroidX 组件：`appcompat`、`constraintlayout`
- Material Design：`material`
- UI自动化：`androidx.test.uiautomator:uiautomator:2.2.0`
- JSON解析：`com.google.code.gson:gson:2.8.9`
- 测试框架：`junit`、`espresso`


## 注意事项

1. 服务仅在测试进程中运行，需通过`am instrument`命令启动
2. 部分功能（如UI层级dump）可能受设备系统版本或权限限制
3. 网络接口仅支持本地端口转发（通过`adb forward`），不直接支持远程网络访问
4. 模拟触摸操作的坐标为屏幕绝对坐标，需根据设备分辨率调整


## 扩展与定制

- 如需修改默认端口，启动服务时指定`-e port <端口号>`参数
- 可通过修改`HierarchyTest.java`扩展更多接口（如手势操作、应用切换等）
- 如需支持JSON格式的UI层级，可基于Gson扩展`/dump`接口的返回格式
