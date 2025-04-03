package com.neo.tetris

import android.graphics.Color
import android.media.MediaPlayer
import kotlin.random.Random

class GameLogic(private val context: android.content.Context) {

    private var breakSound: MediaPlayer? = null
    private var lastScoreMilestone = 0 // Para rastrear a última marca de 200 pontos
    private var lastSpeedMilestone = 0 // Para rastrear a última marca de 10.000 pontos
    private val initialFallSpeed = 500L // Velocidade inicial para calcular aumentos
    private val initialFastFallSpeed = 100L // Velocidade inicial rápida

    val gridWidth = 13
    val gridHeight = 22
    val blockSize = 80
    val grid = Array(gridHeight) { IntArray(gridWidth) { Color.BLACK } }
    var currentPiece: Piece = createRandomPiece()
    var nextPiece: Piece = createRandomPiece()
    var lockedPositions = mutableMapOf<Pair<Int, Int>, Int>()
    var score = 0
    var fallTime = 0L
    private var fallSpeed = 500L // Velocidade base de queda (500ms)
    private var fastFallSpeed = 100L // Velocidade de queda rápida (100ms)
    private var currentSpeed = fallSpeed // Velocidade atual
    private var isFastFalling = false // Controle de queda rápida
    private var lastFallTime = 0L
    private var level = 1
    private var linesCleared = 0
    var isGameOver = false
    private var isPaused = false
    var mediaPlayer: MediaPlayer? = null
    private var lastSoundPlayTime = 0L
    private val SOUND_COOLDOWN = 200L // Cooldown de 200ms entre sons

    // Contém as diferentes formas das peças
    private val shapes = listOf(
        // Peça I
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0)
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
    
    // Chance da peça bomba aparecer (1/10 = 10% de chance)
    private val BOMB_CHANCE = 10
    
    // Métodos para configurar as velocidades
    fun setFallSpeed(speed: Long) {
        fallSpeed = speed
        if (!isFastFalling) {
            currentSpeed = fallSpeed
        }
    }
    
    fun setFastFallSpeed(speed: Long) {
        fastFallSpeed = speed
        if (isFastFalling) {
            currentSpeed = fastFallSpeed
        }
    }
    
    // Métodos para obter as velocidades atuais
    fun getFallSpeed(): Long {
        return fallSpeed
    }
    
    fun getFastFallSpeed(): Long {
        return fastFallSpeed
    }

    fun update(elapsedTime: Long) {
        if (isPaused || isGameOver) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFallTime >= currentSpeed) {
            moveDown()
            lastFallTime = currentTime
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
        currentSpeed = fastFallSpeed
    }
    
    // Desativa o modo de queda rápida
    fun stopFastFall() {
        isFastFalling = false
        currentSpeed = fallSpeed
    }

    // Método modificado para fazer queda instantânea (hard drop)
    fun hardDrop() {
        while (validMove(currentPiece, 0, 1)) {
            currentPiece.y++
            updateScore(2) // Mais pontos por queda direta
        }
        lockPiece()
        
        // Se for uma peça bomba, ative sua explosão
        if (currentPiece.isBomb) {
            explodeBomb()
        } else {
            clearLines()
        }
        
        currentPiece = nextPiece
        nextPiece = createRandomPiece()
        checkGameOver()
    }

    fun rotate() {
        // Não rota a peça bomba - ela é sempre um único bloco
        if (currentPiece.isBomb) return
        
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

    private fun moveDown() {
        if (validMove(currentPiece, 0, 1)) {
            currentPiece.y++
        } else {
            lockPiece()
            
            // Se for uma peça bomba, ative sua explosão
            if (currentPiece.isBomb) {
                explodeBomb()
            } else {
                clearLines()
            }
            
            currentPiece = nextPiece
            nextPiece = createRandomPiece()
            checkGameOver()
        }
    }
    
    // Método para explodir a bomba e destruir blocos próximos
    private fun explodeBomb() {
        // Área de explosão: 3x3 com centro na posição base da bomba
        val bombX = currentPiece.x
        val bombY = currentPiece.y
        
        // Remover blocos em uma área 3x3
        for (y in bombY-1..bombY+1) {
            for (x in bombX-1..bombX+1) {
                // Verificar se está dentro dos limites da grade
                if (x in 0 until gridWidth && y in 0 until gridHeight) {
                    // Remover o bloco
                    lockedPositions.remove(Pair(x, y))
                }
            }
        }
        
        // Agora precisamos fazer as peças caírem após a explosão
        updateAfterExplosion()
        
        // Atualizar a grade
        updateGrid()
        
        // Pontuar pela explosão
        updateScore(150)
    }
    
    // Método para atualizar a grade após uma explosão
    private fun updateAfterExplosion() {
        // Para cada coluna, verificamos de baixo para cima
        for (x in 0 until gridWidth) {
            // Lista de posições vazias em cada coluna
            val emptySpaces = mutableListOf<Int>()
            
            // Encontrar espaços vazios de baixo para cima
            for (y in gridHeight-1 downTo 0) {
                if (!lockedPositions.containsKey(Pair(x, y))) {
                    emptySpaces.add(y)
                } else if (emptySpaces.isNotEmpty()) {
                    // Se há espaços vazios abaixo desta posição, move o bloco para baixo
                    val lowestEmptyY = emptySpaces.removeAt(0)
                    val color = lockedPositions.remove(Pair(x, y))!!
                    lockedPositions[Pair(x, lowestEmptyY)] = color
                    emptySpaces.add(y) // Agora esta posição está vazia
                    emptySpaces.sort() // Reordenar para que o próximo espaço vazio seja o mais baixo
                }
            }
        }
    }

    private fun playBreakSound() {
        try {
            breakSound?.release()
            breakSound = MediaPlayer.create(context, R.raw.break_sound)
            // Ajustar o volume para 100% (1.0f)
            breakSound?.setVolume(5.0f, 5.0f)
            // Configurar para não repetir
            breakSound?.isLooping = false
            breakSound?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun releaseResources() {
        breakSound?.release()
        breakSound = null
    }

    private fun checkGameOver() {
        // Verifica se a nova peça colide com alguma peça existente na posição inicial
        if (!validMove(currentPiece, 0, 0)) {
            isGameOver = true
            return
        }
        
        // Verifica se alguma peça atingiu o topo da tela (primeiras 2 linhas)
        for (y in 0..1) {
            for (x in 0 until gridWidth) {
                if (grid[y][x] != Color.BLACK) {
                    isGameOver = true
                    return
                }
            }
        }
    }

    private fun lockPiece() {
        // Adiciona cada bloco da peça atual às posições bloqueadas
        for ((y, row) in currentPiece.shape.withIndex()) {
            for ((x, cell) in row.withIndex()) {
                if (cell == 1) {
                    val gridY = currentPiece.y + y
                    val gridX = currentPiece.x + x
                    
                    // Verificação adicional para game over
                    if (gridY <= 1) {
                        isGameOver = true
                    }
                    
                    lockedPositions[Pair(gridX, gridY)] = currentPiece.color
                }
            }
        }
        
        // Se for uma peça bomba, explodir blocos abaixo
        if (currentPiece.isBomb) {
            explodeBomb(currentPiece)
        } else {
            // Verifica se completou alguma linha
            checkLines()
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
                
                // Mover todas as linhas acima para baixo
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

    // Verifica se um movimento é válido
    fun validMove(piece: Piece, offsetX: Int, offsetY: Int): Boolean {
        for ((y, row) in piece.shape.withIndex()) {
            for ((x, cell) in row.withIndex()) {
                if (cell == 1) {
                    val newX = piece.x + x + offsetX
                    val newY = piece.y + y + offsetY
                    
                    // Verificar limites do tabuleiro
                    if (newX < 0 || newX >= gridWidth || newY >= gridHeight) {
                        return false
                    }
                    
                    // Verificar colisão com outras peças
                    if (newY >= 0 && grid[newY][newX] != Color.BLACK) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun createRandomPiece(): Piece {
        // Adicionar chance de criar uma peça bomba
        val bombRoll = (1..BOMB_CHANCE).random()
        val shapeIndex = if (bombRoll == 1) {
            // Retorna a bomba (último índice)
            shapes.size - 1
        } else {
            // Retorna uma peça normal (evitando a bomba)
            (0 until shapes.size - 1).random()
        }
        
        val shape = shapes[shapeIndex]
        val color = colors[shapeIndex]
        
        // Posicionar a peça no topo do grid, horizontalmente centralizada
        val x = (gridWidth - shape[0].size) / 2
        val y = 0
        
        return Piece(shape, color, x, y, shapeIndex == shapes.size - 1)
    }

    private fun updateScore(newPoints: Int) {
        score += newPoints
        
        // Verificar se passou de 200 pontos desde a última marca (para som)
        if (score >= lastScoreMilestone + 200) {
            lastScoreMilestone = score - (score % 200) // Arredonda para baixo para o múltiplo de 200 mais próximo
            playBreakSound() // Toca o som quando atinge 200 pontos
        }
        
        // Aumentar velocidade a cada 10.000 pontos (5% mais rápido)
        if (score >= lastSpeedMilestone + 10000) {
            lastSpeedMilestone = score - (score % 10000) // Arredonda para baixo para múltiplo de 10.000
            
            // Calcular novo fator de velocidade (cada 10.000 pontos = 5% mais rápido)
            val speedIncreaseCount = lastSpeedMilestone / 10000
            val speedFactor = 1.0f / (1.0f + (speedIncreaseCount * 0.05f))
            
            // Aplicar aos tempos de queda (tempo menor = queda mais rápida)
            fallSpeed = (initialFallSpeed * speedFactor).toLong()
            fastFallSpeed = (initialFastFallSpeed * speedFactor).toLong()
            
            // Atualizar velocidade atual
            if (isFastFalling) {
                currentSpeed = fastFallSpeed
            } else {
                currentSpeed = fallSpeed
            }
        }
    }
    
    fun setPaused(paused: Boolean) {
        isPaused = paused
    }
    
    fun isPaused(): Boolean {
        return isPaused
    }

    // Classe para representar uma peça
    inner class Piece(
        val shape: Array<IntArray>,
        val color: Int,
        var x: Int,
        var y: Int,
        val isBomb: Boolean = false // Indica se esta é uma peça bomba
    ) {
        fun rotate(): Array<IntArray> {
            // Não rotacionar a peça 'O' (quadrado) nem a bomba
            if (shape.size == 2 && shape[0].size == 2 || isBomb) return shape
            
            val n = shape.size
            val rotated = Array(n) { IntArray(n) }
            
            for (i in 0 until n) {
                for (j in 0 until n) {
                    rotated[j][n - 1 - i] = shape[i][j]
                }
            }
            return rotated
        }
    }
    
    private fun explodeBomb(piece: Piece) {
        // Encontrar todos os blocos abaixo da peça bomba e explodir
        val bottomY = piece.y + piece.shape.size - 1
        
        // Para cada coluna ocupada pela bomba
        for (x in 0 until piece.shape[0].size) {
            // Verificar se há um bloco da bomba nesta coluna
            var hasBombBlock = false
            
            // Encontrar o bloco mais baixo da bomba em cada coluna
            for (y in piece.shape.size - 1 downTo 0) {
                if (piece.shape[y][x] == 1) {
                    hasBombBlock = true
                    
                    // Explodir blocos abaixo desta posição
                    val gridX = piece.x + x
                    
                    // Verificar se está dentro dos limites do grid
                    if (gridX >= 0 && gridX < gridWidth) {
                        // Explodir todos os blocos abaixo até o fundo
                        for (gridY in (piece.y + y + 1) until gridHeight) {
                            // Remover blocos bloqueados
                            lockedPositions.remove(Pair(gridX, gridY))
                            // Limpar a célula do grid
                            grid[gridY][gridX] = Color.BLACK
                        }
                    }
                    
                    // Já encontramos o bloco mais baixo desta coluna, podemos sair do loop
                    break
                }
            }
        }
        
        // Atualizar score pela explosão (1 ponto por bloco na bomba)
        val bombSize = piece.shape.sumBy { row -> row.sum() }
        updateScore(bombSize * 3) // Pontuação extra pela bomba
        
        // Reproduzir som de explosão
        playBreakSound()
        
        // Atualizar o grid
        updateGrid()
    }
}