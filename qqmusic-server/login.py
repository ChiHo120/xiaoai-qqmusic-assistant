"""Scan with QQ Music and save credentials locally for the bundled Web service."""
import asyncio
import json
import os
from pathlib import Path

from qqmusic_api import Client
from qqmusic_api.models.login import QRLoginType
from qqmusic_api.modules.login_utils import QRCodeLoginSession

ROOT = Path(__file__).resolve().parent
WEB = ROOT / "upstream" / "web"


async def main():
    accounts = WEB / "accounts.toml"
    if accounts.exists():
        raise SystemExit("accounts.toml 已存在。请先自行备份并移走旧文件，再重新登录。")
    os.chdir(ROOT / "upstream")
    async with Client() as client:
        session = QRCodeLoginSession(client.login, QRLoginType.MOBILE,
                                     interval=1.5, timeout_seconds=180.0)
        qr = await session.get_qrcode()
        qr_path = qr.save(ROOT / "login-qrcode")
        if not qr_path:
            raise SystemExit("未获得二维码，请稍后重试。")
        print(f"请用 QQ 音乐 App 扫码并确认登录：{qr_path}")
        if os.name == "nt":
            try:
                os.startfile(str(qr_path))
            except OSError:
                pass
        credential = await session.wait_qrcode_login()
        # TOML basic string containing JSON; no credentials are printed.
        with accounts.open("x", encoding="utf-8") as output:
            output.write("[[account]]\ncredential = " +
                         json.dumps(credential.model_dump_json(), ensure_ascii=False) + "\n")
        config = WEB / "config.toml"
        if not config.exists():
            with config.open("x", encoding="utf-8") as output:
                output.write('[credential]\nenabled = true\n'
                             'api = { song = ["get_song_urls", "get_song_url"] }\n')
        else:
            print("保留已有 config.toml；请确认 [credential] enabled = true。")
        print("登录凭证已保存到本机。请重启服务。不要分享 accounts.toml 或 web/data。")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("已取消登录。")
