package com.pdftoimage.converter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdftoimage.converter.ui.ConverterViewModel
import com.pdftoimage.converter.ui.screens.ConverterScreen
import com.pdftoimage.converter.ui.screens.GalleryScreen
import com.pdftoimage.converter.ui.screens.HistoryScreen
import com.pdftoimage.converter.ui.theme.PdfToImageTheme

class MainActivity : ComponentActivity() {

    private var currentViewModel: ConverterViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PdfToImageTheme {
                val viewModel: ConverterViewModel = viewModel()
                currentViewModel = viewModel

                LaunchedEffect(Unit) {
                    intent?.data?.let { uri ->
                        viewModel.addPdfUris(listOf(uri))
                    }
                }

                MainApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            currentViewModel?.addPdfUris(listOf(uri))
        }
    }
}

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainApp(viewModel: ConverterViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navItems = listOf(
        NavItem("Convert", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("Gallery", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
        NavItem("History", Icons.Filled.History, Icons.Outlined.History)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)

        when (selectedTab) {
            0 -> ConverterScreen(
                viewModel = viewModel,
                modifier = screenModifier,
                onNavigateToGallery = { selectedTab = 1 }
            )
            1 -> GalleryScreen(
                viewModel = viewModel,
                modifier = screenModifier
            )
            2 -> HistoryScreen(
                viewModel = viewModel,
                modifier = screenModifier
            )
        }
    }
}
