package com.neo.tetris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context, private val gameLogic: GameLogic) : SurfaceView(context), SurfaceHolder.Callback {

    private lateinit var thread: GameThread
    private val paint = Paint()
    private var screenWidth = 0
    private var screenHeight = 0
    private var offsetX = 0f
    private var offsetY = 0f

    init {
        try {
            Log.d("GameView", "Inicializando GameView")
            
            // Não inicializar screenWidth e screenHeight aqui, pois a view ainda não foi medida
            // Serão configurados em surfaceChanged
            
            // Configurar o SurfaceHolder
            holder.addCallback(this)
            holder.setFormat(android.graphics.PixelFormat.OPAQUE)  // Mudando para OPAQUE para tela mais consistente
            setZOrderOnTop(false)
            
            // Criar a thread do jogo
            thread = GameThread(holder, this, gameLogic)
            
            Log.d("GameView", "GameView inicializada com sucesso")
        } catch (e: Exception) {
            Log.e("GameView", "Erro ao inicializar GameView: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("GameView", "Surface criada, iniciando thread")
        // Importante: não iniciar a thread até que tenhamos as dimensões corretas
        // A thread será iniciada em surfaceChanged
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("GameView", "Surface alterada: largura=$width, altura=$height")
        
        // Atualizar as dimensões aqui, quando temos certeza de que são válidas
        screenWidth = width
        screenHeight = height
        
        // Calcular o offset para centralizar o tabuleiro
        recalculateOffset()
        
        // Agora é seguro iniciar a thread, pois temos as dimensões corretas
        if (!thread.isAlive) {
            thread.setRunning(true)
            thread.start()
            Log.d("GameView", "Thread iniciada após obter dimensões")
        }
    }

    /**
     * Recalcula o offset para centralizar a grade na tela
     */
    private fun recalculateOffset() {
        try {
            val boardWidth = gameLogic.gridWidth * gameLogic.blockSize
            val boardHeight = gameLogic.gridHeight * gameLogic.blockSize
            
            offsetX = (screenWidth - boardWidth) / 2f
            offsetY = (screenHeight - boardHeight) / 2f
            
            // Garantir que o tabuleiro seja visível
            if (offsetY < 0) offsetY = 0f
            if (offsetX < 0) offsetX = 0f
            
            // Ajustar caso a grade seja muito grande para a tela
            if (boardWidth > screenWidth || boardHeight > screenHeight) {
                // Calcular uma nova escala para o tamanho do bloco
                val scaleX = screenWidth / (gameLogic.gridWidth * gameLogic.blockSize.toFloat())
                val scaleY = screenHeight / (gameLogic.gridHeight * gameLogic.blockSize.toFloat())
                val scale = Math.min(scaleX, scaleY) * 0.9f // 90% do espaço disponível
                
                // Aplicar a nova escala
                val newBlockSize = (gameLogic.blockSize * scale).toInt()
                if (newBlockSize > 10) { // Garantir um tamanho mínimo
                    gameLogic.blockSize = newBlockSize
                    
                    // Recalcular offset com o novo tamanho
                    val newBoardWidth = gameLogic.gridWidth * gameLogic.blockSize
                    val newBoardHeight = gameLogic.gridHeight * gameLogic.blockSize
                    
                    offsetX = (screenWidth - newBoardWidth) / 2f
                    offsetY = (screenHeight - newBoardHeight) / 2f
                }
            }
            
            Log.d("GameView", "Offset recalculado: X=$offsetX, Y=$offsetY, Tamanho do bloco=${gameLogic.blockSize}")
        } catch (e: Exception) {
            Log.e("GameView", "Erro ao recalcular offset: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("GameView", "Surface destruída, encerrando thread")
        var retry = true
        thread.setRunning(false)
        while (retry) {
            try {
                thread.join()
                retry = false
            } catch (e: InterruptedException) {
                Log.e("GameView", "Erro ao encerrar thread: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun pauseGame() {
        try {
            Log.d("GameView", "Pausando o jogo")
            thread.setPaused(true)
        } catch (e: Exception) {
            Log.e("GameView", "Erro ao pausar o jogo: ${e.message}")
            e.printStackTrace()
        }
    }

    fun resumeGame() {
        try {
            Log.d("GameView", "Retomando o jogo")
            
            // Verificar se a thread está viva e atualizar seu estado
            if (thread.isAlive) {
                Log.d("GameView", "Thread ainda está viva, apenas despausando")
                // Despausa a thread e garante que esteja rodando
                thread.setPaused(false)
                thread.setRunning(true)
                
                // Notificar a thread para que ela saia do estado de espera
                synchronized(thread) {
                    (thread as Object).notify()
                }
            } else {
                Log.d("GameView", "Thread não está viva, criando uma nova instância")
                // Se a thread morreu, criamos uma nova thread
                val newThread = GameThread(holder, this, gameLogic)
                
                try {
                    // Tentamos usar reflexão para atualizar a referência da thread
                    val field = GameView::class.java.getDeclaredField("thread")
                    field.isAccessible = true
                    field.set(this, newThread)
                    
                    // Inicia a nova thread
                    newThread.setRunning(true)
                    newThread.start()
                    
                    Log.d("GameView", "Nova thread iniciada com sucesso")
                } catch (e: Exception) {
                    Log.e("GameView", "Erro ao tentar substituir a thread: ${e.message}")
                    // Se falhou em atualizar a referência, fazemos fallback para invalidar a view
                    this.invalidate()
                }
            }
        } catch (e: Exception) {
            Log.e("GameView", "Erro ao retomar o jogo: ${e.message}")
            e.printStackTrace()
            
            // Como último recurso, pedimos para a view se redesenhar
            this.invalidate()
        }
    }

    override fun draw(canvas: Canvas) {
        try {
            // Chamar o método draw da classe pai
            super.draw(canvas)
            
            // Verificar se o canvas está nulo ou a surface não está válida
            if (canvas == null || !holder.surface.isValid) {
                Log.e("GameView", "Canvas nulo ou surface inválida. Ignorando frame.")
                return
            }
            
            // Verificar se as dimensões são válidas
            if (screenWidth <= 0 || screenHeight <= 0) {
                Log.e("GameView", "Dimensões inválidas: $screenWidth x $screenHeight")
                // Tentar usar dimensões do canvas como fallback
                screenWidth = canvas.width
                screenHeight = canvas.height
                
                // Se ainda for inválido, desenhar mensagem de erro e sair
                if (screenWidth <= 0 || screenHeight <= 0) {
                    paint.color = Color.RED
                    paint.textSize = 30f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("ERRO DE DIMENSÕES", canvas.width / 2f, canvas.height / 2f, paint)
                    return
                }
                
                // Recalcular o offset com as novas dimensões
                recalculateOffset()
            }
            
            // Limpar a tela com cor de fundo para garantir que não fique preta
            canvas.drawColor(Color.BLACK)
            
            // Não preencher com preto para permitir que a imagem de fundo seja visível
            // Apenas desenhar um retângulo semi-transparente para melhor contraste
            paint.color = Color.argb(120, 0, 0, 0)
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), paint)

            // Desenhar pontuação no topo
            paint.color = Color.WHITE
            paint.textSize = 40f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PONTOS: ${gameLogic.score}", screenWidth / 2f, 50f, paint)
            
            // Exibir mensagem "SPEED UP" quando necessário
            if (gameLogic.showSpeedUpMessage) {
                val alpha = Math.min(255, 255 * (3000 - gameLogic.speedUpMessageTimer) / 3000).toInt()
                paint.color = Color.argb(alpha, 255, 255, 0) // Amarelo com fade out
                paint.textSize = 80f
                paint.isFakeBoldText = true
                canvas.drawText("SPEED UP!", screenWidth / 2f, screenHeight / 2f - 100, paint)
                paint.isFakeBoldText = false
            }
            
            // Adicionar bordas para mostrar onde está a grade do jogo para debug
            paint.color = Color.RED
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(
                offsetX, 
                offsetY, 
                offsetX + gameLogic.gridWidth * gameLogic.blockSize, 
                offsetY + gameLogic.gridHeight * gameLogic.blockSize, 
                paint
            )
            paint.style = Paint.Style.FILL
            
            // Garantir que o blockSize seja válido
            if (gameLogic.blockSize <= 0) {
                gameLogic.blockSize = Math.max(screenWidth, screenHeight) / 20
                recalculateOffset()
            }
            
            // Adicionar texto para debug para mostrar informações importantes
            paint.color = Color.GREEN
            paint.textSize = 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Tela: ${screenWidth}x${screenHeight} Offset: ${offsetX.toInt()}x${offsetY.toInt()} Bloco: ${gameLogic.blockSize}", 10f, screenHeight - 20f, paint)
            
            // Desenhar grade
            for (y in 0 until gameLogic.gridHeight) {
                for (x in 0 until gameLogic.gridWidth) {
                    val color = gameLogic.grid[y][x]
                    if (color != Color.BLACK) {
                        paint.color = color
                        canvas.drawRect(
                            offsetX + x * gameLogic.blockSize.toFloat(),
                            offsetY + y * gameLogic.blockSize.toFloat(),
                            offsetX + (x + 1) * gameLogic.blockSize.toFloat(),
                            offsetY + (y + 1) * gameLogic.blockSize.toFloat(),
                            paint
                        )
                    }
                    
                    // Desenhar grade
                    paint.color = Color.DKGRAY
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    canvas.drawRect(
                        offsetX + x * gameLogic.blockSize.toFloat(),
                        offsetY + y * gameLogic.blockSize.toFloat(),
                        offsetX + (x + 1) * gameLogic.blockSize.toFloat(),
                        offsetY + (y + 1) * gameLogic.blockSize.toFloat(),
                        paint
                    )
                    paint.style = Paint.Style.FILL
                }
            }

            // Não desenhar a peça atual durante a animação de game over
            if (!gameLogic.isGameOverAnimationPlaying()) {
                drawPiece(canvas)
            }

            // Área para próxima peça
            val infoX = offsetX + gameLogic.gridWidth * gameLogic.blockSize + 20
            val infoWidth = screenWidth - infoX
            
            // Desenhar próxima peça somente se houver espaço suficiente e não estiver em animação de game over
            if (infoWidth >= gameLogic.blockSize * 4 && !gameLogic.isGameOverAnimationPlaying()) {
                // Desenhar "Próxima:" no mesmo nível da pontuação (y=50f)
                paint.color = Color.WHITE
                paint.textSize = 80f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("Próxima:", infoX, 80f, paint)
                
                // Calcular a largura do texto "Próxima:" para dar espaço adequado
                val textWidth = paint.measureText("Próxima:") + 80 // 50 pixels de espaço
                
                // Desenhar a próxima peça ao lado do texto, no mesmo nível
                for ((y, row) in gameLogic.nextPiece.shape.withIndex()) {
                    for ((x, cell) in row.withIndex()) {
                        if (cell == 1) {
                            paint.color = gameLogic.nextPiece.color
                            canvas.drawRect(
                                infoX + textWidth + x * (gameLogic.blockSize/2).toFloat(),
                                30f + y * (gameLogic.blockSize/2).toFloat(), // ligeiramente abaixo do texto para alinhar visualmente
                                infoX + textWidth + (x + 1) * (gameLogic.blockSize/2).toFloat(),
                                30f + (y + 1) * (gameLogic.blockSize/2).toFloat(),
                                paint
                            )
                        }
                    }
                }
            } else if (!gameLogic.isGameOverAnimationPlaying()) {
                // Se não houver espaço à direita, desenhe a próxima peça no topo
                paint.color = Color.WHITE
                paint.textSize = 30f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("Próxima:", 10f, 50f, paint) // Mantém a altura igual à pontuação
                
                // Calcular a largura do texto "Próxima:" para dar espaço adequado
                val textWidth = paint.measureText("Próxima:") + 30 // 30 pixels de espaço
                
                for ((y, row) in gameLogic.nextPiece.shape.withIndex()) {
                    for ((x, cell) in row.withIndex()) {
                        if (cell == 1) {
                            paint.color = gameLogic.nextPiece.color
                            canvas.drawRect(
                                10f + textWidth + x * (gameLogic.blockSize / 2).toFloat(),
                                30f + y * (gameLogic.blockSize / 2).toFloat(),
                                10f + textWidth + (x + 1) * (gameLogic.blockSize / 2).toFloat(),
                                30f + (y + 1) * (gameLogic.blockSize / 2).toFloat(),
                                paint
                            )
                        }
                    }
                }
            }
            
            // Se estiver em animação de game over, desenhar texto "GAME OVER" piscando
            if (gameLogic.isGameOverAnimationPlaying()) {
                // Fazer o texto piscar na animação
                val time = System.currentTimeMillis() % 1000
                if (time < 500) {
                    paint.color = Color.RED
                    paint.textSize = 80f
                    paint.textAlign = Paint.Align.CENTER
                    paint.isFakeBoldText = true
                    canvas.drawText("GAME OVER", screenWidth / 2f, screenHeight / 2f, paint)
                    paint.isFakeBoldText = false
                }
            }
        } catch (e: Exception) {
            Log.e("GameView", "Erro ao desenhar o jogo: ${e.message}")
            e.printStackTrace()
            
            // Tentar pelo menos desenhar uma mensagem de erro
            try {
                paint.color = Color.RED
                paint.textSize = 30f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("ERRO AO DESENHAR", screenWidth / 2f, screenHeight / 2f, paint)
                canvas.drawText("Reinicie o jogo", screenWidth / 2f, screenHeight / 2f + 50, paint)
            } catch (e2: Exception) {
                Log.e("GameView", "Erro fatal ao desenhar mensagem de erro")
            }
        }
    }

    private fun drawPiece(canvas: Canvas) {
        val piece = gameLogic.currentPiece
        if (piece != null) {
            // Desenhar sombra da peça
            val shadowPaint = Paint().apply {
                color = Color.argb(50, 0, 0, 0) // Sombra com 20% de opacidade
                style = Paint.Style.FILL
            }
            
            // Calcular posição da sombra (onde a peça cairá)
            var shadowY = piece.y
            while (gameLogic.validMove(piece, 0, shadowY - piece.y + 1)) {
                shadowY++
            }
            
            // Desenhar sombra
            for ((y, row) in piece.shape.withIndex()) {
                for ((x, cell) in row.withIndex()) {
                    if (cell == 1) {
                        val screenX = offsetX + (piece.x + x) * gameLogic.blockSize.toFloat()
                        val screenY = offsetY + (shadowY + y) * gameLogic.blockSize.toFloat()
                        canvas.drawRect(
                            screenX,
                            screenY,
                            screenX + gameLogic.blockSize.toFloat(),
                            screenY + gameLogic.blockSize.toFloat(),
                            shadowPaint
                        )
                    }
                }
            }
            
            // Desenhar peça atual
            for ((y, row) in piece.shape.withIndex()) {
                for ((x, cell) in row.withIndex()) {
                    if (cell == 1) {
                        val screenX = offsetX + (piece.x + x) * gameLogic.blockSize.toFloat()
                        val screenY = offsetY + (piece.y + y) * gameLogic.blockSize.toFloat()
                        drawBlock(canvas, screenX, screenY, piece.color)
                    }
                }
            }
        }
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, color: Int) {
        // Verificar se é a peça bomba (cor branca)
        val isBomb = color == Color.WHITE && gameLogic.currentPiece.isBomb

        if (isBomb) {
            // Desenhar a bomba como um simples quadrado branco
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawRect(
                x,
                y,
                x + gameLogic.blockSize.toFloat(),
                y + gameLogic.blockSize.toFloat(),
                paint
            )
            
            // Adicionar borda preta para melhor visualização
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(
                x,
                y,
                x + gameLogic.blockSize.toFloat(),
                y + gameLogic.blockSize.toFloat(),
                paint
            )
        } else {
            // Desenhar peça normal
            paint.color = color
            paint.style = Paint.Style.FILL
            canvas.drawRect(
                x,
                y,
                x + gameLogic.blockSize.toFloat(),
                y + gameLogic.blockSize.toFloat(),
                paint
            )
            
            // Adicionar borda para melhor visualização
            paint.color = Color.argb(100, 255, 255, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(
                x,
                y,
                x + gameLogic.blockSize.toFloat(),
                y + gameLogic.blockSize.toFloat(),
                paint
            )
        }
    }
}