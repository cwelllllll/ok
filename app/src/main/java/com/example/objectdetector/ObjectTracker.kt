package com.example.objectdetector

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.pow
import kotlin.math.sqrt

class ObjectTracker {

    private val trackedObjects = mutableMapOf<Int, TrackedObject>()
    private var nextId = 0

    // Maximum number of frames an object can be "lost" before being deregistered.
    private val maxFramesToLive = 10
    // Maximum distance between centroids to consider it the same object.
    private val maxCentroidDistance = 100.0

    data class TrackedObject(
        val id: Int,
        var boundingBox: RectF,
        var label: String,
        var score: Float,
        var framesWithoutDetection: Int = 0
    ) {
        val centroid: PointF
            get() = PointF(boundingBox.centerX(), boundingBox.centerY())
    }

    fun update(detections: List<ObjectDetectorHelper.DetectionResult>): List<TrackedObject> {
        // Increment frames without detection for all tracked objects.
        trackedObjects.values.forEach { it.framesWithoutDetection++ }

        if (detections.isEmpty()) {
            // No new detections, just return the current list after filtering old ones.
            deregisterOldObjects()
            return trackedObjects.values.toList()
        }

        if (trackedObjects.isEmpty()) {
            // No objects are being tracked yet, so register all new detections.
            detections.forEach { register(it) }
            return trackedObjects.values.toList()
        }

        // Match new detections with existing tracked objects.
        val usedDetectionIndices = mutableSetOf<Int>()

        for (trackedObject in trackedObjects.values) {
            var closestDetectionIndex = -1
            var minDistance = Double.MAX_VALUE

            for ((i, detection) in detections.withIndex()) {
                if (i in usedDetectionIndices) continue

                val distance = distance(trackedObject.centroid, getCentroid(detection.boundingBox))
                if (distance < minDistance) {
                    minDistance = distance
                    closestDetectionIndex = i
                }
            }

            if (closestDetectionIndex != -1 && minDistance < maxCentroidDistance) {
                val matchedDetection = detections[closestDetectionIndex]
                // Update the tracked object with the new detection's data.
                trackedObject.boundingBox = matchedDetection.boundingBox
                trackedObject.label = matchedDetection.label
                trackedObject.score = matchedDetection.score
                trackedObject.framesWithoutDetection = 0
                usedDetectionIndices.add(closestDetectionIndex)
            }
        }

        // Register new detections that were not matched.
        for ((i, detection) in detections.withIndex()) {
            if (i !in usedDetectionIndices) {
                register(detection)
            }
        }

        // Remove objects that have been lost for too long.
        deregisterOldObjects()

        return trackedObjects.values.toList()
    }

    private fun register(detection: ObjectDetectorHelper.DetectionResult) {
        trackedObjects[nextId] = TrackedObject(
            id = nextId,
            boundingBox = detection.boundingBox,
            label = detection.label,
            score = detection.score
        )
        nextId++
    }

    private fun deregisterOldObjects() {
        trackedObjects.entries.removeAll { it.value.framesWithoutDetection > maxFramesToLive }
    }

    private fun getCentroid(rect: RectF) = PointF(rect.centerX(), rect.centerY())

    private fun distance(p1: PointF, p2: PointF): Double {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2)).toDouble()
    }
}