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
    private var listaAleatoria = listOf<File>()
    
    // Filas para controle de repetição (Playlist)
    private var filaMaria = mutableListOf<File>()
    private var filaAleatoria = mutableListOf<File>()

    private var tocandoMaria = false
    private var videosAleatoriosTocados = 0

    // Cronômetro para cortar vídeos de futebol/bandas (8 minutos)
    private val handlerCorte = Handler(Looper.getMainLooper())
    private val tempoCorteMs = 8 * 60 * 1000L

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
                    // Se o vídeo for maior que 8 minutos, a barra vai até 8 minutos
                    // Se for menor, vai até o final do vídeo
                    val maxProgress = if (duration > tempoCorteMs) tempoCorteMs else duration
                    progressBarVideo.max = maxProgress.toInt()
                    progressBarVideo.progress = 0
                    
                    // Inicia atualização da barra
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

        btnIniciar.setOnClickListener {
            iniciarSistema()
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
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
            return
        }

        btnIniciar.visibility = View.GONE
        loadingBar.visibility = View.VISIBLE

        Thread {
            val listas = UsbScanner.buscarVideosDoUsb()
            runOnUiThread {
                loadingBar.visibility = View.GONE
                listaMaria = listas.first
                listaAleatoria = listas.second

                if (listaMaria.isEmpty()) {
                    Toast.makeText(this, "Nenhum vídeo MARIA encontrado no USB!", Toast.LENGTH_LONG).show()
                    btnIniciar.visibility = View.VISIBLE
                    return@runOnUiThread
                }

                // Inicializa as filas embaralhadas
                filaMaria = listaMaria.shuffled().toMutableList()
                filaAleatoria = listaAleatoria.shuffled().toMutableList()

                playerView.visibility = View.VISIBLE
                tocarVideoMaria()
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

    private fun tocarVideoMaria() {
        tocandoMaria = true
        videosAleatoriosTocados = 0
        
        // Cancela qualquer corte pendente (Flyer da Maria toca inteiro)
        handlerCorte.removeCallbacksAndMessages(null)

        // Se a fila acabou, recarrega e embaralha de novo
        if (filaMaria.isEmpty()) {
            if (listaMaria.isEmpty()) return
            filaMaria = listaMaria.shuffled().toMutableList()
        }

        val video = filaMaria.removeAt(0)
        tocarArquivo(video)
    }

    private fun tocarVideoAleatorio() {
        tocandoMaria = false
        if (listaAleatoria.isEmpty()) {
            tocarVideoMaria()
            return
        }

        // Se a fila acabou, recarrega e embaralha de novo
        if (filaAleatoria.isEmpty()) {
            filaAleatoria = listaAleatoria.shuffled().toMutableList()
        }

        val videoSorteado = filaAleatoria.removeAt(0)
        tocarArquivo(videoSorteado)

        // Inicia o cronômetro para cortar o vídeo em 8 minutos
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

        // Garante que a barra apareça
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
            val layoutSettings = findViewById<View>(R.id.layoutSettings)
            if (layoutSettings.visibility == View.VISIBLE) {
                layoutSettings.visibility = View.GONE
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun abrirConfiguracoes() {
        val layoutSettings = findViewById<View>(R.id.layoutSettings)
        val switchAutoStart = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAutoStart)
        val btnFecharSettings = findViewById<Button>(R.id.btnFecharSettings)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        switchAutoStart.isChecked = prefs.getBoolean("auto_start", false)

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_start", isChecked).apply()
        }

        btnFecharSettings.setOnClickListener {
            layoutSettings.visibility = View.GONE
        }

        layoutSettings.visibility = View.VISIBLE
        switchAutoStart.requestFocus()
    }

    private fun decidirProximoVideo() {
        // Para o cronômetro de corte para evitar execução duplicada
        handlerCorte.removeCallbacksAndMessages(null)

        if (tocandoMaria) {
            // Se acabou de tocar um MARIA (flyer), vai para o primeiro aleatório
            tocarVideoAleatorio()
        } else {
            videosAleatoriosTocados++
            
            // Toca no máximo 2 aleatórios antes de voltar para o MARIA
            if (videosAleatoriosTocados >= 2) {
                tocarVideoMaria()
            } else {
                tocarVideoAleatorio()
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
