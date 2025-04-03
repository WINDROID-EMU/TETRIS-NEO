package com.neo.tetris

import android.graphics.Canvas
import android.util.Log
import android.view.SurfaceHolder

class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView,
    private val gameLogic: GameLogic
) : Thread() {

    @Volatile
    private var running = false
    private var lastTime = System.currentTimeMillis()
    private var paused = false

    fun setRunning(run: Boolean) {
        Log.d("GameThread", "setRunning: $run")
        running = run
        
        // Se estamos definindo para running = true, também devemos garantir que não está pausado
        if (run) {
            paused = false
        }
        
        // Notificar a thread caso esteja esperando
        synchronized(this) {
            (this as Object).notify()
        }
    }
    
    fun setPaused(pause: Boolean) {
        paused = pause
        Log.d("GameThread", "setPaused: $pause")
    }
    
    fun isPaused(): Boolean {
        return paused
    }

    override fun run() {
        Log.d("GameThread", "Thread iniciada")
        while (running) {
            try {
                if (!paused) {
                    val currentTime = System.currentTimeMillis()
                    val deltaTime = currentTime - lastTime
                    lastTime = currentTime

                    if (surfaceHolder.surface.isValid) {
                        var canvas: Canvas? = null
                        try {
                            canvas = surfaceHolder.lockCanvas()
                            synchronized(surfaceHolder) {
                                gameLogic.update(deltaTime)
                                if (canvas != null) {
                                    gameView.draw(canvas)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("GameThread", "Erro durante o game loop: ${e.message}")
                        } finally {
                            if (canvas != null) {
                                try {
                                    surfaceHolder.unlockCanvasAndPost(canvas)
                                } catch (e: Exception) {
                                    Log.e("GameThread", "Erro ao postar o canvas: ${e.message}")
                                }
                            }
                        }
                    }
                } else {
                    // Se pausado, espera um pouco para não consumir CPU desnecessariamente
                    synchronized(this) {
                        try {
                            (this as Object).wait(100L)
                        } catch (e: InterruptedException) {
                            Log.e("GameThread", "Thread interrompida durante a pausa: ${e.message}")
                        }
                    }
                }
                // Pequeno sleep para controlar o framerate
                sleep(16) // Aproximadamente 60 FPS
            } catch (e: Exception) {
                Log.e("GameThread", "Erro no game loop: ${e.message}")
            }
        }
        Log.d("GameThread", "Thread finalizada")
    }
}