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
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReverseOfficeConverter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val fileOutputManager: FileOutputManager
) {

    suspend fun convertPdfToDocx(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val doc = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val docx = XWPFDocument()

            for (pageNum in 1..doc.numberOfPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val pageText = stripper.getText(doc)
                val paragraphs = pageText.split("\n\n")
                for (para in paragraphs) {
                    val trimmed = para.trim()
                    if (trimmed.isNotEmpty()) {
                        val p = docx.createParagraph()
                        p.spacingAfter = 120 // 6pt
                        val r = p.createRun()
                        r.fontSize = 11
                        r.fontFamily = "Calibri"
                        r.setText(trimmed.replace("\n", " "))
                    }
                }
                if (pageNum < doc.numberOfPages) {
                    val p = docx.createParagraph()
                    p.isPageBreak = true
                }
            }
            doc.close()

            val outStream = ByteArrayOutputStream()
            docx.write(outStream)
            docx.close()

            val outputFileUri = fileOutputManager.saveToDefault(
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

            var slideCxEmu = 9144000L
            var slideCyEmu = 5143500L

            if (doc.numberOfPages > 0) {
                val firstPage = doc.getPage(0)
                val widthPt = firstPage.mediaBox.width
                val heightPt = firstPage.mediaBox.height
                slideCxEmu = (widthPt * 12700L).toLong()
                slideCyEmu = (heightPt * 12700L).toLong()

                try {
                    val ctPres = pptx.javaClass.getMethod("getCTPresentation").invoke(pptx)
                    val sldSz = ctPres?.javaClass?.getMethod("getSldSz")?.invoke(ctPres)
                        ?: ctPres?.javaClass?.getMethod("addNewSldSz")?.invoke(ctPres)
                    if (sldSz != null) {
                        sldSz.javaClass.getMethod("setCx", Long::class.javaPrimitiveType)?.invoke(sldSz, slideCxEmu)
                        sldSz.javaClass.getMethod("setCy", Long::class.javaPrimitiveType)?.invoke(sldSz, slideCyEmu)
                    }
                } catch (_: Throwable) { }
            }

            for (i in 0 until doc.numberOfPages) {
                val bitmap = renderer.renderImageWithDPI(i, 150f)
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val pictureData = pptx.addPicture(stream.toByteArray(), org.apache.poi.sl.usermodel.PictureData.PictureType.JPEG)
                val slide = pptx.createSlide()
                val pic = slide.createPicture(pictureData)

                // Set full-bleed slide bounds in OpenXML EMUs
                try {
                    val xmlObj = pic.javaClass.getMethod("getXmlObject").invoke(pic)
                    val spPr = xmlObj?.javaClass?.getMethod("getSpPr")?.invoke(xmlObj)
                    val xfrm = spPr?.javaClass?.getMethod("getXfrm")?.invoke(spPr)
                        ?: spPr?.javaClass?.getMethod("addNewXfrm")?.invoke(spPr)
                    if (xfrm != null) {
                        val off = xfrm.javaClass.getMethod("getOff")?.invoke(xfrm)
                            ?: xfrm.javaClass.getMethod("addNewOff")?.invoke(xfrm)
                        off?.javaClass?.getMethod("setX", Long::class.javaPrimitiveType)?.invoke(off, 0L)
                        off?.javaClass?.getMethod("setY", Long::class.javaPrimitiveType)?.invoke(off, 0L)

                        val ext = xfrm.javaClass.getMethod("getExt")?.invoke(xfrm)
                            ?: xfrm.javaClass.getMethod("addNewExt")?.invoke(xfrm)
                        ext?.javaClass?.getMethod("setCx", Long::class.javaPrimitiveType)?.invoke(ext, slideCxEmu)
                        ext?.javaClass?.getMethod("setCy", Long::class.javaPrimitiveType)?.invoke(ext, slideCyEmu)
                    }
                } catch (_: Throwable) { }
            }
            doc.close()

            val outStream = ByteArrayOutputStream()
            pptx.write(outStream)
            pptx.close()

            val outputFileUri = fileOutputManager.saveToDefault(
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
