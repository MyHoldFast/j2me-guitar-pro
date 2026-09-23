# GTab server

Server for the GTab app: search in the gtp-tabs.ru archive, tab download, GPX/GP (Guitar Pro 6–8) → GP5 conversion. Python, plain TCP socket, port 7000.

## Setup

```bash
cd gtabsrv
chmod +x *.sh
pip install PyGuitarPro

wget -qO- "https://www.gtp-tabs.ru/n/upload/fileManager/files/gtptabs.com.tgz" | ./tgz2zip.sh - tabs.zip
./build_index.sh
./run.sh
```

`tgz2zip.sh` repacks the archive into `tabs.zip` (~380 MB) without unpacking it to disk. `build_index.sh` creates `index.tsv` (3–4 min).

## Autostart (systemd)

Replace the paths with yours (`echo $VIRTUAL_ENV` shows the Python venv path):

```ini
# /etc/systemd/system/gtabsrv.service
[Unit]
Description=GTab tab server
After=network.target

[Service]
WorkingDirectory=/root/gtabsrv
ExecStart=/root/venv/bin/python3 server.py tabs.zip index.tsv cache 0.0.0.0 7000
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now gtabsrv
```

Logs: `journalctl -u gtabsrv -f`. Don't forget to open port 7000 in the firewall.

## Phone

GTab → Menu → Settings → Server: `your.ip:7000`.

---

# Сервер GTab (RU)

Сервер для приложения GTab: поиск по архиву gtp-tabs.ru, скачивание табов, конвертация GPX/GP (Guitar Pro 6–8) в GP5. Python, обычный TCP-сокет, порт 7000.

## Установка

```bash
cd gtabsrv
chmod +x *.sh
pip install PyGuitarPro

wget -qO- "https://www.gtp-tabs.ru/n/upload/fileManager/files/gtptabs.com.tgz" | ./tgz2zip.sh - tabs.zip
./build_index.sh
./run.sh
```

`tgz2zip.sh` перепаковывает архив в `tabs.zip` (~380 МБ), не распаковывая его на диск. `build_index.sh` строит `index.tsv` (3–4 минуты).

## Автозапуск (systemd)

Подставьте свои пути (путь к venv покажет `echo $VIRTUAL_ENV`):

```ini
# /etc/systemd/system/gtabsrv.service
[Unit]
Description=GTab tab server
After=network.target

[Service]
WorkingDirectory=/root/gtabsrv
ExecStart=/root/venv/bin/python3 server.py tabs.zip index.tsv cache 0.0.0.0 7000
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now gtabsrv
```

Логи: `journalctl -u gtabsrv -f`. Не забудьте открыть порт 7000 в firewall.

## Телефон

GTab → Меню → Настройки → Сервер: `ваш.ip:7000`.