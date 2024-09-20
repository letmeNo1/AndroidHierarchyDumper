# Nico 的依赖工程，用于支持在Android平台上运行

## 构建命令
项目根目录下，执行
```shell
./gradlew clean assemble assembleAndroidTest
```

## 构建输出，手动安装 apk 包
输出目录：./app/build/outputs/apk/
- app-debug-androidTest.apk
- app-debug.apk
  - PackageName: nico.dump_hierarchy

## apk运行情况
- 查看运行端口
  - adb forward --list
- 使用 socket 方式交互
