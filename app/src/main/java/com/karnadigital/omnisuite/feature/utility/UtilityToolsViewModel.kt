package com.karnadigital.omnisuite.feature.utility

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karnadigital.omnisuite.core.engine.utility.UtilityToolsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UtilityToolsViewModel @Inject constructor(
    val utilityToolsRepository: UtilityToolsRepository
) : ViewModel() {

    private val _unitResult = MutableStateFlow("")
    val unitResult: StateFlow<String> = _unitResult.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _colorPicked = MutableStateFlow<Int?>(null)
    val colorPicked: StateFlow<Int?> = _colorPicked.asStateFlow()

    private val _checksumResult = MutableStateFlow<String?>(null)
    val checksumResult: StateFlow<String?> = _checksumResult.asStateFlow()

    fun convertUnit(value: Double, fromUnit: String, toUnit: String, category: String) {
        val result = utilityToolsRepository.convertUnit(value, fromUnit, toUnit, category)
        _unitResult.value = String.format("%.6f", result).trimEnd('0').trimEnd('.')
    }

    fun pickColor(bitmap: Bitmap, x: Int, y: Int) {
        _colorPicked.value = utilityToolsRepository.pickColorFromImage(bitmap, x, y)
    }

    fun autoEnhance(bitmap: Bitmap): Bitmap = utilityToolsRepository.autoEnhance(bitmap)

    fun removeBackground(bitmap: Bitmap, threshold: Int = 30): Bitmap = utilityToolsRepository.removeBackground(bitmap, threshold)

    fun applyBlur(bitmap: Bitmap, radius: Int = 5): Bitmap = utilityToolsRepository.applyBlur(bitmap, radius)

    fun applySharpen(bitmap: Bitmap): Bitmap = utilityToolsRepository.applySharpen(bitmap)

    fun removeRedEye(bitmap: Bitmap): Bitmap = utilityToolsRepository.removeRedEye(bitmap)

    fun createCollage(bitmaps: List<Bitmap>, layout: Int = 0): Bitmap = utilityToolsRepository.createCollage(bitmaps, layout)

    fun createMeme(bitmap: Bitmap, topText: String, bottomText: String): Bitmap = utilityToolsRepository.createMeme(bitmap, topText, bottomText)

    fun resetStatus() {
        _successMessage.value = null
        _errorMessage.value = null
    }
}
