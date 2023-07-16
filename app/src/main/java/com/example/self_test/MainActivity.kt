package com.example.self_test


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.self_test.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val selectedImages = mutableListOf<Uri>()
    private val selectedPdfUris = mutableListOf<Uri>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchImagePicker()
            // openFilePicker()
        } else {
            Toast.makeText(
                this,
                "Permission to read external storage denied",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val openFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.forEach { uri ->
            selectedPdfUris.add(uri)
        }
        mergePdfFiles()
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.forEach { uri ->
            val imagePath = uri
            imagePath.let {
                selectedImages.add(it)
            }
        }
        convertImagesToPdf()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupListeners()

    }

    private fun setupListeners() {
        binding.imgToPdf.setOnClickListener {
            checkPermissionAndPickImages()
        }
        binding.splitPdf.setOnClickListener {
            splitPdf()
        }

        binding.mergePdf.setOnClickListener {
            checkPermissionAndOpenFilePicker()
        }

        binding.txtToPdf.setOnClickListener {
            showConvertDialog()
        }
    }

    private fun checkPermissionAndPickImages() {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        val permissionGranted = ContextCompat.checkSelfPermission(this, permission)
        if (permissionGranted == PackageManager.PERMISSION_GRANTED) {
            launchImagePicker()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun checkPermissionAndOpenFilePicker() {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        val permissionGranted = ContextCompat.checkSelfPermission(this, permission)
        if (permissionGranted == PackageManager.PERMISSION_GRANTED) {
            openFilePicker()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun launchImagePicker() {
        pickImagesLauncher.launch("image/*")
    }

    private fun openFilePicker() {
        openFilePickerLauncher.launch("application/pdf")
    }

    private fun convertImagesToPdf() {
        val outputDir = File("${getExternalFilesDir(null)?.absolutePath}/convertImageToPdf/")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputPdfFile = File(outputDir.path.toString() + "/output.pdf")

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                withContext(Dispatchers.IO) {
                    val pdfDocument = PdfDocument()
                    selectedImages.forEach { imagePath ->
                        val bitmap =
                            BitmapFactory.decodeStream(contentResolver.openInputStream(imagePath))
                        if (bitmap != null) {
                            val pageInfo = PdfDocument.PageInfo.Builder(
                                bitmap.width,
                                bitmap.height,
                                selectedImages.indexOf(imagePath) + 1
                            ).create()
                            val page = pdfDocument.startPage(pageInfo)
                            val canvas = page.canvas
                            canvas.drawBitmap(bitmap, 0f, 0f, null)
                            pdfDocument.finishPage(page)
                        }
                    }
                    val fileOutputStream = FileOutputStream(outputPdfFile)
                    pdfDocument.writeTo(fileOutputStream)
                    fileOutputStream.flush()
                    fileOutputStream.close()
                    pdfDocument.close()
                }
                Toast.makeText(this@MainActivity, "PDF created successfully!", Toast.LENGTH_SHORT)
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error creating PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun splitPdf() {
        val inputFile =
            File("${getExternalFilesDir(null)?.absolutePath}/convertImageToPdf/output.pdf")
        val outputDir = File("${getExternalFilesDir(null)?.absolutePath}/split/")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                withContext(Dispatchers.IO) {
                    val parcelFileDescriptor =
                        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(parcelFileDescriptor)
                    val pageCount = pdfRenderer.pageCount

                    for (pageIndex in 0 until pageCount) {
                        val page = pdfRenderer.openPage(pageIndex)
                        val outputFilePath = "${outputDir}/Page_${pageIndex + 1}.pdf"
                        val outputFile = File(outputFilePath)

                        val outputDocument = PdfDocument()
                        val outputPage = outputDocument.startPage(
                            PdfDocument.PageInfo.Builder(
                                page.width,
                                page.height,
                                pageIndex
                            ).create()
                        )
                        val canvas = outputPage.canvas
                        val bitmap =
                            Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        outputDocument.finishPage(outputPage)

                        val outputStream = FileOutputStream(outputFile)
                        outputDocument.writeTo(outputStream)
                        outputStream.close()
                        outputDocument.close()

                        page.close()
                    }

                    pdfRenderer.close()
                    parcelFileDescriptor.close()
                }

                Toast.makeText(this@MainActivity, "PDF split successfully!", Toast.LENGTH_SHORT)
                    .show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error splitting PDF", Toast.LENGTH_SHORT).show()
            }

        }
    }

    private fun mergePdfFiles() {
        if (selectedPdfUris.size < 2) {
            Toast.makeText(this, "Please select at least 2 PDF files", Toast.LENGTH_SHORT).show()
            return
        }

        val mergedPdfFile = createMergedPdfFile()

        if (mergedPdfFile != null) {
            lifecycleScope.launch(Dispatchers.Main) {
                try {
                    withContext(Dispatchers.IO) {
                        val outputPdfDocument = PdfDocument()

                        selectedPdfUris.forEach { uri ->
                            val inputStream = contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                val inputPfd: ParcelFileDescriptor? =
                                    contentResolver.openFileDescriptor(uri, "r")
                                if (inputPfd != null) {
                                    val renderer = PdfRenderer(inputPfd)
                                    val pageCount = renderer.pageCount

                                    for (pageIndex in 0 until pageCount) {
                                        val page = renderer.openPage(pageIndex)
                                        val bitmap = Bitmap.createBitmap(
                                            page.width,
                                            page.height,
                                            Bitmap.Config.ARGB_8888
                                        )
                                        page.render(
                                            bitmap,
                                            null,
                                            null,
                                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                        )
                                        val newPage = outputPdfDocument.startPage(
                                            PdfDocument.PageInfo.Builder(
                                                page.width,
                                                page.height,
                                                pageIndex
                                            ).create()
                                        )
                                        val canvas = newPage.canvas
                                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                                        outputPdfDocument.finishPage(newPage)
                                        page.close()
                                    }

                                    renderer.close()
                                    inputPfd.close()
                                }
                            }
                        }

                        val outputStream = FileOutputStream(mergedPdfFile)
                        outputPdfDocument.writeTo(outputStream)
                        outputStream.flush()
                        outputStream.close()
                        outputPdfDocument.close()
                    }

                    Toast.makeText(
                        this@MainActivity,
                        "PDF files merged successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Error merging PDF files", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        } else {
            Toast.makeText(this, "Error creating merged PDF file", Toast.LENGTH_SHORT).show()
        }
    }



    private fun createMergedPdfFile(): File? {
        val outputDir = File("${getExternalFilesDir(null)?.absolutePath}/merged_files/")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val mergedPdfFileName = "merged_pdf_file.pdf"

        return try {
            File(outputDir, mergedPdfFileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showConvertDialog() {
        val dialogView: View = LayoutInflater.from(this).inflate(R.layout.dialog_convert, null)
        val editText: EditText = dialogView.findViewById(R.id.edit_text)
        val convertButton: Button = dialogView.findViewById(R.id.convert_button)

        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Convert Text to PDF")

        val alertDialog = dialogBuilder.create()

        convertButton.setOnClickListener {
            val text = editText.text.toString().trim()
            if (TextUtils.isEmpty(text)) {
                editText.error = "Please enter some text"
            } else {
                generatePdfFromText(text)
                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }

    private fun generatePdfFromText(text: String) {

        // Create a new PdfDocument
        val pdfDocument = PdfDocument()

        // Define the A4 page size in points
        val a4PageWidth = 595
        val a4PageHeight = 842

        // Create a page info with the A4 page size
        val pageInfo = PdfDocument.PageInfo.Builder(a4PageWidth, a4PageHeight, 1).create()

        // Start a new page
        val page = pdfDocument.startPage(pageInfo)

        // Create a canvas from the page
        val canvas = page.canvas

        // Set the text attributes
        val textPaint = TextPaint()
        textPaint.color = Color.BLACK
        textPaint.textSize = 12f

        // Calculate the available width and height for the text
        val availableWidth = a4PageWidth - 72 // Leave 1-inch margin on each side
        val availableHeight = a4PageHeight - 72 // Leave 1-inch margin on each side

        // Create a StaticLayout for the text
        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .setEllipsize(null)
                .setMaxLines(Int.MAX_VALUE)
                .build()
        } else {
            StaticLayout(
                text,
                textPaint,
                availableWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1f,
                0f,
                false
            )
        }

        // Calculate the text position
        val x = 36f // Left margin
        val y = 36f // Top margin

        // Draw the StaticLayout on the canvas
        canvas.save()
        canvas.translate(x, y)
        staticLayout.draw(canvas)
        canvas.restore()

        // Finish the page
        pdfDocument.finishPage(page)

        // Generate the output file path
        val outputDir = File("${getExternalFilesDir(null)?.absolutePath}/textToPdf/")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(
            outputDir,
            "text_to_pdf.pdf"
        )

        // Write the PDF document to the output file
        val fileOutputStream = FileOutputStream(outputFile)
        pdfDocument.writeTo(fileOutputStream)

        // Close the document and file output stream
        pdfDocument.close()
        fileOutputStream.close()
        Toast.makeText(this@MainActivity, "Pdf File Created Successfully", Toast.LENGTH_SHORT)
            .show()
    }

}

