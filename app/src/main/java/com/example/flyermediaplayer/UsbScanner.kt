package com.example.flyermediaplayer

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import java.io.File

object UsbScanner {

    fun buscarVideosDoUsb(context: Context): Pair<List<File>, List<File>> {
        val listaMaria = mutableListOf<File>()
        val listaAleatoria = mutableListOf<File>()
        val extensoesVideo = listOf("mp4", "mkv", "avi")

        // Se for Android 11 (API 30) ou superior (ex: Galaxy A55), usa a API moderna de StorageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pastasRemoviveis = obterPastasRemoviveisAndroidModerno(context)
            for (usb in pastasRemoviveis) {
                escanearDiretorioUsb(usb, extensoesVideo, listaMaria, listaAleatoria)
            }
        } else {
            // Android Antigo (ex: TV Box): Mantém exatamente a lógica legada que já funciona
            val pastaStorage = File("/storage")
            if (pastaStorage.exists() && pastaStorage.isDirectory) {
                val pastasUsb = pastaStorage.listFiles() ?: return Pair(emptyList(), emptyList())
                for (usb in pastasUsb) {
                    if (usb.name == "emulated" || usb.name == "self" || usb.name.startsWith(".")) continue
                    escanearDiretorioUsb(usb, extensoesVideo, listaMaria, listaAleatoria)
                }
            }
        }

        // Se a busca via diretórios não encontrou vídeos MARIA no Android moderno, tenta consultar via MediaStore do Android
        if (listaMaria.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            escanearViaMediaStore(context, extensoesVideo, listaMaria, listaAleatoria)
        }

        return Pair(listaMaria, listaAleatoria)
    }

    private fun escanearViaMediaStore(
        context: Context,
        extensoesVideo: List<String>,
        listaMaria: MutableList<File>,
        listaAleatoria: MutableList<File>
    ) {
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Video.Media.DATA,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME
            )
            val cursor = context.contentResolver.query(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                val dataColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                val nameColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)

                while (it.moveToNext()) {
                    val filePath = it.getString(dataColumn) ?: continue
                    val fileName = it.getString(nameColumn) ?: continue
                    val file = File(filePath)

                    // Ignora vídeos da memória interna principal (/emulated/0 ou /self/primary)
                    if (filePath.contains("/emulated/0") || filePath.contains("/self/primary")) continue

                    val extension = file.extension.lowercase()
                    if (extensoesVideo.contains(extension)) {
                        if (fileName.uppercase().contains("MARIA")) {
                            listaMaria.add(file)
                        } else if (filePath.uppercase().contains("/VIDEOS/")) {
                            listaAleatoria.add(file)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun obterPastasRemoviveisAndroidModerno(context: Context): List<File> {
        val listaPastas = mutableSetOf<File>()

        // 1. Tenta obter pelo StorageManager
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null) {
                for (volume in storageManager.storageVolumes) {
                    if (volume.isRemovable) {
                        val dir = volume.directory
                        if (dir != null && dir.exists()) {
                            listaPastas.add(dir)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback via ContextCompat.getExternalFilesDirs
        try {
            val dirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in dirs) {
                if (dir != null) {
                    val path = dir.absolutePath
                    val indexAndroid = path.indexOf("/Android/")
                    if (indexAndroid > 0) {
                        val rootPath = path.substring(0, indexAndroid)
                        val rootFile = File(rootPath)
                        if (rootFile.exists() && rootFile.name != "emulated") {
                            listaPastas.add(rootFile)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback varrendo /storage e /mnt/media_rw sem checagem de canRead() prévia
        val locaisStorage = listOf(File("/storage"), File("/mnt/media_rw"))
        for (local in locaisStorage) {
            try {
                if (local.exists() && local.isDirectory) {
                    local.listFiles()?.forEach { subDir ->
                        if (subDir.isDirectory && subDir.name != "emulated" && subDir.name != "self" && !subDir.name.startsWith(".")) {
                            listaPastas.add(subDir)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return listaPastas.toList()
    }

    private fun escanearDiretorioUsb(
        usb: File,
        extensoesVideo: List<String>,
        listaMaria: MutableList<File>,
        listaAleatoria: MutableList<File>
    ) {
        // 1. Procura vídeos MARIA apenas na RAIZ do volume (Pen Drive / SD Card)
        usb.listFiles()?.forEach { arquivo ->
            if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                if (arquivo.name.uppercase().contains("MARIA")) {
                    listaMaria.add(arquivo)
                }
            }
        }

        // 2. Procura os outros vídeos dentro da pasta /VIDEOS ou /videos
        val pastaVideos = File(usb, "VIDEOS")
        val pastaVideosMinusculo = File(usb, "videos")

        val pastaAlvo = if (pastaVideos.exists()) pastaVideos else if (pastaVideosMinusculo.exists()) pastaVideosMinusculo else null

        pastaAlvo?.walkTopDown()?.forEach { arquivo ->
            if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                listaAleatoria.add(arquivo)
            }
        }
    }
}
