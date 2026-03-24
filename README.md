# Photogrammetry

Монорепозиторий состоит из двух частей:

- `app/` — Android-клиент для выбора фото, упаковки в ZIP, отправки на сервер и просмотра результата.
- `server/` — Flask-сервер, который принимает архив изображений, запускает реконструкцию COLMAP и отдает модель (`.glb` / `.ply`).

## Структура

```text
photogrammetry/
├── app/
│   ├── MainActivity.kt
│   ├── data/UploadRepository.kt
│   └── utils/
│       ├── UriFileMapper.kt
│       └── ZipUtil.kt
├── server/
│   ├── Server.py
│   ├── test_server.py
│   └── requirements.txt
└── README.md
```

## Поток данных

1. В Android-приложении выбираются изображения.
2. Изображения копируются из `Uri` во временные файлы и архивируются в ZIP.
3. ZIP отправляется на `POST /upload`.
4. Сервер распаковывает архив, подготавливает структуру `images/`, запускает COLMAP.
5. Сервер ищет `.ply`, пытается конвертировать в `.glb`, после чего отправляет файл клиенту.
6. Клиент показывает модель в `WebView` через `<model-viewer>`.

## Разработка в IDE

- **Android Studio**: открывать каталог `app/` как Android-проект (или корень репозитория, если в нем добавятся Gradle-файлы).
- **PyCharm**: открывать каталог `server/` как Python-проект, использовать виртуальное окружение и зависимости из `server/requirements.txt`.

Подробные инструкции находятся в:

- `app/README.md`
- `server/README.md`
