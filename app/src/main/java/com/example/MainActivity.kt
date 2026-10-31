package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.local.AppDatabase
import com.example.data.repository.BpsRepository
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.BpsDarkBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BpsViewModel
import com.example.ui.viewmodel.BpsViewModelFactory

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { BpsRepository(database) }
    private val networkMonitor by lazy { com.example.util.NetworkMonitor(applicationContext) }
    private val viewModel by viewModels<BpsViewModel> { BpsViewModelFactory(repository, networkMonitor) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BpsDarkBg
                ) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}
