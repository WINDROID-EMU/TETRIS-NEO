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

        // Desenhar peça atual
        for ((y, row) in gameLogic.currentPiece.shape.withIndex()) {
            for ((x, cell) in row.withIndex()) {
                if (cell == 1) {
                    paint.color = gameLogic.currentPiece.color
                    canvas.drawRect(
                        offsetX + (gameLogic.currentPiece.x + x) * gameLogic.blockSize.toFloat(),
                        offsetY + (gameLogic.currentPiece.y + y) * gameLogic.blockSize.toFloat(),
                        offsetX + (gameLogic.currentPiece.x + x + 1) * gameLogic.blockSize.toFloat(),
                        offsetY + (gameLogic.currentPiece.y + y + 1) * gameLogic.blockSize.toFloat(),
                        paint
                    )
                }
            }
        }

        // Área para próxima peça
        val infoX = offsetX + gameLogic.gridWidth * gameLogic.blockSize + 20
        val infoWidth = screenWidth - infoX
        
        // Desenhar próxima peça somente se houver espaço suficiente
        if (infoWidth >= gameLogic.blockSize * 4) {
            paint.color = Color.WHITE
            paint.textSize = 40f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Próxima:", infoX, offsetY + 50, paint)
            
            for ((y, row) in gameLogic.nextPiece.shape.withIndex()) {
                for ((x, cell) in row.withIndex()) {
                    if (cell == 1) {
                        paint.color = gameLogic.nextPiece.color
                        canvas.drawRect(
                            infoX + x * gameLogic.blockSize.toFloat(),
                            offsetY + 70 + y * gameLogic.blockSize.toFloat(),
                            infoX + (x + 1) * gameLogic.blockSize.toFloat(),
                            offsetY + 70 + (y + 1) * gameLogic.blockSize.toFloat(),
                            paint
                        )
                    }
                }
            }
        } else {
            // Se não houver espaço à direita, desenhe a próxima peça no topo
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Próxima:", 10f, 30f, paint)
            
            for ((y, row) in gameLogic.nextPiece.shape.withIndex()) {
                for ((x, cell) in row.withIndex()) {
                    if (cell == 1) {
                        paint.color = gameLogic.nextPiece.color
                        canvas.drawRect(
                            120 + x * (gameLogic.blockSize / 2).toFloat(),
                            10 + y * (gameLogic.blockSize / 2).toFloat(),
                            120 + (x + 1) * (gameLogic.blockSize / 2).toFloat(),
                            10 + (y + 1) * (gameLogic.blockSize / 2).toFloat(),
                            paint
                        )
                    }
                }
            }
        }
    }
}