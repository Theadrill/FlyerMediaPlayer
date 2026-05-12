package com.example.flyermediaplayer

import java.io.File

object UsbScanner {

    fun buscarVideosDoUsb(): Pair<List<File>, List<File>> {
        val listaMaria = mutableListOf<File>()
        val listaAleatoria = mutableListOf<File>()
        val extensoesVideo = listOf("mp4", "mkv", "avi")

        val pastaStorage = File("/storage")
        
        if (pastaStorage.exists() && pastaStorage.isDirectory) {
            val pastasUsb = pastaStorage.listFiles() ?: return Pair(emptyList(), emptyList())

            for (usb in pastasUsb) {
                // Ignora pastas internas do sistema (Android, emulated, etc)
                if (usb.name == "emulated" || usb.name == "self" || usb.name.startsWith(".")) continue

                // 1. Procura vídeos MARIA apenas na RAIZ do pendrive
                usb.listFiles()?.forEach { arquivo ->
                    if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                        if (arquivo.name.uppercase().contains("MARIA")) {
                            listaMaria.add(arquivo)
                        }
                    }
                }

                // 2. Procura os outros vídeos apenas dentro da pasta /VIDEOS
                val pastaVideos = File(usb, "VIDEOS")
                val pastaVideosMinusculo = File(usb, "videos")
                
                // Tenta encontrar a pasta VIDEOS (maiúscula ou minúscula)
                val pastaAlvo = if (pastaVideos.exists()) pastaVideos else if (pastaVideosMinusculo.exists()) pastaVideosMinusculo else null

                pastaAlvo?.walkTopDown()?.forEach { arquivo ->
                    if (arquivo.isFile && extensoesVideo.contains(arquivo.extension.lowercase())) {
                        // Adiciona todos os vídeos desta pasta como aleatórios
                        listaAleatoria.add(arquivo)
                    }
                }
            }
        }
        return Pair(listaMaria, listaAleatoria)
    }
}
