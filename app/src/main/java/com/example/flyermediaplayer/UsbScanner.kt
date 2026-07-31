package com.example.flyermediaplayer

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import java.io.File

object UsbScanner {

    data class ResultadoScan(
        val principal: List<File>,
        val atracao: List<File>,
        val aleatorios: List<File>,
        val naoSuportados: List<String>
    )

    fun checarMotivoIncompatibilidade(file: File): String? {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    val codecList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                    val hasDecoder = codecList.codecInfos.any { info ->
                        !info.isEncoder && info.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) }
                    }
                    if (!hasDecoder) {
                        return "Codec $mime sem suporte no hardware desta TV Box"
                    }
                }
            }
        } catch (e: Exception) {
            // Em caso de erro na leitura do cabeçalho, permite continuar
        } finally {
            try { extractor.release() } catch (e: Exception) {}
        }
        return null
    }

    private fun processarEAdicionar(
        arquivo: File,
        listaAlvo: MutableList<File>,
        listaNaoSuportados: MutableList<String>
    ) {
        val motivo = checarMotivoIncompatibilidade(arquivo)
        if (motivo == null) {
            listaAlvo.add(arquivo)
        } else {
            val logItem = "${arquivo.name} -> $motivo"
            if (!listaNaoSuportados.contains(logItem)) {
                listaNaoSuportados.add(logItem)
            }
        }
    }

    fun buscarVideosDoUsb(
        context: Context,
        palavraChave: String = "MARIA",
        nomePastaAleatorios: String = "VIDEOS",
        modoAtracaoAtivo: Boolean = false,
        termoAtracao: String = ""
    ): ResultadoScan {
        val listaPrincipal = mutableListOf<File>()
        val listaAtracao = mutableListOf<File>()
        val listaAleatoria = mutableListOf<File>()
        val listaNaoSuportados = mutableListOf<String>()
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
                escanearDiretorioUsb(pastaSalva, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaPrincipal, listaAtracao, listaAleatoria, listaNaoSuportados)
                if (listaPrincipal.isNotEmpty()) {
                    return ResultadoScan(listaPrincipal, listaAtracao, listaAleatoria, listaNaoSuportados)
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
            if (ultimoCaminhoUsbSalvo != null && usb.absolutePath == ultimoCaminhoUsbSalvo) continue

            escanearDiretorioUsb(usb, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaPrincipal, listaAtracao, listaAleatoria, listaNaoSuportados)
            if (listaPrincipal.isNotEmpty()) {
                prefs.edit().putString("ultimo_caminho_usb", usb.absolutePath).apply()
                break
            }
        }

        // 3. Fallback via MediaStore do Android moderno
        if (listaPrincipal.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            escanearViaMediaStore(context, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaPrincipal, listaAtracao, listaAleatoria, listaNaoSuportados)
        }

        // 4. RETRY PÓS-PERMISSÃO
        if (listaPrincipal.isEmpty() && listaAleatoria.isEmpty()) {
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
                escanearDiretorioUsb(usb, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaPrincipal, listaAtracao, listaAleatoria, listaNaoSuportados)
                if (listaPrincipal.isNotEmpty()) break
            }

            if (listaPrincipal.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                escanearViaMediaStore(context, extensoesVideo, termosFiltro, pastaFiltro, modoAtracaoAtivo, atracaoFiltro, listaPrincipal, listaAtracao, listaAleatoria, listaNaoSuportados)
            }
        }

        // 5. FALLBACK INTELIGENTE: Se listaPrincipal continuar vazia, mas existirem vídeos na listaAleatoria
        if (listaPrincipal.isEmpty() && listaAleatoria.isNotEmpty()) {
            listaPrincipal.addAll(listaAleatoria)
        }

        return ResultadoScan(listaPrincipal, listaAtracao, listaAleatoria, listaNaoSuportados)
    }

    private fun escanearViaMediaStore(
        context: Context,
        extensoesVideo: List<String>,
        termosFiltro: List<String>,
        pastaFiltro: String,
        modoAtracaoAtivo: Boolean,
        atracaoFiltro: String,
        listaPrincipal: MutableList<File>,
        listaAtracao: MutableList<File>,
        listaAleatoria: MutableList<File>,
        listaNaoSuportados: MutableList<String>
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

                    if (filePath.contains("/emulated/0") || filePath.contains("/self/primary")) continue

                    val extension = file.extension.lowercase()
                    if (extensoesVideo.contains(extension)) {
                        val pathUpper = filePath.uppercase().replace("\\", "/")
                        val nameUpper = fileName.uppercase()

                        if (modoAtracaoAtivo && atracaoFiltro.isNotEmpty() && (pathUpper.contains(atracaoFiltro) || nameUpper.contains(atracaoFiltro))) {
                            processarEAdicionar(file, listaAtracao, listaNaoSuportados)
                        } else if (termosFiltro.any { termo -> nameUpper.contains(termo) }) {
                            processarEAdicionar(file, listaPrincipal, listaNaoSuportados)
                        } else if (pathUpper.contains("/$pastaFiltro/")) {
                            processarEAdicionar(file, listaAleatoria, listaNaoSuportados)
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
        listaPrincipal: MutableList<File>,
        listaAtracao: MutableList<File>,
        listaAleatoria: MutableList<File>,
        listaNaoSuportados: MutableList<String>
    ) {
        val arquivosRaiz = usb.listFiles() ?: return

        arquivosRaiz.forEach { arquivo ->
            if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                val nameUpper = arquivo.name.uppercase()

                if (modoAtracaoAtivo && atracaoFiltro.isNotEmpty() && nameUpper.contains(atracaoFiltro)) {
                    processarEAdicionar(arquivo, listaAtracao, listaNaoSuportados)
                } else if (termosFiltro.any { termo -> nameUpper.contains(termo) }) {
                    processarEAdicionar(arquivo, listaPrincipal, listaNaoSuportados)
                }
            }
        }

        if (modoAtracaoAtivo && atracaoFiltro.isNotEmpty() && listaAtracao.isEmpty()) {
            val pastaAtracao = File(usb, atracaoFiltro)
            val pastaAtracaoMinusculo = File(usb, atracaoFiltro.lowercase())
            val pastaAlvoAtracao = if (pastaAtracao.exists()) pastaAtracao else if (pastaAtracaoMinusculo.exists()) pastaAtracaoMinusculo else null

            pastaAlvoAtracao?.listFiles()?.forEach { arquivo ->
                if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                    processarEAdicionar(arquivo, listaAtracao, listaNaoSuportados)
                }
            }
        }

        val pastaVideos = File(usb, pastaFiltro)
        val pastaVideosMinusculo = File(usb, pastaFiltro.lowercase())
        val pastaAlvoVideos = if (pastaVideos.exists()) pastaVideos else if (pastaVideosMinusculo.exists()) pastaVideosMinusculo else null

        pastaAlvoVideos?.listFiles()?.forEach { arquivo ->
            if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                processarEAdicionar(arquivo, listaAleatoria, listaNaoSuportados)
            }
        }
    }
}
