package com.neo.tetris

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
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
        
        // Inicializar SharedPreferences
        sharedPref = getSharedPreferences("tetris_prefs", Context.MODE_PRIVATE)
        
        // Inicializar o jogo
        initializeGame()
        
        // Iniciar verificação de game over
        startGameOverCheck()
    }
    
    private fun initializeGame() {
        // Inicializar a lógica do jogo
        gameLogic = GameLogic(this)
        gameView = GameView(this, gameLogic)
        binding.gameContainer.addView(gameView)
        
        // Iniciar música de fundo
        setupGameMusic()
        
        // Configurar controles
        setupControls()
    }
    
    private fun startGameOverCheck() {
        lifecycleScope.launch {
            while (true) {
                delay(1000)
                if (gameLogic.isGameOver && !isPaused) {
                    showGameOverDialog()
                    break
                }
            }
        }
    }
    
    private fun setupGameMusic() {
        try {
            // Verificar se o recurso gamesound.mp3 existe
            val resourceId = resources.getIdentifier("gamesound", "raw", packageName)
            if (resourceId != 0) {
                mediaPlayer = MediaPlayer.create(this, resourceId)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } else {
                Log.w("GameActivity", "Arquivo de música 'gamesound.mp3' não encontrado. Adicione o arquivo na pasta res/raw/")
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao iniciar música: ${e.message}")
        }
    }
    
    private fun setupControls() {
        try {
            // Configurar botões de controle direcional
            binding.leftButton.setOnClickListener {
                try {
                    if (!isPaused) gameLogic.moveLeft()
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro no botão esquerdo: ${e.message}")
                }
            }
            
            binding.rightButton.setOnClickListener {
                try {
                    if (!isPaused) gameLogic.moveRight()
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro no botão direito: ${e.message}")
                }
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
            
            // Botão de pause/resume - com tratamento de erro
            binding.pauseButton.setOnClickListener {
                try {
                    Log.d("GameActivity", "Botão de pausa clicado. Estado atual: ${if(isPaused) "PAUSADO" else "EM EXECUÇÃO"}")
                    togglePause()
                    Log.d("GameActivity", "Após togglePause: ${if(isPaused) "PAUSADO" else "EM EXECUÇÃO"}")
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro ao pausar/resumir: ${e.message}")
                    e.printStackTrace()
                    // Tentar recuperar de possíveis erros
                    try {
                        isPaused = !isPaused
                        if (!isPaused) {
                            gameView.resumeGame()
                            binding.pauseButton.text = getString(R.string.pause)
                            mediaPlayer?.start()
                        } else {
                            gameView.pauseGame()
                            binding.pauseButton.text = getString(R.string.resume)
                            mediaPlayer?.pause()
                        }
                    } catch (e2: Exception) {
                        Log.e("GameActivity", "Falha ao recuperar erro de pausa: ${e2.message}")
                    }
                }
            }
            
            // Botão de reset - com tratamento de erro
            binding.resetButton.setOnClickListener {
                try {
                    resetGame()
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro ao resetar o jogo: ${e.message}")
                    e.printStackTrace()
                    // Tentar voltar para a tela inicial mesmo com o erro
                    try {
                        val intent = Intent(this, StartActivity::class.java)
                        startActivity(intent)
                        finish()
                    } catch (e2: Exception) {
                        Log.e("GameActivity", "Falha total ao voltar para tela inicial: ${e2.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro crítico ao configurar controles: ${e.message}")
            e.printStackTrace()
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
    
    private fun showGameOverDialog() {
        try {
            // Primeiro pausamos o jogo para evitar atualizações no fundo
            if (!isPaused) {
                isPaused = true
                gameView.pauseGame()
            }
            
            // Verificar e salvar pontuação mais alta
            val currentHighScore = sharedPref.getInt("high_score", 0)
            if (gameLogic.score > currentHighScore) {
                sharedPref.edit().putInt("high_score", gameLogic.score).apply()
            }
            
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Game Over")
            builder.setMessage("Sua pontuação: ${gameLogic.score}\nRecord: ${Math.max(currentHighScore, gameLogic.score)}")
            builder.setPositiveButton("Menu Principal") { _, _ ->
                resetGame()
            }
            builder.setNegativeButton("Jogar Novamente") { _, _ ->
                recreate()
            }
            builder.setCancelable(false)
            builder.show()
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao mostrar diálogo de Game Over: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isPaused) {
            togglePause()
        }
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
        
        if (!isPaused && mediaPlayer != null) {
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
            // Liberar os recursos de mídia
            mediaPlayer?.release()
            mediaPlayer = null
            
            // Liberar recursos do GameLogic
            if (::gameLogic.isInitialized) {
                gameLogic.releaseResources()
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao liberar recursos: ${e.message}")
        }
    }
} 