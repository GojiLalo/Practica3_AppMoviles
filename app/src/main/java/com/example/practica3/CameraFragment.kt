package com.example.practica3

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.icu.text.SimpleDateFormat
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.printStackTrace

class CameraFragment : Fragment() {

    private lateinit var textureView: TextureView
    private lateinit var captureButton: Button
    private lateinit var imageView: ImageView

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private val cameraManager by lazy {
        requireContext().getSystemService(CameraManager::class.java)
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            requireActivity().finish()
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        textureView = view.findViewById(R.id.textureView)
        captureButton = view.findViewById(R.id.captureButton)
        imageView = view.findViewById(R.id.imageView)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Verificar permisos
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }

        captureButton.setOnClickListener {
            takePicture()
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (textureView.isAvailable) {
                    openCamera()
                } else {
                    textureView.surfaceTextureListener = textureListener
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permiso de cámara denegado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper ?: Looper.getMainLooper())
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0] // Usa la cámara trasera por defecto
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(
                textureView.width,
                textureView.height
            )
            val surface = Surface(texture)

            val captureRequestBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )
            captureRequestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        captureRequestBuilder?.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        )
                        session.setRepeatingRequest(
                            captureRequestBuilder?.build()!!,
                            null,
                            backgroundHandler
                        )
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(
                            requireContext(),
                            "Error al configurar la cámara",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun takePicture() {
        if (cameraDevice == null) return

        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)

            val captureRequestBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE
            )
            captureRequestBuilder?.addTarget(surface)

            cameraCaptureSession?.capture(
                captureRequestBuilder?.build()!!,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        activity?.runOnUiThread {
                            val bitmap = textureView.bitmap
                            if (bitmap != null) {
                                imageView.setImageBitmap(bitmap)
                                imageView.visibility = View.VISIBLE
                                saveImage(bitmap) // Guardar la imagen
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Error al capturar la imagen",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun saveImage(bitmap: Bitmap): File? {
        return try {
            // Crear directorio si no existe
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (!storageDir?.exists()!!) {
                storageDir.mkdirs()
            }

            // Crear archivo con nombre único
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFile = File(storageDir, "IMG_${timeStamp}.jpg")

            // Guardar bitmap como JPEG
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Notificar a la galería (opcional)
            MediaScannerConnection.scanFile(
                requireContext(),
                arrayOf(imageFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )

            imageFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            CameraFragment().apply {
                arguments = Bundle().apply {
                    putString("param1", param1)
                    putString("param2", param2)
                }
            }
    }

}