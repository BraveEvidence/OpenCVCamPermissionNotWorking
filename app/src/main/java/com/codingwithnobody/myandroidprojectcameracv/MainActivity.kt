package com.codingwithnobody.myandroidprojectcameracv

import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.CameraActivity
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import androidx.core.app.ActivityCompat

class MainActivity : CameraActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: JavaCameraView
    private lateinit var button: Button

    private var shouldCapture = false

    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = mutableListOf(android.Manifest.permission.CAMERA).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                if(OpenCVLoader.initLocal()){
                    Toast.makeText(this, "Permissions granted by the user.", Toast.LENGTH_LONG).show()
                    cameraView.visibility = View.VISIBLE
                    cameraView.enableView()
                    cameraView.setCvCameraViewListener(this@MainActivity)
                }

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        cameraView = findViewById(R.id.cameraView)
        button = findViewById(R.id.capture)


            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )


        button.setOnClickListener {
            shouldCapture = true
        }

    }

    override fun getCameraViewList(): MutableList<out CameraBridgeViewBase> {
        return Collections.singletonList(cameraView)
    }

    override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    override fun onResume() {
        super.onResume()
        cameraView.enableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        cameraView.setMaxFrameSize(width, height)
    }

    override fun onCameraViewStopped() {

    }

    private fun matToByteArray(mat: Mat, ext: String = ".png"): ByteArray {
        val converted = Mat()
        Imgproc.cvtColor(mat, converted, Imgproc.COLOR_RGBA2BGR)

        val buf = MatOfByte()
        Imgcodecs.imencode(ext, converted, buf)

        return buf.toArray()
    }


    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        val rgba = inputFrame!!.rgba()
        val center = Point(rgba.cols() / 2.0, rgba.rows() / 2.0)
        Imgproc.circle(rgba, center, 100, Scalar(255.0, 0.0, 0.0, 255.0), 5)

        if (shouldCapture) {
            shouldCapture = false
            val frame = rgba.clone()

            CoroutineScope(Dispatchers.Main).launch {
                val bytes = matToByteArray(frame, ".png")
                try {
                    val uri = saveImageToExternalStorage(
                        bytes = bytes,
                        fileName = "opencv_${System.currentTimeMillis()}",
                        extension = "png",
                        mimeType = "image/png"
                    )
                    Toast.makeText(this@MainActivity, "Saved to: $uri", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return rgba
    }

    private suspend fun saveImageToExternalStorage(
        bytes: ByteArray,
        fileName: String,
        extension: String,
        mimeType: String
    ): Uri = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$fileName.$extension")
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val resolver = contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uriSavedVideo = resolver.insert(collection, cv)

        uriSavedVideo?.let { uri ->
            resolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    ByteArrayInputStream(bytes).use { inputStream ->
                        val buffer = ByteArray(8192)
                        var length: Int
                        while (inputStream.read(buffer).also { length = it } > 0) {
                            out.write(buffer, 0, length)
                        }
                    }
                }
                // Update the content values to mark the video as not pending
                cv.clear()
                resolver.update(uri, cv, null, null)
            }
        }

        uriSavedVideo ?: throw IOException("Failed to create new MediaStore record.")
    }
}

//https://opencv.org/releases/
//File => nEw => Import Modules => Select sdk => Rename it to opencv
//Update minSdk and amxSdk of opencv gradle
//Update  sourceCompatibility JavaVersion.VERSION_21
//        targetCompatibility JavaVersion.VERSION_21
//File +> Project Structure => Dependencies =>Select All Modules => Click + => Select Module Dependency => Select app => Click ok =>
//https://github.com/opencv/opencv/issues/26129