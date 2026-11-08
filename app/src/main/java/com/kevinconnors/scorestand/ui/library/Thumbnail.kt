package com.kevinconnors.scorestand.ui.library

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val thumbCache = LruCache<String, ImageBitmap>(200)

@Composable
fun ScoreThumbnail(file: File, modifier: Modifier = Modifier) {
    val image by remember(file.path) { mutableStateOf(thumbCache.get(file.path)) }.also { state ->
        LaunchedEffect(file.path) {
            if (state.value == null) {
                state.value = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { thumbCache.put(file.path, it) }
                }
            }
        }
    }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        } else {
            Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
