// ==================== ActorDetailsActivity.kt ====================
package com.example.moviesdb

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

// ==================== Data Classes ====================
data class PersonDetails(
    val id: Int,
    val name: String,
    val biography: String,
    val birthday: String?,
    val deathday: String?,
    val place_of_birth: String?,
    val profile_path: String?,
    val known_for_department: String,
    val popularity: Double,
    val gender: Int, // 1 = Female, 2 = Male
    val homepage: String?,
    val imdb_id: String?,
    val also_known_as: List<String>
)

data class MovieCredit(
    val id: Int,
    val title: String,
    val character: String?,
    val job: String?,
    val release_date: String?,
    val poster_path: String?,
    val vote_average: Double,
    val popularity: Double
)

data class PersonMovieCredits(
    val cast: List<MovieCredit>,
    val crew: List<MovieCredit>
)

data class PersonImages(
    val profiles: List<ProfileImage>
)

data class ProfileImage(
    val file_path: String,
    val width: Int,
    val height: Int,
    val vote_average: Double
)

// ==================== API Interface ====================
interface PersonApiService {
    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Header("Authorization") token: String,
        @Query("api_key") apiKey: String = "002a8036d4ee4c4c63d34d3dedf29101",
        @Query("language") language: String = "en-US"
    ): PersonDetails

    @GET("person/{person_id}/movie_credits")
    suspend fun getPersonMovieCredits(
        @Path("person_id") personId: Int,
        @Header("Authorization") token: String,
        @Query("api_key") apiKey: String = "002a8036d4ee4c4c63d34d3dedf29101",
        @Query("language") language: String = "en-US"
    ): PersonMovieCredits

    @GET("person/{person_id}/images")
    suspend fun getPersonImages(
        @Path("person_id") personId: Int,
        @Header("Authorization") token: String,
        @Query("api_key") apiKey: String = "002a8036d4ee4c4c63d34d3dedf29101"
    ): PersonImages
}

// ==================== ViewModel ====================
data class ActorDetailsUiState(
    val personDetails: PersonDetails? = null,
    val movieCredits: PersonMovieCredits? = null,
    val images: PersonImages? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ActorDetailsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ActorDetailsUiState())
    val uiState: StateFlow<ActorDetailsUiState> = _uiState.asStateFlow()

    private val api = ApiClient.retrofit.create(PersonApiService::class.java)

    fun loadActorDetails(personId: Int) {
        viewModelScope.launch {
            _uiState.value = ActorDetailsUiState(isLoading = true)
            try {
                val details = api.getPersonDetails(personId, TMDBConfig.BEARER_TOKEN)
                val credits = api.getPersonMovieCredits(personId, TMDBConfig.BEARER_TOKEN)
                val images = api.getPersonImages(personId, TMDBConfig.BEARER_TOKEN)

                _uiState.value = ActorDetailsUiState(
                    personDetails = details,
                    movieCredits = credits,
                    images = images,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = ActorDetailsUiState(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
}

// ==================== Activity ====================
class ActorDetailsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PERSON_ID = "extra_person_id"
        const val EXTRA_PERSON_NAME = "extra_person_name"

        fun createIntent(context: Context, personId: Int, personName: String): Intent {
            return Intent(context, ActorDetailsActivity::class.java).apply {
                putExtra(EXTRA_PERSON_ID, personId)
                putExtra(EXTRA_PERSON_NAME, personName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val personId = intent.getIntExtra(EXTRA_PERSON_ID, -1)
        val personName = intent.getStringExtra(EXTRA_PERSON_NAME) ?: "Actor Details"

        if (personId == -1) {
            finish()
            return
        }

        setContent {
            MovieVaultTheme {
                ActorDetailsScreen(
                    personId = personId,
                    personName = personName,
                    onBackPressed = { finish() },
                    onMovieClick = { movieId, movieTitle ->
                        startActivity(
                            Intent(this@ActorDetailsActivity, MovieDetailsActivity::class.java).apply {
                                putExtra(MovieDetailsActivity.EXTRA_MOVIE_ID, movieId)
                                putExtra(MovieDetailsActivity.EXTRA_MOVIE_TITLE, movieTitle)
                            }
                        )
                    }
                )
            }
        }
    }
}

// ==================== Compose UI ====================
@Composable
fun ActorDetailsScreen(
    personId: Int,
    personName: String,
    onBackPressed: () -> Unit,
    onMovieClick: (Int, String) -> Unit,
    viewModel: ActorDetailsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(personId) {
        viewModel.loadActorDetails(personId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0C0C0C),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.errorMessage != null -> ErrorScreen(state.errorMessage!!) {
                viewModel.loadActorDetails(personId)
            }
            state.personDetails != null -> ActorDetailsContent(
                personDetails = state.personDetails!!,
                movieCredits = state.movieCredits,
                images = state.images,
                scrollState = scrollState,
                onMovieClick = onMovieClick
            )
        }

        // Top Bar
        TopBarActor(onBackPressed, state.personDetails)
    }
}

@Composable
fun TopBarActor(onBackPressed: () -> Unit, personDetails: PersonDetails?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Card(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
            border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onBackPressed() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ActorDetailsContent(
    personDetails: PersonDetails,
    movieCredits: PersonMovieCredits?,
    images: PersonImages?,
    scrollState: ScrollState,
    onMovieClick: (Int, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Hero Section
        ActorHeroSection(personDetails)

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Basic Info
            ActorBasicInfoSection(personDetails)

            // Stats
            ActorStatsSection(personDetails, movieCredits)

            // Biography
            if (personDetails.biography.isNotEmpty()) {
                ActorBiographySection(personDetails.biography)
            }

            // Personal Info
            ActorPersonalInfoSection(personDetails)

            // Photos
            if (images?.profiles?.isNotEmpty() == true) {
                ActorPhotosSection(images.profiles)
            }

            // Known For (Popular Movies)
            if (movieCredits?.cast?.isNotEmpty() == true) {
                ActorKnownForSection(
                    movies = movieCredits.cast.sortedByDescending { it.popularity }.take(10),
                    onMovieClick = onMovieClick
                )
            }

            // All Movies
            if (movieCredits?.cast?.isNotEmpty() == true) {
                ActorAllMoviesSection(
                    movies = movieCredits.cast.sortedByDescending { it.release_date },
                    onMovieClick = onMovieClick
                )
            }

            // Crew Work (if any)
            if (movieCredits?.crew?.isNotEmpty() == true) {
                ActorCrewWorkSection(
                    crewWork = movieCredits.crew.sortedByDescending { it.release_date },
                    onMovieClick = onMovieClick
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun ActorHeroSection(personDetails: PersonDetails) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        if (personDetails.profile_path != null) {
            SubcomposeAsyncImage(
                model = TMDBConfig.getBackdropUrl(personDetails.profile_path),
                contentDescription = personDetails.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MovieVaultColors.OnSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )
    }
}

@Composable
fun ActorBasicInfoSection(personDetails: PersonDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = personDetails.name,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = personDetails.known_for_department,
                color = MovieVaultColors.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (personDetails.birthday != null) {
                Text("•", color = MovieVaultColors.OnSurfaceVariant)
                Text(
                    text = "Age ${calculateAge(personDetails.birthday, personDetails.deathday)}",
                    color = MovieVaultColors.OnSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun ActorStatsSection(personDetails: PersonDetails, movieCredits: PersonMovieCredits?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
        border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItemActor(
                "Popularity",
                String.format("%.0f", personDetails.popularity),
                "📈"
            )
            StatItemActor(
                "Movies",
                (movieCredits?.cast?.size ?: 0).toString(),
                "🎬"
            )
            StatItemActor(
                "Gender",
                if (personDetails.gender == 2) "Male" else "Female",
                if (personDetails.gender == 2) "👨" else "👩"
            )
        }
    }
}

@Composable
fun StatItemActor(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = MovieVaultColors.OnSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
fun ActorBiographySection(biography: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Biography",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
            border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
        ) {
            Text(
                biography,
                color = MovieVaultColors.OnSurface,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
fun ActorPersonalInfoSection(personDetails: PersonDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Personal Info",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
            border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (personDetails.birthday != null) {
                    PersonalInfoRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Birthday",
                        value = formatDate(personDetails.birthday)
                    )
                }

                if (personDetails.place_of_birth != null) {
                    PersonalInfoRow(
                        icon = Icons.Default.LocationOn,
                        label = "Place of Birth",
                        value = personDetails.place_of_birth
                    )
                }

                if (personDetails.also_known_as.isNotEmpty()) {
                    PersonalInfoRow(
                        icon = Icons.Default.Person,
                        label = "Also Known As",
                        value = personDetails.also_known_as.take(3).joinToString(", ")
                    )
                }
            }
        }
    }
}

@Composable
fun PersonalInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MovieVaultColors.Primary,
            modifier = Modifier.size(20.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                color = MovieVaultColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                value,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ActorPhotosSection(photos: List<ProfileImage>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Photos",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(photos.take(8)) { photo ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(120.dp, 180.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = TMDBConfig.getPosterUrl(photo.file_path),
                        contentDescription = "Actor photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun ActorKnownForSection(
    movies: List<MovieCredit>,
    onMovieClick: (Int, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Known For",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(movies) { movie ->
                MovieCreditCard(movie, onMovieClick)
            }
        }
    }
}

@Composable
fun ActorAllMoviesSection(
    movies: List<MovieCredit>,
    onMovieClick: (Int, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "All Movies",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(movies.take(15)) { movie ->
                MovieCreditCard(movie, onMovieClick)
            }
        }
    }
}

@Composable
fun ActorCrewWorkSection(
    crewWork: List<MovieCredit>,
    onMovieClick: (Int, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Crew Work",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(crewWork.take(10)) { work ->
                CrewWorkCard(work, onMovieClick)
            }
        }
    }
}

@Composable
fun MovieCreditCard(
    movie: MovieCredit,
    onMovieClick: (Int, String) -> Unit
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable { onMovieClick(movie.id, movie.title) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
        border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
    ) {
        Column {
            // Movie Poster
            if (movie.poster_path != null) {
                SubcomposeAsyncImage(
                    model = TMDBConfig.getPosterUrl(movie.poster_path),
                    contentDescription = movie.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎬", fontSize = 30.sp)
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    movie.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (movie.character != null) {
                    Text(
                        movie.character,
                        color = MovieVaultColors.OnSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        String.format("%.1f", movie.vote_average),
                        color = MovieVaultColors.OnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                if (movie.release_date != null && movie.release_date.isNotEmpty()) {
                    Text(
                        movie.release_date.take(4),
                        color = MovieVaultColors.Secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun CrewWorkCard(
    work: MovieCredit,
    onMovieClick: (Int, String) -> Unit
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .clickable { onMovieClick(work.id, work.title) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MovieVaultColors.GlassSurface),
        border = BorderStroke(1.dp, MovieVaultColors.GlassBorder)
    ) {
        Column {
            // Movie Poster
            if (work.poster_path != null) {
                SubcomposeAsyncImage(
                    model = TMDBConfig.getPosterUrl(work.poster_path),
                    contentDescription = work.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MovieVaultColors.OnSurfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎬", fontSize = 30.sp)
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    work.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (work.job != null) {
                    Text(
                        work.job,
                        color = MovieVaultColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (work.release_date != null && work.release_date.isNotEmpty()) {
                    Text(
                        work.release_date.take(4),
                        color = MovieVaultColors.Secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ==================== Utility Functions ====================
fun calculateAge(birthday: String, deathday: String?): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val birthDate = format.parse(birthday)
        val endDate = if (deathday != null) format.parse(deathday) else Date()

        if (birthDate != null && endDate != null) {
            val calendar = Calendar.getInstance()
            calendar.time = endDate
            val endYear = calendar.get(Calendar.YEAR)

            calendar.time = birthDate
            val birthYear = calendar.get(Calendar.YEAR)

            (endYear - birthYear).toString()
        } else {
            "Unknown"
        }
    } catch (e: Exception) {
        "Unknown"
    }
}

fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        if (date != null) outputFormat.format(date) else dateString
    } catch (e: Exception) {
        dateString
    }
}
