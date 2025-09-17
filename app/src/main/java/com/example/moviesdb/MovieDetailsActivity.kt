package com.example.moviesdb

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

// ------------------------- Data classes -------------------------
data class MovieDetails(
    val id: Int,
    val title: String,
    val original_title: String,
    val overview: String,
    val release_date: String,
    val vote_average: Double,
    val vote_count: Int,
    val popularity: Double,
    val runtime: Int?,
    val status: String,
    val tagline: String?,
    val budget: Long,
    val revenue: Long,
    val genres: List<Genre>,
    val production_companies: List<ProductionCompany>,
    val production_countries: List<ProductionCountry>,
    val spoken_languages: List<SpokenLanguage>,
    val backdrop_path: String?,
    val poster_path: String?,
    val homepage: String?
)

data class ProductionCompany(val id: Int, val name: String, val logo_path: String?, val origin_country: String)
data class ProductionCountry(val iso_3166_1: String, val name: String)
data class SpokenLanguage(val english_name: String, val iso_639_1: String, val name: String)

// --- Trailer video info ---
data class Video(
    val id: String,
    val key: String,
    val name: String,
    val site: String,
    val type: String,
    val official: Boolean?
)
data class VideoResponse(val id: Int, val results: List<Video>)

// --- Cast and Crew data classes ---
data class Cast(
    val id: Int,
    val name: String,
    val character: String,
    val profile_path: String?,
    val order: Int
)

data class Crew(
    val id: Int,
    val name: String,
    val job: String,
    val department: String,
    val profile_path: String?
)

data class Credits(
    val id: Int,
    val cast: List<Cast>,
    val crew: List<Crew>
)

// ------------------------- API -------------------------
interface TMDBApiServiceExtended : TMDBApiService {
    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Header("Authorization") token: String,
        @Query("api_key") apiKey: String = "002a8036d4ee4c4c63d34d3dedf29101",
        @Query("language") language: String = "en-US"
    ): MovieDetails

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int,
        @Header("Authorization") token: String,
        @Query("api_key") apiKey: String = "002a8036d4ee4c4c63d34d3dedf29101",
        @Query("language") language: String = "en-US"
    ): VideoResponse

    @GET("movie/{movie_id}/credits")
    suspend fun getMovieCredits(
        @Path("movie_id") movieId: Int,
        @Header("Authorization") token: String,
        @Query("api_key") apiKey: String = "002a8036d4ee4c4c63d34d3dedf29101",
        @Query("language") language: String = "en-US"
    ): Credits
}

// ------------------------- ViewModel -------------------------
data class MovieDetailsUiState(
    val movieDetails: MovieDetails? = null,
    val credits: Credits? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val trailerKey: String? = null
)

class MovieDetailsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MovieDetailsUiState())
    val uiState: StateFlow<MovieDetailsUiState> = _uiState.asStateFlow()

    private val api = ApiClient.retrofit.create(TMDBApiServiceExtended::class.java)

    fun loadMovie(movieId: Int) {
        viewModelScope.launch {
            _uiState.value = MovieDetailsUiState(isLoading = true)
            try {
                val details = api.getMovieDetails(movieId, TMDBConfig.BEARER_TOKEN)
                val videos = api.getMovieVideos(movieId, TMDBConfig.BEARER_TOKEN).results
                val credits = api.getMovieCredits(movieId, TMDBConfig.BEARER_TOKEN)
                val key = videos.firstOrNull {
                    it.site.equals("YouTube", true) && it.type.equals("Trailer", true)
                }?.key ?: videos.firstOrNull { it.site.equals("YouTube", true) }?.key
                _uiState.value = MovieDetailsUiState(details, credits, false, null, key)
            } catch (e: Exception) {
                _uiState.value = MovieDetailsUiState(isLoading = false, errorMessage = e.message)
            }
        }
    }
}

// ------------------------- Activity -------------------------
class MovieDetailsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MOVIE_ID = "extra_movie_id"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val id = intent.getIntExtra(EXTRA_MOVIE_ID, -1)
        val title = intent.getStringExtra(EXTRA_MOVIE_TITLE) ?: "Movie Details"
        if (id == -1) { finish(); return }

        setContent {
            MovieVaultTheme {
                MovieDetailsScreen(
                    movieId = id,
                    movieTitle = title,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

// ------------------------- Compose UI -------------------------
@Composable
fun MovieDetailsScreen(
    movieId: Int,
    movieTitle: String,
    onBackPressed: () -> Unit,
    viewModel: MovieDetailsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()
    var showTrailer by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    LaunchedEffect(movieId) { viewModel.loadMovie(movieId) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0C0C0C), Color(0xFF1A1A2E), Color(0xFF16213E)))
        )
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.errorMessage != null -> ErrorScreen(state.errorMessage!!) { viewModel.loadMovie(movieId) }
            state.movieDetails != null -> MovieDetailsContent(
                movieDetails = state.movieDetails!!,
                credits = state.credits,
                scrollState = scroll,
                trailerKey = state.trailerKey
            ) {
                if (state.trailerKey != null) showTrailer = true
                else Toast.makeText(ctx, "Trailer not available", Toast.LENGTH_SHORT).show()
            }
        }
        TopBar(onBackPressed, state.movieDetails)
        if (showTrailer && state.trailerKey != null) {
            TrailerPlayer(videoKey = state.trailerKey!!) { showTrailer = false }
        }
    }
}

// ---- Top Bar / Buttons ----
@Composable
fun TopBar(onBackPressed: () -> Unit, movieDetails: MovieDetails?) {
    Row(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Card(
            Modifier.size(48.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
            border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
        ) {
            Box(Modifier.fillMaxSize().clickable { onBackPressed() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        if (movieDetails != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(Icons.Default.BookmarkBorder) {}
                ActionButton(Icons.Default.Share) {}
            }
        }
    }
}

@Composable
fun ActionButton(icon: ImageVector, onClick: () -> Unit) {
    Card(
        Modifier.size(48.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
        border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
    ) {
        Box(Modifier.fillMaxSize().clickable { onClick() }, contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
    }
}

// ---- Loading / Error ----
@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        CircularProgressIndicator(color = MovieVaultColors.Primary)
    }
}

/*@Composable
fun ErrorScreen(msg: String, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Error: $msg", color = Color.White)
        Button(onClick = retry) { Text("Retry") }
    }
}*/

// ---- Details Content ----
@Composable
fun MovieDetailsContent(
    movieDetails: MovieDetails,
    credits: Credits?,
    scrollState: ScrollState,
    trailerKey: String?,
    onPlayTrailer: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
        MovieHeroSection(movieDetails)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MovieTitleSection(movieDetails)
            ActionButtonsSection(trailerKey, onPlayTrailer)
            MovieStatsSection(movieDetails)
            if (movieDetails.overview.isNotEmpty()) MovieOverviewSection(movieDetails.overview)
            if (movieDetails.genres.isNotEmpty()) MovieGenresSection(movieDetails.genres)

            // Cast Section
            if (credits?.cast?.isNotEmpty() == true) {
                MovieCastSection(credits.cast)
            }

            // Key Crew Section
            if (credits?.crew?.isNotEmpty() == true) {
                MovieKeyCrewSection(credits.crew)
            }

            MovieAdditionalDetailsSection(movieDetails)
            if (movieDetails.production_companies.isNotEmpty())
                MovieProductionSection(movieDetails.production_companies)
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
fun ActionButtonsSection(trailerKey: String?, onPlayTrailer: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                if (trailerKey != null) onPlayTrailer()
                else Toast.makeText(ctx, "Trailer not available", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MovieVaultColors.Primary),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play Trailer")
        }
    }
}

// ---- Cast Section ----
@Composable
fun MovieCastSection(cast: List<Cast>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cast", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cast.take(10)) { castMember ->
                CastMemberCard(castMember)
            }
        }
    }
}

@Composable
fun CastMemberCard(castMember: Cast) {

    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(200.dp) // Fixed height for consistency
            .clickable {

                context.startActivity(
                    ActorDetailsActivity.createIntent(context, castMember.id, castMember.name)
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
        border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile Image
            if (castMember.profile_path != null) {
                SubcomposeAsyncImage(
                    model = TMDBConfig.getPosterUrl(castMember.profile_path),
                    contentDescription = castMember.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MovieVaultColors.Primary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "👤",
                                fontSize = 30.sp,
                                color = MovieVaultColors.OnSurfaceVariant
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "👤",
                        fontSize = 30.sp,
                        color = MovieVaultColors.OnSurfaceVariant
                    )
                }
            }

            // Cast Member Name
            Text(
                castMember.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Character Name
            Text(
                castMember.character,
                color = MovieVaultColors.OnSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---- Key Crew Section ----
@Composable
fun MovieKeyCrewSection(crew: List<Crew>) {
    // Filter key crew members (Director, Producer, Writer, etc.)
    val keyCrew = crew.filter {
        it.job in listOf("Director", "Producer", "Executive Producer", "Writer", "Screenplay", "Story", "Director of Photography", "Music", "Editor")
    }.distinctBy { it.id } // Remove duplicates based on person id

    if (keyCrew.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Key Crew", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(keyCrew.take(8)) { crewMember ->
                    CrewMemberCard(crewMember)
                }
            }
        }
    }
}

@Composable
fun CrewMemberCard(crewMember: Crew) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(200.dp) // Fixed height for consistency
            .clickable {
                context.startActivity(
                    ActorDetailsActivity.createIntent(context, crewMember.id, crewMember.name)
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
        border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile Image
            if (crewMember.profile_path != null) {
                SubcomposeAsyncImage(
                    model = TMDBConfig.getPosterUrl(crewMember.profile_path),
                    contentDescription = crewMember.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MovieVaultColors.Primary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "👤",
                                fontSize = 30.sp,
                                color = MovieVaultColors.OnSurfaceVariant
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "👤",
                        fontSize = 30.sp,
                        color = MovieVaultColors.OnSurfaceVariant
                    )
                }
            }

            // Crew Member Name
            Text(
                crewMember.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Job Title
            Text(
                crewMember.job,
                color = MovieVaultColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---- Trailer Player (YouTube) ----
@Composable
fun TrailerPlayer(videoKey: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    // Add lifecycle observer
                    (ctx as? ComponentActivity)?.lifecycle?.addObserver(this)
                    enableAutomaticInitialization = false
                    initialize(object : AbstractYouTubePlayerListener() {
                        override fun onReady(player: YouTubePlayer) {
                            player.loadVideo(videoKey, 0f)
                        }
                    }, true)
                }
            }
        )
        Card(
            Modifier.padding(16.dp).align(Alignment.TopEnd),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

// ---- Hero / Title / Stats / Overview ----
@Composable
fun MovieHeroSection(movieDetails: MovieDetails) {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        val url = TMDBConfig.getBackdropUrl(movieDetails.backdrop_path)
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = movieDetails.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .3f),
                        Color.Black.copy(alpha = .7f), Color.Black.copy(alpha = .9f))
                )
            )
        )
    }
}

@Composable
fun MovieTitleSection(movieDetails: MovieDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(movieDetails.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (!movieDetails.tagline.isNullOrBlank())
            Text("\"${movieDetails.tagline}\"", color = MovieVaultColors.Primary,
                fontSize = 16.sp, fontStyle = FontStyle.Italic)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(movieDetails.release_date.takeIf { it.isNotEmpty() }?.substring(0,4) ?: "N/A",
                color = MovieVaultColors.Secondary, fontWeight = FontWeight.SemiBold)
            Text("•", color = MovieVaultColors.OnSurfaceVariant)
            Text(movieDetails.status, color = MovieVaultColors.OnSurfaceVariant, fontSize = 14.sp)
            movieDetails.runtime?.let {
                if (it > 0) {
                    Text("•", color = MovieVaultColors.OnSurfaceVariant)
                    Text("${it}min", color = MovieVaultColors.OnSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun MovieStatsSection(details: MovieDetails) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
        border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), Arrangement.SpaceEvenly) {
            StatItem("Rating", String.format("%.1f", details.vote_average), "⭐")
            StatItem("Votes", formatNumber(details.vote_count.toLong()), "👥")
            StatItem("Popularity", String.format("%.0f", details.popularity), "📈")
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MovieVaultColors.OnSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun MovieOverviewSection(overview: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Overview", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
            border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
        ) {
            Text(overview, color = MovieVaultColors.OnSurface, fontSize = 16.sp,
                lineHeight = 24.sp, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
fun MovieGenresSection(genres: List<Genre>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Genres", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(genres) { g ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MovieVaultColors.Primary.copy(alpha = .2f)),
                    border = BorderStroke(1.dp, MovieVaultColors.Primary.copy(alpha = .5f))
                ) {
                    Text(
                        g.name,
                        color = MovieVaultColors.Secondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MovieAdditionalDetailsSection(details: MovieDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Details", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
            border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (details.budget > 0) DetailRow("Budget", "$${formatCurrency(details.budget)}")
                if (details.revenue > 0) DetailRow("Revenue", "$${formatCurrency(details.revenue)}")
                DetailRow("Original Title", details.original_title)
                if (details.production_countries.isNotEmpty())
                    DetailRow("Countries", details.production_countries.joinToString { it.name })
                if (details.spoken_languages.isNotEmpty())
                    DetailRow("Languages", details.spoken_languages.joinToString { it.english_name })
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MovieVaultColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun MovieProductionSection(companies: List<ProductionCompany>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Production", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(companies.take(5)) { c ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
                    border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🏢", fontSize = 24.sp)
                        Text(
                            c.name, color = Color.White, fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---- Utils ----
fun formatNumber(number: Long): String =
    when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> number.toString()
    }

fun formatCurrency(amount: Long): String =
    when {
        amount >= 1_000_000_000 -> String.format("%.1fB", amount / 1_000_000_000.0)
        amount >= 1_000_000 -> String.format("%.1fM", amount / 1_000_000.0)
        amount >= 1_000 -> String.format("%.1fK", amount / 1_000.0)
        else -> amount.toString()
    }