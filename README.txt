将 .github 目录解压到 Flutter 项目根目录。
工作流：.github/workflows/android-arm64.yml

功能：
- Flutter 固定 3.44.4 stable
- JDK 21
- 仅构建 arm64-v8a Release APK
- 手动触发或 main 分支代码推送时触发
- 成功后在 Actions 页面下载 APK Artifact
