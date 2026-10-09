package com.example.learningdashboard

import androidx.compose.foundation.background
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val instructor: String,
    val totalLessons: Int
)

@Entity(
    tableName = "lessons",
    foreignKeys = [ForeignKey(
        entity = CourseEntity::class,
        parentColumns = ["id"],
        childColumns = ["courseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("courseId")]
)
data class LessonEntity(
    @PrimaryKey val id: Int,
    val courseId: Int,
    val title: String,
    val completed: Boolean = false
)

@Dao
interface LearningDao {
    @Query("SELECT * FROM courses ORDER BY id")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM lessons WHERE courseId = :courseId ORDER BY id")
    fun observeLessons(courseId: Int): Flow<List<LessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCourses(items: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLessons(items: List<LessonEntity>)

    @Query("UPDATE lessons SET completed = :completed WHERE id = :lessonId")
    suspend fun setLessonCompleted(lessonId: Int, completed: Boolean)

    @Query("SELECT COUNT(*) FROM lessons WHERE courseId = :courseId")
    suspend fun lessonCount(courseId: Int): Int

    @Query("SELECT COUNT(*) FROM lessons WHERE courseId = :courseId AND completed = 1")
    suspend fun completedCount(courseId: Int): Int
}

@Database(entities = [CourseEntity::class, LessonEntity::class], version = 1, exportSchema = false)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao

    companion object {
        @Volatile private var INSTANCE: LearningDatabase? = null
        fun get(app: Application): LearningDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    app, LearningDatabase::class.java, "learning-dashboard.db"
                ).build().also { INSTANCE = it }
            }
    }
}

data class CourseUi(
    val id: Int,
    val title: String,
    val instructor: String,
    val lessons: Int,
    val completed: Int
) {
    val progress: Int get() = calculateProgress(completed, lessons)
}

fun calculateProgress(completed: Int, total: Int): Int =
    if (total <= 0) 0 else ((completed.coerceIn(0, total) * 100f) / total).toInt()

class MockCourseApi {
    suspend fun fetchCourses(): Pair<List<CourseEntity>, List<LessonEntity>> {
        delay(650)
        return Pair(
            listOf(
                CourseEntity(1, "Python Programming", "John Smith", 5),
                CourseEntity(2, "Generative AI", "Sarah Williams", 4),
                CourseEntity(3, "Full Stack Development", "David Brown", 6)
            ),
            listOf(
                LessonEntity(101, 1, "Introduction", true),
                LessonEntity(102, 1, "Variables & Data Types", true),
                LessonEntity(103, 1, "Functions"),
                LessonEntity(104, 1, "Object-Oriented Programming"),
                LessonEntity(105, 1, "Exception Handling"),
                LessonEntity(201, 2, "What is Generative AI?", true),
                LessonEntity(202, 2, "Prompt Engineering"),
                LessonEntity(203, 2, "Large Language Models"),
                LessonEntity(204, 2, "Responsible AI"),
                LessonEntity(301, 3, "HTML & CSS", true),
                LessonEntity(302, 3, "JavaScript"),
                LessonEntity(303, 3, "React Basics"),
                LessonEntity(304, 3, "Backend APIs"),
                LessonEntity(305, 3, "Databases"),
                LessonEntity(306, 3, "Deployment")
            )
        )
    }
}

class CourseRepository(private val dao: LearningDao, private val api: MockCourseApi) {
    private var simulateFailure = false

    fun setSimulateFailure(value: Boolean) { simulateFailure = value }

    suspend fun refreshCourses() {
        if (simulateFailure) error("Simulated API failure")
        val (courses, lessons) = api.fetchCourses()
        dao.saveCourses(courses)
        dao.saveLessons(lessons)
    }

    fun observeCourses(): Flow<List<CourseUi>> = combine(
        dao.observeCourses(),
        flow { emit(Unit) }.onStart { emit(Unit) }
    ) { courses, _ ->
        courses.map { c ->
            val count = dao.lessonCount(c.id)
            val done = dao.completedCount(c.id)
            CourseUi(c.id, c.title, c.instructor, count.takeIf { it > 0 } ?: c.totalLessons, done)
        }
    }

    fun observeLessons(courseId: Int) = dao.observeLessons(courseId)

    suspend fun completeLesson(lessonId: Int, completed: Boolean) =
        dao.setLessonCompleted(lessonId, completed)
}

class LearningViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = LearningDatabase.get(application).learningDao()
    private val repository = CourseRepository(dao, MockCourseApi())

    private val _screen = MutableStateFlow("login")
    val screen = _screen.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()
    private val _selectedCourse = MutableStateFlow<Int?>(null)
    val selectedCourse = _selectedCourse.asStateFlow()
    private val _courses = MutableStateFlow<List<CourseUi>>(emptyList())
    val courses = _courses.asStateFlow()
    private val _lessons = MutableStateFlow<List<LessonEntity>>(emptyList())
    val lessons = _lessons.asStateFlow()
    private val _offlineMessage = MutableStateFlow<String?>(null)
    val offlineMessage = _offlineMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCourses().collect { _courses.value = it }
        }
    }

    fun updateEmail(value: String) { _email.value = value }
    fun login(password: String) {
        val e = _email.value.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            _error.value = "Enter a valid email address"; return
        }
        if (password.length < 6) {
            _error.value = "Password must be at least 6 characters"; return
        }
        _error.value = null
        viewModelScope.launch {
            _loading.value = true
            delay(500)
            _loading.value = false
            _screen.value = "dashboard"
            loadCourses()
        }
    }

    fun loadCourses(simulateFailure: Boolean = false) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repository.setSimulateFailure(simulateFailure)
            try {
                repository.refreshCourses()
                _offlineMessage.value = null
            } catch (e: Exception) {
                if (_courses.value.isNotEmpty()) {
                    _offlineMessage.value = "API unavailable — showing saved courses from Room."
                } else {
                    _error.value = "Unable to load courses. Try again."
                }
            } finally { _loading.value = false }
        }
    }

    fun openCourse(id: Int) {
        _selectedCourse.value = id
        _screen.value = "details"
        viewModelScope.launch {
            repository.observeLessons(id).collect { _lessons.value = it }
        }
    }

    fun backToDashboard() { _screen.value = "dashboard" }

    fun toggleLesson(lesson: LessonEntity) {
        viewModelScope.launch {
            repository.completeLesson(lesson.id, !lesson.completed)
            // Refresh derived progress after Room updates.
            val current = _selectedCourse.value ?: return@launch
            val course = _courses.value.firstOrNull { it.id == current }
            if (course != null) {
                _courses.value = _courses.value.map {
                    if (it.id == current) it.copy(completed = (it.completed + if (!lesson.completed) 1 else -1).coerceIn(0, it.lessons)) else it
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF3659C9))) {
                Surface(Modifier.fillMaxSize()) { LearningApp() }
            }
        }
    }
}

private val Ink = Color(0xFF172B4D)
private val Muted = Color(0xFF6B7890)
private val Canvas = Color(0xFFF5F7FB)
private val Teal = Color(0xFF18A999)
private val Blue = Color(0xFF4967D5)
private val Lilac = Color(0xFF8774D8)

@Composable
fun LearningApp(vm: LearningViewModel = viewModel()) {
    val screen by vm.screen.collectAsState()
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Blue, onPrimary = Color.White, secondary = Teal,
        background = Canvas, surface = Color.White, onSurface = Ink,
        onSurfaceVariant = Muted, error = Color(0xFFB93838)
    )) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            when (screen) {
                "login" -> LoginScreen(vm)
                "dashboard" -> DashboardScreen(vm)
                else -> DetailsScreen(vm)
            }
        }
    }
}

@Composable
fun LoginScreen(vm: LearningViewModel) {
    val email by vm.email.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(58.dp).background(Blue, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Text("L", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(22.dp))
        Text("Learn with purpose.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("Your learning, progress and insights in one place.", color = Muted)
        Spacer(Modifier.height(28.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp)) {
                Text("Welcome back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Sign in to continue your learning journey", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(email, vm::updateEmail, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { vm.login(password) }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(if (loading) "Signing in…" else "Sign in  →", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Text("Demo access: any valid email and password with 6+ characters", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: LearningViewModel) {
    val courses by vm.courses.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val message by vm.offlineMessage.collectAsState()
    val totalLessons = courses.sumOf { it.lessons }
    val completedLessons = courses.sumOf { it.completed }
    val overall = calculateProgress(completedLessons, totalLessons)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LurnySense", color = Blue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Learning overview", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Your team's learning at a glance", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Box(Modifier.size(44.dp).background(Color(0xFFE8ECFF), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Text("S", color = Blue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(Modifier.padding(18.dp)) {
                    Text("YOUR LEARNING SNAPSHOT", color = Color(0xFFB9C7E2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Small steps. Real progress.", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Keep building your skills — you're making progress.", color = Color(0xFFD5DDF0), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Overall completion", color = Color(0xFFD5DDF0), style = MaterialTheme.typography.bodySmall)
                            Text("$overall%", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.size(70.dp).background(Color(0xFF2B426A), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                            Text("↗", color = Color(0xFF65E0C6), style = MaterialTheme.typography.headlineLarge)
                        }
                    }
                    LinearProgressIndicator(progress = { overall / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Color(0xFF65E0C6), trackColor = Color(0xFF34496D))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("COURSES", "${courses.size}", "In your library", Blue, Modifier.weight(1f))
                MetricCard("LESSONS DONE", "$completedLessons/$totalLessons", "Keep it up", Teal, Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Completion trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Progress across your courses", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("THIS WEEK", color = Blue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        val bars = listOf(0.34f, 0.48f, 0.43f, 0.66f, 0.57f, 0.78f, (overall.coerceAtLeast(12) / 100f).coerceAtMost(1f))
                        bars.forEachIndexed { index, value ->
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height((78 * value).dp).background(if (index == bars.lastIndex) Teal else Color(0xFFDCE4FF), RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your learning paths", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Pick up where you left off", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.loadCourses() }, enabled = !loading) { Text("Refresh") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.loadCourses(simulateFailure = true) }, enabled = !loading, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) { Text("Test offline mode", style = MaterialTheme.typography.labelMedium) }
            }
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text("●  ${message!!}", color = Teal, style = MaterialTheme.typography.bodySmall)
            }
            if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp)) }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = { vm.loadCourses() }) { Text("Try again") }
            }
            if (courses.isEmpty() && !loading && error == null) Text("No courses available yet.", color = Muted, modifier = Modifier.padding(top = 12.dp))
        }
        items(courses, key = { it.id }) { course ->
            CourseCard(course = course, onClick = { vm.openCourse(course.id) })
        }
        item {
            Text("Data shown is for this demo. Course information is saved on this device for offline access.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, subtitle: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.width(28.dp).height(4.dp).background(accent, RoundedCornerShape(50)))
            Spacer(Modifier.height(12.dp))
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CourseCard(course: CourseUi, onClick: () -> Unit) {
    val accent = when (course.id) { 1 -> Blue; 2 -> Lilac; else -> Teal }
    val symbol = when (course.id) { 1 -> "Py"; 2 -> "AI"; else -> "FS" }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Text(symbol, color = accent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(course.title, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("By ${course.instructor}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("${course.completed} of ${course.lessons} lessons completed", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("${course.progress}%", color = accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(progress = { course.progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = accent, trackColor = Color(0xFFEDF0F6))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (course.progress == 100) "Completed" else "Continue learning", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Open course  →", color = Ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DetailsScreen(vm: LearningViewModel) {
    val selectedId by vm.selectedCourse.collectAsState()
    val courses by vm.courses.collectAsState()
    val lessons by vm.lessons.collectAsState()
    val course = courses.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        TextButton(onClick = vm::backToDashboard, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) { Text("←  Learning overview") }
        if (course == null) {
            Text("Course not found", color = Ink)
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("LEARNING PATH", color = Color(0xFFB9C7E2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(course.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("By ${course.instructor}", color = Color(0xFFD5DDF0))
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Course completion", color = Color(0xFFD5DDF0), style = MaterialTheme.typography.bodySmall)
                                Text("${course.progress}%", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            }
                            Text("${course.completed}/${course.lessons} lessons", color = Color(0xFFD5DDF0), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { course.progress / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Color(0xFF65E0C6), trackColor = Color(0xFF34496D))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("Course content", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Mark lessons complete as you progress.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                lessons.forEachIndexed { index, lesson ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { vm.toggleLesson(lesson) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).background(if (lesson.completed) Color(0xFFE0F6F1) else Color(0xFFF0F2F8), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text(if (lesson.completed) "✓" else "${index + 1}", color = if (lesson.completed) Teal else Muted, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(lesson.title, color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(if (lesson.completed) "Completed" else "Not started", color = if (lesson.completed) Teal else Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Checkbox(checked = lesson.completed, onCheckedChange = { vm.toggleLesson(lesson) })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Your progress is saved locally and remains available offline.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
