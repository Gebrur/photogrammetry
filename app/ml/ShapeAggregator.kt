package com.example.photogrammetryapp.ml

object ShapeAggregator {

    /**
     * Возвращает итоговый класс, который чаще всего встречался на выбранных фото.
     * Если классы совпали по голосам — выбираем класс с большей средней уверенностью.
     */
    fun aggregate(predictions: List<ShapePrediction>): ShapePrediction? {
        if (predictions.isEmpty()) return null

        val grouped = predictions.groupBy { it.label }

        val winner = grouped
            .map { (label, items) ->
                val meanConfidence = items.map { it.confidence }.average().toFloat()
                Triple(label, items.size, meanConfidence)
            }
            .sortedWith(
                compareByDescending<Triple<String, Int, Float>> { it.second }
                    .thenByDescending { it.third }
            )
            .firstOrNull()

        return winner?.let { ShapePrediction(it.first, it.third) }
    }
}
