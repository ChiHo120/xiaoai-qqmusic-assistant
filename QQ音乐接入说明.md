# QQ 音乐语音点歌

安卓端已接入你指定的 https://github.com/l-1124/QQMusicApi 。Python 服务代码放在 `qqmusic-server/upstream`，固定版本为 `27861e51432ea6b6e88dc35c4e8c8e239c0339e8`，保留上游源码与 GPL-3.0-or-later 许可证。

## 使用

1. 电脑安装 uv（https://docs.astral.sh/uv/getting-started/installation/），在本项目根目录执行 `powershell -ExecutionPolicy Bypass -File .\qqmusic-server\start.ps1 -Install`。后续启动无需 `-Install`。电脑必须保持服务运行。
2. 手机和电脑连同一个局域网。在手机 App 设置的「QQMusicApi 服务地址」填写 `http://电脑局域网IP:8080`，点击「保存 QQ 音乐接入」。真机不能填 localhost；安卓模拟器可以用 `http://10.0.2.2:8080`。服务接口说明在 `http://电脑IP:8080/swagger`。若连接失败，检查电脑防火墙是否允许私有网络的 8080 端口。
3. 首次安装依赖后，另开 PowerShell，在项目根目录执行 `.\qqmusic-server\.venv\Scripts\python.exe .\qqmusic-server\login.py`，用 QQ 音乐 App 扫描弹出的二维码并确认登录。工具会保存 `upstream/web/accounts.toml`，并在没有配置文件时创建启用凭证的 `web/config.toml`；如已有配置，请自行确认 `[credential]` 的 `enabled = true`，且 `api = { song = ["get_song_urls", "get_song_url"] }`。然后重启服务。工具不会覆盖已有账号文件；更换账号前先自行备份并移走旧文件。登录凭证留在电脑，不写进 APK，不要分享账号文件或 `web/data`。已有 QQ 音乐 App 的登录不会自动传给本服务。
4. 点击「测试 QQ 音乐接入」，然后说「小爱同学，播放周杰伦的晴天」或「小爱同学，我想听晴天」。测试使用晴天，可能因账号权限无法取得链接；这不等同于服务完全不可用。

## 播放行为

配置服务后：语音识别 → 提取歌名和歌手 → QQMusicApi 搜索 → 获取 CDN 和标准 MP3 授权地址 → App 内自动播放。优先同名歌曲，最多尝试三个搜索结果，播放器失败则换候选。支持原有暂停、继续、停止指令。停止或再次点歌会取消旧搜索，避免旧歌曲突然播放。

未取得授权链接或候选播放失败时，回退到已安装的音乐应用搜索。未填写服务地址时，保留原有 QQ 音乐跳转和公开试听行为。清空并保存服务地址即可停用新接入。

歌曲是否能完整播放取决于 QQ 音乐返回的权限，会员、地区、单曲购买和设备限制仍然有效；该开源项目不是 QQ 音乐官方授权 API，不提供破解、解密或会员绕过。服务目前用于个人可信局域网，不要直接公开账号凭证服务到互联网。

## 验证与许可

安卓编译及 13 项单元测试通过；接口契约测试覆盖搜索参数、媒体 MID、CDN 拼接、无权限/空地址过滤与服务错误。真实服务已验证能搜索到周杰伦《晴天》，CDN 接口正常；未登录时该歌曲返回 104003 和空播放地址，已确认应用会过滤。扫码登录流程及完整音乐播放仍需你自己的账号和真机验证。

上游源码与许可证在 `qqmusic-server/upstream/LICENSE`；分发服务时请遵守该许可证并保留源码。
