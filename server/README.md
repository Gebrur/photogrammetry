# Сервер (`server`)

## Назначение

Flask-сервер принимает ZIP с изображениями, запускает пайплайн COLMAP и возвращает результат реконструкции.

## Компоненты

- `Server.py`:
  - endpoint `POST /upload`;
  - распаковка ZIP;
  - запуск COLMAP (`automatic_reconstructor`);
  - конвертация `PLY -> GLB` через `trimesh`;
  - отдача файла клиенту.
- `test_server.py`:
  - базовые тесты API и сценариев ошибок;
  - мокирование запуска COLMAP и конвертации.

## Быстрый старт в PyCharm

1. Откройте папку `server/` как Python-проект.
2. Создайте виртуальное окружение (Python 3.10+).
3. Установите зависимости:
   ```bash
   pip install -r requirements.txt
   ```
4. Запустите сервер:
   ```bash
   python Server.py
   ```

## Запуск тестов

```bash
pytest -q
```

## Примечание по COLMAP

В `Server.py` путь до `COLMAP.bat` сейчас зашит константой:

```python
COLMAP_BIN = r"D:\colmap\COLMAP.bat"
```

Перед использованием замените путь под свою машину/окружение.
