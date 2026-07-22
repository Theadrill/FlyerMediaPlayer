package com.example.flyermediaplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var btnIniciar: Button
    private lateinit var loadingBar: View
    private lateinit var progressBarVideo: ProgressBar

    private var listaMaria = listOf<File>()
    private var listaAtracao = listOf<File>()
    private var listaAleatoria = listOf<File>()
    
    // Filas para controle de repetição (Playlist)
    private var filaMaria = mutableListOf<File>()
    private var filaAtracao = mutableListOf<File>()
    private var filaAleatoria = mutableListOf<File>()

    private enum class TipoVideoAtual { MARIA, ATRACAO, ALEATORIO }
    private var tipoAtual = TipoVideoAtual.MARIA

    private var videosTocadosNoBloco = 0
    private var qtdMariaConfig = 1
    private var qtdAtracaoConfig = 1
    private var qtdAleatoriosConfig = 2
    private var tempoCorteMs = 8 * 60 * 1000L

    private var modoAtracaoAtivoConfig = false
    private var tocarAleatoriosModoAtracaoConfig = true
    private var intercalarPrincipaisConfig = false

    private var ultimoMariaTocado: File? = null
    private var ultimoAtracaoTocado: File? = null
    private var ultimoAleatorioTocado: File? = null

    private data class HistoricoItem(
        val arquivo: File,
        val tipo: TipoVideoAtual,
        val fase: FasePlaylist,
        val contadorNoBloco: Int
    )

    private val historicoReproducao = java.util.ArrayDeque<HistoricoItem>()
    private var voltandoVideo = false

    private val handlerCorte = Handler(Looper.getMainLooper())

    private val handlerProgresso = Handler(Looper.getMainLooper())
    private val updateProgressoRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                val currentPos = player.currentPosition
                progressBarVideo.progress = currentPos.toInt()
                handlerProgresso.postDelayed(this, 1000)
            }
        }
    } 

    private val handlerWatchdog = Handler(Looper.getMainLooper())
    private var ultimaPosicaoAssistida = -1L
    private var contadorSegundosSemAvanco = 0

    private var ultimoTempoPulo = 0L

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                val posAtual = player.currentPosition
                // Se o relógio do player não avançar por 4 segundos
                if (posAtual == ultimaPosicaoAssistida && posAtual > 0) {
                    contadorSegundosSemAvanco++
                    if (contadorSegundosSemAvanco >= 4) {
                        Toast.makeText(this@MainActivity, "Vídeo travado/incompatível. Pulando...", Toast.LENGTH_SHORT).show()
                        resetarWatchdog()
                        decidirProximoVideo()
                        return
                    }
                } else {
                    ultimaPosicaoAssistida = posAtual
                    contadorSegundosSemAvanco = 0
                }
                handlerWatchdog.postDelayed(this, 1000)
            }
        }
    }

    private fun resetarWatchdog() {
        handlerWatchdog.removeCallbacks(watchdogRunnable)
        ultimaPosicaoAssistida = -1L
        contadorSegundosSemAvanco = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.playerView)
        btnIniciar = findViewById(R.id.btnIniciar)
        val btnConfiguracoes = findViewById<Button>(R.id.btnConfiguracoes)
        loadingBar = findViewById(R.id.loadingBar)
        progressBarVideo = findViewById(R.id.progressBarVideo)

        // Solicita todas as permissões necessárias imediatamente ao abrir o app
        verificarESolicitarPermissoes()

        // 1. FALLBACK DE DECODIFICAÇÃO (Hardware primeiro; se falhar, tenta Software)
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this, renderersFactory).build()
        playerView.player = player

        // Listener para detectar fim da reprodução, erros e monitorar congelamentos
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    resetarWatchdog()
                    decidirProximoVideo()
                } else if (state == Player.STATE_READY) {
                    val duration = player.duration
                    
                    // A barra de progresso só usa o limite de corte (tempoCorteMs) se for vídeo ALEATÓRIO.
                    // Vídeos Principais (MARIA) e Atração SEMPRE mostram a duração REAL completa.
                    val maxProgress = if (tipoAtual == TipoVideoAtual.ALEATORIO && duration > tempoCorteMs) {
                        tempoCorteMs
                    } else {
                        duration
                    }
                    
                    progressBarVideo.max = maxProgress.toInt()
                    progressBarVideo.progress = 0
                    
                    handlerProgresso.removeCallbacks(updateProgressoRunnable)
                    handlerProgresso.post(updateProgressoRunnable)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    handlerProgresso.post(updateProgressoRunnable)
                    resetarWatchdog()
                    handlerWatchdog.postDelayed(watchdogRunnable, 1000)
                } else {
                    handlerProgresso.removeCallbacks(updateProgressoRunnable)
                    resetarWatchdog()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@MainActivity, "Erro ao reproduzir vídeo. Pulando...", Toast.LENGTH_SHORT).show()
                resetarWatchdog()
                decidirProximoVideo()
            }
        })

        // Configuração de gesto: 3 zonas de toque (Esquerda=Voltar, Centro=Config, Direita=Proximo)
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                val screenWidth = playerView.width
                if (screenWidth <= 0) return false

                val touchX = e.x
                val oneThird = screenWidth / 3f

                when {
                    touchX < oneThird -> {
                        // Zona da Esquerda: Voltar para o vídeo anterior
                        decidirVideoAnterior()
                    }
                    touchX < oneThird * 2 -> {
                        // Zona do Meio: Abrir Configurações
                        abrirConfiguracoes()
                    }
                    else -> {
                        // Zona da Direita: Pular para o próximo vídeo
                        decidirProximoVideo()
                    }
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        btnIniciar.setOnClickListener {
            iniciarSistema()
        }

        btnConfiguracoes.setOnClickListener {
            abrirConfiguracoes()
        }

        val btnGithub = findViewById<android.widget.ImageView>(R.id.btnGithub)
        val btnInstagram = findViewById<android.widget.ImageView>(R.id.btnInstagram)

        btnGithub?.setOnClickListener {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/rodrigoVernaschi"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show()
            }
        }

        btnInstagram?.setOnClickListener {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/rodrigo_Vernaschi"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show()
            }
        }

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("auto_start", false)) {
            iniciarSistema()
        }
    }

    private fun todasPermissoesConcedidas(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                return false
            }
        }

        return true
    }

    private var abriuConfiguracoesPermissao = false
    private var handlerMonitorPermissao = Handler(Looper.getMainLooper())
    private var checkPermissaoRunnable: Runnable? = null

    private fun monitorarConcessaoPermissaoEAutoVoltar() {
        checkPermissaoRunnable?.let { handlerMonitorPermissao.removeCallbacks(it) }
        checkPermissaoRunnable = object : Runnable {
            override fun run() {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    if (android.os.Environment.isExternalStorageManager()) {
                        handlerMonitorPermissao.removeCallbacks(this)
                        reiniciarAppProcesso()
                        return
                    }
                }
                handlerMonitorPermissao.postDelayed(this, 300)
            }
        }
        handlerMonitorPermissao.postDelayed(checkPermissaoRunnable!!, 500)
    }

    private fun reiniciarAppProcesso() {
        checkPermissaoRunnable?.let { handlerMonitorPermissao.removeCallbacks(it) }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finishAffinity()
            kotlin.system.exitProcess(0)
        }
    }

    private fun verificarESolicitarPermissoes(): Boolean {
        val permissoesParaPedir = mutableListOf<String>()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissoesParaPedir.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissoesParaPedir.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissoesParaPedir.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissoesParaPedir.toTypedArray(), 1)
            return false
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                abriuConfiguracoesPermissao = true
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                    Toast.makeText(this, "Ative a opção 'Permitir gerenciamento de todos os arquivos' para ler o Pen Drive", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
                monitorarConcessaoPermissaoEAutoVoltar()
                return false
            }
        }

        return true
    }

    private var dialogoPrimeiraConfigExibido = false

    private fun verificarPrimeiraExecucao() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val ehPrimeiraVez = prefs.getBoolean("primeira_execucao", true)

        if (ehPrimeiraVez && todasPermissoesConcedidas() && !dialogoPrimeiraConfigExibido) {
            dialogoPrimeiraConfigExibido = true
            exibirDialogoPrimeiraConfiguracao()
        }
    }

    private fun exibirDialogoPrimeiraConfiguracao() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_primeira_configuracao, null)
        builder.setView(view)
        builder.setCancelable(false)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val passo1Container = view.findViewById<View>(R.id.passo1Container)
        val passo2Container = view.findViewById<View>(R.id.passo2Container)

        val btnPasso1Proximo = view.findViewById<Button>(R.id.btnPasso1Proximo)
        val input = view.findViewById<EditText>(R.id.etPrimeiraPalavraChave)
        val btnSalvar = view.findViewById<Button>(R.id.btnSalvarPrimeiraConfig)

        btnPasso1Proximo.setOnClickListener {
            passo1Container.visibility = View.GONE
            passo2Container.visibility = View.VISIBLE
            input.requestFocus()
        }

        val palavraChaveAtual = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("palavra_chave_maria", "MARIA") ?: "MARIA"
        input.setText(palavraChaveAtual)

        input.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                esconderTeclado(v)
                true
            } else {
                false
            }
        }

        btnSalvar.setOnClickListener {
            val novaPalavra = input.text.toString().trim()
            val palavraFinal = if (novaPalavra.isNotEmpty()) novaPalavra else "MARIA"

            getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                .putString("palavra_chave_maria", palavraFinal)
                .putBoolean("primeira_execucao", false)
                .apply()

            findViewById<EditText>(R.id.editNomeMaria)?.setText(palavraFinal)

            Toast.makeText(this, "Palavra-chave '$palavraFinal' salva com sucesso!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        if (abriuConfiguracoesPermissao) {
            abriuConfiguracoesPermissao = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager()) {
                reiniciarAppProcesso()
            }
        } else {
            verificarPrimeiraExecucao()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Ao aceitar o primeiro diálogo de mídias, passa imediatamente para a 2ª permissão (arquivos)
                verificarESolicitarPermissoes()
            } else {
                Toast.makeText(this, "Permissões necessárias para acessar o Pen Drive", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun iniciarSistema() {
        if (!verificarESolicitarPermissoes()) {
            return
        }

        val layoutInicio = findViewById<View>(R.id.layoutInicio)
        layoutInicio.visibility = View.GONE
        loadingBar.visibility = View.VISIBLE

        Thread {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val palavraChave = prefs.getString("nome_video_principal", "MARIA") ?: "MARIA"
            val pastaAleatorios = prefs.getString("nome_pasta_aleatorios", "VIDEOS") ?: "VIDEOS"
            modoAtracaoAtivoConfig = prefs.getBoolean("modo_atracao_ativo", false)
            val videoAtracao = prefs.getString("video_atracao", "") ?: ""
            tocarAleatoriosModoAtracaoConfig = prefs.getBoolean("tocar_aleatorios_atracao", true)
            intercalarPrincipaisConfig = prefs.getBoolean("intercalar_principais", false)

            qtdMariaConfig = prefs.getInt("qtd_maria", 1).coerceAtLeast(1)
            qtdAtracaoConfig = prefs.getInt("qtd_atracao", 1).coerceAtLeast(1)
            qtdAleatoriosConfig = prefs.getInt("qtd_aleatorios", 2).coerceAtLeast(1)
            val tempoMinutos = prefs.getInt("tempo_max_minutos", 8).coerceAtLeast(1)
            tempoCorteMs = tempoMinutos * 60 * 1000L

            val resultado = UsbScanner.buscarVideosDoUsb(this, palavraChave, pastaAleatorios, modoAtracaoAtivoConfig, videoAtracao)
            runOnUiThread {
                loadingBar.visibility = View.GONE
                listaMaria = resultado.first
                listaAtracao = resultado.second
                listaAleatoria = resultado.third

                val mariaVazio = listaMaria.isEmpty()
                val atracaoVazio = modoAtracaoAtivoConfig && listaAtracao.isEmpty()
                val aleatoriosVazio = (!modoAtracaoAtivoConfig || tocarAleatoriosModoAtracaoConfig) && listaAleatoria.isEmpty()

                if (mariaVazio || atracaoVazio || aleatoriosVazio) {
                    val mensagemErro = when {
                        atracaoVazio -> "Erro Modo Atração: Vídeo/Pasta '$videoAtracao' não encontrado no USB!"
                        mariaVazio -> "Erro: Nenhum vídeo principal '$palavraChave' foi encontrado no USB!"
                        else -> "Erro: Nenhuma pasta '$pastaAleatorios' com vídeos foi encontrada no USB!"
                    }
                    Toast.makeText(this, mensagemErro, Toast.LENGTH_LONG).show()
                    layoutInicio.visibility = View.VISIBLE
                    return@runOnUiThread
                }

                // Inicializa as filas embaralhadas
                filaMaria = listaMaria.shuffled().toMutableList()
                filaAtracao = listaAtracao.shuffled().toMutableList()
                filaAleatoria = listaAleatoria.shuffled().toMutableList()

                playerView.visibility = View.VISIBLE
                faseAtual = FasePlaylist.MARIA
                contadorNoBlocoAtual = 1
                tocarVideoMaria()
            }
        }.start()
    }



    private enum class FasePlaylist { MARIA, ATRACAO, ALEATORIO }
    private var faseAtual = FasePlaylist.MARIA
    private var contadorNoBlocoAtual = 0

    private fun tocarVideoMaria() {
        tipoAtual = TipoVideoAtual.MARIA
        handlerCorte.removeCallbacksAndMessages(null)

        if (filaMaria.isEmpty()) {
            if (listaMaria.isEmpty()) return
            var novaFila = listaMaria.shuffled().toMutableList()
            if (novaFila.size > 1 && novaFila.first() == ultimoMariaTocado) {
                val itemDuplicado = novaFila.removeAt(0)
                novaFila.add(itemDuplicado)
            }
            filaMaria = novaFila
        }

        val video = filaMaria.removeAt(0)
        ultimoMariaTocado = video
        tocarArquivo(video)
    }

    private fun tocarVideoAtracao() {
        tipoAtual = TipoVideoAtual.ATRACAO
        handlerCorte.removeCallbacksAndMessages(null)

        if (filaAtracao.isEmpty()) {
            if (listaAtracao.isEmpty()) return
            var novaFila = listaAtracao.shuffled().toMutableList()
            if (novaFila.size > 1 && novaFila.first() == ultimoAtracaoTocado) {
                val itemDuplicado = novaFila.removeAt(0)
                novaFila.add(itemDuplicado)
            }
            filaAtracao = novaFila
        }

        val video = filaAtracao.removeAt(0)
        ultimoAtracaoTocado = video
        tocarArquivo(video)
    }

    private fun tocarVideoAleatorio() {
        tipoAtual = TipoVideoAtual.ALEATORIO
        if (listaAleatoria.isEmpty()) {
            irParaProximaFaseAposMaria()
            return
        }

        if (filaAleatoria.isEmpty()) {
            var novaFila = listaAleatoria.shuffled().toMutableList()
            if (novaFila.size > 1 && novaFila.first() == ultimoAleatorioTocado) {
                val itemDuplicado = novaFila.removeAt(0)
                novaFila.add(itemDuplicado)
            }
            filaAleatoria = novaFila
        }

        val videoSorteado = filaAleatoria.removeAt(0)
        ultimoAleatorioTocado = videoSorteado
        tocarArquivo(videoSorteado)

        handlerCorte.removeCallbacksAndMessages(null)
        handlerCorte.postDelayed({
            decidirProximoVideo()
        }, tempoCorteMs)
    }

    private fun tocarArquivo(arquivo: File) {
        if (!voltandoVideo) {
            val itemAtual = HistoricoItem(arquivo, tipoAtual, faseAtual, contadorNoBlocoAtual)
            historicoReproducao.push(itemAtual)
            if (historicoReproducao.size > 50) {
                historicoReproducao.removeLast()
            }
        }

        player.clearMediaItems()
        val mediaItem = MediaItem.fromUri("file://${arquivo.absolutePath}")
        player.setMediaItem(mediaItem)
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        player.prepare()
        player.play()
        progressBarVideo.visibility = View.VISIBLE
    }

    private fun decidirProximoVideo() {
        val agora = System.currentTimeMillis()
        if (agora - ultimoTempoPulo < 500L) return
        ultimoTempoPulo = agora

        handlerCorte.removeCallbacksAndMessages(null)
        resetarWatchdog()
        avancarPlaylist()
    }

    private fun decidirVideoAnterior() {
        val agora = System.currentTimeMillis()
        if (agora - ultimoTempoPulo < 500L) return
        ultimoTempoPulo = agora

        if (historicoReproducao.size <= 1) {
            Toast.makeText(this, "Início da lista de reprodução", Toast.LENGTH_SHORT).show()
            return
        }

        handlerCorte.removeCallbacksAndMessages(null)
        resetarWatchdog()

        // Remove o vídeo atual que estava sendo exibido no topo do histórico
        historicoReproducao.pop()

        // Obtém o vídeo anterior reproduzido
        val anterior = historicoReproducao.peek()
        if (anterior != null) {
            voltandoVideo = true
            faseAtual = anterior.fase
            contadorNoBlocoAtual = anterior.contadorNoBloco
            tipoAtual = anterior.tipo

            tocarArquivo(anterior.arquivo)
            voltandoVideo = false

            // Re-agenda o temporizador de corte se o vídeo anterior restaurado for ALEATORIO
            if (tipoAtual == TipoVideoAtual.ALEATORIO) {
                handlerCorte.removeCallbacksAndMessages(null)
                handlerCorte.postDelayed({
                    decidirProximoVideo()
                }, tempoCorteMs)
            }
        }
    }

    private fun avancarPlaylist() {
        when (faseAtual) {
            FasePlaylist.MARIA -> {
                // Se INTERCALAR estiver ON: toca 1 flyer por ciclo (intercalado 1 a 1).
                // Se INTERCALAR estiver OFF: toca o bloco com todos os flyers disponíveis (ou a Qtd configurada).
                val limite = if (intercalarPrincipaisConfig) {
                    1
                } else {
                    maxOf(qtdMariaConfig, listaMaria.size)
                }

                if (contadorNoBlocoAtual < limite) {
                    contadorNoBlocoAtual++
                    tocarVideoMaria()
                } else {
                    irParaProximaFaseAposMaria()
                }
            }
            FasePlaylist.ATRACAO -> {
                if (contadorNoBlocoAtual < qtdAtracaoConfig) {
                    contadorNoBlocoAtual++
                    tocarVideoAtracao()
                } else {
                    irParaProximaFaseAposAtracao()
                }
            }
            FasePlaylist.ALEATORIO -> {
                if (contadorNoBlocoAtual < qtdAleatoriosConfig && listaAleatoria.isNotEmpty()) {
                    contadorNoBlocoAtual++
                    tocarVideoAleatorio()
                } else {
                    faseAtual = FasePlaylist.MARIA
                    contadorNoBlocoAtual = 1
                    tocarVideoMaria()
                }
            }
        }
    }

    private fun irParaProximaFaseAposMaria() {
        if (modoAtracaoAtivoConfig && listaAtracao.isNotEmpty()) {
            faseAtual = FasePlaylist.ATRACAO
            contadorNoBlocoAtual = 1
            tocarVideoAtracao()
        } else if (listaAleatoria.isNotEmpty()) {
            faseAtual = FasePlaylist.ALEATORIO
            contadorNoBlocoAtual = 1
            tocarVideoAleatorio()
        } else {
            faseAtual = FasePlaylist.MARIA
            contadorNoBlocoAtual = 1
            tocarVideoMaria()
        }
    }

    private fun irParaProximaFaseAposAtracao() {
        if (tocarAleatoriosModoAtracaoConfig && listaAleatoria.isNotEmpty()) {
            faseAtual = FasePlaylist.ALEATORIO
            contadorNoBlocoAtual = 1
            tocarVideoAleatorio()
        } else {
            faseAtual = FasePlaylist.MARIA
            contadorNoBlocoAtual = 1
            tocarVideoMaria()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            abrirConfiguracoes()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            decidirProximoVideo()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            decidirVideoAnterior()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (tratarAcaoVoltar()) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun tratarAcaoVoltar(): Boolean {
        // 1. Se o modal de verificação estiver visível, fecha o modal
        val layoutModalConfirmacao = findViewById<View>(R.id.layoutModalConfirmacao)
        if (layoutModalConfirmacao.visibility == View.VISIBLE) {
            layoutModalConfirmacao.visibility = View.GONE
            return true
        }

        // 2. Se a tela de configurações estiver aberta, fecha as configurações
        val layoutSettingsContainer = findViewById<View>(R.id.layoutSettingsContainer)
        if (layoutSettingsContainer.visibility == View.VISIBLE) {
            layoutSettingsContainer.visibility = View.GONE
            return true
        }

        // 3. Se o player estiver rodando (ou visível), para o vídeo e volta para a tela inicial
        if (playerView.visibility == View.VISIBLE || (::player.isInitialized && player.isPlaying)) {
            handlerCorte.removeCallbacksAndMessages(null)
            handlerProgresso.removeCallbacks(updateProgressoRunnable)
            resetarWatchdog()
            if (::player.isInitialized) {
                player.stop()
            }

            playerView.visibility = View.GONE
            progressBarVideo.visibility = View.GONE
            val layoutInicio = findViewById<View>(R.id.layoutInicio)
            layoutInicio.visibility = View.VISIBLE
            btnIniciar.requestFocus()
            Toast.makeText(this, "Pressione Voltar novamente para sair", Toast.LENGTH_SHORT).show()
            return true
        }

        // 4. Se já estiver na tela inicial (layoutInicio visível), retorna false para permitir o fechamento do app
        return false
    }

    private fun esconderTeclado(view: View) {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun abrirConfiguracoes() {
        val layoutSettingsContainer = findViewById<View>(R.id.layoutSettingsContainer)
        val switchAutoStart = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAutoStart)
        val switchIntercalarPrincipais = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchIntercalarPrincipais)
        
        val switchModoAtracao = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchModoAtracao)
        val layoutModoAtracaoCampos = findViewById<View>(R.id.layoutModoAtracaoCampos)
        val editVideoAtracao = findViewById<android.widget.EditText>(R.id.editVideoAtracao)
        val editQtdAtracao = findViewById<android.widget.EditText>(R.id.editQtdAtracao)
        val switchTocarAleatoriosAtracao = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchTocarAleatoriosAtracao)

        val editNomeMaria = findViewById<android.widget.EditText>(R.id.editNomeMaria)
        val editPastaAleatorios = findViewById<android.widget.EditText>(R.id.editPastaAleatorios)
        val editQtdMaria = findViewById<android.widget.EditText>(R.id.editQtdMaria)
        val editQtdAleatorios = findViewById<android.widget.EditText>(R.id.editQtdAleatorios)
        val editTempoMaxMinutos = findViewById<android.widget.EditText>(R.id.editTempoMaxMinutos)
        val btnSalvarSettings = findViewById<Button>(R.id.btnSalvarSettings)
        val btnFecharSettings = findViewById<Button>(R.id.btnFecharSettings)

        val listenerFecharTeclado = android.widget.TextView.OnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                esconderTeclado(v)
                true
            } else {
                false
            }
        }

        listOf(editNomeMaria, editPastaAleatorios, editQtdMaria, editQtdAleatorios, editTempoMaxMinutos, editVideoAtracao, editQtdAtracao).forEach {
            it.setOnEditorActionListener(listenerFecharTeclado)
        }

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        switchAutoStart.isChecked = prefs.getBoolean("auto_start", false)
        switchIntercalarPrincipais.isChecked = prefs.getBoolean("intercalar_principais", false)

        val modoAtracaoAtivo = prefs.getBoolean("modo_atracao_ativo", false)
        switchModoAtracao.isChecked = modoAtracaoAtivo
        layoutModoAtracaoCampos.visibility = if (modoAtracaoAtivo) View.VISIBLE else View.GONE
        editVideoAtracao.setText(prefs.getString("video_atracao", ""))
        editQtdAtracao.setText(prefs.getInt("qtd_atracao", 1).toString())
        switchTocarAleatoriosAtracao.isChecked = prefs.getBoolean("tocar_aleatorios_atracao", true)

        switchModoAtracao.setOnCheckedChangeListener { _, isChecked ->
            layoutModoAtracaoCampos.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        editNomeMaria.setText(prefs.getString("nome_video_principal", "MARIA"))
        editPastaAleatorios.setText(prefs.getString("nome_pasta_aleatorios", "VIDEOS"))
        editQtdMaria.setText(prefs.getInt("qtd_maria", 1).toString())
        editQtdAleatorios.setText(prefs.getInt("qtd_aleatorios", 2).toString())
        editTempoMaxMinutos.setText(prefs.getInt("tempo_max_minutos", 8).toString())

        btnFecharSettings.setOnClickListener {
            layoutSettingsContainer.visibility = View.GONE
        }

        btnSalvarSettings.setOnClickListener {
            val novoNome = editNomeMaria.text.toString().trim().uppercase().ifEmpty { "MARIA" }
            val novaPasta = editPastaAleatorios.text.toString().trim().uppercase().ifEmpty { "VIDEOS" }
            val novaQtdMaria = editQtdMaria.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val novaQtdAleatorios = editQtdAleatorios.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 2
            val novoTempo = editTempoMaxMinutos.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 8
            val autoStart = switchAutoStart.isChecked
            val intercalar = switchIntercalarPrincipais.isChecked

            val novoModoAtracao = switchModoAtracao.isChecked
            val novoVideoAtracao = editVideoAtracao.text.toString().trim().uppercase()
            val novaQtdAtracao = editQtdAtracao.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val novoTocarAleatoriosAtracao = switchTocarAleatoriosAtracao.isChecked

            testarESalvarConfiguracoes(
                novoNome, novaPasta, novaQtdMaria, novaQtdAleatorios, novoTempo, autoStart,
                novoModoAtracao, novoVideoAtracao, novaQtdAtracao, novoTocarAleatoriosAtracao, intercalar
            )
        }

        layoutSettingsContainer.visibility = View.VISIBLE
        btnSalvarSettings.requestFocus()
    }

    private fun testarESalvarConfiguracoes(
        novoNome: String,
        novaPasta: String,
        novaQtdMaria: Int,
        novaQtdAleatorios: Int,
        novoTempo: Int,
        autoStart: Boolean,
        modoAtracaoAtivo: Boolean,
        videoAtracao: String,
        novaQtdAtracao: Int,
        tocarAleatoriosAtracao: Boolean,
        intercalar: Boolean
    ) {
        val layoutModalConfirmacao = findViewById<View>(R.id.layoutModalConfirmacao)
        val txtTituloModal = findViewById<android.widget.TextView>(R.id.txtTituloModal)
        val txtMensagemModal = findViewById<android.widget.TextView>(R.id.txtMensagemModal)
        val loadingBarModal = findViewById<View>(R.id.loadingBarModal)
        val btnSalvarMesmoAssimModal = findViewById<Button>(R.id.btnSalvarMesmoAssimModal)
        val btnFecharModal = findViewById<Button>(R.id.btnFecharModal)

        txtTituloModal.text = "Verificando USB..."
        txtMensagemModal.text = "Escaneando arquivos no USB..."
        loadingBarModal.visibility = View.VISIBLE
        btnSalvarMesmoAssimModal.visibility = View.GONE
        btnFecharModal.visibility = View.GONE
        layoutModalConfirmacao.visibility = View.VISIBLE

        val salvarDados = {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit()
                .putBoolean("auto_start", autoStart)
                .putBoolean("intercalar_principais", intercalar)
                .putBoolean("modo_atracao_ativo", modoAtracaoAtivo)
                .putString("video_atracao", videoAtracao)
                .putInt("qtd_atracao", novaQtdAtracao)
                .putBoolean("tocar_aleatorios_atracao", tocarAleatoriosAtracao)
                .putString("nome_video_principal", novoNome)
                .putString("nome_pasta_aleatorios", novaPasta)
                .putInt("qtd_maria", novaQtdMaria)
                .putInt("qtd_aleatorios", novaQtdAleatorios)
                .putInt("tempo_max_minutos", novoTempo)
                .apply()

            modoAtracaoAtivoConfig = modoAtracaoAtivo
            qtdAtracaoConfig = novaQtdAtracao
            tocarAleatoriosModoAtracaoConfig = tocarAleatoriosAtracao
            qtdMariaConfig = novaQtdMaria
            qtdAleatoriosConfig = novaQtdAleatorios
            tempoCorteMs = novoTempo * 60 * 1000L
            intercalarPrincipaisConfig = intercalar

            layoutModalConfirmacao.visibility = View.GONE
            findViewById<View>(R.id.layoutSettingsContainer).visibility = View.GONE
            Toast.makeText(this, "Configurações salvas com sucesso!", Toast.LENGTH_SHORT).show()
        }

        Thread {
            val resultado = UsbScanner.buscarVideosDoUsb(this, novoNome, novaPasta, modoAtracaoAtivo, videoAtracao)
            runOnUiThread {
                loadingBarModal.visibility = View.GONE
                btnFecharModal.visibility = View.VISIBLE

                val mariaVazio = resultado.first.isEmpty()
                val atracaoVazio = modoAtracaoAtivo && resultado.second.isEmpty()
                val aleatoriosVazio = (!modoAtracaoAtivo || tocarAleatoriosAtracao) && resultado.third.isEmpty()

                if (!mariaVazio && !atracaoVazio && !aleatoriosVazio) {
                    salvarDados()
                } else {
                    txtTituloModal.text = "Atenção ao salvar"
                    val mensagemErro = when {
                        atracaoVazio -> "Vídeo/Pasta da atração '$videoAtracao' não foi encontrado no USB."
                        mariaVazio -> "Nenhum vídeo principal '$novoNome' foi encontrado no USB."
                        else -> "Nenhuma pasta '$novaPasta' com vídeos foi encontrada no USB."
                    }
                    txtMensagemModal.text = "$mensagemErro\n\nDeseja salvar mesmo assim?"
                    btnSalvarMesmoAssimModal.visibility = View.VISIBLE

                    btnSalvarMesmoAssimModal.setOnClickListener {
                        salvarDados()
                    }

                    btnFecharModal.setOnClickListener {
                        layoutModalConfirmacao.visibility = View.GONE
                    }
                    btnFecharModal.requestFocus()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerCorte.removeCallbacksAndMessages(null)
        handlerProgresso.removeCallbacks(updateProgressoRunnable)
        resetarWatchdog()
        player.release()
    }
}
