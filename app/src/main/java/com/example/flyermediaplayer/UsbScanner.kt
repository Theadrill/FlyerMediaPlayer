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

        val termosFiltro = palavraChave.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("MARIA") }
        val pastaFiltro = nomePastaAleatorios.trim().uppercase().ifEmpty { "VIDEOS" }
        val atracaoFiltro = termoAtracao.trim().uppercase()

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val ultimoCaminhoUsbSalvo = prefs.getString("ultimo_caminho_usb", null)

        // 1. TENTA PRIMEIRO O ÚLTIMO CAMINHO VÁLIDO QUE FOI LEMBRADO (FAST PATH)
        if (!ultimoCaminhoUsbSalvo.isNullOrEmpty()) {
            val pastaSalva = File(ultimoCaminhoUsbSalvo)
            if (pastaSalva.exists() && pastaSalva.isDirectory) {
                escanearDiretorioUsb(pastaSalva, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaMaria, listaAtracao, listaAleatoria)
                if (listaMaria.isNotEmpty()) {
                    // Encontrou imediatamente! Retorna sem perder tempo varrendo outros armazenamentos
                    return Triple(listaMaria, listaAtracao, listaAleatoria)
                }
            }
        }

        // 2. SE O CAMINHO LEMBRADO NÃO FUNCIONOU (OU É A PRIMEIRA VEZ), VARRE TODOS OS LOCAIS
        val pastasUsb = mutableListOf<File>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pastasUsb.addAll(obterPastasRemoviveisAndroidModerno(context))
        } else {
            val pastaStorage = File("/storage")
            if (pastaStorage.exists() && pastaStorage.isDirectory) {
                pastaStorage.listFiles()?.forEach { sub ->
                    if (sub.isDirectory && sub.name != "emulated" && sub.name != "self" && !sub.name.startsWith(".")) {
                        pastasUsb.add(sub)
                    }
                }
            }
        }

        for (usb in pastasUsb) {
            // Evita escanear de novo a pasta salva se já testamos no Fast Path
            if (ultimoCaminhoUsbSalvo != null && usb.absolutePath == ultimoCaminhoUsbSalvo) continue

            escanearDiretorioUsb(usb, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaMaria, listaAtracao, listaAleatoria)
            if (listaMaria.isNotEmpty()) {
                // Guarda o novo caminho que deu certo para as próximas vezes
                prefs.edit().putString("ultimo_caminho_usb", usb.absolutePath).apply()
                break
            }
        }

        // 3. Fallback via MediaStore do Android moderno
        if (listaMaria.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            escanearViaMediaStore(context, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaMaria, listaAtracao, listaAleatoria)
        }

        // 4. RETRY PÓS-PERMISSÃO: Se o sistema operacional concedeu a permissão recentemente, faz uma 2ª tentativa rápida para atualizar os descritores do USB
        if (listaMaria.isEmpty() && listaAleatoria.isEmpty()) {
            try { Thread.sleep(350) } catch (e: Exception) {}
            pastasUsb.clear()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pastasUsb.addAll(obterPastasRemoviveisAndroidModerno(context))
            } else {
                val pastaStorage = File("/storage")
                if (pastaStorage.exists() && pastaStorage.isDirectory) {
                    pastaStorage.listFiles()?.forEach { sub ->
                        if (sub.isDirectory && sub.name != "emulated" && sub.name != "self" && !sub.name.startsWith(".")) {
                            pastasUsb.add(sub)
                        }
                    }
                }
            }

            for (usb in pastasUsb) {
                escanearDiretorioUsb(usb, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaMaria, listaAtracao, listaAleatoria)
                if (listaMaria.isNotEmpty()) break
            }

            if (listaMaria.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                escanearViaMediaStore(context, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaMaria, listaAtracao, listaAleatoria)
            }
        }

        // 5. FALLBACK INTELIGENTE: Se listaMaria continuar vazia, mas existirem vídeos na listaAleatoria ou no USB,
        // promove os vídeos para a lista principal para que o app NUNCA fique travado sem reproduzir!
        if (listaMaria.isEmpty() && listaAleatoria.isNotEmpty()) {
            listaMaria.addAll(listaAleatoria)
        }

        return Triple(listaMaria, listaAtracao, listaAleatoria)
    }

    private fun escanearViaMediaStore(
        context: Context,
        extensoesVideo: List<String>,
        termosFiltro: List<String>,
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
                        } else if (termosFiltro.any { termo -> nameUpper.contains(termo) }) {
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
        termosFiltro: List<String>,
        pastaFiltro: String,
        modoAtracaoAtivo: Boolean,
        atracaoFiltro: String,
        listaMaria: MutableList<File>,
        listaAtracao: MutableList<File>,
        listaAleatoria: MutableList<File>
    ) {
        val arquivosRaiz = usb.listFiles() ?: return

        // 1. Procura vídeos principais e vídeos de atração APENAS na RAIZ do volume
        arquivosRaiz.forEach { arquivo ->
            if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                val nameUpper = arquivo.name.uppercase()

                if (modoAtracaoAtivo && atracaoFiltro.isNotEmpty() && nameUpper.contains(atracaoFiltro)) {
                    listaAtracao.add(arquivo)
                } else if (termosFiltro.any { termo -> nameUpper.contains(termo) }) {
                    listaMaria.add(arquivo)
                }
            }
        }

        // 2. Se a Atração for uma pasta (ex: GRUPOS/GRUPO XXX ou PASTA_ATRACAO), verifica diretamente a pasta
        if (modoAtracaoAtivo && atracaoFiltro.isNotEmpty() && listaAtracao.isEmpty()) {
            val pastaAtracao = File(usb, atracaoFiltro)
            val pastaAtracaoMinusculo = File(usb, atracaoFiltro.lowercase())
            val pastaAlvoAtracao = if (pastaAtracao.exists()) pastaAtracao else if (pastaAtracaoMinusculo.exists()) pastaAtracaoMinusculo else null

            pastaAlvoAtracao?.listFiles()?.forEach { arquivo ->
                if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                    listaAtracao.add(arquivo)
                }
            }
        }

        // 3. Procura vídeos aleatórios EXCLUSIVAMENTE dentro da pasta parametrizada (ex: /VIDEOS)
        val pastaVideos = File(usb, pastaFiltro)
        val pastaVideosMinusculo = File(usb, pastaFiltro.lowercase())
        val pastaAlvoVideos = if (pastaVideos.exists()) pastaVideos else if (pastaVideosMinusculo.exists()) pastaVideosMinusculo else null

        pastaAlvoVideos?.listFiles()?.forEach { arquivo ->
            if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                listaAleatoria.add(arquivo)
            }
        }
    }
}
