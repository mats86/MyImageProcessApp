package com.malattas.ziadorlina.classifier

import android.graphics.Bitmap

interface Classifier {
    fun recognizeImage(bitmap: Bitmap): Result
}