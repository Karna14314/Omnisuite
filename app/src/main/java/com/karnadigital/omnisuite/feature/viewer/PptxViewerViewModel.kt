package com.karnadigital.omnisuite.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.model.RecentFile
import com.karnadigital.omnisuite.core.repository.RecentFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.sl.usermodel.Placeholder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject

data class PptxTextBlock(
    val id: String,
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null
)

data class PptxSlide(
    val slideNumber: Int,
    val title: PptxTextBlock,
    val textBlocks: List<PptxTextBlock>,
    val imageUrls: List<String> = emptyList(),
    val comment: String? = null
)

data class PptxPresentation(val slides: List<PptxSlide>)

sealed class PptxLoadState {
    object Loading : PptxLoadState()
    data class Success(val presentation: PptxPresentation, val fileName: String) : PptxLoadState()
    data class Error(val message: String) : PptxLoadState()
}

@HiltViewModel
class PptxViewerViewModel @Inject constructor(
    private val recentFileRepository: RecentFileRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<PptxLoadState>(PptxLoadState.Loading)
    val loadState: StateFlow<PptxLoadState> = _loadState.asStateFlow()

    private val _saveStatus = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val saveStatus = _saveStatus.asSharedFlow()

    private var activePresentation: XMLSlideShow? = null
    private var activeFilePath: String? = null

    private fun loadSlideComments(filePath: String): Map<Int, String> {
        val commentsFile = File("$filePath.comments")
        if (!commentsFile.exists()) return emptyMap()
        val map = mutableMapOf<Int, String>()
        try {
            commentsFile.readLines().forEach { line ->
                val idx = line.indexOf(':')
                if (idx != -1) {
                    val sIdx = line.substring(0, idx).toIntOrNull()
                    val commentText = line.substring(idx + 1)
                    if (sIdx != null) {
                        map[sIdx] = commentText
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun saveSlideComments(filePath: String, comments: Map<Int, String>) {
        val commentsFile = File("$filePath.comments")
        try {
            val lines = comments.filter { it.value.isNotBlank() }
                .map { "${it.key}:${it.value}" }
            commentsFile.writeText(lines.joinToString("\n"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadPptxFile(filePath: String) {
        viewModelScope.launch {
            _loadState.value = PptxLoadState.Loading
            withContext(Dispatchers.IO) {
                // Free previous reference if any
                try {
                    activePresentation?.close()
                } catch (e: Exception) {}
                activePresentation = null
                activeFilePath = null

                var fileInputStream: FileInputStream? = null
                var ppt: XMLSlideShow? = null
                try {
                    val file = File(filePath)
                    if (!file.exists() || !file.isFile) {
                        _loadState.value = PptxLoadState.Error("Target presentation does not exist or is corrupted.")
                        return@withContext
                    }

                    fileInputStream = FileInputStream(file)
                    ppt = XMLSlideShow(fileInputStream)

                    val commentsMap = loadSlideComments(filePath)
                    val slides = mutableListOf<PptxSlide>()
                    for ((index, slide) in ppt.slides.withIndex()) {
                        val textBlocks = mutableListOf<PptxTextBlock>()
                        var titleBlock = PptxTextBlock("title", "Slide ${index + 1}")
                        var bodyCount = 0
                        val imageUrls = mutableListOf<String>()

                        for (shape in slide.shapes) {
                            if (shape is XSLFTextShape) {
                                val text = shape.text ?: ""
                                if (text.isNotBlank()) {
                                    val isTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                                    val firstParagraph = shape.textParagraphs.firstOrNull()
                                    val firstRun = firstParagraph?.textRuns?.firstOrNull()
                                    
                                    val isBold = firstRun?.isBold ?: false
                                    val isItalic = firstRun?.isItalic ?: false
                                    val isUnderline = firstRun?.isUnderlined ?: false
                                    var textColorHex: String? = null
                                    
                                    try {
                                        val colorObj = firstRun?.fontColor
                                        if (colorObj != null) {
                                            val rgbInt = colorObj.javaClass.getMethod("getRGB").invoke(colorObj) as Int
                                            textColorHex = String.format("#%06X", 0xFFFFFF and rgbInt)
                                        }
                                    } catch (e: Throwable) {}

                                    if (isTitle) {
                                        titleBlock = PptxTextBlock("title", text, isBold, isItalic, isUnderline, textColorHex)
                                    } else {
                                        textBlocks.add(PptxTextBlock("body_$bodyCount", text, isBold, isItalic, isUnderline, textColorHex))
                                        bodyCount++
                                    }
                                }
                            } else if (shape.javaClass.simpleName.contains("Picture")) {
                                try {
                                    val picDataObj = shape.javaClass.getMethod("getPictureData").invoke(shape)
                                    if (picDataObj != null) {
                                        val dataBytes = picDataObj.javaClass.getMethod("getData").invoke(picDataObj) as ByteArray
                                        val suggestExt = picDataObj.javaClass.getMethod("suggestFileExtension").invoke(picDataObj) as? String ?: "png"
                                        val tempFile = File.createTempFile("pptx_img_", ".$suggestExt")
                                        tempFile.writeBytes(dataBytes)
                                        imageUrls.add(tempFile.absolutePath)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        val slideComment = commentsMap[index]
                        slides.add(PptxSlide(index + 1, titleBlock, textBlocks, imageUrls, slideComment))
                    }

                    // Update RecentFiles DB offline logger
                    try {
                        val recentFile = RecentFile(
                            fileUri = file.absolutePath,
                            fileName = file.name,
                            mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            fileSize = file.length(),
                            lastOpened = System.currentTimeMillis()
                        )
                        recentFileRepository.insertRecentFile(recentFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    activePresentation = ppt
                    activeFilePath = filePath

                    _loadState.value = PptxLoadState.Success(
                        presentation = PptxPresentation(slides),
                        fileName = file.name
                    )

                } catch (e: Throwable) {
                    e.printStackTrace()
                    _loadState.value = PptxLoadState.Error("Apache POI PowerPoint parser failure: ${e.localizedMessage}")
                } finally {
                    try {
                        fileInputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun updateSlideTextShape(
        slideIndex: Int,
        isTitle: Boolean,
        blockIndex: Int,
        newText: String,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        isUnderline: Boolean = false,
        textColorHex: String? = null,
        comment: String? = null
    ) {
        val ppt = activePresentation ?: return
        val slides = ppt.slides
        if (slideIndex in slides.indices) {
            val slide = slides[slideIndex]
            var blockIdx = 0
            
            // Locate the target text shape
            for (shape in slide.shapes) {
                if (shape is XSLFTextShape) {
                    val isShapeTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                    val targetMatch = if (isTitle) isShapeTitle else (!isShapeTitle && blockIdx == blockIndex)
                    
                    if (targetMatch) {
                        shape.clearText()
                        val p = shape.addNewTextParagraph()
                        val r = p.addNewTextRun()
                        r.setText(newText)
                        r.isBold = isBold
                        r.isItalic = isItalic
                        r.isUnderlined = isUnderline
                        
                        if (textColorHex != null) {
                            try {
                                val colorClass = Class.forName("java.awt.Color")
                                val colorConstructor = colorClass.getConstructor(Int::class.java)
                                val rgbInt = android.graphics.Color.parseColor(textColorHex)
                                val colorObj = colorConstructor.newInstance(rgbInt)
                                r.javaClass.getMethod("setFontColor", colorClass).invoke(r, colorObj)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        break
                    }
                    if (!isShapeTitle) {
                        blockIdx++
                    }
                }
            }

            // Save comment to sidecar list
            val filePath = activeFilePath
            if (filePath != null) {
                val commentsMap = loadSlideComments(filePath).toMutableMap()
                if (comment.isNullOrBlank()) {
                    commentsMap.remove(slideIndex)
                } else {
                    commentsMap[slideIndex] = comment
                }
                saveSlideComments(filePath, commentsMap)
            }

            // Re-parse internally and update the Success State
            val presentationSlides = mutableListOf<PptxSlide>()
            val commentsMap = activeFilePath?.let { loadSlideComments(it) } ?: emptyMap()
            for ((idx, slideItem) in ppt.slides.withIndex()) {
                val textBlocks = mutableListOf<PptxTextBlock>()
                var titleBlock = PptxTextBlock("title", "Slide ${idx + 1}")
                var bodyCount = 0
                val imageUrls = mutableListOf<String>()

                for (shape in slideItem.shapes) {
                    if (shape is XSLFTextShape) {
                        val text = shape.text ?: ""
                        if (text.isNotBlank()) {
                            val isShTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                            val firstParagraph = shape.textParagraphs.firstOrNull()
                            val firstRun = firstParagraph?.textRuns?.firstOrNull()
                            
                            val runBold = firstRun?.isBold ?: false
                            val runItalic = firstRun?.isItalic ?: false
                            val runUnderline = firstRun?.isUnderlined ?: false
                            var colorHex: String? = null
                            try {
                                val colorObj = firstRun?.fontColor
                                if (colorObj != null) {
                                    val rgbInt = colorObj.javaClass.getMethod("getRGB").invoke(colorObj) as Int
                                    colorHex = String.format("#%06X", 0xFFFFFF and rgbInt)
                                }
                            } catch (e: Throwable) {}

                            if (isShTitle) {
                                titleBlock = PptxTextBlock("title", text, runBold, runItalic, runUnderline, colorHex)
                            } else {
                                textBlocks.add(PptxTextBlock("body_$bodyCount", text, runBold, runItalic, runUnderline, colorHex))
                                bodyCount++
                            }
                        }
                    } else if (shape.javaClass.simpleName.contains("Picture")) {
                        try {
                            val picDataObj = shape.javaClass.getMethod("getPictureData").invoke(shape)
                            if (picDataObj != null) {
                                val dataBytes = picDataObj.javaClass.getMethod("getData").invoke(picDataObj) as ByteArray
                                val suggestExt = picDataObj.javaClass.getMethod("suggestFileExtension").invoke(picDataObj) as? String ?: "png"
                                val tempFile = File.createTempFile("pptx_img_", ".$suggestExt")
                                tempFile.writeBytes(dataBytes)
                                imageUrls.add(tempFile.absolutePath)
                            }
                        } catch (e: Exception) {}
                    }
                }
                val slideComment = commentsMap[idx]
                presentationSlides.add(PptxSlide(idx + 1, titleBlock, textBlocks, imageUrls, slideComment))
            }

            _loadState.value = PptxLoadState.Success(
                presentation = PptxPresentation(presentationSlides),
                fileName = File(activeFilePath!!).name
            )
        }
    }

    fun insertImageIntoSlide(slideIndex: Int, imagePath: String) {
        val ppt = activePresentation ?: return
        val slides = ppt.slides
        if (slideIndex in slides.indices) {
            val slide = slides[slideIndex]
            try {
                val imageBytes = File(imagePath).readBytes()
                val pictureData = ppt.addPicture(imageBytes, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
                val pictureShape = slide.createPicture(pictureData)
                
                // Set anchor dynamically via AWT reflection
                val rectClass = Class.forName("java.awt.Rectangle")
                val rectConstructor = rectClass.getConstructor(Int::class.java, Int::class.java, Int::class.java, Int::class.java)
                val rectObj = rectConstructor.newInstance(100, 150, 400, 300)
                pictureShape.javaClass.getMethod("setAnchor", Class.forName("java.awt.geom.Rectangle2D")).invoke(pictureShape, rectObj)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Re-parse and update Success State
            val presentationSlides = mutableListOf<PptxSlide>()
            val commentsMap = activeFilePath?.let { loadSlideComments(it) } ?: emptyMap()
            for ((idx, slideItem) in ppt.slides.withIndex()) {
                val textBlocks = mutableListOf<PptxTextBlock>()
                var titleBlock = PptxTextBlock("title", "Slide ${idx + 1}")
                var bodyCount = 0
                val imageUrls = mutableListOf<String>()

                for (shape in slideItem.shapes) {
                    if (shape is XSLFTextShape) {
                        val text = shape.text ?: ""
                        if (text.isNotBlank()) {
                            val isShTitle = shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)
                            val firstParagraph = shape.textParagraphs.firstOrNull()
                            val firstRun = firstParagraph?.textRuns?.firstOrNull()
                            
                            val runBold = firstRun?.isBold ?: false
                            val runItalic = firstRun?.isItalic ?: false
                            val runUnderline = firstRun?.isUnderlined ?: false
                            var colorHex: String? = null
                            try {
                                val colorObj = firstRun?.fontColor
                                if (colorObj != null) {
                                    val rgbInt = colorObj.javaClass.getMethod("getRGB").invoke(colorObj) as Int
                                    colorHex = String.format("#%06X", 0xFFFFFF and rgbInt)
                                }
                            } catch (e: Throwable) {}

                            if (isShTitle) {
                                titleBlock = PptxTextBlock("title", text, runBold, runItalic, runUnderline, colorHex)
                            } else {
                                textBlocks.add(PptxTextBlock("body_$bodyCount", text, runBold, runItalic, runUnderline, colorHex))
                                bodyCount++
                            }
                        }
                    } else if (shape.javaClass.simpleName.contains("Picture")) {
                        try {
                            val picDataObj = shape.javaClass.getMethod("getPictureData").invoke(shape)
                            if (picDataObj != null) {
                                val dataBytes = picDataObj.javaClass.getMethod("getData").invoke(picDataObj) as ByteArray
                                val suggestExt = picDataObj.javaClass.getMethod("suggestFileExtension").invoke(picDataObj) as? String ?: "png"
                                val tempFile = File.createTempFile("pptx_img_", ".$suggestExt")
                                tempFile.writeBytes(dataBytes)
                                imageUrls.add(tempFile.absolutePath)
                            }
                        } catch (e: Exception) {}
                    }
                }
                val slideComment = commentsMap[idx]
                presentationSlides.add(PptxSlide(idx + 1, titleBlock, textBlocks, imageUrls, slideComment))
            }

            _loadState.value = PptxLoadState.Success(
                presentation = PptxPresentation(presentationSlides),
                fileName = File(activeFilePath!!).name
            )
        }
    }

    fun commitChanges() {
        viewModelScope.launch {
            val ppt = activePresentation
            val filePath = activeFilePath
            if (ppt == null || filePath == null) {
                _saveStatus.emit("No active presentation loaded.")
                return@launch
            }

            withContext(Dispatchers.IO) {
                var fileOutputStream: FileOutputStream? = null
                try {
                    fileOutputStream = FileOutputStream(File(filePath))
                    ppt.write(fileOutputStream)
                    _saveStatus.emit("PowerPoint changes committed successfully!")
                } catch (e: Exception) {
                    e.printStackTrace()
                    _saveStatus.emit("Failed to save changes: ${e.localizedMessage}")
                } finally {
                    try {
                        fileOutputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            activePresentation?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}