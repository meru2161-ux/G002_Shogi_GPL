package com.melapplyworks.g002shogi

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melapplyworks.g002shogi.analysis.MoveAssessment
import com.melapplyworks.g002shogi.analysis.ShogiEngineFactory
import com.melapplyworks.g002shogi.analysis.usi.AndroidUsiBackendFactory
import com.melapplyworks.g002shogi.coaching.MoveComparison
import com.melapplyworks.g002shogi.coaching.MoveTeachingSummaryFactory
import com.melapplyworks.g002shogi.coaching.PlayerMoveCoach
import com.melapplyworks.g002shogi.game.*
import com.melapplyworks.g002shogi.model.*
import com.melapplyworks.g002shogi.rules.ShogiRules
import com.melapplyworks.g002shogi.ui.ShogiDisplay
import com.melapplyworks.g002shogi.ui.ShogiMoveSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF244967), secondary = Color(0xFF8C4B20))) {
                G002App()
            }
        }
    }
}

private const val AnalysisLogTag = "G002Analysis"

private data class GameLaunch(val configuration: GameConfiguration, val savedGame: SavedGame? = null)

/** Centralised board tokens keep the original look adjustable without touching rules or session state. */
private object ShogiUi {
    val BoardFrame = Color(0xFF6C3F19)
    val BoardEdge = Color(0xFF4A2A10)
    val BoardWood = Color(0xFFE5BA70)
    val WoodGrain = Color(0xFF9B622D).copy(alpha = 0.18f)
    val GridLine = Color(0xFF7B4B21)
    val PieceFace = Color(0xFFFFE7B0)
    val PieceBorder = Color(0xFF714416)
    val PieceText = Color(0xFF24170C)
    val PromotedText = Color(0xFFAE311D)
    val Selected = Color(0xFFF7D57A)
    val SelectedBorder = Color(0xFF9C5D00)
    val LegalTarget = Color(0xFFBFE4D8)
    val LegalBorder = Color(0xFF147A6C)
    val LastMove = Color(0xFFF1CE91)
    val GuideFrom = Color(0xFFFFDA70)
    val GuideTo = Color(0xFFB8E5BD)
    val GuideArrow = Color(0xFF0D6E7A).copy(alpha = 0.82f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun G002App() {
    val context = LocalContext.current
    val repository = remember { LocalGameRepository(context.applicationContext) }
    var savedGame by remember { mutableStateOf(repository.loadActive()) }
    var setupMode by remember { mutableStateOf<GameMode?>(null) }
    var launch by remember { mutableStateOf<GameLaunch?>(null) }
    when (val active = launch) {
        null -> when (val mode = setupMode) {
            null -> ModeSelectScreen(
                savedGame = savedGame,
                onContinue = { savedGame?.let { launch = GameLaunch(it.configuration, it) } },
                onMode = { setupMode = it }
            )
            else -> GameSetupScreen(
                mode = mode,
                onBack = { setupMode = null },
                onStart = { configuration ->
                    repository.clearActive()
                    savedGame = null
                    launch = GameLaunch(configuration)
                    setupMode = null
                }
            )
        }
        else -> GameScreen(
            launch = active,
            repository = repository,
            onSaved = { savedGame = it },
            onExit = { savedGame = repository.loadActive(); launch = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectScreen(savedGame: SavedGame?, onContinue: () -> Unit, onMode: (GameMode) -> Unit) {
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("G002 将棋", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("将棋の先生と、実際に指す。", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("候補手の理由を学ぶ指導モードと、気軽に遊べるAI対局モードを選べます。", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            savedGame?.let {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F2EA)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("続きから", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${if (it.configuration.mode == GameMode.COACHING) "先生と学ぶ" else "AIと対局"}・${it.configuration.difficulty.label}・${it.moves.size}手")
                        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(if (it.outcome == null) "対局を再開" else "終局局面を振り返る") }
                    }
                }
            }
            ModeCard("指導モード", "先生AIが候補3手・狙い・注意点を説明。あなたの手も比べて学べます。", "学びながら指す") { onMode(GameMode.COACHING) }
            ModeCard("AI対局モード", "説明を最小限にした、普通の人間 vs AI 対局です。", "対局を始める") { onMode(GameMode.AI_MATCH) }
            Text("オフラインで動作します。通信・課金・アカウント登録はありません。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameSetupScreen(mode: GameMode, onBack: () -> Unit, onStart: (GameConfiguration) -> Unit) {
    var humanPlayer by remember { mutableStateOf(Player.SENTE) }
    var difficulty by remember(mode) { mutableStateOf(defaultOpponentDifficulty(mode)) }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text(if (mode == GameMode.COACHING) "先生と学ぶ設定" else "AI対局設定", fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("戻る") } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("自分の手番", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Player.entries.forEach { player ->
                    FilterChip(
                        selected = humanPlayer == player,
                        onClick = { humanPlayer = player },
                        label = { Text(if (player == Player.SENTE) "先手" else "後手") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text("相手AIの強さ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            GameDifficulty.entries.forEach { option ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { difficulty = option },
                    colors = CardDefaults.cardColors(containerColor = if (difficulty == option) Color(0xFFE7F0F7) else Color(0xFFFAF7F1))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = difficulty == option, onClick = { difficulty = option })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(option.label, fontWeight = FontWeight.Bold)
                            Text(option.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(if (mode == GameMode.COACHING) "先生の解析精度は相手AIの強さとは別に、強めの設定を使用します。" else "通常対局では先生の長文解説を表示しません。", fontSize = 13.sp)
            Button(onClick = { onStart(GameConfiguration(mode, humanPlayer, difficulty)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("この設定で始める")
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F2E7))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, lineHeight = 21.sp)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreen(launch: GameLaunch, repository: LocalGameRepository, onSaved: (SavedGame) -> Unit, onExit: () -> Unit) {
    val configuration = launch.configuration
    val mode = configuration.mode
    val applicationContext = LocalContext.current.applicationContext
    val engine = remember { ShogiEngineFactory.create(AndroidUsiBackendFactory.create(applicationContext)) }
    DisposableEffect(engine) {
        onDispose { engine.close() }
    }
    val session = remember(launch) {
        ShogiGameSession(
            engine,
            mode = mode,
            humanPlayer = configuration.humanPlayer,
            restoredMoves = launch.savedGame?.moves.orEmpty(),
            restoredOutcome = launch.savedGame?.outcome
        )
    }
    var state by remember(launch) { mutableStateOf(session.state) }
    var selectedSquare by remember(mode) { mutableStateOf<Square?>(null) }
    var selectedDrop by remember(mode) { mutableStateOf<PieceType?>(null) }
    var promotionChoices by remember(mode) { mutableStateOf<List<Move>?>(null) }
    var selectedCandidateId by remember { mutableStateOf<String?>(null) }
    var showReview by remember { mutableStateOf(true) }
    var showTeacherDetails by rememberSaveable(mode) { mutableStateOf(false) }
    var showCoordinates by rememberSaveable(mode) { mutableStateOf(true) }
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    var reviewCandidateIndex by remember { mutableIntStateOf(0) }
    var questionMessage by remember { mutableStateOf("盤上の駒をタップして、合法手を確認できます。") }

    val analysis = state.analysis
    val selectedCandidate = analysis?.candidates?.firstOrNull { it.id == selectedCandidateId } ?: analysis?.candidates?.firstOrNull()
    val legalMoves = remember(state.position, state.phase) {
        if (state.phase == SessionPhase.WAITING_FOR_HUMAN) ShogiRules.legalMoves(state.position) else emptyList()
    }
    val legalTargets = if (state.phase == SessionPhase.WAITING_FOR_HUMAN) {
        ShogiMoveSelection.legalTargets(legalMoves, selectedSquare, selectedDrop)
    } else emptySet()
    val activePlayerInCheck = remember(state.position) {
        ShogiRules.isInCheck(state.position, state.position.activePlayer)
    }
    val checkedKingSquare = remember(state.position, activePlayerInCheck) {
        if (!activePlayerInCheck) null else state.position.board.entries.firstOrNull {
            it.value.owner == state.position.activePlayer && it.value.type == PieceType.KING
        }?.key
    }
    val lastMove = state.records.lastOrNull()?.move
    val review = state.records.lastOrNull()?.review
    val comparison = review?.let { PlayerMoveCoach.compare(it, reviewCandidateIndex) }

    fun persistCurrent() {
        val saved = SavedGame(
            configuration = configuration,
            moves = session.state.records.map { it.move },
            outcome = session.state.outcome,
            savedAtEpochMillis = System.currentTimeMillis()
        )
        if (repository.saveActive(saved)) onSaved(saved)
    }

    LaunchedEffect(state.phase, state.positionId, mode) {
        val expectedId = state.positionId
        val effectContext = currentCoroutineContext()
        when (state.phase) {
            SessionPhase.ANALYZING -> {
                val requestedAt = SystemClock.elapsedRealtime()
                val timed = withContext(Dispatchers.Default) {
                    val engineStartedAt = SystemClock.elapsedRealtime()
                    val result = session.analyzeCurrent(teacherAnalysisRequest(expectedId) { !effectContext.isActive })
                    result to (SystemClock.elapsedRealtime() - engineStartedAt)
                }
                val result = timed.first
                Log.i(AnalysisLogTag, "kind=position position=$expectedId engineMs=${timed.second} requestToResultMs=${SystemClock.elapsedRealtime() - requestedAt} candidates=${result.candidates.size}")
                if (currentCoroutineContext().isActive && session.installAnalysis(result)) {
                    state = session.state
                    selectedCandidateId = session.state.analysis?.candidates?.firstOrNull()?.id
                    questionMessage = "新しい局面を解析しました。候補3手を比べてから、自分の手を選びましょう。"
                }
            }
            SessionPhase.AI_THINKING -> {
                val requestedAt = SystemClock.elapsedRealtime()
                val timed = withContext(Dispatchers.Default) {
                    val engineStartedAt = SystemClock.elapsedRealtime()
                    val result = session.analyzeCurrent(configuration.difficulty.analysisRequest(expectedId) { !effectContext.isActive })
                    result to (SystemClock.elapsedRealtime() - engineStartedAt)
                }
                val result = timed.first
                Log.i(AnalysisLogTag, "kind=ai position=$expectedId engineMs=${timed.second} requestToResultMs=${SystemClock.elapsedRealtime() - requestedAt} candidates=${result.candidates.size}")
                if (currentCoroutineContext().isActive && session.playAiMove(result)) {
                    state = session.state
                    persistCurrent()
                    selectedSquare = null; selectedDrop = null
                    val aiMove = state.records.lastOrNull()?.move
                    questionMessage = aiMove?.let { "相手は ${ShogiMoveText.display(it)} と指しました。相手の狙いと次の候補を確認しましょう。" } ?: state.statusMessage
                }
            }
            SessionPhase.REVIEWING_HUMAN -> {
                val last = state.records.lastOrNull()
                if (last != null && last.isHuman && last.assessment == null) {
                    val requestedAt = SystemClock.elapsedRealtime()
                    val timed = withContext(Dispatchers.Default) {
                        val engineStartedAt = SystemClock.elapsedRealtime()
                        val assessment = session.assessLastHumanMove(teacherAnalysisRequest(expectedId) { !effectContext.isActive })
                        assessment to (SystemClock.elapsedRealtime() - engineStartedAt)
                    }
                    val assessmentResult = timed.first
                    Log.i(AnalysisLogTag, "kind=review position=$expectedId engineMs=${timed.second} requestToResultMs=${SystemClock.elapsedRealtime() - requestedAt} available=${assessmentResult?.assessment != null}")
                    if (assessmentResult != null && currentCoroutineContext().isActive && session.installUserAssessment(assessmentResult)) state = session.state
                }
            }
            else -> Unit
        }
    }

    fun submit(move: Move) {
        if (session.submitHumanMove(move)) {
            state = session.state
            persistCurrent()
            selectedSquare = null; selectedDrop = null; showReview = true; reviewCandidateIndex = 0
            promotionChoices = null
            showTeacherDetails = false
            questionMessage = "あなたの手を受け付けました。先生のレビューを確認できます。"
        }
    }
    fun tapSquare(square: Square) {
        if (state.phase != SessionPhase.WAITING_FOR_HUMAN) return
        val choices = ShogiMoveSelection.choicesForTarget(legalMoves, selectedSquare, selectedDrop, square)
        when {
            choices.size == 1 -> submit(choices.single())
            ShogiMoveSelection.isOptionalPromotionChoice(choices) -> promotionChoices = choices
            state.position.board[square]?.owner == state.humanPlayer -> {
                promotionChoices = null; selectedSquare = square; selectedDrop = null
            }
            else -> { promotionChoices = null; selectedSquare = null; selectedDrop = null }
        }
    }
    fun restartGame() {
        session.restart()
        state = session.state
        persistCurrent()
        selectedSquare = null
        selectedDrop = null
        promotionChoices = null
        selectedCandidateId = null
        showTeacherDetails = false
        confirmRestart = false
        confirmResign = false
        questionMessage = "新しい対局を準備しています。"
    }

    val screenScroll = rememberScrollState()
    val screenScope = rememberCoroutineScope()

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text(if (mode == GameMode.COACHING) "指導モード" else "AI対局", fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = { persistCurrent(); onExit() }) { Text("ホーム") } },
            actions = {
                TextButton(onClick = { if (session.undoLastMove()) { state = session.state; persistCurrent(); selectedSquare = null; selectedDrop = null; promotionChoices = null } }, enabled = state.records.isNotEmpty() && state.phase != SessionPhase.FINISHED) { Text("戻す") }
                TextButton(onClick = { if (state.records.isNotEmpty() && state.phase != SessionPhase.FINISHED) confirmRestart = true else restartGame() }) { Text("新局") }
            }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(screenScroll).padding(12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val visibleStatus = if (mode == GameMode.AI_MATCH && state.phase == SessionPhase.WAITING_FOR_HUMAN) {
                "あなたの番です。駒を選んで指してください。"
            } else state.statusMessage
            val turnLabel = when {
                state.phase == SessionPhase.FINISHED -> "終局"
                state.position.activePlayer == Player.SENTE -> "先手の番"
                else -> "後手の番"
            }
            Surface(color = if (state.phase == SessionPhase.FINISHED) Color(0xFFF8E9E7) else Color(0xFFEAF2F4), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = if (state.phase == SessionPhase.FINISHED) Color(0xFF9A3D28) else Color(0xFF24566E),
                        shape = RoundedCornerShape(7.dp)
                    ) { Text(turnLabel, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    Text(visibleStatus, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
                }
            }
            if (state.phase != SessionPhase.FINISHED && activePlayerInCheck) {
                Surface(color = Color(0xFFFFE8E5), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFC74A3D), RoundedCornerShape(10.dp))) {
                    Text("王手です。玉を安全にする合法手だけを選べます。", Modifier.padding(10.dp), color = Color(0xFF8B251B), fontWeight = FontWeight.Bold)
                }
            }
            if (mode == GameMode.COACHING && state.phase == SessionPhase.WAITING_FOR_HUMAN && selectedCandidate != null) {
                Surface(color = Color(0xFFEAF2FA), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("先生 ${selectedCandidate.move.notation}", fontWeight = FontWeight.Bold, color = Color(0xFF244967))
                        Spacer(Modifier.width(8.dp))
                        Text(selectedCandidate.purpose, Modifier.weight(1f), fontSize = 13.sp, maxLines = 2)
                    }
                }
            }
            HandArea("後手の持ち駒", state.position.hands[Player.GOTE].orEmpty(), Player.GOTE, selectedDrop, if (state.phase == SessionPhase.WAITING_FOR_HUMAN && state.humanPlayer == Player.GOTE) ({ type -> promotionChoices = null; selectedDrop = if (selectedDrop == type) null else type; selectedSquare = null }) else null)
            ShogiBoard(
                position = state.position,
                guide = if (mode == GameMode.COACHING && selectedSquare == null && selectedDrop == null) selectedCandidate?.move else null,
                lastMove = lastMove,
                selected = selectedSquare,
                legalTargets = legalTargets,
                checkedKing = checkedKingSquare,
                showCoordinates = showCoordinates,
                enabled = state.phase == SessionPhase.WAITING_FOR_HUMAN,
                onSquareTap = ::tapSquare
            )
            BoardGuideLegend(mode == GameMode.COACHING, showCoordinates, onToggleCoordinates = { showCoordinates = !showCoordinates })
            HandArea("先手の持ち駒", state.position.hands[Player.SENTE].orEmpty(), Player.SENTE, selectedDrop, if (state.phase == SessionPhase.WAITING_FOR_HUMAN && state.humanPlayer == Player.SENTE) ({ type -> promotionChoices = null; selectedDrop = if (selectedDrop == type) null else type; selectedSquare = null }) else null)

            if (state.phase == SessionPhase.FINISHED) {
                GameOverCard(
                    state.outcome,
                    state.statusMessage,
                    onReviewHistory = { screenScope.launch { screenScroll.animateScrollTo(screenScroll.maxValue) } },
                    onRestart = ::restartGame
                )
            } else if (mode == GameMode.COACHING) {
                CoachingPanel(state, selectedCandidate, review, comparison, state.records.lastOrNull()?.assessment, state.records.lastOrNull()?.takeIf { !it.isHuman }?.analysisCandidate, showReview, reviewCandidateIndex,
                    showTeacherDetails = showTeacherDetails,
                    onCandidate = { selectedCandidateId = it.id; showTeacherDetails = false; questionMessage = "${it.move.notation} の矢印と説明を表示しています。" },
                    onReviewCandidate = { reviewCandidateIndex = it; showReview = true },
                    onContinue = { if (session.continueAfterReview()) { state = session.state; showReview = false } },
                    onToggleTeacherDetails = { showTeacherDetails = !showTeacherDetails },
                    onQuestion = { questionMessage = it })
            } else MatchPanel(state, onResign = { confirmResign = true })

            Text("対局履歴", fontWeight = FontWeight.Bold)
            Text(state.records.takeLast(18).joinToString("  ") { ShogiMoveText.display(it.move) }.ifBlank { "まだ指していません。" }, lineHeight = 20.sp)
            if (mode == GameMode.COACHING) AssistChip(onClick = {}, label = { Text(questionMessage) })
        }
        promotionChoices?.let { choices ->
            val promoted = choices.first { it.promote }
            val unpromoted = choices.first { !it.promote }
            AlertDialog(
                onDismissRequest = { promotionChoices = null },
                containerColor = Color(0xFFFFF8EC),
                title = { Text("成りを選択", fontWeight = FontWeight.Bold) },
                text = { Text("${promoted.piece.label}を ${promoted.from?.japanese} から ${promoted.to.japanese} へ動かします。\nこの手を成りますか？") },
                confirmButton = { TextButton(onClick = { promotionChoices = null; submit(promoted) }) { Text("成る", fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { promotionChoices = null; submit(unpromoted) }) { Text("成らない") } }
            )
        }
        if (confirmRestart) {
            AlertDialog(
                onDismissRequest = { confirmRestart = false },
                title = { Text("新しい対局を始めますか？", fontWeight = FontWeight.Bold) },
                text = { Text("現在の対局は終了し、盤面と履歴が初期局面へ戻ります。") },
                confirmButton = { TextButton(onClick = ::restartGame) { Text("新局を始める", fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("取り消す") } }
            )
        }
        if (confirmResign) {
            AlertDialog(
                onDismissRequest = { confirmResign = false },
                title = { Text("投了しますか？", fontWeight = FontWeight.Bold) },
                text = { Text("投了するとこの対局は終了します。棋譜は終局後も振り返れます。") },
                confirmButton = { TextButton(onClick = { confirmResign = false; if (session.resign()) { state = session.state; persistCurrent() } }) { Text("投了する", fontWeight = FontWeight.Bold) } },
                dismissButton = { TextButton(onClick = { confirmResign = false }) { Text("対局を続ける") } }
            )
        }
    }
}

@Composable
private fun CoachingPanel(state: GameSessionState, selected: CandidateMove?, review: com.melapplyworks.g002shogi.coaching.UserMoveReview?, comparison: MoveComparison?, assessment: MoveAssessment?, lastAiCandidate: CandidateMove?, showReview: Boolean, reviewIndex: Int, showTeacherDetails: Boolean, onCandidate: (CandidateMove) -> Unit, onReviewCandidate: (Int) -> Unit, onContinue: () -> Unit, onToggleTeacherDetails: () -> Unit, onQuestion: (String) -> Unit) {
    if (lastAiCandidate != null && (state.phase == SessionPhase.ANALYZING || state.phase == SessionPhase.WAITING_FOR_HUMAN)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E7)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("相手の狙い：${lastAiCandidate.move.notation}", fontWeight = FontWeight.Bold)
                Text(lastAiCandidate.explanation.meaning, lineHeight = 20.sp)
                Text("注意：${lastAiCandidate.explanation.caution}", lineHeight = 20.sp)
            }
        }
    }
    if (state.phase == SessionPhase.WAITING_FOR_HUMAN && selected != null) {
        LearningFocusCard()
        Text("先生AIの候補手", fontWeight = FontWeight.Bold)
        if (state.analysis?.reachedSearchLimit == true) {
            Text("探索上限に達したため、候補と評価は今回の解析での目安です。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.analysis?.candidates.orEmpty().forEachIndexed { index, candidate ->
            CandidateCard(candidate, index + 1, candidate.id == selected.id, onClick = { onCandidate(candidate) })
        }
        TeacherCard(selected, showTeacherDetails, onToggleTeacherDetails)
        QuestionArea { label ->
            val answer = when (label) {
                "なぜ？" -> selected.explanation.meaning
                "他の手は？" -> "他の候補カードを選ぶと、狙いと読み筋を盤上で比較できます。"
                "相手がこう指したら？" -> selected.explanation.opponentResponse
                "次は？" -> selected.explanation.continuation
                "この手の注意点は？" -> selected.explanation.caution
                "自分で考える" -> "いったん候補を答えだと思わず、自分の玉・相手の玉・次に働かせたい駒を盤上で探してみましょう。考え終えたら候補と比べられます。"
                else -> selected.comparisonSummary
            }
            onQuestion(answer)
        }
    }
    if (state.phase == SessionPhase.REVIEWING_HUMAN && review != null && comparison != null) {
        if (showReview) MoveComparisonCard(comparison, assessment, review.candidatesAtSource.firstOrNull(), review.candidatesAtSource.size)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            review.candidatesAtSource.indices.forEach { index -> OutlinedButton(onClick = { onReviewCandidate(index) }, modifier = Modifier.weight(1f)) { Text("候補${index + 1}") } }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("相手の応手を見る") }
    }
}

@Composable
private fun LearningFocusCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7F1)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("指す前の3ステップ", fontWeight = FontWeight.Bold, color = Color(0xFF245C42))
            Text("① 自分の玉は安全？  ② 相手の次の狙いは？  ③ どの駒を働かせる？", lineHeight = 20.sp)
            Text("候補は答えではなく、考えた後に比べるためのヒントです。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MatchPanel(state: GameSessionState, onResign: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("手数：${state.records.size}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onResign, enabled = state.phase != SessionPhase.FINISHED) { Text("投了") }
    }
    if (state.phase == SessionPhase.FINISHED) Text("結果：${state.outcome}", fontWeight = FontWeight.Bold)
}

@Composable
private fun GameOverCard(outcome: SessionOutcome?, statusMessage: String, onReviewHistory: () -> Unit, onRestart: () -> Unit) {
    if (statusMessage.startsWith("投了")) {
        GameOverContent(
            title = "投了しました",
            summary = "新しい対局では、相手の狙いを早めに見つけることを意識してみましょう。",
            onReviewHistory = onReviewHistory,
            onRestart = onRestart
        )
        return
    }
    val (title, summary) = when (outcome) {
        SessionOutcome.SENTE_WIN -> "先手の勝ちです" to "終局まで指し切りました。良かった局面と、次に改善したい局面を対局履歴から振り返りましょう。"
        SessionOutcome.GOTE_WIN -> "後手の勝ちです" to "ここまでの対局を振り返り、相手に主導権を渡した場面と次の備えを探してみましょう。"
        SessionOutcome.DRAW_REPETITION -> "千日手です" to "同じ局面が4回現れました。次は同じ手順を避け、別の駒を働かせる方針を考えましょう。"
        SessionOutcome.SENTE_LOSES_PERPETUAL_CHECK -> "連続王手の千日手です" to "先手の連続王手で千日手となりました。攻めを続ける前に、別の勝ち筋を探しましょう。"
        SessionOutcome.GOTE_LOSES_PERPETUAL_CHECK -> "連続王手の千日手です" to "後手の連続王手で千日手となりました。玉の安全を確かめながら、別の応手を考えましょう。"
        SessionOutcome.NO_LEGAL_MOVE -> "対局を終了しました" to "合法手がない局面です。棋譜を振り返って、詰みや手詰まりの原因を確認しましょう。"
        SessionOutcome.RESIGNED -> "投了しました" to "新しい対局では、相手の狙いを早めに見つけることを意識してみましょう。"
        null -> "対局を終了しました" to "新しい対局を始められます。"
    }
    GameOverContent(title, summary, onReviewHistory, onRestart)
}

@Composable
private fun GameOverContent(title: String, summary: String, onReviewHistory: () -> Unit, onRestart: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E9)), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE0B88C), RoundedCornerShape(18.dp))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = Color(0xFF8C4B20), shape = RoundedCornerShape(8.dp)) { Text("対局結果", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF4C2714))
            Text(summary, lineHeight = 20.sp)
            OutlinedButton(onClick = onReviewHistory, modifier = Modifier.fillMaxWidth()) { Text("棋譜を振り返る") }
            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("新しい対局を始める") }
        }
    }
}

@Composable
private fun HandArea(title: String, pieces: List<PieceType>, player: Player, selected: PieceType?, onPieceClick: ((PieceType) -> Unit)?) {
    val grouped = ShogiDisplay.orderedHandCounts(pieces)
    val activeSelection = selected.takeIf { onPieceClick != null }
    Surface(color = Color(0xFFFCF8F1), tonalElevation = 1.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(0.8.dp, Color(0xFFE5D5BC), RoundedCornerShape(12.dp))) {
        if (grouped.isEmpty()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF5B3820), modifier = Modifier.weight(1f))
                Text("なし", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        } else {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF5B3820), modifier = Modifier.weight(1f))
                    if (activeSelection != null) Text("${activeSelection.label}を打つ場所を選択中", color = ShogiUi.LegalBorder, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                grouped.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { (type, count) -> HandPiece(type, count, player, activeSelection == type, onPieceClick?.let { { it(type) } }) }
                }
            }
            }
        }
    }
}

@Composable
private fun HandPiece(type: PieceType, count: Int, owner: Player, selected: Boolean, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0xFFFFE6A0) else Color.Transparent)
            .border(if (selected) 1.5.dp else 0.6.dp, if (selected) ShogiUi.SelectedBorder else Color(0xFFD4B58A), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(Modifier.size(width = 28.dp, height = 34.dp).graphicsLayer { rotationZ = ShogiDisplay.rotationDegrees(owner) }.shadow(0.8.dp, ShogiPieceShape).clip(ShogiPieceShape).background(ShogiUi.PieceFace).border(0.6.dp, ShogiUi.PieceBorder, ShogiPieceShape), contentAlignment = Alignment.Center) {
            Text(type.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (type.unpromoted != type) ShogiUi.PromotedText else ShogiUi.PieceText)
        }
        Text("×$count", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF51331B))
    }
}

@Composable
private fun CandidateCard(candidate: CandidateMove, order: Int, active: Boolean, onClick: () -> Unit) {
    val border = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(shape = RoundedCornerShape(10.dp), tonalElevation = if (active) 3.dp else 0.dp, modifier = Modifier.fillMaxWidth().border(2.dp, border, RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(88.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("候補$order", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(candidate.move.notation, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Column(Modifier.weight(1f)) { Text(candidate.purpose, fontWeight = FontWeight.SemiBold); Text("${candidate.move.from?.japanese ?: "持ち駒"} → ${candidate.move.to.japanese}", fontSize = 12.sp) }
            candidate.evaluationForFuture?.let { Text(String.format("%+.1f", it), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BoardGuideLegend(showTeacherGuide: Boolean, showCoordinates: Boolean, onToggleCoordinates: () -> Unit) {
    Surface(color = Color(0xFFFFFBF4), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showTeacherGuide) "金枠＝選択　緑点／枠＝合法手　茶＝前手　青矢印＝先生"
                else "金枠＝選択　緑点／枠＝合法手　茶＝前手",
                Modifier.weight(1f),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
            TextButton(onClick = onToggleCoordinates, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(if (showCoordinates) "符号を隠す" else "符号を表示", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TeacherCard(candidate: CandidateMove, showDetails: Boolean, onToggleDetails: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FA)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI先生の要点", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFF244967))
                TextButton(onClick = onToggleDetails, modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(if (showDetails) "詳しい説明を閉じる" else "詳しい説明を見る", fontSize = 12.sp)
                }
            }
            CoachingSection("この手の意味", candidate.explanation.meaning)
            CoachingSection("狙い", candidate.explanation.intent)
            if (showDetails) {
                Divider(color = Color(0xFFB8CBDD))
                CoachingSection("相手の応手", candidate.explanation.opponentResponse)
                CoachingSection("その後", candidate.explanation.continuation)
                CoachingSection("注意点", candidate.explanation.caution)
                Text("読み筋：${candidate.variation.moves.joinToString(" ") { it.notation }}", fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun CoachingSection(label: String, value: String) { Text("【$label】", fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(value, lineHeight = 19.sp) }

@Composable
private fun MoveComparisonCard(comparison: MoveComparison, assessment: MoveAssessment?, bestCandidate: CandidateMove?, count: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5E7)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("先生の一手レビュー", fontWeight = FontWeight.Bold, color = Color(0xFF8C4B20))
            Text(comparison.teacherMessage, fontWeight = FontWeight.SemiBold)
            assessment?.let {
                val teaching = MoveTeachingSummaryFactory.from(it, bestCandidate)
                CoachingSection("この手の評価", "${teaching.quality.label}：${teaching.verdict}（${teaching.scorePresentation}）")
                CoachingSection("勝ち筋・方針", teaching.winningPlan)
                CoachingSection("苦しくなる流れ", teaching.dangerLine)
            } ?: Text("この手を短く再解析しています…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CoachingSection("あなたの手の狙い", comparison.userIntent)
            CoachingSection("候補との違い", comparison.difference)
            CoachingSection("この手からの方針", comparison.continuation)
            CoachingSection("注意点", comparison.caution)
            Text("比較候補：$count 手", fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuestionArea(onQuestion: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("先生に質問する", fontWeight = FontWeight.Bold)
        listOf(listOf("なぜ？", "他の手は？", "相手がこう指したら？"), listOf("次は？", "この手の注意点は？", "別の手と比較"), listOf("自分で考える")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { label -> OutlinedButton(onClick = { onQuestion(label) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(label, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center) } }
            }
        }
    }
}

private val ShogiPieceShape = GenericShape { size, _ ->
    moveTo(size.width * .5f, 0f); lineTo(size.width, size.height * .2f); lineTo(size.width * .86f, size.height); lineTo(size.width * .14f, size.height); lineTo(0f, size.height * .2f); close()
}

@Composable
private fun ShogiBoard(position: ShogiPosition, guide: Move?, lastMove: Move?, selected: Square?, legalTargets: Set<Square>, checkedKing: Square?, showCoordinates: Boolean, enabled: Boolean, onSquareTap: (Square) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val coordinateGutter = if (showCoordinates) 18.dp else 0.dp
        val boardSize = (maxWidth - coordinateGutter).coerceAtMost(430.dp)
        Column(Modifier.width(boardSize + coordinateGutter), horizontalAlignment = Alignment.Start) {
            if (showCoordinates) {
                Row(Modifier.width(boardSize).height(18.dp)) {
                    ShogiDisplay.fileLabels.forEach { label ->
                        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color(0xFF5D3B22))
                    }
                }
            }
            Row {
                Box(Modifier.size(boardSize).shadow(4.dp, RoundedCornerShape(7.dp)).background(ShogiUi.BoardFrame, RoundedCornerShape(7.dp)).padding(4.dp).border(1.dp, ShogiUi.BoardEdge, RoundedCornerShape(7.dp))) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(ShogiUi.BoardWood)
                        val spacing = size.height / 8f
                        repeat(8) { index ->
                            val y = spacing * (index + .55f)
                            drawLine(ShogiUi.WoodGrain, Offset(0f, y), Offset(size.width, y + (if (index % 2 == 0) 2f else -2f)), strokeWidth = 1.4f)
                        }
                    }
                    guide?.from?.let { MoveArrow(it, guide.to) }
                    Column(Modifier.fillMaxSize()) {
                        (1..9).forEach { rank -> Row(Modifier.fillMaxWidth().weight(1f)) {
                            (9 downTo 1).forEach { file ->
                                val square = Square(file, rank)
                                val piece = position.board[square]
                                val color = when (square) {
                                    selected -> ShogiUi.Selected
                                    in legalTargets -> ShogiUi.LegalTarget.copy(alpha = 0.38f)
                                    guide?.from -> ShogiUi.GuideFrom.copy(alpha = 0.72f)
                                    guide?.to -> ShogiUi.GuideTo.copy(alpha = 0.72f)
                                    lastMove?.from, lastMove?.to -> ShogiUi.LastMove.copy(alpha = 0.62f)
                                    else -> Color.Transparent
                                }
                                val outline = when (square) {
                                    selected -> ShogiUi.SelectedBorder
                                    in legalTargets -> ShogiUi.LegalBorder
                                    guide?.from -> Color(0xFFC57600)
                                    guide?.to -> Color(0xFF238B45)
                                    else -> ShogiUi.GridLine
                                }
                                val outlineWidth = if (square == selected || square in legalTargets || square == guide?.from || square == guide?.to) 1.7.dp else 0.55.dp
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .semantics { contentDescription = ShogiDisplay.squareDescription(square) }
                                        .background(color)
                                        .border(outlineWidth, outline)
                                        .then(if (enabled) Modifier.clickable { onSquareTap(square) } else Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (square in legalTargets && piece == null) {
                                        Canvas(Modifier.size(10.dp)) { drawCircle(ShogiUi.LegalBorder, radius = size.minDimension / 2f) }
                                    }
                                    piece?.let { PieceTile(it, boardSize) }
                                    if (square == checkedKing) {
                                        Box(Modifier.matchParentSize().padding(2.dp).border(2.dp, Color(0xFFC73D32), RoundedCornerShape(3.dp)))
                                    }
                                }
                            }
                        }
                        }
                    }
                }
                if (showCoordinates) {
                    Column(Modifier.width(coordinateGutter).height(boardSize)) {
                        ShogiDisplay.rankLabels.forEach { label ->
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(label, fontSize = 10.sp, color = Color(0xFF5D3B22))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PieceTile(piece: Piece, boardSize: Dp) {
    Box(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp).graphicsLayer { rotationZ = ShogiDisplay.rotationDegrees(piece.owner) }.shadow(1.6.dp, ShogiPieceShape).clip(ShogiPieceShape).background(ShogiUi.PieceFace).border(0.9.dp, ShogiUi.PieceBorder, ShogiPieceShape), contentAlignment = Alignment.Center) {
        Text(piece.type.label, fontWeight = FontWeight.Bold, fontSize = (boardSize.value / 19).sp, color = if (piece.type.unpromoted != piece.type) ShogiUi.PromotedText else ShogiUi.PieceText)
    }
}

@Composable
private fun MoveArrow(from: Square, to: Square) {
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.width / 9f
        fun center(square: Square) = Offset((9 - square.file + .5f) * cell, (square.rank - .5f) * cell)
        val start = center(from); val end = center(to)
        drawLine(ShogiUi.GuideArrow, start, end, strokeWidth = cell * .065f, cap = StrokeCap.Round)
        val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()); val head = cell * .28f
        val left = Offset((end.x - head * cos(angle - .55)).toFloat(), (end.y - head * sin(angle - .55)).toFloat())
        val right = Offset((end.x - head * cos(angle + .55)).toFloat(), (end.y - head * sin(angle + .55)).toFloat())
        drawLine(ShogiUi.GuideArrow, end, left, strokeWidth = cell * .065f, cap = StrokeCap.Round); drawLine(ShogiUi.GuideArrow, end, right, strokeWidth = cell * .065f, cap = StrokeCap.Round)
    }
}
