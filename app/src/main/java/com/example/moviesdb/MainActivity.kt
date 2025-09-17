package com.example.moviesdb

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.example.moviesdb.ui.theme.MoviesDBTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// Data Classes
data class Movie(
    val id: Int,
    val title: String,
    val original_title: String,
    val overview: String,
    val release_date: String,
    val vote_average: Double,
    val vote_count: Int,
    val popularity: Double,
    val genre_ids: List<Int>,
    val backdrop_path: String?,
    val poster_path: String?
)

data class TMDBResponse(
    val page: Int,
    val results: List<Movie>,
    val total_pages: Int,
    val total_results: Int
)

data class Genre(
    val id: Int,
    val name: String
)

// API Service Interface
interface TMDBApiService {
    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TMDBResponse

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TMDBResponse

    @GET("search/movie")
    suspend fun searchMovies(
        @Header("Authorization") token: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TMDBResponse
}

// API Configuration
object TMDBConfig {
    const val BASE_URL = "https://api.themoviedb.org/3/"
    const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
    const val BACKDROP_SIZE = "w780"
    const val POSTER_SIZE = "w500"

    const val BEARER_TOKEN = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIwMDJhODAzNmQ0ZWU0YzRjNjNkMzRkM2RlZGYyOTEwMSIsIm5iZiI6MTY0Njc4NjM1MC44MjUsInN1YiI6IjYyMjdmNzJlNTVjMWY0MDA0N2EyYjgxOSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.vIom8Ywkl6xszOEDBEfh7nYQIbu_FQmRCLmq-rp-wsE"

    fun getBackdropUrl(backdropPath: String?): String? {
        return if (backdropPath != null) {
            "$IMAGE_BASE_URL$BACKDROP_SIZE$backdropPath"
        } else null
    }

    fun getPosterUrl(posterPath: String?): String? {
        return if (posterPath != null) {
            "$IMAGE_BASE_URL$POSTER_SIZE$posterPath"
        } else null
    }
}

// Retrofit Client
object ApiClient {
    val retrofit = Retrofit.Builder()
        .baseUrl(TMDBConfig.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: TMDBApiService = retrofit.create(TMDBApiService::class.java)
}

// UI State
data class MoviesUiState(
    val movies: List<Movie> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false
)

// ViewModel
class MoviesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MoviesUiState(isLoading = true))
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadTopRatedMovies()
    }

    fun loadTopRatedMovies(page: Int = 1, loadMore: Boolean = false) {
        viewModelScope.launch {
            if (!loadMore) {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null,
                    isSearchMode = false,
                    searchQuery = ""
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            try {
                val response = ApiClient.apiService.getTopRatedMovies(TMDBConfig.BEARER_TOKEN, page)
                val currentMovies = if (loadMore) _uiState.value.movies else emptyList()

                _uiState.value = _uiState.value.copy(
                    movies = currentMovies + response.results,
                    isLoading = false,
                    isLoadingMore = false,
                    currentPage = response.page,
                    totalPages = response.total_pages,
                    hasNextPage = response.page < response.total_pages
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "Failed to load movies: ${e.message}"
                )
            }
        }
    }

    fun loadPopularMovies(page: Int = 1, loadMore: Boolean = false) {
        viewModelScope.launch {
            if (!loadMore) {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null,
                    isSearchMode = false,
                    searchQuery = ""
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            try {
                val response = ApiClient.apiService.getPopularMovies(TMDBConfig.BEARER_TOKEN, page)
                val currentMovies = if (loadMore) _uiState.value.movies else emptyList()

                _uiState.value = _uiState.value.copy(
                    movies = currentMovies + response.results,
                    isLoading = false,
                    isLoadingMore = false,
                    currentPage = response.page,
                    totalPages = response.total_pages,
                    hasNextPage = response.page < response.total_pages
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "Failed to load movies: ${e.message}"
                )
            }
        }
    }

    fun searchMovies(query: String, page: Int = 1, loadMore: Boolean = false) {
        searchJob?.cancel()

        if (query.isBlank()) {
            loadTopRatedMovies()
            return
        }

        searchJob = viewModelScope.launch {
            if (!loadMore) {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null,
                    searchQuery = query,
                    isSearchMode = true
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true)
            }

            try {
                delay(if (loadMore) 0 else 500) // Debounce for new searches
                val response = ApiClient.apiService.searchMovies(TMDBConfig.BEARER_TOKEN, query, page)
                val currentMovies = if (loadMore) _uiState.value.movies else emptyList()

                _uiState.value = _uiState.value.copy(
                    movies = currentMovies + response.results,
                    isLoading = false,
                    isLoadingMore = false,
                    currentPage = response.page,
                    totalPages = response.total_pages,
                    hasNextPage = response.page < response.total_pages,
                    searchQuery = query,
                    isSearchMode = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "Failed to search movies: ${e.message}"
                )
            }
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.hasNextPage && !currentState.isLoadingMore) {
            val nextPage = currentState.currentPage + 1

            when {
                currentState.isSearchMode -> searchMovies(currentState.searchQuery, nextPage, true)
                // Add logic here to determine which category was being viewed
                else -> loadTopRatedMovies(nextPage, true) // Default to top rated
            }
        }
    }

    fun retryLastOperation() {
        val currentState = _uiState.value
        when {
            currentState.isSearchMode -> searchMovies(currentState.searchQuery)
            else -> loadTopRatedMovies()
        }
    }
}

// Genre mapping
val genreMap = mapOf(
    18 to "Drama", 80 to "Crime", 36 to "History", 10752 to "War",
    16 to "Animation", 10751 to "Family", 14 to "Fantasy",
    28 to "Action", 53 to "Thriller", 35 to "Comedy", 10749 to "Romance",
    12 to "Adventure", 878 to "Sci-Fi", 37 to "Western"
)

// Color scheme
object MovieVaultColors {
    val Primary = Color(0xFF8B5CF6)
    val PrimaryVariant = Color(0xFF6366F1)
    val Secondary = Color(0xFFC4B5FD)
    val Background = Color(0xFF0F0F23)
    val Surface = Color(0xFF1A1A2E)
    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceVariant = Color(0xFFA0A0A0)
    val GlassSurface = Color(0x1AFFFFFF)
    val GlassBorder = Color(0x33FFFFFF)
}

// MainActivity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieVaultTheme {
                MovieVaultApp()
            }
        }
    }
}

@Composable
fun MovieVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MovieVaultColors.Primary,
            secondary = MovieVaultColors.Secondary,
            background = MovieVaultColors.Background,
            surface = MovieVaultColors.Surface,
            onSurface = MovieVaultColors.OnSurface
        )
    ) {
        content()
    }
}

@Composable
fun MovieVaultApp(
    viewModel: MoviesViewModel = viewModel()
) {
    var selectedBottomNavItem by remember { mutableIntStateOf(0) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var searchText by remember { mutableStateOf("") }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Handle search text changes
    LaunchedEffect(searchText) {
        if (searchText.isNotEmpty()) {
            viewModel.searchMovies(searchText)
        } else if (uiState.isSearchMode) {
            // If search is cleared, go back to the selected filter
            when (selectedFilterIndex) {
                0 -> viewModel.loadTopRatedMovies()
                1 -> viewModel.loadPopularMovies()
            }
        }
    }

    // Handle filter changes (only when not in search mode)
    LaunchedEffect(selectedFilterIndex) {
        if (!uiState.isSearchMode) {
            when (selectedFilterIndex) {
                0 -> viewModel.loadTopRatedMovies()
                1 -> viewModel.loadPopularMovies()
            }
        }
    }

    // Handle infinite scrolling
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val lastVisibleItem = visibleItems.lastOrNull()
                if (lastVisibleItem != null &&
                    lastVisibleItem.index >= uiState.movies.size - 3 &&
                    uiState.hasNextPage &&
                    !uiState.isLoadingMore) {
                    viewModel.loadNextPage()
                }
            }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0C0C0C),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            ),
        topBar = {
            Column {
                StatusBar()
                AppHeader(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it }
                )
                if (!uiState.isSearchMode) {
                    FilterTabs(
                        selectedIndex = selectedFilterIndex,
                        onFilterSelected = { selectedFilterIndex = it }
                    )
                }
            }
        },
        bottomBar = {
            BottomNavigation(
                selectedIndex = selectedBottomNavItem,
                onItemSelected = { selectedBottomNavItem = it }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MovieVaultColors.Primary,
                                    modifier = Modifier.size(50.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (uiState.isSearchMode) "Searching movies..." else "Loading movies...",
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    uiState.errorMessage != null -> {
                        ErrorScreen(
                            errorMessage = uiState.errorMessage!!,
                            onRetry = { viewModel.retryLastOperation() }
                        )
                    }
                    else -> {
                        MoviesList(
                            movies = uiState.movies,
                            selectedMovie = selectedMovie,
                            onMovieSelected = { selectedMovie = it },
                            listState = listState,
                            isLoadingMore = uiState.isLoadingMore,
                            searchQuery = if (uiState.isSearchMode) uiState.searchQuery else null,
                            currentPage = uiState.currentPage,
                            totalPages = uiState.totalPages
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun StatusBar() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp))
}

@Composable
fun AppHeader(
    searchText: String,
    onSearchTextChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MovieVaultColors.GlassSurface,
                        Color.Transparent
                    )
                )
            )
            .padding(20.dp)
    ) {
        Text(
            text = "Good evening",
            color = MovieVaultColors.Primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "MovieVault",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        var isFocused by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isFocused) 1.02f else 1f,
            animationSpec = tween(200),
            label = "searchBarScale"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(
                containerColor = MovieVaultColors.GlassSurface
            ),
            border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (searchText.isEmpty()) {
                            Text(
                                text = "Search movies...",
                                color = MovieVaultColors.OnSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )

                if (searchText.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MovieVaultColors.OnSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onSearchTextChange("") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MovieVaultColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun FilterTabs(
    selectedIndex: Int,
    onFilterSelected: (Int) -> Unit
) {
    val filters = listOf("Top Rated", "Popular")

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        itemsIndexed(filters) { index, filter ->
            FilterTab(
                text = filter,
                isSelected = selectedIndex == index,
                onClick = { onFilterSelected(index) }
            )
        }
    }
}

@Composable
fun FilterTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "filterTabScale"
    )

    Card(
        modifier = Modifier
            .scale(animatedScale)
            .clickable { onClick() },
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MovieVaultColors.Primary
            } else {
                MovieVaultColors.GlassSurface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color.Transparent else MovieVaultColors.GlassBorder
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 0.dp
        )
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun ErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "😕",
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Something went wrong",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorMessage,
                color = MovieVaultColors.OnSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MovieVaultColors.Primary
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = "Try Again",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun MoviesList(
    movies: List<Movie>,
    selectedMovie: Movie?,
    onMovieSelected: (Movie) -> Unit,
    listState: LazyListState,
    isLoadingMore: Boolean = false,
    searchQuery: String? = null,
    currentPage: Int = 1,
    totalPages: Int = 1
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                if (searchQuery != null) {
                    Text(
                        text = "Search Results",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$searchQuery\" • ${movies.size} results",
                        color = MovieVaultColors.OnSurfaceVariant,
                        fontSize = 14.sp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Movies",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Page $currentPage of $totalPages",
                            color = MovieVaultColors.OnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (movies.isEmpty() && searchQuery != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔍",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No movies found",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Try searching with different keywords",
                            color = MovieVaultColors.OnSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        itemsIndexed(movies) { index, movie ->
            var isVisible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay((index * 100).toLong())
                isVisible = true
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { 100 },
                    animationSpec = tween(600)
                ) + fadeIn(animationSpec = tween(600))
            ) {
                MovieCard(
                    movie = movie,
                    isSelected = movie == selectedMovie,
                    onClick = { onMovieSelected(movie) }
                )
            }
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MovieVaultColors.Primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading more movies...",
                            color = MovieVaultColors.OnSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieCard(
    movie: Movie,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }


    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.98f
            isSelected -> 1.02f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "movieCardScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 8.dp,
        animationSpec = tween(300),
        label = "movieCardElevation"
    )

    val context = LocalContext.current
    val activity = context as? Activity

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true

                val intent = Intent(context, MovieDetailsActivity::class.java).apply {
                    putExtra(MovieDetailsActivity.EXTRA_MOVIE_ID, movie.id)      // ✅ correct key
                    putExtra(MovieDetailsActivity.EXTRA_MOVIE_TITLE, movie.title) // ✅ optional title
                }
                context.startActivity(intent)

                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MovieVaultColors.GlassSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MovieVaultColors.Primary else MovieVaultColors.GlassBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val imageUrl = TMDBConfig.getBackdropUrl(movie.backdrop_path)

                if (imageUrl != null) {
                    SubcomposeAsyncImage(
                        model = imageUrl,
                        contentDescription = movie.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MovieVaultColors.Surface,
                                                Color(0xFF16213E)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MovieVaultColors.Primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                MovieVaultColors.Surface,
                                                Color(0xFF16213E)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🎬",
                                    fontSize = 32.sp,
                                    color = MovieVaultColors.Primary
                                )
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MovieVaultColors.Surface,
                                        Color(0xFF16213E)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎬",
                            fontSize = 32.sp,
                            color = MovieVaultColors.Primary
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(15.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MovieVaultColors.Primary.copy(alpha = 0.9f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("⭐", fontSize = 12.sp)
                        Text(
                            text = String.format("%.1f", movie.vote_average),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 100f
                            )
                        )
                )
            }

            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (movie.release_date.isNotEmpty()) movie.release_date.substring(0, 4) else "N/A",
                        color = MovieVaultColors.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "•",
                        color = MovieVaultColors.OnSurfaceVariant,
                        fontSize = 14.sp
                    )

                    Text(
                        text = "${movie.vote_count} votes",
                        color = MovieVaultColors.OnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(movie.genre_ids.take(3)) { genreId ->
                        genreMap[genreId]?.let { genreName ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MovieVaultColors.Primary.copy(alpha = 0.2f)
                                ),
                                border = BorderStroke(1.dp, MovieVaultColors.Primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = genreName,
                                    color = MovieVaultColors.Secondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = movie.overview,
                    color = MovieVaultColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

@Composable
fun BottomNavigation(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        Icons.Default.Home to "Home",
        Icons.Default.Search to "Search",
        Icons.Default.BookmarkBorder to "Saved",
        Icons.Default.Person to "Profile"
    )

    Card(
        modifier = modifier.padding(30.dp),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(
            containerColor = MovieVaultColors.GlassSurface
        ),
        border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            navItems.forEachIndexed { index, (icon, contentDescription) ->
                val isSelected = selectedIndex == index
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "bottomNavScale"
                )

                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (isSelected) MovieVaultColors.Primary else MovieVaultColors.OnSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(scale)
                        .clickable { onItemSelected(index) }
                )
            }
        }
    }
}