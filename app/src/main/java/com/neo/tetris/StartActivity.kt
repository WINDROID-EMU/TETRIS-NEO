package com.neo.tetris

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.neo.tetris.databinding.ActivityStartBinding

class StartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartBinding
    private var mediaPlayer: MediaPlayer? = null
    
    // Valores padrão para configurações
    private var soundEnabled = true
    private var gameSpeed = "normal" // Pode ser "slow", "normal" ou "fast"

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

        // Carregar as configurações salvas
        loadSettings()

        // Carregar e exibir a maior pontuação
        val sharedPref = getSharedPreferences("tetris_prefs", Context.MODE_PRIVATE)
        val highScore = sharedPref.getInt("high_score", 0)
        binding.highScoreText.text = "RECORD: $highScore"

        // Tentar iniciar música de fundo se estiver habilitada
        if (soundEnabled) {
            startBackgroundMusic()
        }

        // Configurar botão de início
        binding.startButton.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            // Passar as configurações para o GameActivity
            intent.putExtra("game_speed", gameSpeed)
            intent.putExtra("sound_enabled", soundEnabled)
            startActivity(intent)
        }
        
        // Configurar botão de configurações
        binding.settingsButton.setOnClickListener {
            showSettingsDialog()
        }
    }
    
    private fun loadSettings() {
        val sharedPref = getSharedPreferences("tetris_prefs", Context.MODE_PRIVATE)
        soundEnabled = sharedPref.getBoolean("sound_enabled", true)
        gameSpeed = sharedPref.getString("game_speed", "normal") ?: "normal"
    }
    
    private fun saveSettings() {
        val sharedPref = getSharedPreferences("tetris_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("sound_enabled", soundEnabled)
            putString("game_speed", gameSpeed)
            apply()
        }
    }
    
    private fun startBackgroundMusic() {
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
    }
    
    private fun showSettingsDialog() {
        // Inflar o layout para o diálogo
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        // Encontrar os componentes no layout
        val soundCheckbox = dialogView.findViewById<CheckBox>(R.id.soundCheckbox)
        val speedRadioGroup = dialogView.findViewById<RadioGroup>(R.id.speedRadioGroup)
        val slowRadio = dialogView.findViewById<RadioButton>(R.id.slowRadio)
        val normalRadio = dialogView.findViewById<RadioButton>(R.id.normalRadio)
        val fastRadio = dialogView.findViewById<RadioButton>(R.id.fastRadio)
        
        // Configurar o estado inicial dos componentes
        soundCheckbox.isChecked = soundEnabled
        
        when (gameSpeed) {
            "slow" -> slowRadio.isChecked = true
            "normal" -> normalRadio.isChecked = true
            "fast" -> fastRadio.isChecked = true
        }
        
        // Construir o diálogo
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Configurações")
        builder.setView(dialogView)
        builder.setPositiveButton("Salvar") { _, _ ->
            // Atualizar as configurações com os novos valores
            soundEnabled = soundCheckbox.isChecked
            
            gameSpeed = when {
                slowRadio.isChecked -> "slow"
                normalRadio.isChecked -> "normal"
                fastRadio.isChecked -> "fast"
                else -> "normal"
            }
            
            // Salvar as configurações
            saveSettings()
            
            // Aplicar as alterações de som imediatamente
            if (soundEnabled && mediaPlayer == null) {
                startBackgroundMusic()
            } else if (!soundEnabled && mediaPlayer != null) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }
        
        builder.setNegativeButton("Cancelar", null)
        
        // Mostrar o diálogo
        builder.show()
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
        
        // Iniciar música se estiver habilitada
        if (soundEnabled && mediaPlayer != null) {
            mediaPlayer?.start()
        }
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