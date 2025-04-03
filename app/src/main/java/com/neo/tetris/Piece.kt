package com.neo.tetris

import android.graphics.Color

data class Piece(val shape: List<List<Int>>, val color: Int, var x: Int = 3, var y: Int = 0, val isBomb: Boolean = false)