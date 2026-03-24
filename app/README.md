# Android-приложение (`app`)

## Назначение

Клиент выполняет:

- выбор нескольких изображений из памяти телефона;
- распознавание объекта на каждом фото через on-device нейросетевую модель (`TensorFlow Lite`);
- агрегацию предсказаний по всем выбранным фото в один итоговый класс;
- подготовку файлов для отправки (копирование `Uri` → `File`, ZIP-архивация);
- отправку архива на сервер;
- получение 3D-модели и отображение в `WebView`.

## Ключевые классы

- `MainActivity.kt` — UI-экран, orchestration классификации + пайплайна отправки/получения.
- `data/UploadRepository.kt` — HTTP-загрузка архива и получение бинарного ответа.
- `ml/ShapeClassifier.kt` — запуск TFLite-модели `shape_classifier.tflite` по каждому изображению.
- `ml/ShapeAggregator.kt` — итоговый вывод по всем фото (majority vote + mean confidence).
- `utils/UriFileMapper.kt` — преобразование `Uri` в временный файл.
- `utils/ZipUtil.kt` — упаковка изображений в ZIP.

## Файлы модели

- `assets/shape_classifier.tflite` — pre-trained TFLite-модель для классов фигур.
- `assets/shape_labels.txt` — список меток классов (sphere, cube, pyramid, cylinder, cone).

> В репозитории добавлен `shape_labels.txt` и код inference. Файл `shape_classifier.tflite` нужно положить в `assets/` (готовую предобученную модель, без обучения с нуля).

## Рекомендации для Android Studio

1. Убедитесь, что сервер доступен из сети устройства/эмулятора.
2. Проверьте `SERVER_URL` в `MainActivity.kt`.
3. Для локальной отладки используйте устройство и сервер в одной сети.
4. До запуска приложения убедитесь, что `shape_classifier.tflite` находится в `assets/`.
