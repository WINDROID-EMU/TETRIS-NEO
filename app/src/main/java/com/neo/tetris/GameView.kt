package com.neo.tetris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context, private val gameLogic: GameLogic) : SurfaceView(context), SurfaceHolder.Callback {

    private val thread: GameThread
    private val paint = Paint()
    private var screenWidth = 0
    private var screenHeight = 0
    private var offsetX = 0f
    private var offsetY = 0f

    init {
        holder.addCallback(this)
        thread = GameThread(holder, this, gameLogic)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread.setRunning(true)
        thread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        
        // Calcular o offset para centralizar o tabuleiro
        val boardWidth = gameLogic.gridWidth * gameLogic.blockSize
        val boardHeight = gameLogic.gridHeight * gameLogic.blockSize
        
        offsetX = (width - boardWidth) / 2f
        offsetY = (height - boardHeight) / 2f
        
        // Garantir que o tabuleiro seja visível
        if (offsetY < 0) offsetY = 0f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        thread.setRunning(false)
        while (retry) {
            try {
                thread.join()
                retry = false
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun pauseGame() {
        try {
            thread.setPaused(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumeGame() {
        try {
            if (!thread.isAlive) {
                thread.setRunning(true)
                thread.start()
            } else {
                thread.setPaused(false)
                thread.setRunning(true)
                synchronized(thread) {
                    (thread as Object).notify()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
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
        
        // Desenhar grade
        paint.style = Paint.Style.FILL
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