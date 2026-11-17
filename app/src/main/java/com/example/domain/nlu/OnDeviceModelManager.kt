package com.example.domain.nlu

import android.content.Context
import java.io.File

data class LocalModelStatus(
  val modelsDirPath: String,
  val llmModelFound: Boolean,
  val llmFileName: String?,
  val llmSizeMb: Long,
  val sttModelFound: Boolean,
  val sttFolderName: String?,
)

/**
 * Inspects app-private on-device model storage (`filesDir/saathi_models/`) for user-installed
 * MediaPipe/GGUF Gemma/Qwen LLM bundles and Vosk/Whisper speech models.
 * Zero automatic downloads or hidden network traffic.
 */
object OnDeviceModelManager {
  private const val MODELS_DIR = "saathi_models"

  fun getModelsDirectory(context: Context): File {
    return File(context.filesDir, MODELS_DIR).apply { if (!exists()) mkdirs() }
  }

  fun inspectLocalModels(context: Context): LocalModelStatus {
    val dir = getModelsDirectory(context)
    val files = dir.listFiles().orEmpty()

    val llmFile =
      files.firstOrNull {
        it.isFile &&
          (it.name.endsWith(".task", true) ||
            it.name.endsWith(".bin", true) ||
            it.name.endsWith(".gguf", true) ||
            it.name.endsWith(".tflite", true))
      }

    val sttFolder =
      files.firstOrNull {
        it.isDirectory &&
          (it.name.contains("vosk", true) || it.name.contains("whisper", true) || it.name.contains("sherpa", true))
      }

    return LocalModelStatus(
      modelsDirPath = dir.absolutePath,
      llmModelFound = llmFile != null,
      llmFileName = llmFile?.name,
      llmSizeMb = (llmFile?.length() ?: 0L) / (1024L * 1024L),
      sttModelFound = sttFolder != null,
      sttFolderName = sttFolder?.name,
    )
  }
}
