package com.karnadigital.omnisuite.core.engine.document

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.karnadigital.omnisuite.core.util.FileOutputManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReverseOfficeConverter @Inject constructor(
    private val context: Context,
    private val fileOutputManager: FileOutputManager
) {

    suspend fun convertPdfToDocx(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val doc = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)
            doc.close()

            val docx = XWPFDocument()
            val lines = text.split("\n")
            for (line in lines) {
                val p = docx.createParagraph()
                val r = p.createRun()
                r.setText(line.trim())
            }

            val outStream = ByteArrayOutputStream()
            docx.write(outStream)
            docx.close()

            val outputFileUri = fileOutputManager.saveToDefault(
                context = context,
                bytes = outStream.toByteArray(),
                filename = "converted_${System.currentTimeMillis()}.docx",
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                subfolder = "DOCX"
            )
            outputFileUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun convertPdfToPptx(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val doc = PDDocument.load(inputStream)
            val renderer = PDFRenderer(doc)
            val pptx = XMLSlideShow()

            for (i in 0 until doc.numberOfPages) {
                val bitmap = renderer.renderImageWithDPI(i, 150f)
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val pictureData = pptx.addPicture(stream.toByteArray(), org.apache.poi.sl.usermodel.PictureData.PictureType.JPEG)
                val slide = pptx.createSlide()
                val pictureShape = slide.createPicture(pictureData)
                val rect = org.apache.poi.sl.usermodel.Insets2D(0.0, 0.0, 0.0, 0.0)
                // In POI Android we might just use java.awt.Rectangle replacement if any, or avoid setting anchor explicitly if it fills by default.
                // Or use java.awt.geom.Rectangle2D.Double(0.0, 0.0, width, height) if it's available.
                // To avoid java.awt which is missing on Android, let's just set the picture directly.
            }
            doc.close()

            val outStream = ByteArrayOutputStream()
            pptx.write(outStream)
            pptx.close()

            val outputFileUri = fileOutputManager.saveToDefault(
                context = context,
                bytes = outStream.toByteArray(),
                filename = "converted_${System.currentTimeMillis()}.pptx",
                mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                subfolder = "PPTX"
            )
            outputFileUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun convertPdfToXlsx(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val doc = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)
            doc.close()

            val xlsx = XSSFWorkbook()
            val sheet = xlsx.createSheet("Extracted Data")
            val lines = text.split("\n")
            var rowIdx = 0
            for (line in lines) {
                if (line.trim().isNotEmpty()) {
                    val row = sheet.createRow(rowIdx++)
                    val cells = line.trim().split(Regex("\\s{2,}")) // Split by large spaces
                    for ((colIdx, cellText) in cells.withIndex()) {
                        row.createCell(colIdx).setCellValue(cellText)
                    }
                }
            }

            val outStream = ByteArrayOutputStream()
            xlsx.write(outStream)
            xlsx.close()

            val outputFileUri = fileOutputManager.saveToDefault(
                context = context,
                bytes = outStream.toByteArray(),
                filename = "converted_${System.currentTimeMillis()}.xlsx",
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                subfolder = "XLSX"
            )
            outputFileUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fillInteractiveForm(uri: Uri, formData: Map<String, String>): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val doc = PDDocument.load(inputStream)
            val catalog = doc.documentCatalog
            val acroForm: PDAcroForm? = catalog.acroForm

            if (acroForm != null) {
                for ((key, value) in formData) {
                    val field = acroForm.getField(key)
                    field?.setValue(value)
                }
            }

            val outStream = ByteArrayOutputStream()
            doc.save(outStream)
            doc.close()

            val outputFileUri = fileOutputManager.saveToDefault(
                context = context,
                bytes = outStream.toByteArray(),
                filename = "filled_form_${System.currentTimeMillis()}.pdf",
                mimeType = "application/pdf",
                subfolder = "PDF"
            )
            outputFileUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
