package com.malattas.textrecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer
import kotlinx.android.synthetic.main.activity_text_recognition.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import kotlin.properties.Delegates

class TextRecognitionActivity : AppCompatActivity() {

    private var btn: Button? = null
    private var imageview: ImageView? = null
    private val GALLERY = 1
    private val CAMERA = 2

    private val PERMISSION_REQUEST_CAMERA = 100

    private var textRecognizer by Delegates.notNull<TextRecognizer>()
    private var mCameraSource by Delegates.notNull<CameraSource>()
    private var mFrame by Delegates.notNull<Frame>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_recognition)

        btn = findViewById<View>(R.id.btnSelectImage) as Button
        imageview = findViewById<View>(R.id.imageView) as ImageView

        btn!!.setOnClickListener { showPictureDialog() }
    }

    private fun showPictureDialog() {
        val pictureDialog = AlertDialog.Builder(this)
        pictureDialog.setTitle("Select Action")
        val pictureDialogItems = arrayOf("Select photo from gallery", "Capture photo from camera")
        pictureDialog.setItems(pictureDialogItems) {
                _, which ->
            when (which) {
                0 -> choosePhotoFromGallery()
                1 -> takePhotoFromCamera()
            }
        }
        pictureDialog.show()
    }

    private fun choosePhotoFromGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, GALLERY)
    }

    private fun takePhotoFromCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, CAMERA)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GALLERY)
        {
            if (data != null)
            {
                val contentURI = data.data
                try
                {
                    imageview!!.visibility = View.VISIBLE
                    val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, contentURI)
                    val newPath = FilePickUtils.getPath(this, contentURI)
                    val path = saveImage(bitmap)
                    val newBitmap = modifyOrientation(bitmap, newPath.orEmpty())
                    startFrane(newBitmap)

                    Toast.makeText(this@TextRecognitionActivity, "Image saved! $path ", Toast.LENGTH_SHORT).show()
                    imageview!!.setImageBitmap(newBitmap)

                }
                catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@TextRecognitionActivity, "Failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        else if (requestCode == CAMERA)
        {
            surface_camera_preview.visibility = View.VISIBLE
            startCameraSource()
//            val thumbnail = data!!.extras!!.get("data") as Bitmap
//            imageview!!.setImageBitmap(thumbnail)
//            saveImage(thumbnail)
//            Toast.makeText(this@TextRecognitionActivity, "Image saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun modifyOrientation(bitmap: Bitmap, image_absolute_path: String): Bitmap {
        val ei = ExifInterface(image_absolute_path)
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipImage(bitmap, true, false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipImage(bitmap, false, true)
            else -> bitmap
        }
    }

    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun flipImage(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix()
        matrix.preScale((if (horizontal) -1 else 1).toFloat(), (if (vertical) -1 else 1).toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun saveImage(myBitmap: Bitmap):String {
        val bytes = ByteArrayOutputStream()
        myBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
        val wallpaperDirectory = File(
            (Environment.getExternalStorageDirectory()).toString() + IMAGE_DIRECTORY)
        // have the object build the directory structure, if needed.
        Log.d("fee", wallpaperDirectory.toString())
        if (!wallpaperDirectory.exists())
        {
            wallpaperDirectory.mkdirs()
        }
        try {
            Log.d("heel",wallpaperDirectory.toString())
            val f = File(wallpaperDirectory, ((Calendar.getInstance()
                .timeInMillis).toString() + ".jpg"))
            f.createNewFile()
            val fo = FileOutputStream(f)
            fo.write(bytes.toByteArray())
            MediaScannerConnection.scanFile(this,
                arrayOf(f.path),
                arrayOf("image/jpeg"), null)
            fo.close()
            Log.d("TAG", "File Saved::--->" + f.absolutePath)

            return f.absolutePath
        }
        catch (e1: IOException) {
            e1.printStackTrace()
        }

        return ""
    }

    private fun startFrane(myBitmap: Bitmap) {
        // Create text Recognizer
        textRecognizer = TextRecognizer.Builder(this@TextRecognitionActivity).build()

        if (!textRecognizer.isOperational) {
            Toast.makeText(this@TextRecognitionActivity,
                "Dependencies are not loaded yet...please try after few moment!!", Toast.LENGTH_SHORT).show()
            return
        }

        mFrame = Frame.Builder()
            .setBitmap(myBitmap)
            .build()

        val textBlocks = textRecognizer.detect(mFrame)
        val stringBuilder = StringBuilder()
        for (i in 0 until textBlocks.size()) {
            val item = textBlocks.valueAt(i)
            stringBuilder.append(item.value)
            stringBuilder.append("\n")
        }

        tv_result.text = stringBuilder.toString()
    }

    private fun startCameraSource() {

        // Create text Recognizer
        textRecognizer = TextRecognizer.Builder(this@TextRecognitionActivity).build()

        if (!textRecognizer.isOperational) {
            Toast.makeText(this@TextRecognitionActivity,
                "Dependencies are not loaded yet...please try after few moment!!", Toast.LENGTH_SHORT).show()
            return
        }

        //  Init camera source to use high resolution and auto focus
        mCameraSource = CameraSource.Builder(applicationContext, textRecognizer)
            .setFacing(CameraSource.CAMERA_FACING_BACK)
            .setRequestedPreviewSize(1280, 1024)
            .setAutoFocusEnabled(true)
            .setRequestedFps(2.0f)
            .build()

        surface_camera_preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                mCameraSource.stop()
            }

            @SuppressLint("MissingPermission")
            override fun surfaceCreated(holder: SurfaceHolder?) {
                try {
                    if (isCameraPermissionGranted()) {
                        mCameraSource.start(surface_camera_preview.holder)
                    } else {
                        requestForPermission()
                    }
                } catch (e: Exception) {

                }
            }
        })

        textRecognizer.setProcessor(object : Detector.Processor<TextBlock> {
            override fun release() {}

            override fun receiveDetections(detections: Detector.Detections<TextBlock>) {
                val items = detections.detectedItems

                if (items.size() <= 0) {
                    return
                }

                tv_result.post {
                    val stringBuilder = StringBuilder()
                    for (i in 0 until items.size()) {
                        val item = items.valueAt(i)
                        stringBuilder.append(item.value)
                        stringBuilder.append("\n")
                    }
                    tv_result.text = stringBuilder.toString()
                }
            }
        })
    }

    fun isCameraPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(this@TextRecognitionActivity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestForPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CAMERA)
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != PERMISSION_REQUEST_CAMERA) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (isCameraPermissionGranted()) {
                mCameraSource.start(surface_camera_preview.holder)
            } else {

                finish()
            }
        }
    }

    companion object {
        private const val IMAGE_DIRECTORY = "/imageProcess"
    }
}
