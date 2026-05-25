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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.sl.usermodel.Placeholder
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

data class PptxSlide(
    val slideNumber: Int,
    val title: String,
    val textBlocks: List<String>
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

    fun loadPptxFile(filePath: String) {
        viewModelScope.launch {
            _loadState.value = PptxLoadState.Loading
            withContext(Dispatchers.IO) {
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

                    val slides = mutableListOf<PptxSlide>()
                    for ((index, slide) in ppt.slides.withIndex()) {
                        val textBlocks = mutableListOf<String>()
                        var title = ""

                        for (shape in slide.shapes) {
                            if (shape is XSLFTextShape) {
                                val text = shape.text ?: ""
                                if (text.isNotBlank()) {
                                    if (shape.isPlaceholder && (shape.textType == Placeholder.TITLE || shape.textType == Placeholder.CENTERED_TITLE)) {
                                        title = text
                                    } else {
                                        textBlocks.add(text)
                                    }
                                }
                            }
                        }

                        if (title.isBlank()) {
                            title = "Slide ${index + 1}"
                        }
                        slides.add(PptxSlide(index + 1, title, textBlocks))
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

                    _loadState.value = PptxLoadState.Success(
                        presentation = PptxPresentation(slides),
                        fileName = file.name
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                    _loadState.value = PptxLoadState.Error("Apache POI PowerPoint parser failure: ${e.localizedMessage}")
                } finally {
                    try {
                        ppt?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        fileInputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun updateSlideText(slideIndex: Int, newTitle: String) {
        val currentState = _loadState.value
        if (currentState is PptxLoadState.Success) {
            val slides = currentState.presentation.slides.toMutableList()
            if (slideIndex in slides.indices) {
                val oldSlide = slides[slideIndex]
                slides[slideIndex] = oldSlide.copy(title = newTitle)
                _loadState.value = PptxLoadState.Success(PptxPresentation(slides), currentState.fileName)
            }
        }
    }
}