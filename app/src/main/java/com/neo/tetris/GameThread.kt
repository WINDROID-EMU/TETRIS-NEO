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
        Log.d("GameThread", "setRunning: $run. Thread ID: ${id}")
        running = run
        
        // Se estamos definindo para running = true, também devemos garantir que não está pausado
        if (run) {
            paused = false
            Log.d("GameThread", "Definindo paused=false ao ativar running")
        }
        
        // Notificar a thread caso esteja esperando
        synchronized(this) {
            (this as Object).notify()
            Log.d("GameThread", "Thread notificada após mudança de running")
        }
    }
    
    fun setPaused(pause: Boolean) {
        Log.d("GameThread", "setPaused: $pause -> ${!pause}. Thread ID: ${id}, Estado anterior: running=$running, paused=$paused")
        paused = pause
    }
    
    fun isPaused(): Boolean {
        return paused
    }

    override fun run() {
        Log.d("GameThread", "Thread iniciada. ID: ${id}")
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
                    } else {
                        Log.w("GameThread", "Surface não está válida para desenho")
                    }
                } else {
                    // Se pausado, espera um pouco para não consumir CPU desnecessariamente
                    Log.v("GameThread", "Thread em pausa, aguardando...")
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
                e.printStackTrace()
            }
        }
        Log.d("GameThread", "Thread finalizada. ID: ${id}")
    }
}