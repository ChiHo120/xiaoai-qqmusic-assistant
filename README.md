# 小爱同学 · QQ 音乐语音助手

Android 语音助手，使用 Kotlin / Jetpack Compose 构建，通过 QQMusicApi 服务实现语音点歌与 App 内播放。

## 功能

- 识别“播放周杰伦的晴天”“我想听晴天”等点歌指令。
- 搜索歌曲、获取账号有权访问的音频地址并在手机播放。
- 支持暂停、继续、停止；无法获取音频时尝试跳转音乐应用。
- 服务端保存 QQ 音乐登录凭证，不将账号写入 APK。

## 获取源码

```bash
git clone --recurse-submodules https://github.com/ChiHo120/xiaoai-qqmusic-assistant.git
```

已克隆的仓库执行 `git submodule update --init --recursive`。

## 使用

详见 [QQ 音乐接入说明](QQ音乐接入说明.md)。Windows 下运行 `qqmusic-server/start.ps1` 启动服务，使用 `qqmusic-server/login.py` 扫码登录，然后在手机设置填写服务地址。手机与服务器需要网络可达。不要将带账号凭证的服务直接暴露到公网。

服务可以部署到独立设备；Ubuntu ARM64 树莓派部署尚未实测，当前启动脚本为 Windows PowerShell，需要另行配置 Linux 启动与自启。

## Android 构建

使用 Android Studio 打开项目，配置 JDK 17、Android SDK 34。项目使用 Gradle 8.7；已有 Gradle 环境可执行：

```bash
gradle assembleDebug testDebugUnitTest
```

Windows 也可使用项目中的 `build.ps1`。最低支持 Android 8.0（API 26）。APK、构建缓存和本机 SDK 路径不纳入源码仓库。

## 验证状态

Android 构建及 13 项单元测试通过，真实歌曲搜索和 CDN 接口已验证。账号扫码登录与授权后的完整播放仍需用户账号和真机验证。

## 上游与权限

QQMusicApi 来源：[l-1124/QQMusicApi](https://github.com/l-1124/QQMusicApi)，通过子模块固定版本，许可证见其 LICENSE（GPL-3.0-or-later）。遵守上游许可与音乐服务条款。会员、购买、地区和账号限制仍然有效，本项目不提供会员或 DRM 绕过。
