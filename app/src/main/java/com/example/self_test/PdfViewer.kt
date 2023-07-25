package com.example.self_test

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PdfViewer : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var pdfRenderer: PdfRenderer
    private val pdfPageCache: HashMap<Int, Bitmap> = HashMap()

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                handleSelectedPdf(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        recyclerView = findViewById(R.id.recyclerViewPdf)
        recyclerView.layoutManager = LinearLayoutManager(this)


            filePickerLauncher.launch("application/pdf")

    }

    private fun handleSelectedPdf(uri: Uri) {
        try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            if (parcelFileDescriptor != null) {
                pdfRenderer = PdfRenderer(parcelFileDescriptor)
                val pdfPageAdapter = PdfPageAdapter(pdfRenderer)
                recyclerView.adapter = pdfPageAdapter
            } else {
                Toast.makeText(this, "Unable to open the PDF file.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening the PDF file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class PdfPageAdapter(private val pdfRenderer: PdfRenderer) :
        RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.pdf_page_item, parent, false)
            return PdfPageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
            val bitmap = pdfPageCache[position]
            if (bitmap != null) {
                holder.imageView.setImageBitmap(bitmap)
            } else {
                // If the bitmap is not cached, render the page and store it in the cache
                val page = pdfRenderer.openPage(position)
                val newBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(newBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                holder.imageView.setImageBitmap(newBitmap)
                pdfPageCache[position] = newBitmap
                page.close()
            }
        }

        override fun getItemCount(): Int {
            return pdfRenderer.pageCount
        }

        inner class PdfPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.imageView)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::pdfRenderer.isInitialized) {
            pdfRenderer.close()
        }

        // Clear the cache when the activity is destroyed
        pdfPageCache.values.forEach { bitmap -> bitmap.recycle() }
        pdfPageCache.clear()
    }
}
