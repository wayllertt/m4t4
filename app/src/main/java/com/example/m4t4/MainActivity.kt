package com.example.m4t4

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.m4t4.ui.theme.M4t4Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            M4t4Theme {
                SocialFeedApp(context = applicationContext)
            }
        }
    }
}

enum class CardStatus { Loading, Ready, Error }

data class Post(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
    val avatarUrl: String
)

data class Comment(
    val postId: Int,
    val id: Int,
    val name: String,
    val body: String
)

data class PostCardUiState(
    val post: Post,
    val status: CardStatus = CardStatus.Loading,
    val avatarColor: Color? = null,
    val comments: List<Comment> = emptyList(),
    val avatarError: Boolean = false,
    val commentsError: Boolean = false
)

private object SocialRepository {
    suspend fun loadPosts(context: Context): List<Post> = withContext(Dispatchers.IO) {
        val json = context.assets.open("social_posts.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        buildList {
            repeat(array.length()) { i ->
                val item = array.getJSONObject(i)
                add(
                    Post(
                        id = item.getInt("id"),
                        userId = item.getInt("userId"),
                        title = item.getString("title"),
                        body = item.getString("body"),
                        avatarUrl = item.getString("avatarUrl")
                    )
                )
            }
        }
    }

    suspend fun loadAvatar(post: Post): Color {
        delay(Random.nextLong(350, 1200))
        if (Random.nextInt(100) < 25) {
            error("Avatar loading failed for post=${post.id}")
        }
        return colorFromSeed(post.avatarUrl)
    }

    suspend fun loadComments(context: Context, postId: Int): List<Comment> {
        delay(Random.nextLong(500, 1500))
        if (Random.nextInt(100) < 20) {
            error("Comments loading failed for post=$postId")
        }
        return withContext(Dispatchers.IO) {
            val json = context.assets.open("comments.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            buildList {
                repeat(array.length()) { i ->
                    val item = array.getJSONObject(i)
                    if (item.getInt("postId") == postId) {
                        add(
                            Comment(
                                postId = item.getInt("postId"),
                                id = item.getInt("id"),
                                name = item.getString("name"),
                                body = item.getString("body")
                            )
                        )
                    }
                }
            }
        }
    }

    private fun colorFromSeed(seed: String): Color {
        val hash = seed.hashCode()
        val r = ((hash shr 16) and 0xFF).coerceIn(80, 220)
        val g = ((hash shr 8) and 0xFF).coerceIn(80, 220)
        val b = (hash and 0xFF).coerceIn(80, 220)
        return Color(r, g, b)
    }
}

@Composable
fun SocialFeedApp(context: Context) {
    val scope = rememberCoroutineScope()
    val cards = remember { mutableStateListOf<PostCardUiState>() }
    var loadingJob by remember { mutableStateOf<Job?>(null) }

    fun refreshFeed() {
        loadingJob?.cancel()
        cards.clear()
        loadingJob = scope.launch {
            val posts = SocialRepository.loadPosts(context).shuffled().take(Random.nextInt(8, 16))
            cards.addAll(posts.map { PostCardUiState(post = it, status = CardStatus.Loading) })

            posts.forEachIndexed { index, post ->
                launch {
                    val result = supervisorScope {
                        val avatarDeferred = async {
                            try {
                                SocialRepository.loadAvatar(post)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        val commentsDeferred = async {
                            try {
                                SocialRepository.loadComments(context, post.id)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        avatarDeferred.await() to commentsDeferred.await()
                    }

                    val (avatar, comments) = result
                    val hasError = avatar == null || comments == null
                    cards[index] = cards[index].copy(
                        status = if (hasError) CardStatus.Error else CardStatus.Ready,
                        avatarColor = avatar,
                        comments = comments.orEmpty(),
                        avatarError = avatar == null,
                        commentsError = comments == null
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { refreshFeed() }

    DisposableEffect(Unit) {
        onDispose { loadingJob?.cancel() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Социальная лента", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { refreshFeed() }) {
                    Text("Обновить")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = cards, key = { it.post.id }) { card ->
                PostCard(card)
            }
        }
    }
}

@Composable
private fun PostCard(card: PostCardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AvatarIndicator(card)
                Column {
                    Text(text = "Автор #${card.post.userId}", fontWeight = FontWeight.Bold)
                    Text(text = "Состояние: ${card.status}")
                }
            }
            Text(text = card.post.title, style = MaterialTheme.typography.titleMedium)
            Text(text = card.post.body)
            Text(text = "Комментарии:", fontWeight = FontWeight.SemiBold)

            when {
                card.commentsError -> Text(text = "⚠ Не удалось загрузить комментарии")
                card.status == CardStatus.Loading -> Text(text = "Загрузка комментариев...")
                card.comments.isEmpty() -> Text(text = "Пока нет комментариев")
                else -> card.comments.take(3).forEach {
                    Text(text = "• ${it.name}: ${it.body}")
                }
            }
        }
    }
}

@Composable
private fun AvatarIndicator(card: PostCardUiState) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                when {
                    card.avatarColor != null -> card.avatarColor
                    card.avatarError -> Color(0xFFEF5350)
                    else -> Color.LightGray
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val label = when {
            card.avatarColor != null -> card.post.userId.toString()
            card.avatarError -> "!"
            else -> "..."
        }
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true)
@Composable
fun FeedPreview() {
    M4t4Theme {
        PostCard(
            PostCardUiState(
                post = Post(1, 1, "Preview title", "Preview body", "url"),
                status = CardStatus.Ready,
                avatarColor = Color(0xFF5C6BC0),
                comments = listOf(Comment(1, 1, "Тест", "Отличный пост!"))
            )
        )
    }
}
