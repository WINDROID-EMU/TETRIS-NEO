package com.neo.tetris

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.neo.tetris.databinding.ActivityGameBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import android.widget.FrameLayout
import android.graphics.Color

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var gameView: GameView
    private lateinit var gameLogic: GameLogic
    private var isPaused = false
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var sharedPref: SharedPreferences
    
    // Controle para movimento contínuo
    private var moveJob: kotlinx.coroutines.Job? = null
    private val moveDelay = 100L // Tempo em milissegundos entre movimentos
    
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
        try {
            Log.d("GameActivity", "Iniciando inicialização do jogo")
            
            // Inicializar a lógica do jogo
            gameLogic = GameLogic(this)
            
            // Ajustar o tamanho dos blocos com base no tamanho da tela
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Define o tamanho do bloco como uma porcentagem da largura da tela
            // para se adaptar melhor a diferentes tamanhos de tela
            val blockSize = (screenWidth / 12) // Aproximadamente 1/12 da largura da tela
            gameLogic.blockSize = blockSize
            
            Log.d("GameActivity", "Tamanho da tela: ${screenWidth}x${screenHeight}, tamanho do bloco: $blockSize")
            
            // Verificar se deve continuar um jogo salvo
            val continueGame = intent.getBooleanExtra("continue_game", false)
            if (continueGame) {
                try {
                    // Carregar o estado salvo do jogo
                    val score = sharedPref.getInt("current_score", 0)
                    val fallSpeed = sharedPref.getInt("fall_speed", 500)
                    val isGameOver = sharedPref.getBoolean("is_game_over", false)
                    // Importante: O jogo carregado não deve iniciar pausado por padrão
                    isPaused = false
                    
                    // Atualizar o estado do jogo com os valores salvos
                    gameLogic.score = score
                    gameLogic.fallSpeed = fallSpeed
                    gameLogic.isGameOver = isGameOver
                    
                    // Carregar mais detalhes do estado do jogo
                    val currentPieceX = sharedPref.getInt("current_piece_x", 3)
                    val currentPieceY = sharedPref.getInt("current_piece_y", 0)
                    val currentPieceColor = sharedPref.getInt("current_piece_color", Color.CYAN)
                    
                    // Carregar dados da próxima peça
                    val nextPieceX = sharedPref.getInt("next_piece_x", 3)
                    val nextPieceY = sharedPref.getInt("next_piece_y", 0)
                    val nextPieceColor = sharedPref.getInt("next_piece_color", Color.YELLOW)
                    
                    // Restaurar o estado da grade
                    restoreGridState()
                    
                    // Atualizar o texto do botão de pausa (sempre em modo "pausar" ao carregar)
                    binding.pauseButton.text = getString(R.string.pause)
                    
                    Log.d("GameActivity", "Jogo continuado do estado salvo")
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro ao carregar estado salvo do jogo: ${e.message}")
                    // Se falhar ao carregar o estado, iniciar novo jogo
                    isPaused = false
                }
            } else {
                // Limpar qualquer estado de jogo salvo anteriormente
                with(sharedPref.edit()) {
                    putBoolean("game_in_progress", false)
                    apply()
                }
                isPaused = false
            }
            
            // Garantir que o container do jogo esteja visível e com tamanho correto
            binding.gameContainer.visibility = View.VISIBLE
            
            // Garantir que o layout foi medido antes de criar o GameView
            binding.gameContainer.post {
                try {
                    // Inicializar a view e adicionar ao layout após o layout ser calculado
                    val containerWidth = binding.gameContainer.width
                    val containerHeight = binding.gameContainer.height
                    Log.d("GameActivity", "Tamanho do container: ${containerWidth}x${containerHeight}")
                    
                    if (containerWidth <= 0 || containerHeight <= 0) {
                        Log.e("GameActivity", "Container tem dimensões inválidas!")
                    }
                    
                    // Criar e adicionar o GameView
                    Log.d("GameActivity", "Criando GameView")
                    gameView = GameView(this@GameActivity, gameLogic)
                    
                    // Definir o layout para ocupar todo o espaço do container
                    val layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    binding.gameContainer.addView(gameView, layoutParams)
                    
                    // Iniciar música de fundo
                    setupGameMusic()
                    
                    // Configurar controles
                    setupControls()
                    
                    // Verificar se o jogo deve iniciar pausado e aplicar esse estado
                    if (isPaused) {
                        // Garantir que o jogo seja pausado corretamente
                        gameView.pauseGame()
                        Log.d("GameActivity", "Jogo iniciado em estado pausado")
                    } else {
                        // Garantir que o jogo seja despausado corretamente
                        // É fundamental que isso seja chamado para garantir que os controles funcionem
                        gameView.resumeGame()
                        Log.d("GameActivity", "Jogo iniciado em estado ativo")
                    }
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro ao criar GameView: ${e.message}")
                    e.printStackTrace()
                    
                    // Mostrar mensagem de erro
                    Toast.makeText(this@GameActivity, "Erro ao inicializar o jogo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            Log.d("GameActivity", "Inicialização do jogo concluída com sucesso")
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro crítico na inicialização do jogo: ${e.message}")
            e.printStackTrace()
            
            // Tentar recuperar de possíveis erros
            try {
                AlertDialog.Builder(this)
                    .setTitle("Erro")
                    .setMessage("Ocorreu um erro ao inicializar o jogo. Deseja tentar novamente?")
                    .setPositiveButton("Sim") { _, _ -> recreate() }
                    .setNegativeButton("Não") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e2: Exception) {
                Log.e("GameActivity", "Erro fatal: ${e2.message}")
                finish()
            }
        }
    }
    
    /**
     * Restaura o estado da grade a partir das preferências salvas
     */
    private fun restoreGridState() {
        try {
            // Limpar a grade atual
            for (y in 0 until gameLogic.gridHeight) {
                for (x in 0 until gameLogic.gridWidth) {
                    gameLogic.grid[y][x] = Color.BLACK
                }
            }
            
            // Restaurar as posições salvas na grade
            gameLogic.lockedPositions.clear()
            
            var blocosRestaurados = 0
            
            // Percorrer todas as posições possíveis da grade
            for (y in 0 until gameLogic.gridHeight) {
                for (x in 0 until gameLogic.gridWidth) {
                    val key = "grid_${x}_${y}"
                    // Verificar se existe um bloco salvo nesta posição
                    if (sharedPref.contains(key)) {
                        val color = sharedPref.getInt(key, Color.BLACK)
                        if (color != Color.BLACK) {
                            // Restaurar o bloco na grade e nas posições travadas
                            gameLogic.grid[y][x] = color
                            gameLogic.lockedPositions[Pair(x, y)] = color
                            blocosRestaurados++
                        }
                    }
                }
            }
            
            Log.d("GameActivity", "Grade restaurada com $blocosRestaurados blocos")
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao restaurar estado da grade: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun startGameOverCheck() {
        lifecycleScope.launch {
            while (true) {
                delay(1000)
                if (gameLogic.isGameOver && !isPaused) {
                    // Parar qualquer movimento contínuo
                    stopContinuousMove()
                    
                    // Mostrar o diálogo de game over
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
            Log.d("GameActivity", "Configurando controles. Estado de pausa atual: ${if(isPaused) "PAUSADO" else "ATIVO"}")
            
            // Configurar botões de controle direcional com movimentação contínua
            binding.leftButton.setOnTouchListener { _, event ->
                try {
                    if (!isPaused && !gameLogic.isGameOver) {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                // Inicia a movimentação contínua para esquerda
                                startContinuousMove("left")
                                return@setOnTouchListener true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                // Para a movimentação contínua
                                stopContinuousMove()
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
                    if (!isPaused && !gameLogic.isGameOver) {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                // Inicia a movimentação contínua para direita
                                startContinuousMove("right")
                                return@setOnTouchListener true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                // Para a movimentação contínua
                                stopContinuousMove()
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
                    if (!isPaused && !gameLogic.isGameOver) {
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
                    if (!isPaused && !gameLogic.isGameOver) gameLogic.hardDrop()
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro no hard drop: ${e.message}")
                }
            }
            
            binding.rotateButton.setOnClickListener {
                try {
                    if (!isPaused && !gameLogic.isGameOver) gameLogic.rotate()
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro na rotação: ${e.message}")
                }
            }
            
            // Botão de pause/resume - com tratamento de erro
            binding.pauseButton.setOnClickListener {
                try {
                    Log.d("GameActivity", "Botão de pausa clicado. Estado atual: ${if(isPaused) "PAUSADO" else "EM EXECUÇÃO"}")
                    
                    // Se o jogo não estiver pausado, pausar e mostrar menu de pausa
                    if (!isPaused) {
                        isPaused = true
                        gameView.pauseGame()
                        binding.pauseButton.text = getString(R.string.resume)
                        mediaPlayer?.pause()
                        
                        // Mostrar menu de pausa
                        showPauseMenu()
                    } else {
                        // Se já estiver pausado, apenas despausa
                        isPaused = false
                        gameView.resumeGame()
                        binding.pauseButton.text = getString(R.string.pause)
                        mediaPlayer?.start()
                    }
                    
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
            
            // Botão de back - agora também abre menu de pausa
            binding.resetButton.setOnClickListener {
                try {
                    // Pausar o jogo e abrir o menu de pausa
                    if (!isPaused) {
                        isPaused = true
                        gameView.pauseGame()
                        binding.pauseButton.text = getString(R.string.resume)
                        mediaPlayer?.pause()
                    }
                    
                    // Mostrar menu de pausa
                    showPauseMenu()
                } catch (e: Exception) {
                    Log.e("GameActivity", "Erro ao abrir menu de pausa: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            Log.d("GameActivity", "Controles configurados com sucesso")
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro crítico ao configurar controles: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun startContinuousMove(direction: String) {
        // Primeiro cancelamos qualquer job em andamento
        stopContinuousMove()
        
        // Iniciamos um novo job para movimento contínuo
        moveJob = lifecycleScope.launch {
            while (true) {
                if (!isPaused) {
                    when (direction) {
                        "left" -> gameLogic.moveLeft()
                        "right" -> gameLogic.moveRight()
                    }
                }
                delay(moveDelay)
            }
        }
    }
    
    private fun stopContinuousMove() {
        moveJob?.cancel()
        moveJob = null
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
            
            // Limpar estado do jogo
            sharedPref.edit().putBoolean("game_in_progress", false).apply()
            
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
            
            // Limpar o flag de jogo em andamento após Game Over
            with(sharedPref.edit()) {
                putBoolean("game_in_progress", false)
                apply()
            }
            
            // Iniciar a animação de game over
            gameLogic.startGameOverAnimation {
                // Este bloco é executado após a animação terminar
                runOnUiThread {
                    try {
                        // Verificar e salvar pontuação mais alta
                        val currentHighScore = sharedPref.getInt("high_score", 0)
                        if (gameLogic.score > currentHighScore) {
                            sharedPref.edit().putInt("high_score", gameLogic.score).apply()
                        }
                        
                        // Inflar o layout personalizado
                        val gameOverView = layoutInflater.inflate(R.layout.game_over_layout, null)
                        
                        // Configurar os textos de pontuação
                        val scoreText = gameOverView.findViewById<TextView>(R.id.scoreText)
                        val highScoreText = gameOverView.findViewById<TextView>(R.id.highScoreText)
                        scoreText.text = "Pontuação: ${gameLogic.score}"
                        highScoreText.text = "Recorde: ${Math.max(currentHighScore, gameLogic.score)}"
                        
                        // Configurar os botões
                        val playAgainButton = gameOverView.findViewById<Button>(R.id.playAgainButton)
                        val mainMenuButton = gameOverView.findViewById<Button>(R.id.mainMenuButton)
                        
                        // Criar o diálogo com o layout personalizado
                        val builder = AlertDialog.Builder(this)
                        builder.setView(gameOverView)
                        builder.setCancelable(false)
                        
                        val dialog = builder.create()
                        
                        // Remover fundo/bordas brancas do diálogo
                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        
                        dialog.show()
                        
                        // Configurar ações dos botões
                        playAgainButton.setOnClickListener {
                            dialog.dismiss()
                            recreate()
                        }
                        
                        mainMenuButton.setOnClickListener {
                            dialog.dismiss()
                            resetGame()
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Erro ao mostrar diálogo de Game Over: ${e.message}")
                        e.printStackTrace()
                        
                        // Fallback para o diálogo simples em caso de erro
                        val simpleBuilder = AlertDialog.Builder(this)
                        simpleBuilder.setTitle("Game Over")
                        simpleBuilder.setMessage("Sua pontuação: ${gameLogic.score}")
                        simpleBuilder.setPositiveButton("Menu Principal") { _, _ ->
                            resetGame()
                        }
                        simpleBuilder.setNegativeButton("Jogar Novamente") { _, _ ->
                            recreate()
                        }
                        simpleBuilder.setCancelable(false)
                        simpleBuilder.show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao iniciar sequência de Game Over: ${e.message}")
            e.printStackTrace()
            
            // Fallback em caso de erro no processo inteiro
            try {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Game Over")
                builder.setMessage("Sua pontuação: ${gameLogic.score}")
                builder.setPositiveButton("Menu Principal") { _, _ ->
                    resetGame()
                }
                builder.setNegativeButton("Jogar Novamente") { _, _ ->
                    recreate()
                }
                builder.setCancelable(false)
                builder.show()
            } catch (e2: Exception) {
                Log.e("GameActivity", "Erro fatal ao mostrar Game Over: ${e2.message}")
                // Apenas reseta o jogo como última opção
                resetGame()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        
        // Não salvar estado se for Game Over
        if (gameLogic.isGameOver) {
            // Limpar qualquer estado salvo
            sharedPref.edit().putBoolean("game_in_progress", false).apply()
            return
        }
        
        // Salvar o estado do jogo quando o aplicativo vai para segundo plano
        try {
            val editor = sharedPref.edit()
            
            // Salvar o estado atual do jogo
            editor.putBoolean("game_in_progress", true)
            editor.putBoolean("game_paused", isPaused)
            
            // Salvar os dados essenciais do jogo
            editor.putInt("current_score", gameLogic.score)
            editor.putInt("fall_speed", gameLogic.fallSpeed)
            editor.putBoolean("is_game_over", gameLogic.isGameOver)
            
            // Salvar posição e forma da peça atual
            editor.putInt("current_piece_x", gameLogic.currentPiece.x)
            editor.putInt("current_piece_y", gameLogic.currentPiece.y)
            editor.putInt("current_piece_color", gameLogic.currentPiece.color)
            
            // Salvar a posição e forma da próxima peça
            editor.putInt("next_piece_x", gameLogic.nextPiece.x)
            editor.putInt("next_piece_y", gameLogic.nextPiece.y)
            editor.putInt("next_piece_color", gameLogic.nextPiece.color)
            
            // Salvar o estado da grade
            // Primeiro limpar quaisquer posições antigas
            val allKeys = sharedPref.all.keys.filter { it.startsWith("grid_") }
            for (key in allKeys) {
                editor.remove(key)
            }
            
            // Agora salvar as posições atuais
            for (y in 0 until gameLogic.gridHeight) {
                for (x in 0 until gameLogic.gridWidth) {
                    val color = gameLogic.grid[y][x]
                    if (color != Color.BLACK) {
                        val key = "grid_${x}_${y}"
                        editor.putInt(key, color)
                    }
                }
            }
            
            // Um jogo pausado quando vai para background deve continuar pausado quando retornar
            if (!isPaused) {
                gameView.pauseGame()
                isPaused = true
                editor.putBoolean("was_active", true)
            } else {
                editor.putBoolean("was_active", false)
            }
            
            editor.apply()
            
            Log.d("GameActivity", "Estado do jogo salvo em onStop")
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao salvar estado do jogo: ${e.message}")
        }
    }
    
    override fun onPause() {
        super.onPause()
        stopContinuousMove()
        
        // Pausar o jogo e a música
        if (!isPaused) {
            togglePause()
        }
        
        // Garantir que a música seja pausada mesmo que o jogo já esteja pausado
        mediaPlayer?.pause()
    }
    
    override fun onStart() {
        super.onStart()
        
        // Verificar se o jogo estava ativo antes de ir para segundo plano
        val wasActive = sharedPref.getBoolean("was_active", false)
        
        // Se o jogo estava ativo e existe um jogo em andamento, tentar restaurar
        if (wasActive && sharedPref.getBoolean("game_in_progress", false)) {
            try {
                if (gameView != null) {
                    // Despausar o jogo ao retornar
                    isPaused = false
                    gameView.resumeGame()
                    binding.pauseButton.text = getString(R.string.pause)
                    
                    Log.d("GameActivity", "Jogo restaurado em estado ativo ao retornar")
                }
            } catch (e: Exception) {
                Log.e("GameActivity", "Erro ao restaurar estado do jogo: ${e.message}")
            }
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
        
        // Verificar se a view do jogo está corretamente inicializada
        if (::gameView.isInitialized) {
            if (!isPaused && mediaPlayer != null) {
                // Retomar a música se o jogo não estiver pausado
                mediaPlayer?.start()
            }
            
            // Garantir que o thread do jogo esteja rodando
            if (!isPaused) {
                gameView.resumeGame()
                Log.d("GameActivity", "Thread do jogo retomado em onResume")
            } else {
                // Garantir que o jogo permaneça pausado, mas que os controles sejam atualizados
                gameView.pauseGame()
                Log.d("GameActivity", "Thread do jogo mantido pausado em onResume")
            }
        } else {
            Log.e("GameActivity", "gameView não foi inicializada corretamente")
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
            // Parar job de movimento contínuo
            stopContinuousMove()
            
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Salvar o estado do jogo
        outState.putSerializable("gameLogic", gameLogic.saveState())
        outState.putBoolean("isPaused", isPaused)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restaurar o estado do jogo
        val savedGameLogic = savedInstanceState.getSerializable("gameLogic") as? GameLogic.State
        if (savedGameLogic != null) {
            gameLogic.restoreState(savedGameLogic)
        }
        isPaused = savedInstanceState.getBoolean("isPaused", false)
        if (isPaused) {
            gameView.pauseGame()
            mediaPlayer?.pause()
        } else {
            gameView.resumeGame()
            mediaPlayer?.start()
        }
    }

    /**
     * Salva explicitamente o estado atual do jogo
     */
    private fun saveGameState() {
        try {
            val editor = sharedPref.edit()
            
            // Salvar o estado atual do jogo
            editor.putBoolean("game_in_progress", true)
            editor.putBoolean("game_paused", isPaused)
            
            // Salvar os dados essenciais do jogo
            editor.putInt("current_score", gameLogic.score)
            editor.putInt("fall_speed", gameLogic.fallSpeed)
            editor.putBoolean("is_game_over", gameLogic.isGameOver)
            
            // Salvar posição e forma da peça atual
            editor.putInt("current_piece_x", gameLogic.currentPiece.x)
            editor.putInt("current_piece_y", gameLogic.currentPiece.y)
            editor.putInt("current_piece_color", gameLogic.currentPiece.color)
            
            // Salvar a posição e forma da próxima peça
            editor.putInt("next_piece_x", gameLogic.nextPiece.x)
            editor.putInt("next_piece_y", gameLogic.nextPiece.y)
            editor.putInt("next_piece_color", gameLogic.nextPiece.color)
            
            // Salvar o estado da grade
            // Primeiro limpar quaisquer posições antigas
            val allKeys = sharedPref.all.keys.filter { it.startsWith("grid_") }
            for (key in allKeys) {
                editor.remove(key)
            }
            
            // Agora salvar as posições atuais
            for (y in 0 until gameLogic.gridHeight) {
                for (x in 0 until gameLogic.gridWidth) {
                    val color = gameLogic.grid[y][x]
                    if (color != Color.BLACK) {
                        val key = "grid_${x}_${y}"
                        editor.putInt(key, color)
                    }
                }
            }
            
            editor.apply()
            
            Toast.makeText(this, "Jogo salvo", Toast.LENGTH_SHORT).show()
            Log.d("GameActivity", "Estado do jogo salvo manualmente")
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao salvar estado do jogo: ${e.message}")
            Toast.makeText(this, "Erro ao salvar o jogo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Mostra o menu de pausa com opções para continuar, reiniciar ou voltar ao menu principal
     */
    private fun showPauseMenu() {
        try {
            // Garantir que o jogo esteja pausado
            if (!isPaused) {
                isPaused = true
                gameView.pauseGame()
                binding.pauseButton.text = getString(R.string.resume)
                mediaPlayer?.pause()
            }
            
            // Inflar o layout de menu de pausa
            val pauseMenuView = layoutInflater.inflate(R.layout.pause_menu_layout, null)
            
            // Criar o diálogo com layout personalizado
            val builder = AlertDialog.Builder(this, R.style.PauseDialogTheme)
            builder.setView(pauseMenuView)
            builder.setCancelable(false)
            
            val dialog = builder.create()
            
            // Remover fundo/bordas brancas do diálogo e ajustar dimensões
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
            
            dialog.show()
            
            // Configurar botões
            val continueButton = pauseMenuView.findViewById<Button>(R.id.continueButton)
            val saveButton = pauseMenuView.findViewById<Button>(R.id.saveButton)
            val restartButton = pauseMenuView.findViewById<Button>(R.id.restartButton)
            val mainMenuButton = pauseMenuView.findViewById<Button>(R.id.mainMenuButton)
            
            // Botão Continuar
            continueButton.setOnClickListener {
                // Fechar o diálogo
                dialog.dismiss()
                
                // Despausar o jogo
                isPaused = false
                gameView.resumeGame()
                binding.pauseButton.text = getString(R.string.pause)
                mediaPlayer?.start()
            }
            
            // Botão Salvar Jogo
            saveButton.setOnClickListener {
                try {
                    // Salvar o estado do jogo
                    saveGameState()
                    
                    // Mostrar mensagem de sucesso
                    Toast.makeText(this, "Jogo salvo com sucesso!", Toast.LENGTH_SHORT).show()
                    
                    // Manter o diálogo aberto para permitir outras ações
                } catch (e: Exception) {
                    // Mostrar mensagem de erro
                    Toast.makeText(this, "Erro ao salvar o jogo: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("GameActivity", "Erro ao salvar jogo: ${e.message}")
                }
            }
            
            // Botão Reiniciar
            restartButton.setOnClickListener {
                // Fechar o diálogo
                dialog.dismiss()
                
                // Confirmar reinício
                AlertDialog.Builder(this, R.style.PauseDialogTheme)
                    .setTitle("Reiniciar Jogo")
                    .setMessage("Tem certeza que deseja reiniciar o jogo? Seu progresso atual será perdido.")
                    .setPositiveButton("Sim") { _, _ ->
                        // Reiniciar o jogo
                        recreate()
                    }
                    .setNegativeButton("Não") { _, _ ->
                        // Mostrar o menu de pausa novamente
                        showPauseMenu()
                    }
                    .setCancelable(false)
                    .show()
            }
            
            // Botão Menu Principal
            mainMenuButton.setOnClickListener {
                // Fechar o diálogo
                dialog.dismiss()
                
                // Confirmar saída para o menu principal
                AlertDialog.Builder(this, R.style.PauseDialogTheme)
                    .setTitle("Voltar ao Menu")
                    .setMessage("Tem certeza que deseja voltar ao menu principal? Seu progresso atual será perdido.")
                    .setPositiveButton("Sim") { _, _ ->
                        // Voltar ao menu principal
                        resetGame()
                    }
                    .setNegativeButton("Não") { _, _ ->
                        // Mostrar o menu de pausa novamente
                        showPauseMenu()
                    }
                    .setCancelable(false)
                    .show()
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Erro ao mostrar menu de pausa: ${e.message}")
            e.printStackTrace()
            
            // Em caso de erro, tentar ao menos garantir que os controles funcionem
            try {
                if (isPaused) {
                    // Desbloquear o jogo se houver erro no menu de pausa
                    isPaused = false
                    gameView.resumeGame()
                    binding.pauseButton.text = getString(R.string.pause)
                    mediaPlayer?.start()
                }
            } catch (e2: Exception) {
                Log.e("GameActivity", "Erro crítico no menu de pausa: ${e2.message}")
            }
        }
    }
} 