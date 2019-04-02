package com.malattas.ziadorlina

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.malattas.ziadorlina.classifier.*
import com.malattas.ziadorlina.utils.getCroppedBitmap
import com.malattas.ziadorlina.utils.getUriFromFilePath
import kotlinx.android.synthetic.main.activity_ziador_lina.*
import java.io.File
import java.io.IOException

class ZiadorLinaActivity : AppCompatActivity() {

    private var btn: Button? = null
    private var imageview: ImageView? = null
    private val GALLERY = 1
    private val CAMERA = 2

    private val PERMISSION_REQUEST_CAMERA = 100

    private val handler = Handler()
    private lateinit var classifier: Classifier
    private var photoFilePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ziador_lina)

        btn = findViewById<View>(R.id.btnSelectImage) as Button
        imageview = findViewById<View>(R.id.imagePhoto) as ImageView

        btn!!.setOnClickListener { showPictureDialog() }

        //checkPermissions()
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

    private fun createClassifier() {
        classifier = ImageClassifierFactory.create(
            assets,
            GRAPH_FILE_PATH,
            LABELS_FILE_PATH,
            IMAGE_SIZE,
            GRAPH_INPUT_NAME,
            GRAPH_OUTPUT_NAME
        )
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        createClassifier()

        if (requestCode == GALLERY)
        {
            if (data != null)
            {
                val contentURI = data.data
                /*try
                {
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
                }*/
            }
        }
        else if (requestCode == CAMERA)
        {
            takePhoto()
            val file = File(photoFilePath)
            if (file.exists()) {
                classifyPhoto(file)
            }
        }
    }

    private fun takePhoto() {
        photoFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/${System.currentTimeMillis()}.jpg"
        val currentPhotoUri = getUriFromFilePath(this, photoFilePath)

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
        takePictureIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, CAMERA)
        }
    }

    private fun classifyPhoto(file: File) {
        val photoBitmap = BitmapFactory.decodeFile(file.absolutePath)
        val croppedBitmap = getCroppedBitmap(photoBitmap)
        classifyAndShowResult(croppedBitmap)
        imagePhoto.setImageBitmap(photoBitmap)
    }

    private fun classifyAndShowResult(croppedBitmap: Bitmap) {
        runInBackground(
            Runnable {
                val result = classifier.recognizeImage(croppedBitmap)
                showResult(result)
            })
    }

    @Synchronized
    private fun runInBackground(runnable: Runnable) {
        handler.post(runnable)
    }

    private fun showResult(result: Result) {
        textResult.text = result.result.toUpperCase()
        textResult.setBackgroundColor(getColorFromResult(result.result))
        imagePhoto
    }

    @Suppress("DEPRECATION")
    private fun getColorFromResult(result: String): Int {
        return when (result) {
            "Ziad" -> resources.getColor(R.color.ziad)
            "Lina" -> resources.getColor(R.color.lina)
            "Mohammed" -> resources.getColor(R.color.mohammed)
            else -> {
                resources.getColor(R.color.unknown)
            }
        }
    }
}