package com.neo.tetris

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.util.Log
import kotlin.random.Random

class GameLogic(private val context: Context) {

    val gridWidth = 10
    val gridHeight = 20
    var blockSize = 85
    val grid = Array(gridHeight) { IntArray(gridWidth) { Color.BLACK } }
    var currentPiece: Piece = createRandomPiece()
    var nextPiece: Piece = createRandomPiece()
    var lockedPositions = mutableMapOf<Pair<Int, Int>, Int>()
    var score = 0
    var fallTime = 0L
    var fallSpeed = 500 // Alterado para var para poder ser modificado
    val fastFallSpeed = 100 // Tempo em milissegundos para a peça cair quando o botão pra baixo está pressionado
    var isGameOver = false
    var isFastFalling = false // Controla se a peça está em modo de queda rápida
    
    // Controle de aumento de velocidade
    private var lastSpeedUpMilestone = 0
    private val speedUpInterval = 10000 // A cada 10.000 pontos
    private val speedUpPercentage = 0.90f // Reduz o tempo em 10% (90% do tempo original)
    var showSpeedUpMessage = false
    var speedUpMessageTimer = 0L
    
    // MediaPlayer para sons
    private var pontosSound: MediaPlayer? = null
    private var gameOverSound: MediaPlayer? = null
    private var speedUpSound: MediaPlayer? = null
    
    // Controle para animação de game over
    private var isPlayingGameOverAnimation = false
    private var gameOverAnimationRow = 0
    private var gameOverAnimationDelay = 100L // tempo entre cada linha preenchida
    private var gameOverAnimationTimer = 0L
    
    // Controle de quando o último som foi tocado
    private var lastScoreMilestone = 0
    
    // Cores para a animação de game over
    private val gameOverColors = listOf(
        Color.RED,
        Color.BLUE,
        Color.GREEN,
        Color.YELLOW,
        Color.MAGENTA,
        Color.CYAN
    )
    
    // Formas das peças
    private val shapes = arrayOf(
        // Peça I
        arrayOf(
            intArrayOf(1, 1, 1, 1)
        ),
        // Peça O
        arrayOf(
            intArrayOf(1, 1),
            intArrayOf(1, 1)
        ),
        // Peça T
        arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 0, 0)
        ),
        // Peça S
        arrayOf(
            intArrayOf(0, 1, 1),
            intArrayOf(1, 1, 0),
            intArrayOf(0, 0, 0)
        ),
        // Peça Z
        arrayOf(
            intArrayOf(1, 1, 0),
            intArrayOf(0, 1, 1),
            intArrayOf(0, 0, 0)
        ),
        // Peça J
        arrayOf(
            intArrayOf(1, 0, 0),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 0, 0)
        ),
        // Peça L
        arrayOf(
            intArrayOf(0, 0, 1),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 0, 0)
        ),
        // Peça Bomba (nova)
        arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 1, 0)
        )
    )

    // Cores correspondentes a cada peça
    private val colors = listOf(
        Color.CYAN,       // I - Ciano
        Color.YELLOW,     // O - Amarelo
        Color.MAGENTA,    // T - Magenta
        Color.GREEN,      // S - Verde
        Color.RED,        // Z - Vermelho
        Color.BLUE,       // J - Azul
        Color.rgb(255, 165, 0), // L - Laranja
        Color.rgb(128, 0, 128)  // Bomba - Roxo escuro
    )

    fun update(deltaTime: Long) {
        // Durante a animação de game over, não atualizamos a lógica normal do jogo
        if (isPlayingGameOverAnimation) {
            return
        }
        
        if (isGameOver) return
        
        fallTime += deltaTime
        
        // Atualizar o timer da mensagem de speed up
        if (showSpeedUpMessage) {
            speedUpMessageTimer += deltaTime
            if (speedUpMessageTimer >= 3000) { // 3 segundos
                showSpeedUpMessage = false
                speedUpMessageTimer = 0
            }
        }
        
        val currentSpeed = if (isFastFalling) fastFallSpeed else fallSpeed
        
        if (fallTime >= currentSpeed) {
            fallTime = 0
            movePieceDown()
        }
    }

    fun moveLeft() {
        if (validMove(currentPiece, -1, 0)) {
            currentPiece.x--
        }
    }

    fun moveRight() {
        if (validMove(currentPiece, 1, 0)) {
            currentPiece.x++
        }
    }
    
    // Ativa o modo de queda rápida
    fun startFastFall() {
        isFastFalling = true
    }
    
    // Desativa o modo de queda rápida
    fun stopFastFall() {
        isFastFalling = false
    }

    // Método modificado para fazer queda instantânea (hard drop)
    fun hardDrop() {
        while (validMove(currentPiece, 0, 1)) {
            currentPiece.y++
            updateScore(2) // Mais pontos por queda direta
        }
        
        // Verificar se a peça atual é uma bomba
        if (currentPiece.isBomb) {
            // Se a bomba está tocando em outra peça ou no chão, ativa a explosão
            explodeBomb()
            
            // Após a explosão, prosseguir com nova peça
            currentPiece = nextPiece
            nextPiece = createRandomPiece()
            checkGameOver()
            return
        }
        
        // Comportamento normal para outras peças
        lockPiece()
        clearLines()
        
        currentPiece = nextPiece
        nextPiece = createRandomPiece()
        checkGameOver()
    }

    fun rotate() {
        // Não rota a peça bomba - ela é sempre um único bloco
        if (currentPiece.isBomb) return
        
        // Lógica de rotação para peças normais do Tetris
        val rotatedShape = rotateShape(currentPiece.shape)
        val tempPiece = Piece(rotatedShape, currentPiece.color, currentPiece.x, currentPiece.y)
        
        if (validMove(tempPiece, 0, 0)) {
            currentPiece = tempPiece
        } else if (validMove(tempPiece, 1, 0)) {
            // Tenta mover para direita se não puder rodar na posição atual
            currentPiece = tempPiece
            currentPiece.x++
        } else if (validMove(tempPiece, -1, 0)) {
            // Tenta mover para esquerda se não puder rodar na posição atual
            currentPiece = tempPiece
            currentPiece.x--
        }
    }

    private fun rotateShape(shape: List<List<Int>>): List<List<Int>> {
        val rows = shape.size
        val cols = shape[0].size
        val rotated = MutableList(cols) { MutableList(rows) { 0 } }
        
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                rotated[c][rows - 1 - r] = shape[r][c]
            }
        }
        
        return rotated
    }

    private fun movePieceDown() {
        if (validMove(currentPiece, 0, 1)) {
            currentPiece.y++
        } else {
            // Verificar se a peça atual é uma bomba
            if (currentPiece.isBomb) {
                // Se a bomba está tocando em outra peça ou no chão, ativa a explosão
                explodeBomb()
                
                // Após a explosão, prosseguir com nova peça
                currentPiece = nextPiece
                nextPiece = createRandomPiece()
                checkGameOver()
                return
            }
            
            // Comportamento normal para outras peças
            lockPiece()
            clearLines()
            
            currentPiece = nextPiece
            nextPiece = createRandomPiece()
            checkGameOver()
        }
    }
    
    private fun checkGameOver() {
        if (!validMove(currentPiece, 0, 0)) {
            isGameOver = true
            Log.d("GameLogic", "Game Over")
        }
    }

    private fun lockPiece() {
        for ((y, row) in currentPiece.shape.withIndex()) {
            for ((x, cell) in row.withIndex()) {
                if (cell == 1) {
                    lockedPositions[Pair(currentPiece.x + x, currentPiece.y + y)] = currentPiece.color
                }
            }
        }
        updateGrid()
    }

    private fun updateGrid() {
        grid.forEachIndexed { y, row ->
            row.indices.forEach { x ->
                grid[y][x] = lockedPositions[Pair(x, y)] ?: Color.BLACK
            }
        }
    }

    private fun clearLines() {
        val fullLines = mutableListOf<Int>()
        for (y in 0 until gridHeight) {
            if (grid[y].all { it != Color.BLACK }) {
                fullLines.add(y)
            }
        }
        
        if (fullLines.isNotEmpty()) {
            for (line in fullLines) {
                // Remover a linha completa
                for (x in 0 until gridWidth) {
                    lockedPositions.remove(Pair(x, line))
                }
            }
            
            // Mover todas as linhas acima para baixo
            for (line in fullLines) {
                val newPositions = mutableMapOf<Pair<Int, Int>, Int>()
                for ((pos, color) in lockedPositions) {
                    val (px, py) = pos
                    if (py < line) {
                        newPositions[Pair(px, py + 1)] = color
                    } else {
                        newPositions[pos] = color
                    }
                }
                lockedPositions = newPositions
            }
            
            updateGrid()
            
            // Pontuar baseado no número de linhas limpas de uma vez
            when (fullLines.size) {
                1 -> updateScore(100)
                2 -> updateScore(300)
                3 -> updateScore(500)
                4 -> updateScore(800)
            }
        }
    }

    fun validMove(piece: Piece, offsetX: Int, offsetY: Int): Boolean {
        for ((y, row) in piece.shape.withIndex()) {
            for ((x, cell) in row.withIndex()) {
                if (cell == 1) {
                    val newX = piece.x + x + offsetX
                    val newY = piece.y + y + offsetY
                    if (newX < 0 || newX >= gridWidth || newY >= gridHeight || 
                        (newY >= 0 && lockedPositions.containsKey(Pair(newX, newY)))) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun createRandomPiece(): Piece {
        // 15% de chance de criar uma peça bomba
        if (Random.nextInt(100) < 15) {
            // Peça bomba como um único quadrado branco
            val bombShape = listOf(listOf(1))
            val startX = Random.nextInt(gridWidth)
            return Piece(bombShape, Color.WHITE, startX, 0, true)
        }
        
        val shapes = listOf(
            listOf(listOf(1, 1, 1, 1)),                         // I
            listOf(listOf(1, 1), listOf(1, 1)),                 // O
            listOf(listOf(0, 1, 0), listOf(1, 1, 1)),           // T
            listOf(listOf(0, 1, 1), listOf(1, 1, 0)),           // S
            listOf(listOf(1, 1, 0), listOf(0, 1, 1)),           // Z
            listOf(listOf(1, 0, 0), listOf(1, 1, 1)),           // L
            listOf(listOf(0, 0, 1), listOf(1, 1, 1))            // J
        )
        val colors = listOf(
            Color.CYAN,
            Color.YELLOW,
            Color.MAGENTA,
            Color.GREEN,
            Color.RED,
            Color.rgb(255, 165, 0), // Orange
            Color.BLUE
        )
        
        val index = Random.nextInt(shapes.size)
        val shape = shapes[index]
        
        // Calcular posição inicial da peça para que ela apareça centralizada no topo
        val startX = (gridWidth / 2) - (shape[0].size / 2)
        
        return Piece(shape, colors[index], startX, 0)
    }

    private fun updateScore(newPoints: Int) {
        score += newPoints
        
        // Verificar se passou de 200 pontos desde a última marca para som de pontuação
        if (score >= lastScoreMilestone + 200) {
            lastScoreMilestone = score - (score % 200) // Arredonda para baixo para o múltiplo de 200 mais próximo
            playPontosSound() // Toca o som quando atinge 200 pontos
        }
        
        // Verificar se passou de 10.000 pontos desde a última marca para aumento de velocidade
        if (score >= lastSpeedUpMilestone + speedUpInterval) {
            lastSpeedUpMilestone = score - (score % speedUpInterval) // Arredonda para baixo para o múltiplo de 10000 mais próximo
            increaseSpeed() // Aumenta a velocidade
        }
    }
    
    private fun playPontosSound() {
        try {
            // Liberar MediaPlayer anterior se existir
            pontosSound?.release()
            
            // Criar um novo MediaPlayer para o som de pontuação
            pontosSound = MediaPlayer.create(context, R.raw.pontos_sound)
            
            // Configurar volume para 100%
            pontosSound?.setVolume(1.0f, 1.0f)
            
            // Configurar para não repetir
            pontosSound?.isLooping = false
            
            // Tocar o som
            pontosSound?.start()
            
            Log.d("GameLogic", "Som de pontuação tocado")
        } catch (e: Exception) {
            Log.e("GameLogic", "Erro ao tocar som de pontuação: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // Método para iniciar a animação de game over
    fun startGameOverAnimation(onAnimationComplete: () -> Unit) {
        if (isPlayingGameOverAnimation) return
        
        isPlayingGameOverAnimation = true
        gameOverAnimationRow = gridHeight - 1 // Começa da linha de baixo
        gameOverAnimationTimer = 0L
        
        // Reproduzir som de game over
        try {
            // Usar o arquivo gameover.mp3 que existe na pasta raw
            gameOverSound = MediaPlayer.create(context, R.raw.gameover)
            gameOverSound?.setOnCompletionListener {
                it.release()
                Log.d("GameLogic", "Som de game over finalizado")
            }
            gameOverSound?.start()
        } catch (e: Exception) {
            Log.e("GameLogic", "Erro ao tocar som de game over: ${e.message}")
        }
        
        // Iniciar thread para animação
        Thread {
            try {
                // Esperar para sincronizar com o som
                Thread.sleep(200)
                
                // Preencher o grid linha por linha, de baixo para cima
                while (gameOverAnimationRow >= 0) {
                    // Preencher a linha atual com blocos coloridos
                    for (x in 0 until gridWidth) {
                        val randomColor = gameOverColors[Random.nextInt(gameOverColors.size)]
                        grid[gameOverAnimationRow][x] = randomColor
                    }
                    
                    // Avançar para a próxima linha (de baixo para cima)
                    gameOverAnimationRow--
                    
                    // Esperar o delay para a próxima linha
                    Thread.sleep(gameOverAnimationDelay)
                }
                
                // Esperar um pouco após preencher todo o grid
                Thread.sleep(500)
                
                // Indicar que a animação terminou
                isPlayingGameOverAnimation = false
                
                // Chamar o callback quando a animação terminar
                onAnimationComplete()
                
            } catch (e: Exception) {
                Log.e("GameLogic", "Erro na animação de game over: ${e.message}")
                isPlayingGameOverAnimation = false
                onAnimationComplete()
            }
        }.start()
    }
    
    // Método para verificar se a animação de game over está em andamento
    fun isGameOverAnimationPlaying(): Boolean {
        return isPlayingGameOverAnimation
    }
    
    // Método para aumentar a velocidade do jogo
    private fun increaseSpeed() {
        try {
            // Aumentar velocidade em 10% (reduzir o tempo)
            fallSpeed = (fallSpeed * speedUpPercentage).toInt()
            
            // Mostrar mensagem por 3 segundos
            showSpeedUpMessage = true
            speedUpMessageTimer = 0
            
            // Tocar som de aumento de velocidade
            playSpeedUpSound()
            
            Log.d("GameLogic", "Velocidade aumentada para: $fallSpeed ms")
        } catch (e: Exception) {
            Log.e("GameLogic", "Erro ao aumentar velocidade: ${e.message}")
        }
    }
    
    // Método para tocar o som de aumento de velocidade
    private fun playSpeedUpSound() {
        try {
            // Liberar MediaPlayer anterior se existir
            speedUpSound?.release()
            
            // Tentar usar som específico para speed up, ou usar som de pontuação como fallback
            val resourceId = context.resources.getIdentifier("speed_up", "raw", context.packageName)
            
            // Se não houver som específico, usar o som de pontuação
            speedUpSound = if (resourceId != 0) {
                MediaPlayer.create(context, resourceId)
            } else {
                MediaPlayer.create(context, R.raw.pontos_sound)
            }
            
            // Configurar volume para 100%
            speedUpSound?.setVolume(1.0f, 1.0f)
            
            // Configurar para não repetir
            speedUpSound?.isLooping = false
            
            // Tocar o som
            speedUpSound?.start()
            
            Log.d("GameLogic", "Som de speed up tocado")
        } catch (e: Exception) {
            Log.e("GameLogic", "Erro ao tocar som de speed up: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun releaseResources() {
        // Liberar recursos ao fechar o jogo
        pontosSound?.release()
        pontosSound = null
        
        gameOverSound?.release()
        gameOverSound = null
        
        speedUpSound?.release()
        speedUpSound = null
    }

    // Classe interna para armazenar o estado do jogo
    data class State(
        val grid: Array<IntArray>,
        val currentPiece: Piece,
        val nextPiece: Piece,
        val lockedPositions: Map<Pair<Int, Int>, Int>,
        val score: Int,
        val fallTime: Long,
        val fallSpeed: Int,
        val isGameOver: Boolean,
        val isFastFalling: Boolean
    ) : java.io.Serializable

    // Método para salvar o estado atual do jogo
    fun saveState(): State {
        return State(
            grid.map { it.clone() }.toTypedArray(),
            currentPiece.copy(),
            nextPiece.copy(),
            HashMap(lockedPositions),
            score,
            fallTime,
            fallSpeed,
            isGameOver,
            isFastFalling
        )
    }

    // Método para restaurar o estado do jogo
    fun restoreState(state: State) {
        grid.forEachIndexed { index, row ->
            row.indices.forEach { colIndex ->
                grid[index][colIndex] = state.grid[index][colIndex]
            }
        }
        currentPiece = state.currentPiece.copy()
        nextPiece = state.nextPiece.copy()
        lockedPositions = HashMap(state.lockedPositions)
        score = state.score
        fallTime = state.fallTime
        fallSpeed = state.fallSpeed
        isGameOver = state.isGameOver
        isFastFalling = state.isFastFalling
    }

    // Método para explodir a bomba conforme solicitado (apenas o que está abaixo e nas diagonais)
    private fun explodeBomb() {
        try {
            // Lista para armazenar as posições destruídas pela bomba
            val destroyedPositions = mutableListOf<Pair<Int, Int>>()
            var blocosDestruidos = 0
            
            // Posição da bomba na grade (é apenas um bloco)
            val gridX = currentPiece.x
            val gridY = currentPiece.y
            
            // Verificar e destruir bloco diretamente abaixo
            if (gridY + 1 < gridHeight && lockedPositions.containsKey(Pair(gridX, gridY + 1))) {
                lockedPositions.remove(Pair(gridX, gridY + 1))
                grid[gridY + 1][gridX] = Color.BLACK
                destroyedPositions.add(Pair(gridX, gridY + 1))
                blocosDestruidos++
            }
            
            // Verificar e destruir bloco diagonal inferior esquerda
            if (gridX - 1 >= 0 && gridY + 1 < gridHeight && lockedPositions.containsKey(Pair(gridX - 1, gridY + 1))) {
                lockedPositions.remove(Pair(gridX - 1, gridY + 1))
                grid[gridY + 1][gridX - 1] = Color.BLACK
                destroyedPositions.add(Pair(gridX - 1, gridY + 1))
                blocosDestruidos++
            }
            
            // Verificar e destruir bloco diagonal inferior direita
            if (gridX + 1 < gridWidth && gridY + 1 < gridHeight && lockedPositions.containsKey(Pair(gridX + 1, gridY + 1))) {
                lockedPositions.remove(Pair(gridX + 1, gridY + 1))
                grid[gridY + 1][gridX + 1] = Color.BLACK
                destroyedPositions.add(Pair(gridX + 1, gridY + 1))
                blocosDestruidos++
            }
            
            // Atualizar a grade
            updateGrid()
            
            // Fazer com que blocos flutuantes caiam apenas nas colunas afetadas
            if (blocosDestruidos > 0) {
                updateAfterExplosion(destroyedPositions)
            }
            
            // Pontuar pela explosão
            updateScore(100) // 100 pontos de bônus por usar a bomba
            
            // Reproduzir som de explosão se tiver algum
            try {
                val resourceId = context.resources.getIdentifier("break_sound", "raw", context.packageName)
                if (resourceId != 0) {
                    val explosionSound = MediaPlayer.create(context, resourceId)
                    explosionSound?.setVolume(1.0f, 1.0f)
                    explosionSound?.setOnCompletionListener { it.release() }
                    explosionSound?.start()
                }
            } catch (e: Exception) {
                Log.e("GameLogic", "Erro ao tocar som de explosão: ${e.message}")
            }
            
            Log.d("GameLogic", "Bomba explodiu destruindo blocos adjacentes")
        } catch (e: Exception) {
            Log.e("GameLogic", "Erro na explosão da bomba: ${e.message}")
        }
    }
    
    // Método para atualizar a grade após uma explosão - fazer os blocos flutuantes caírem
    private fun updateAfterExplosion(destroyedPositions: List<Pair<Int, Int>>) {
        // Obter apenas as colunas afetadas pela explosão da bomba
        val affectedColumns = destroyedPositions.map { it.first }.distinct().sorted()
        var blocosCairam = false
        
        Log.d("GameLogic", "Atualizando após explosão em colunas: $affectedColumns")
        
        // Processamos cada coluna individualmente
        for (coluna in affectedColumns) {
            var mudancasFeitas = true
            
            // Continuamos até que nenhum bloco possa mais cair nesta coluna
            while (mudancasFeitas) {
                mudancasFeitas = false
                
                // Processamos de baixo para cima para simular gravidade (exceto a última linha)
                for (linha in gridHeight - 2 downTo 0) {
                    // Se houver um bloco nesta posição
                    if (grid[linha][coluna] != Color.BLACK) {
                        // Verificar se a posição abaixo está vazia
                        if (linha + 1 < gridHeight && grid[linha + 1][coluna] == Color.BLACK) {
                            // Mover o bloco uma posição para baixo
                            val color = grid[linha][coluna]
                            
                            // Atualizar a grade
                            grid[linha + 1][coluna] = color
                            grid[linha][coluna] = Color.BLACK
                            
                            // Atualizar posições travadas
                            lockedPositions.remove(Pair(coluna, linha))
                            lockedPositions[Pair(coluna, linha + 1)] = color
                            
                            mudancasFeitas = true
                            blocosCairam = true
                        }
                    }
                }
            }
        }
        
        // Tocar som de queda se algum bloco caiu
        if (blocosCairam) {
            playCrashSound()
        }
    }
    
    // Método para tocar som de peças caindo e batendo
    private fun playCrashSound() {
        try {
            // Buscar o som de colapso (ou usar o som de break como fallback)
            val resourceId = context.resources.getIdentifier("break_sound", "raw", context.packageName)
            if (resourceId != 0) {
                val crashSound = MediaPlayer.create(context, resourceId)
                crashSound?.setVolume(0.7f, 0.7f) // Volume um pouco mais baixo
                crashSound?.setOnCompletionListener { it.release() }
                crashSound?.start()
            }
        } catch (e: Exception) {
            Log.e("GameLogic", "Erro ao tocar som de colapso: ${e.message}")
        }
    }
}