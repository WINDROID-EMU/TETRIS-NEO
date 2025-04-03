package com.neo.tetris

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.neo.tetris.databinding.ActivityStartBinding

class StartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartBinding
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Esconder completamente a barra de status
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Carregar e exibir a maior pontuação
        val sharedPref = getSharedPreferences("tetris_prefs", Context.MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0)
        binding.highScoreText.text = "RECORD: $highScore"

        // Tentar iniciar música de fundo se o recurso existir
        try {
            // Verificar se o recurso raw.tetris_theme existe
            val resourceId = resources.getIdentifier("tetris_theme", "raw", packageName)
            if (resourceId != 0) {
                mediaPlayer = MediaPlayer.create(this, resourceId)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } else {
                Log.w("StartActivity", "Arquivo de música 'tetris_theme' não encontrado. Adicione o arquivo na pasta res/raw/")
            }
        } catch (e: Exception) {
            Log.e("StartActivity", "Erro ao iniciar música: ${e.message}")
        }

        // Configurar botão de início
        binding.startButton.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Atualizar a pontuação máxima ao retornar para esta tela
        val sharedPref = getSharedPreferences("tetris_prefs", Context.MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0)
        binding.highScoreText.text = "RECORD: $highScore"
        
        // Garantir que as barras de sistema permaneçam ocultas após retomar a atividade
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        
        mediaPlayer?.start()
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
} 