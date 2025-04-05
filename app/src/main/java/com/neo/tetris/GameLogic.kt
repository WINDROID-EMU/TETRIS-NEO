package com.neo.tetris

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.util.Log
import kotlin.random.Random

class GameLogic(private val context: Context) {

    val gridWidth = 10
    val gridHeight = 20
    val blockSize = 85
    val grid = Array(gridHeight) { IntArray(gridWidth) { Color.BLACK } }
    var currentPiece: Piece = createRandomPiece()
    var nextPiece: Piece = createRandomPiece()
    var lockedPositions = mutableMapOf<Pair<Int, Int>, Int>()
    var score = 0
    var fallTime = 0L
    val fallSpeed = 500 // Tempo em milissegundos para a peça cair normalmente
    val fastFallSpeed = 100 // Tempo em milissegundos para a peça cair quando o botão pra baixo está pressionado
    var isGameOver = false
    var isFastFalling = false // Controla se a peça está em modo de queda rápida
    
    // MediaPlayer para sons
    private var pontosSound: MediaPlayer? = null
    
    // Controle de quando o último som foi tocado
    private var lastScoreMilestone = 0
    
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
        if (isGameOver) return
        
        fallTime += deltaTime
        
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

    private fun movePieceDown() {
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

    private fun checkGameOver() {
        if (!validMove(currentPiece, 0, 0)) {
            isGameOver = true
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

    private fun validMove(piece: Piece, offsetX: Int, offsetY: Int): Boolean {
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
        // 20% de chance de criar uma peça bomba
        if (Random.nextInt(100) < 20) {
            // Peça bomba é um único bloco branco
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
        
        // Verificar se passou de 200 pontos desde a última marca
        if (score >= lastScoreMilestone + 200) {
            lastScoreMilestone = score - (score % 200) // Arredonda para baixo para o múltiplo de 200 mais próximo
            playPontosSound() // Toca o som quando atinge 200 pontos
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
    
    fun releaseResources() {
        // Liberar recursos ao fechar o jogo
        pontosSound?.release()
        pontosSound = null
    }
}