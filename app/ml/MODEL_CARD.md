# Shape classifier model contract

Приложение ожидает pre-trained TensorFlow Lite модель в файле:

- `app/assets/shape_classifier.tflite`

## Требования к модели

- Тип: image classification (single-label)
- Input: `FLOAT32`, `[1, 224, 224, 3]`
- Preprocessing: normalize `x / 255.0`
- Output: `FLOAT32`, `[1, N]` вероятности классов
- Labels: должны соответствовать строкам в `app/assets/shape_labels.txt`

## Целевые классы

- sphere
- cube
- pyramid
- cylinder
- cone

## Почему такой формат

Этот контракт позволяет быстро заменить модель без изменения бизнес-логики:

- `ShapeClassifier` отвечает за inference одной фотографии;
- `ShapeAggregator` делает итоговый вывод по набору фотографий пользователя.

## Примеры готовых моделей (без обучения с нуля)

Ниже варианты, которые подходят для нашей задачи распознавания простых фигур:

1. **TensorFlow Lite Model Maker ImageClassifier (EfficientNet-Lite0, transfer learning)**  
   - Берётся готовый backbone EfficientNet-Lite0 и дообучается только классификатор под `sphere/cube/pyramid/...`.  
   - Плюс: высокая точность для небольшого датасета фигур, экспорт напрямую в `.tflite`.

2. **MobileNetV3-Small / MobileNetV2 (transfer learning, TFLite)**  
   - Лёгкая и быстрая модель для on-device inference.  
   - Плюс: хороший баланс скорости/качества для телефонов среднего уровня.

3. **NASNetMobile / EfficientNet-Lite (custom head, экспорт в TFLite)**  
   - Подходит, если в будущем добавим больше классов (например, torus/prism и т.д.).  
   - Плюс: лучше масштабируется при росте набора фигур.

### Рекомендуемый выбор для текущего приложения

**MobileNetV3-Small (или EfficientNet-Lite0) с transfer learning и экспортом в TFLite**:  
- быстро работает на Android;  
- не требует обучения с нуля;  
- легко подгоняется под наши 5 классов фигур.
