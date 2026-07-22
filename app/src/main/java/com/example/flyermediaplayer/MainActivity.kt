package com.example.flyermediaplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Mantém a tela sempre ligada
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.playerView)
        btnIniciar = findViewById(R.id.btnIniciar)
        loadingBar = findViewById(R.id.loadingBar)
        progressBarVideo = findViewById(R.id.progressBarVideo)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // Listener para detectar fim da reprodução e decidir o próximo vídeo
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    decidirProximoVideo()
                } else if (state == Player.STATE_READY) {
                    val duration = player.duration
                    val maxProgress = if (duration > tempoCorteMs) tempoCorteMs else duration
                    progressBarVideo.max = maxProgress.toInt()
                    progressBarVideo.progress = 0
                    
                    handlerProgresso.removeCallbacks(updateProgressoRunnable)
                    handlerProgresso.post(updateProgressoRunnable)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    handlerProgresso.post(updateProgressoRunnable)
                } else {
                    handlerProgresso.removeCallbacks(updateProgressoRunnable)
                }
            }
        })

        val btnConfiguracoes = findViewById<Button>(R.id.btnConfiguracoes)

        btnIniciar.setOnClickListener {
            iniciarSistema()
        }

        btnConfiguracoes.setOnClickListener {
            abrirConfiguracoes()
        }

        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                abrirConfiguracoes()
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun iniciarSistema() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                    Toast.makeText(this, "Conceda a permissão para acessar os arquivos do Pen Drive", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
                return
            }
        } else {
            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
                return
            }
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
                videosTocadosNoBloco = 0
                tocarProximoVideoPrincipal()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarSistema()
        } else {
            Toast.makeText(this, "Permissão necessária para ler o USB", Toast.LENGTH_LONG).show()
        }
    }

    private fun tocarProximoVideoPrincipal() {
        tipoAtual = TipoVideoAtual.MARIA
        handlerCorte.removeCallbacksAndMessages(null)

        if (filaMaria.isEmpty()) {
            if (listaMaria.isEmpty()) return
            filaMaria = listaMaria.shuffled().toMutableList()
        }

        val video = filaMaria.removeAt(0)
        tocarArquivo(video)
    }

    private fun tocarProximoVideoAtracao() {
        tipoAtual = TipoVideoAtual.ATRACAO
        handlerCorte.removeCallbacksAndMessages(null)

        if (filaAtracao.isEmpty()) {
            if (listaAtracao.isEmpty()) return
            filaAtracao = listaAtracao.shuffled().toMutableList()
        }

        val video = filaAtracao.removeAt(0)
        tocarArquivo(video)
    }

    private fun tocarProximoVideoAleatorio() {
        tipoAtual = TipoVideoAtual.ALEATORIO
        if (listaAleatoria.isEmpty()) {
            tocarProximoVideoPrincipal()
            return
        }

        if (filaAleatoria.isEmpty()) {
            filaAleatoria = listaAleatoria.shuffled().toMutableList()
        }

        val videoSorteado = filaAleatoria.removeAt(0)
        tocarArquivo(videoSorteado)

        handlerCorte.removeCallbacksAndMessages(null)
        handlerCorte.postDelayed({
            decidirProximoVideo()
        }, tempoCorteMs)
    }

    private fun tocarArquivo(arquivo: File) {
        player.clearMediaItems()
        val mediaItem = MediaItem.fromUri("file://${arquivo.absolutePath}")
        player.setMediaItem(mediaItem)
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        player.prepare()
        player.play()
        progressBarVideo.visibility = View.VISIBLE
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val layoutModalConfirmacao = findViewById<View>(R.id.layoutModalConfirmacao)
            if (layoutModalConfirmacao.visibility == View.VISIBLE) {
                layoutModalConfirmacao.visibility = View.GONE
                return true
            }

            val layoutSettingsContainer = findViewById<View>(R.id.layoutSettingsContainer)
            if (layoutSettingsContainer.visibility == View.VISIBLE) {
                layoutSettingsContainer.visibility = View.GONE
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun abrirConfiguracoes() {
        val layoutSettingsContainer = findViewById<View>(R.id.layoutSettingsContainer)
        val switchAutoStart = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAutoStart)
        
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

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        switchAutoStart.isChecked = prefs.getBoolean("auto_start", false)

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
            val novoModoAtracao = switchModoAtracao.isChecked
            val novoVideoAtracao = editVideoAtracao.text.toString().trim().uppercase()
            val novaQtdAtracao = editQtdAtracao.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val novoTocarAleatoriosAtracao = switchTocarAleatoriosAtracao.isChecked

            val novoNome = editNomeMaria.text.toString().trim().uppercase().ifEmpty { "MARIA" }
            val novaPasta = editPastaAleatorios.text.toString().trim().uppercase().ifEmpty { "VIDEOS" }
            val novaQtdMaria = editQtdMaria.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val novaQtdAleatorios = editQtdAleatorios.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 2
            val novoTempo = editTempoMaxMinutos.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 8
            val autoStart = switchAutoStart.isChecked

            testarESalvarConfiguracoes(
                novoNome, novaPasta, novaQtdMaria, novaQtdAleatorios, novoTempo, autoStart,
                novoModoAtracao, novoVideoAtracao, novaQtdAtracao, novoTocarAleatoriosAtracao
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
        tocarAleatoriosAtracao: Boolean
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

    private fun decidirProximoVideo() {
        handlerCorte.removeCallbacksAndMessages(null)
        videosTocadosNoBloco++

        if (modoAtracaoAtivoConfig) {
            // MODO ATRAÇÃO ATIVO
            when (tipoAtual) {
                TipoVideoAtual.MARIA -> {
                    if (videosTocadosNoBloco < qtdMariaConfig) {
                        tocarProximoVideoPrincipal()
                    } else {
                        videosTocadosNoBloco = 0
                        tocarProximoVideoAtracao()
                    }
                }
                TipoVideoAtual.ATRACAO -> {
                    if (videosTocadosNoBloco < qtdAtracaoConfig) {
                        tocarProximoVideoAtracao()
                    } else {
                        videosTocadosNoBloco = 0
                        if (tocarAleatoriosModoAtracaoConfig && listaAleatoria.isNotEmpty()) {
                            tocarProximoVideoAleatorio()
                        } else {
                            tocarProximoVideoPrincipal()
                        }
                    }
                }
                TipoVideoAtual.ALEATORIO -> {
                    if (videosTocadosNoBloco < qtdAleatoriosConfig && listaAleatoria.isNotEmpty()) {
                        tocarProximoVideoAleatorio()
                    } else {
                        videosTocadosNoBloco = 0
                        tocarProximoVideoPrincipal()
                    }
                }
            }
        } else {
            // MODO NORMAL (MARIA -> ALEATÓRIOS)
            when (tipoAtual) {
                TipoVideoAtual.MARIA -> {
                    if (videosTocadosNoBloco < qtdMariaConfig) {
                        tocarProximoVideoPrincipal()
                    } else {
                        videosTocadosNoBloco = 0
                        tocarProximoVideoAleatorio()
                    }
                }
                else -> {
                    if (videosTocadosNoBloco < qtdAleatoriosConfig && listaAleatoria.isNotEmpty()) {
                        tocarProximoVideoAleatorio()
                    } else {
                        videosTocadosNoBloco = 0
                        tocarProximoVideoPrincipal()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handlerCorte.removeCallbacksAndMessages(null)
        handlerProgresso.removeCallbacks(updateProgressoRunnable)
        player.release()
    }
}
