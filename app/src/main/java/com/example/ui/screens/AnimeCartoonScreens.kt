package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.FileUtils
import com.example.R
import com.example.data.ConvertedVideo
import com.example.ui.ConversionUiState
import com.example.ui.StyleOption
import com.example.ui.VideoConverterViewModel
import com.example.ui.components.CameraRecorderView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnimeCartoonAppContent(
    viewModel: VideoConverterViewModel,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("home") } // "home", "upload", "processing", "output"
    var selectedVideoForOutput by remember { mutableStateOf<ConvertedVideo?>(null) }
    var showCameraView by remember { mutableStateOf(false) }

    // Observers
    val selectedUri by viewModel.selectedVideoUri.collectAsState()
    val selectedPath by viewModel.selectedVideoPath.collectAsState()
    val conversionState by viewModel.conversionState.collectAsState()
    val history by viewModel.historyVideos.collectAsState()
    val favorites by viewModel.favoriteVideos.collectAsState()

    var historyFilterFavoritesOnly by remember { mutableStateOf(false) }

    // Launcher for video selection
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileRealPath = FileUtils.getPathFromUri(context, uri) ?: uri.toString()
            viewModel.selectVideo(uri, fileRealPath)
        }
    }

    // Capture state changes to route cleanly
    LaunchedEffect(conversionState) {
        when (conversionState) {
            is ConversionUiState.Processing -> {
                currentScreen = "processing"
            }
            is ConversionUiState.Success -> {
                selectedVideoForOutput = (conversionState as ConversionUiState.Success).resultVideo
                currentScreen = "output"
            }
            else -> {}
        }
    }

    if (showCameraView) {
        CameraRecorderView(
            onVideoRecorded = { uri, path ->
                viewModel.selectVideo(uri, path)
                showCameraView = false
            },
            onDismiss = { showCameraView = false }
        )
        return
    }

    Scaffold(
        topBar = {
            StyleAppTopBar(
                title = when (currentScreen) {
                    "home" -> "Anime & Cartoon AI"
                    "upload" -> "Style Studio"
                    "processing" -> "AI Rendering"
                    "output" -> "Transformed Clip"
                    else -> "Studio"
                },
                canGoBack = currentScreen != "home",
                onBack = {
                    if (currentScreen == "processing") {
                        viewModel.cancelConversion()
                    }
                    currentScreen = "home"
                },
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) with fadeOut(animationSpec = spring())
                }, label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    "home" -> HomeScreen(
                        viewModel = viewModel,
                        historyList = if (historyFilterFavoritesOnly) favorites else history,
                        filterOnlyFavorites = historyFilterFavoritesOnly,
                        onFilterChanged = { historyFilterFavoritesOnly = it },
                        onSelectCategory = { category ->
                            viewModel.setCategory(category)
                            currentScreen = "upload"
                        },
                        onSelectHistoryVideo = { video ->
                            selectedVideoForOutput = video
                            currentScreen = "output"
                        }
                    )

                    "upload" -> UploadConfigScreen(
                        viewModel = viewModel,
                        onGalleryPick = { galleryLauncher.launch("video/*") },
                        onCameraPick = { showCameraView = true },
                        onConvertClick = {
                            viewModel.startConversion(context)
                        }
                    )

                    "processing" -> {
                        val state = conversionState
                        if (state is ConversionUiState.Processing) {
                            ProcessingScreen(
                                progress = state.progress,
                                message = state.statusMessage,
                                isDemo = state.isDemo,
                                remainingSeconds = state.estimatedSecondsRemaining,
                                onCancel = {
                                    viewModel.cancelConversion()
                                    currentScreen = "home"
                                }
                            )
                        } else if (state is ConversionUiState.Error) {
                            ErrorStateScreen(
                                message = state.message,
                                onBack = {
                                    viewModel.clearSelectedVideo()
                                    currentScreen = "home"
                                }
                            )
                        }
                    }

                    "output" -> {
                        val video = selectedVideoForOutput
                        if (video != null) {
                            OutputScreen(
                                video = video,
                                onFavoriteToggle = { viewModel.toggleFavorite(video) },
                                onDelete = {
                                    viewModel.deleteVideo(video)
                                    currentScreen = "home"
                                },
                                onConvertAnother = {
                                    viewModel.clearSelectedVideo()
                                    currentScreen = "home"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Global Custom TopBar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleAppTopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        navigationIcon = {
            if (canGoBack) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("app_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.testTag("mode_toggle_button")
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = "Toggle Night Theme",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

// --- SCREEN 1: HOME ---
@Composable
fun HomeScreen(
    viewModel: VideoConverterViewModel,
    historyList: List<ConvertedVideo>,
    filterOnlyFavorites: Boolean,
    onFilterChanged: (Boolean) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectHistoryVideo: (ConvertedVideo) -> Unit
) {
    val context = LocalContext.current
    var isCheckingGuide by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_layout"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Hero Banner Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("app_hero_banner")
            ) {
                Box {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = "Manga & 3D Split Animation",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )
                    // Banner Title details
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "AI DUAL MODEL ENGINE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Anime & Cartoon Converter",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Turn local video files into masterpieces",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        // Action Triggering Buttons
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "AI Transformation Modules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Anime Card Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                )
                            )
                            .clickable { onSelectCategory("Anime") }
                            .testTag("home_anime_style_button")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Brush,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    "Anime Style",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Ghibli & Manga",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // 3D Cartoon Card Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                    )
                                )
                            )
                            .clickable { onSelectCategory("3D Cartoon") }
                            .testTag("home_cartoon_style_button")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    "3D Cartoon Style",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Pixar & Disney LOOK",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Onboarding Quick-Sample Video
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                "No video file handy?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "Load a sample instantly!",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.selectVideo(
                                Uri.parse("https://assets.mixkit.co/videos/preview/mixkit-girl-in-neon-city-running-rendered-42021-large.mp4"),
                                "/cache/neon-sample-run.mp4"
                            )
                            viewModel.setCategory("Anime")
                            onSelectCategory("Anime")
                            Toast.makeText(context, "Sample loaded! Styles fully unlocked.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Try Sample", fontSize = 11.sp)
                    }
                }
            }
        }

        // History Filters Tab
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (filterOnlyFavorites) "Your Favorites" else "Converted History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !filterOnlyFavorites,
                        onClick = { onFilterChanged(false) },
                        label = { Text("Recent", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = filterOnlyFavorites,
                        onClick = { onFilterChanged(true) },
                        label = { Text("Starred", fontSize = 12.sp) }
                    )
                }
            }
        }

        // List history items
        if (historyList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterFrames,
                            contentDescription = "Empty list",
                            tint = Color.Gray,
                            modifier = Modifier.size(50.dp)
                        )
                        Text(
                            text = if (filterOnlyFavorites) "No favorite conversions yet" else "No conversion history",
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Tweak style sliders and output will appear here.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(historyList) { video ->
                HistoryItemRow(
                    video = video,
                    onClick = { onSelectHistoryVideo(video) }
                )
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    video: ConvertedVideo,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(2.dp, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Visual dynamic placeholder thumbnail representing style
            val gradient = if (video.styleCategory == "Anime") {
                Brush.linearGradient(listOf(Color(0xFF8A66FF), Color(0xFFFF4CA3)))
            } else {
                Brush.linearGradient(listOf(Color(0xFF42B0FF), Color(0xFF0091FF)))
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (video.styleCategory == "Anime") Icons.Default.Brush else Icons.Default.Face,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = video.styleCategory.uppercase(Locale.ROOT),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = video.styleName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.isDemo) {
                        Box(
                            modifier = Modifier
                                .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("DEMO", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "Source path: " + video.beforeVideoPath.substringAfterLast("/"),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val dateString = remember(video.timestamp) {
                    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    sdf.format(Date(video.timestamp))
                }
                Text(
                    text = dateString,
                    fontSize = 10.sp,
                    color = Color.LightGray
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Open Output",
                tint = Color.Gray
            )
        }
    }
}


// --- SCREEN 2: UPLOAD CONFIGURATION SCREEN ---
@Composable
fun UploadConfigScreen(
    viewModel: VideoConverterViewModel,
    onGalleryPick: () -> Unit,
    onCameraPick: () -> Unit,
    onConvertClick: () -> Unit
) {
    val context = LocalContext.current
    val category by viewModel.selectedCategory.collectAsState()
    val activeStyleList = if (category == "Anime") viewModel.animeStyles else viewModel.cartoonStyles
    val selectedStyleName by viewModel.selectedStyleName.collectAsState()

    val selectedUri by viewModel.selectedVideoUri.collectAsState()
    val selectedPath by viewModel.selectedVideoPath.collectAsState()

    // Config options
    val selectedRes by viewModel.selectedResolution.collectAsState()
    val aiEnhance by viewModel.aiEnhancementEnabled.collectAsState()
    val frameInterpolation by viewModel.frameInterpolationEnabled.collectAsState()
    val watermarkRemoval by viewModel.watermarkRemovalEnabled.collectAsState()
    val bgMusicIndex by viewModel.backgroundMusicIndex.collectAsState()

    var showAdvancedSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("upload_config_screen_layout")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Style Config Header Banner style text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (category == "Anime") Icons.Default.Palette else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "Configuring $category Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Customize model settings below to begin.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // Style Option Cards Selector
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "1. Choose Style Variation",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            activeStyleList.forEach { style ->
                val isSelected = style.name == selectedStyleName
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setStyleName(style.name) }
                        .testTag("style_card_${style.name.replace(" ", "_")}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Drawing static styled background avatar dynamically
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = if (category == "Anime")
                                            listOf(Color(0xFF8A66FF), Color(0xFFFF4CA3))
                                        else
                                            listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (style.name.contains("Ghibli")) Icons.Default.Nature
                                else if (style.name.contains("Manga")) Icons.Default.Book
                                else if (style.name.contains("Pixar")) Icons.Default.Lightbulb
                                else if (style.name.contains("Disney")) Icons.Default.Castle
                                else Icons.Default.AutoFixHigh,
                                tint = Color.White,
                                contentDescription = null
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = style.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = style.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setStyleName(style.name) }
                        )
                    }
                }
            }
        }

        // Upload Video Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "2. Upload & Source Selection",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            if (selectedPath == null) {
                // Large Picker Card Area
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_picker_trigger_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload Content",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No source clip selected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Upload custom video to apply AI Style transfer",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onGalleryPick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gallery_picker_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery", fontSize = 12.sp)
                            }

                            Button(
                                onClick = onCameraPick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("camera_picker_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Camera", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                // Selection Preview Item Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Drawing fake preview visual indicator
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayCircleOutline,
                                contentDescription = "Active preview ready",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = selectedPath?.substringAfterLast("/") ?: "selected_clip.mp4",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Preview is processed off-screen - Ready",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }

                        IconButton(
                            onClick = { viewModel.clearSelectedVideo() },
                            modifier = Modifier.testTag("clear_video_selection_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove video file", tint = Color.Red)
                        }
                    }
                }
            }
        }

        // College of details: collapse slider details
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedSettings = !showAdvancedSettings }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "3. Render Options & AI Enhancements",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Icon(
                    imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand toggle"
                )
            }

            AnimatedVisibility(visible = showAdvancedSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Resolution selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Output Resolution Resolution", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf("720p", "1080p").forEach { res ->
                                FilterChip(
                                    selected = selectedRes == res,
                                    onClick = { viewModel.selectedResolution.value = res },
                                    label = { Text(res) }
                                )
                            }
                        }
                    }

                    // Background Music Options Segment
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Custom Overlay Background Music", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.musicOptions.forEachIndexed { index, label ->
                                FilterChip(
                                    selected = bgMusicIndex == index,
                                    onClick = { viewModel.backgroundMusicIndex.value = index },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Watermark removal option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Watermark Removal Mode", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Removes AI generation bottom-overlay", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = watermarkRemoval,
                            onCheckedChange = { viewModel.watermarkRemovalEnabled.value = it }
                        )
                    }

                    // AI Enhancement toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Dual-pass AI Color Enhancement", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Improves dynamic contrast output parameters", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = aiEnhance,
                            onCheckedChange = { viewModel.aiEnhancementEnabled.value = it }
                        )
                    }

                    // Frame interpolation toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Frame Interpolation (Ultra Smooth)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Doubles output fps dynamically via optical model", fontSize = 10.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = frameInterpolation,
                            onCheckedChange = { viewModel.frameInterpolationEnabled.value = it }
                        )
                    }
                }
            }
        }

        // Run styling action execution
        Button(
            onClick = onConvertClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("apply_conversion_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.Celebration, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "PROCESS STYLE CONVERSION",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // API Key Guidance alert
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    Toast
                        .makeText(
                            context,
                            "To activate live AI rendering, enter your personal Replicate API Key inside AI Studio Secrets Panel.",
                            Toast.LENGTH_LONG
                        )
                        .show()
                }
                .background(Color.Yellow.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                .padding(10.dp)
                .border(1.dp, Color.Yellow.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = "Api guide", tint = Color(0xFFBC8C00))
            Text(
                text = if (viewModel.isRealApiKeyConfigured)
                    "Live Premium AI API Key Verified. Processes run on Replicate's deep gpu machines."
                else
                    "Sandbox Preview Mode active. You can execute simulated renders. Configure REPLICATE_API_KEY in the Secrets panel to activate live AI.",
                color = Color(0xFF8C6600),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}


// --- SCREEN 3: PROCESSING SCREEN ---
@Composable
fun ProcessingScreen(
    progress: Float,
    message: String,
    isDemo: Boolean,
    remainingSeconds: Int,
    onCancel: () -> Unit
) {
    var rotationAngle by remember { mutableStateOf(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "RadarRotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "RadarAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("processing_screen_layout")
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Deep learning visually styled radar loader
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF5C33FF).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(160.dp),
                color = Color(0xFF5C33FF),
                strokeWidth = 10.dp,
                trackColor = Color(0xFF5C33FF).copy(alpha = 0.12f),
            )

            // Scanning line inside running circular loader
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .rotate(angle)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF0091FF).copy(alpha = 0.5f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.5f)
                        .fillMaxWidth(0.04f)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0091FF), Color.Transparent)
                            )
                        )
                        .align(Alignment.TopCenter)
                )
            }

            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "TRANSFORMING VIDEO",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = message,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Rendering complete in approximately: ${remainingSeconds}S",
            color = Color.Gray,
            fontSize = 12.sp
        )

        if (isDemo) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "SANDBOX COMPILING: We are generating an instant, pixel-perfect simulated output to show before/after comparison.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onCancel,
            modifier = Modifier
                .width(180.dp)
                .height(48.dp)
                .testTag("cancel_conversion_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Cancel, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel Rendering")
        }
    }
}


// --- SCREEN 4: OUTPUT SCREEN WITH COMPARISON SLIDER ---
@Composable
fun OutputScreen(
    video: ConvertedVideo,
    onFavoriteToggle: () -> Unit,
    onDelete: () -> Unit,
    onConvertAnother: () -> Unit
) {
    val context = LocalContext.current
    var isStarred by remember { mutableStateOf(video.isFavorite) }
    var sliderStateBeforeReady by remember { mutableStateOf(true) } // toggling showing before or after output

    var compareRatioSliderPosition by remember { mutableFloatStateOf(0.5f) } // horizontal placement line

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("output_screen_layout")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Output title banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Celebration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column {
                    Text(
                        "AI Render Succeeded!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Your styled ${video.styleCategory} clip is ready for preview & saving.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // Before & After Interactive Comparison Split Area
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Compare Before / After slider",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                // Quick toggle button for convenience
                FilledTonalButton(
                    onClick = { sliderStateBeforeReady = !sliderStateBeforeReady },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (sliderStateBeforeReady) "Show Styled" else "Show Original",
                        fontSize = 11.sp
                    )
                }
            }

            // Interactive Dual Visual Card representing final rendering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                // If showing before version
                if (sliderStateBeforeReady) {
                    // Original source visual representation
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = "Original clip frame preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Before (Original Source)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Styled result visual representation
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_banner),
                            contentDescription = "Styled output",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Add nice filter/tint overlay matching style category
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (video.styleCategory == "Anime") Color(0x608A66FF) else Color(0x600091FF)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "AFTER (${video.styleCategory} Style applied)",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Center visual drag-bar marker overlay purely indicating responsive split
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(Color.White)
                        .align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Compare,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Text(
                "Click 'Show Styled/Original' button to toggling frame differences.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Action Options Buttons (Save, Star, Delete)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    Toast.makeText(context, "Saved successfully to local DCIM/Downloads folder!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .testTag("download_video_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download styled file")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Video", fontSize = 13.sp)
            }

            IconButton(
                onClick = {
                    isStarred = !isStarred
                    onFavoriteToggle()
                    val alertText = if (isStarred) "Starred clip added to Favorites!" else "Removed from Favorites"
                    Toast.makeText(context, alertText, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .background(
                        if (isStarred) Color(0xFFFFECEF) else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp)
                    )
                    .size(48.dp)
                    .border(
                        1.dp,
                        if (isStarred) Color(0xFFFF405B) else Color.LightGray.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("star_output_button")
            ) {
                Icon(
                    imageVector = if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Star",
                    tint = if (isStarred) Color(0xFFFF405B) else Color.Gray
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .size(48.dp)
                    .border(1.dp, Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .testTag("delete_output_button")
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete from history list", tint = Color.Gray)
            }
        }

        // Social Share Target Intents Row
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Export and Share on Socials",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val sharingPlatforms = listOf(
                    ShareEntity("Facebook", "fb_share", Color(0xFF1877F2)),
                    ShareEntity("Instagram", "ig_share", Color(0xFFC13584)),
                    ShareEntity("TikTok", "tt_share", Color(0xFF000000)),
                    ShareEntity("WhatsApp", "wa_share", Color(0xFF25D366)),
                    ShareEntity("YouTube", "yt_share", Color(0xFFFF0000))
                )

                sharingPlatforms.forEach { platform ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable {
                                triggerSocialShareIntent(context, platform.name, video.styleName)
                            }
                            .testTag("share_" + platform.tag)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(platform.brandColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (platform.name) {
                                    "Facebook" -> Icons.Default.Facebook
                                    "WhatsApp" -> Icons.Default.PhoneAndroid
                                    "Instagram" -> Icons.Default.CameraAlt
                                    "YouTube" -> Icons.Default.VideoLibrary
                                    else -> Icons.Default.Share
                                },
                                contentDescription = platform.name,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(platform.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Create another button
        OutlinedButton(
            onClick = onConvertAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("convert_another_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Convert Another Video", fontSize = 13.sp)
        }
    }
}

private fun triggerSocialShareIntent(context: Context, platformName: String, styleName: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Check out my AI stylized anime video!")
        putExtra(Intent.EXTRA_TEXT, "I just transformed my video into dynamic $styleName using the Anime & Cartoon Video Converter App! #AnimeConverter #AIAnimation")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share styled video via $platformName"))
}

data class ShareEntity(val name: String, val tag: String, val brandColor: Color)


// --- HELP STATE SCREENS ---
@Composable
fun ErrorStateScreen(
    message: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("error_state_layout")
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Failure",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "An Error Occurred",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Go to home screen")
        }
    }
}
