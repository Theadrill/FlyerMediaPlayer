package com.example.flyermediaplayer

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import java.io.File

object UsbScanner {

    fun buscarVideosDoUsb(
        context: Context,
        palavraChave: String = "MARIA",
        nomePastaAleatorios: String = "VIDEOS",
        modoAtracaoAtivo: Boolean = false,
        termoAtracao: String = ""
    ): Triple<List<File>, List<File>, List<File>> {
        val listaMaria = mutableListOf<File>()
        val listaAtracao = mutableListOf<File>()
        val listaAleatoria = mutableListOf<File>()
        val extensoesVideo = listOf("mp4", "mkv", "avi")

        val termoFiltro = palavraChave.trim().uppercase().ifEmpty { "MARIA" }
        val pastaFiltro = nomePastaAleatorios.trim().uppercase().ifEmpty { "VIDEOS" }
        val atracaoFiltro = termoAtracao.trim().uppercase()

        // Se for Android 11 (API 30) ou superior (ex: Galaxy A55), usa a API moderna de StorageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pastasRemoviveis = obterPastasRemoviveisAndroidModerno(context)
            for (usb in pastasRemoviveis) {
                escanearDiretorioUsb(usb, extensoesVideo, termoFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaMaria, listaAtracao, listaAleatoria)
            }
        } else {
            // Android Antigo (ex: TV Box)
            val pastaStorage = File("/storage")
            if (pastaStorage.exists() && pastaStorage.isDirectory) {
                val pastasUsb = pastaStorage.listFiles() ?: return Triple(emptyList(), emptyList(), emptyList())
                for (usb in pastasUsb) {
                    if (usb.name == "emulated" || usb.name == "self" || usb.name.startsWith(".")) continue
                    escanearDiretorioUsb(usb, extensoesVideo, termoFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaMaria, listaAtracao, listaAleatoria)
                }
            }
        }

        // Se a busca via diretórios não encontrou vídeos MARIA no Android moderno, tenta consultar via MediaStore do Android
        if (listaMaria.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            escanearViaMediaStore(context, extensoesVideo, termoFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaMaria, listaAtracao, listaAleatoria)
        }

        return Triple(listaMaria, listaAtracao, listaAleatoria)
    }

    private fun escanearViaMediaStore(
        context: Context,
        extensoesVideo: List<String>,
        termoFiltro: String,
        pastaFiltro: String,
        modoAtracaoAtivo: Boolean,
        atracaoFiltro: String,
        listaMaria: MutableList<File>,
        listaAtracao: MutableList<File>,
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
                        val pathUpper = filePath.uppercase().replace("\\", "/")
                        val nameUpper = fileName.uppercase()

                        if (modoAtracaoAtivo && atracaoFiltro.isNotEmpty() && (pathUpper.contains(atracaoFiltro) || nameUpper.contains(atracaoFiltro))) {
                            listaAtracao.add(file)
                        } else if (nameUpper.contains(termoFiltro)) {
                            listaMaria.add(file)
                        } else if (pathUpper.contains("/$pastaFiltro/")) {
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
        termoFiltro: String,
        pastaFiltro: String,
        modoAtracaoAtivo: Boolean,
        atracaoFiltro: String,
        listaMaria: MutableList<File>,
        listaAtracao: MutableList<File>,
        listaAleatoria: MutableList<File>
    ) {
        usb.walkTopDown().forEach { arquivo ->
            if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                val relPathUpper = arquivo.absolutePath.substring(usb.absolutePath.length).uppercase().replace("\\", "/")
                val nameUpper = arquivo.name.uppercase()

                // Se Modo Atração estiver ligado e o arquivo bater com nome ou caminho da atração
                if (modoAtracaoAtivo && atracaoFiltro.isNotEmpty() && (relPathUpper.contains(atracaoFiltro) || nameUpper.contains(atracaoFiltro))) {
                    listaAtracao.add(arquivo)
                } else if (nameUpper.contains(termoFiltro)) {
                    // Vídeos principais (na raiz ou com o termoFiltro)
                    listaMaria.add(arquivo)
                } else if (relPathUpper.contains("/$pastaFiltro/")) {
                    // Vídeos aleatórios dentro da pasta parametrizada
                    listaAleatoria.add(arquivo)
                }
            }
        }
    }
}
