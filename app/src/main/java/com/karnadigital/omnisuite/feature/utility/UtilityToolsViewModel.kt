package com.karnadigital.omnisuite.feature.utility

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    var isProcessing by mutableStateOf(false)
    var successMessage by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    private val _colorPicked = MutableStateFlow<Int?>(null)
    val colorPicked: StateFlow<Int?> = _colorPicked.asStateFlow()

    var fileEncryptInputUri by mutableStateOf<Uri?>(null)
    var fileEncryptPassword by mutableStateOf("")

    var fileDecryptInputUri by mutableStateOf<Uri?>(null)
    var fileDecryptPassword by mutableStateOf("")

    var checksumInputUri by mutableStateOf<Uri?>(null)
    var checksumAlgorithm by mutableStateOf("SHA-256")
    var checksumResult by mutableStateOf<String?>(null)

    var textCompare1 by mutableStateOf("")
    var textCompare2 by mutableStateOf("")
    var textCompareResult by mutableStateOf<String?>(null)

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

    fun encryptFile(customFilename: String? = null) {
        val inputUri = fileEncryptInputUri ?: run { errorMessage = "Please select a file."; return }
        if (fileEncryptPassword.isBlank()) { errorMessage = "Password required."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = utilityToolsRepository.encryptFile(inputUri, fileEncryptPassword, customFilename)
            result.onSuccess {
                successMessage = "File encrypted with AES-256!"
                fileEncryptInputUri = null
                fileEncryptPassword = ""
            }.onFailure { e ->
                errorMessage = "Failed: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun decryptFile(customFilename: String? = null) {
        val inputUri = fileDecryptInputUri ?: run { errorMessage = "Please select an encrypted file."; return }
        if (fileDecryptPassword.isBlank()) { errorMessage = "Password required."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = utilityToolsRepository.decryptFile(inputUri, fileDecryptPassword, customFilename)
            result.onSuccess {
                successMessage = "File decrypted successfully!"
                fileDecryptInputUri = null
                fileDecryptPassword = ""
            }.onFailure { e ->
                errorMessage = "Failed: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun getFileChecksum() {
        val inputUri = checksumInputUri ?: run { errorMessage = "Please select a file."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = utilityToolsRepository.getFileChecksum(inputUri, checksumAlgorithm)
            result.onSuccess { hash ->
                checksumResult = "$checksumAlgorithm: $hash"
                successMessage = "Checksum calculated!"
            }.onFailure { e ->
                errorMessage = "Failed: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun compareText() {
        if (textCompare1.isBlank() && textCompare2.isBlank()) { errorMessage = "Enter text to compare."; return }
        isProcessing = true; resetStatus()
        viewModelScope.launch {
            val result = utilityToolsRepository.compareText(textCompare1, textCompare2)
            result.onSuccess { diff ->
                textCompareResult = diff
                successMessage = "Comparison complete!"
            }.onFailure { e ->
                errorMessage = "Failed: ${e.localizedMessage}"
            }
            isProcessing = false
        }
    }

    fun getWordCount(text: String): Map<String, Any> = utilityToolsRepository.getWordCount(text)

    fun resetStatus() {
        successMessage = null
        errorMessage = null
    }
}
