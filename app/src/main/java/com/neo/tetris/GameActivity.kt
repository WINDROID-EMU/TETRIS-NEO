package com.neo.tetris

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.neo.tetris.databinding.ActivityGameBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var gameView: GameView
    private lateinit var gameLogic: GameLogic
    private var isPaused = false
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var sharedPref: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isMovingLeft = false
    private var isMovingRight = false
    
    // Configurações do jogo
    private var soundEnabled = true
    private var gameSpeed = "normal"
    
    private val moveRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                if (isMovingLeft) {
                    gameLogic.moveLeft()
                    handler.postDelayed(this, 150) // Movimento mais lento e constante
                }
                if (isMovingRight) {
                    gameLogic.moveRight()
                    handler.postDelayed(this, 150) // Movimento mais lento e constante
                }
            }
        }
    }
    
    // Runnable para verificar game over continuamente
    private val gameOverCheckRunnable = object : Runnable {
        override fun run() {
            if (gameLogic.isGameOver && !isPaused) {
                showGameOverDialog()
            } else {
                // Continuar verificando a cada 500ms
                handler.postDelayed(this, 500)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Esconder completamente a barra de status
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Carregar configurações
        sharedPref = getSharedPreferences("GameSettings", Context.MODE_PRIVATE)
        soundEnabled = sharedPref.getBoolean("sound_enabled", true)
        gameSpeed = sharedPref.getString("game_speed", "normal") ?: "normal"
        
        // Inicializar o jogo
        gameLogic = GameLogic(this)
        gameView = GameView(this, gameLogic)
        binding.gameContainer.addView(gameView)
        
        // Configurar controles
        setupControls()
        
        // Configurar botão de pausa
        binding.pauseButton.setOnClickListener {
            togglePause()
        }
        
        // Configurar botão de reset
        binding.resetButton.setOnClickListener {
            resetGame()
        }
        
        // Configurar música de fundo
        setupBackgroundMusic()
        
        // Iniciar verificação de game over
        startGameOverCheck()
    }
    
    private fun setupControls() {
        try {
            // Configurar botões de controle direcional
            binding.leftButton.setOnTouchListener { _, event ->
                try {
                    if (!isPaused) {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isMovingLeft = true
                                gameLogic.moveLeft() // Movimento inicial imediato
                                handler.postDelayed(moveRunnable, 150) // Inicia o movimento contínuo
                                return@setOnTouchListener true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isMovingLeft = false
                                handler.removeCallbacks(moveRunnable)
                                return@setOnTouchListener true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro no touch do botão esquerdo: ${e.message}")
                }
                return@setOnTouchListener false
            }
            
            binding.rightButton.setOnTouchListener { _, event ->
                try {
                    if (!isPaused) {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isMovingRight = true
                                gameLogic.moveRight() // Movimento inicial imediato
                                handler.postDelayed(moveRunnable, 150) // Inicia o movimento contínuo
                                return@setOnTouchListener true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isMovingRight = false
                                handler.removeCallbacks(moveRunnable)
                                return@setOnTouchListener true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro no touch do botão direito: ${e.message}")
                }
                return@setOnTouchListener false
            }
            
            // Configuração para queda gradativa quando o botão para baixo é pressionado
            binding.downButton.setOnTouchListener { _, event ->
                try {
                    if (!isPaused) {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                // Inicia a queda rápida quando o botão é pressionado
                                gameLogic.startFastFall()
                                return@setOnTouchListener true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                // Para a queda rápida quando o botão é solto
                                gameLogic.stopFastFall()
                                return@setOnTouchListener true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro no touch do botão baixo: ${e.message}")
                }
                return@setOnTouchListener false
            }
            
            // Adicionar evento de clique para queda instantânea (hard drop) com duplo toque
            binding.downButton.setOnClickListener {
                try {
                    if (!isPaused) gameLogic.hardDrop()
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro no hard drop: ${e.message}")
                }
            }
            
            binding.rotateButton.setOnClickListener {
                try {
                    if (!isPaused) gameLogic.rotate()
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro na rotação: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao configurar controles: ${e.message}")
        }
    }
    
    private fun setupBackgroundMusic() {
        try {
            // Verificar se o recurso gamesound.mp3 existe
            val resourceId = resources.getIdentifier("gamesound", "raw", packageName)
            if (resourceId != 0) {
                mediaPlayer = MediaPlayer.create(this, resourceId)
                mediaPlayer?.isLooping = true
                // Reduzir o volume da música de fundo para 30%
                mediaPlayer?.setVolume(0.5f, 0.5f)
                mediaPlayer?.start()
            } else {
                Log.w("GameActivity", "Arquivo de música 'gamesound.mp3' não encontrado. Adicione o arquivo na pasta res/raw/")
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao iniciar música: ${e.message}")
        }
    }
    
    private fun togglePause() {
        try {
            Log.d("GameActivity", "togglePause - Alterando estado de ${if(isPaused) "PAUSADO" else "EM EXECUÇÃO"}")
            
            // Invertemos o estado
            isPaused = !isPaused
            
            if (isPaused) {
                // Pausar o jogo
                Log.d("GameActivity", "Pausando o jogo")
                gameView.pauseGame()
                binding.pauseButton.text = getString(R.string.resume)
                mediaPlayer?.pause()
            } else {
                // Despausar o jogo
                Log.d("GameActivity", "Despausando o jogo")
                gameView.resumeGame()
                binding.pauseButton.text = getString(R.string.pause)
                mediaPlayer?.start()
            }
            
            // Atualizar visualmente o botão
            binding.pauseButton.invalidate()
            
            Log.d("GameActivity", "Estado após alteração: ${if(isPaused) "PAUSADO" else "EM EXECUÇÃO"}")
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro no togglePause: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun resetGame() {
        try {
            // Primeiro pausamos o jogo e liberamos recursos
            if (!isPaused) {
                isPaused = true
                gameView.pauseGame()
            }
            
            // Liberamos a mídia
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {
                Log.e("GameActivity", "Erro ao liberar mídia: ${e.message}")
            }
            
            // Voltamos para a tela inicial
            val intent = Intent(this, StartActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish() // Finalizamos a atividade atual
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro crítico ao resetar: ${e.message}")
            e.printStackTrace()
            
            // Em último caso, tentamos apenas voltar para a tela inicial
            try {
                val intent = Intent(this, StartActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e2: Exception) {
                Log.e("GameActivity", "Falha total: ${e2.message}")
            }
        }
    }
    
    private fun startGameOverCheck() {
        handler.post(gameOverCheckRunnable)
    }
    
    private fun showGameOverDialog() {
        // Primeiro pausamos o jogo para evitar atualizações no fundo
        isPaused = true
        gameView.pauseGame()
        
        // Pausar música de fundo
        mediaPlayer?.pause()
        
        // Salvar high score se necessário
        val currentScore = gameLogic.score
        val highScore = sharedPref.getInt("high_score", 0)
        if (currentScore > highScore) {
            sharedPref.edit().putInt("high_score", currentScore).apply()
        }
        
        // Criar e exibir o diálogo de game over
        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage("Sua pontuação: $currentScore\nMelhor pontuação: ${Math.max(highScore, currentScore)}")
            .setCancelable(false)
            .setPositiveButton("Jogar Novamente") { _, _ ->
                restartGame()
            }
            .setNegativeButton("Menu Inicial") { _, _ ->
                // Voltar para o menu inicial
                val intent = Intent(this, StartActivity::class.java)
                startActivity(intent)
                finish()
            }
            .show()
    }
    
    private fun restartGame() {
        // Reiniciar completamente a atividade
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("sound_enabled", soundEnabled)
        intent.putExtra("game_speed", gameSpeed)
        startActivity(intent)
        finish()
    }
    
    override fun onPause() {
        super.onPause()
        if (!isPaused) {
            togglePause()
        }
        mediaPlayer?.pause()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Garantir que as barras de sistema permaneçam ocultas após retomar a atividade
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        
        if (!isPaused && !gameLogic.isGameOver) {
            mediaPlayer?.start()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Em vez de fechar diretamente, mostrar diálogo de confirmação
        AlertDialog.Builder(this)
            .setTitle("Sair do jogo")
            .setMessage("Deseja realmente sair do jogo?")
            .setPositiveButton("Sim") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("Não", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            handler.removeCallbacks(moveRunnable)
            handler.removeCallbacks(gameOverCheckRunnable)
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao liberar recursos: ${e.message}")
        }
        gameLogic.releaseResources()
    }
} 