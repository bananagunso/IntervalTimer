package com.example.intervaltimer

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.intervaltimer.ui.theme.IntervalTimerTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.view.WindowManager
import android.content.Context
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.widget.Toast
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.alpha
import androidx.core.content.edit
import java.util.Locale
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

data class TimerStep(
    val name: String,
    val durationSeconds: Int,
    val tempo: Int,
    val color: Color
)

data class TrainingMenu(
    val name: String,
    val steps: List<TimerStep>
)

// アプリ全体のダーク背景・文字色を定義
val DarkBackgroundColor = Color(0xFF121212)
val DarkSurfaceColor = Color(0xFF1E1E1E)
val DarkTextColor = Color(0xFFE0E0E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IntervalTimerTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val sharedPreferences = remember {
                    context.getSharedPreferences("interval_timer_prefs", MODE_PRIVATE)
                }
                val gson = remember { Gson() }

                val presetNames = remember {
                    val json = sharedPreferences.getString("preset_names", null)
                    val presets = if (json != null) {
                        val type = object : TypeToken<List<String>>() {}.type
                        gson.fromJson(json, type)
                    } else {
                        listOf("ウォームアップ", "メイン", "ファスト", "トルク", "レスト", "クールダウン")
                    }
                    mutableStateListOf<String>().apply { addAll(presets) }
                }

                val allSavedMenus = remember {
                    val json = sharedPreferences.getString("saved_menus", null)
                    val menuList = try {
                        if (json != null) {
                            val type = object : TypeToken<List<TrainingMenu>>() {}.type
                            gson.fromJson<List<TrainingMenu>>(json, type)
                        } else {
                            emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    mutableStateListOf<TrainingMenu>().apply { addAll(menuList) }
                }

                LaunchedEffect(allSavedMenus.toList()) {
                    val json = gson.toJson(allSavedMenus.toList())
                    sharedPreferences.edit {putString("saved_menus", json)}
                }

                LaunchedEffect(presetNames.toList()) {
                    val json = gson.toJson(presetNames.toList())
                    sharedPreferences.edit {putString("preset_names", json)}
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackgroundColor
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = DarkBackgroundColor
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "top",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            composable("top") {
                                TopScreen(
                                    onNavigateToAdd = { navController.navigate("add_menu/-1") },
                                    onNavigateToRunning = { navController.navigate("menu_list") },
                                    onNavigateToEdit = { navController.navigate("edit_presets") }
                                )
                            }
                            composable("menu_list") {
                                MenuListScreen(
                                    menus = allSavedMenus,
                                    onSelectMenu = { index ->
                                        navController.navigate("menu_manage/$index")
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("menu_manage/{menuIndex}") { backStackEntry ->
                                val index = backStackEntry.arguments?.getString("menuIndex")?.toIntOrNull() ?: 0
                                if (index in allSavedMenus.indices) {
                                    MenuManageScreen(
                                        menu = allSavedMenus[index],
                                        onNavigateToRunning = { navController.navigate("running/$index") },
                                        onNavigateToEdit = { navController.navigate("add_menu/$index") },
                                        onDeleteMenu = {
                                            allSavedMenus.removeAt(index)
                                            navController.popBackStack()
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) { navController.popBackStack() }
                                }
                            }
                            composable("running/{menuIndex}") { backStackEntry ->
                                val index = backStackEntry.arguments?.getString("menuIndex")?.toIntOrNull() ?: 0
                                RunningScreen(
                                    steps = allSavedMenus[index].steps,
                                    onFinish = { navController.popBackStack() }
                                )
                            }
                            composable("add_menu/{menuIndex}") { backStackEntry ->
                                val menuIndex = backStackEntry.arguments?.getString("menuIndex")?.toIntOrNull() ?: -1
                                TimerScreen(
                                    presetNames = presetNames,
                                    allSavedMenus = allSavedMenus,
                                    editMenuIndex = menuIndex,
                                    onSaveFinished = { navController.popBackStack() }
                                )
                            }
                            composable("edit_presets") {
                                PresetEditScreen(presetNames = presetNames)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopScreen(onNavigateToAdd: () -> Unit, onNavigateToRunning: () -> Unit, onNavigateToEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackgroundColor)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(id = R.string.title_interval_timer), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkTextColor)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNavigateToAdd,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor, contentColor = Color.White)
        ) {
            Text(text = stringResource(id = R.string.btn_create_menu), fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNavigateToRunning,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
        ) {
            Text(text = stringResource(id = R.string.btn_start_training), fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNavigateToEdit,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor, contentColor = Color.White)
        ) {
            Text(text = stringResource(id = R.string.btn_edit_presets), fontSize = 18.sp)
        }
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    presetNames: SnapshotStateList<String>,
    allSavedMenus: SnapshotStateList<TrainingMenu>,
    editMenuIndex: Int,
    onSaveFinished: () -> Unit
) {
    var inputName by rememberSaveable { mutableStateOf("") }
    var inputTempo by rememberSaveable { mutableStateOf("85") }
    var inputMinutes by rememberSaveable { mutableIntStateOf(0) }
    var inputSeconds by rememberSaveable { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val tempSteps = rememberSaveable { mutableStateListOf<TimerStep>() }
    var menuName by rememberSaveable { mutableStateOf("新規トレーニング") }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showTempoPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (editMenuIndex in allSavedMenus.indices) {
            val targetMenu = allSavedMenus[editMenuIndex]
            menuName = targetMenu.name
            tempSteps.clear()
            tempSteps.addAll(targetMenu.steps)
        }
    }

    // 元の5色にユーザー様が足してくれた「白」を含めた完璧な6色のリスト
    val colorOptions = listOf(
        Color(0xFFF44336),
        Color(0xFFFFEB3B),
        Color(0xFF8BC34A),
        Color(0xFF00BCD4),
        Color(0xFF673AB7),
        Color(0xFFFFFFFF),
    )
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedColor = colorOptions[selectedIndex]

    Column(modifier = Modifier.fillMaxSize().background(DarkBackgroundColor).padding(16.dp)) {
        Text(text = stringResource(id = if (editMenuIndex == -1) R.string.title_create_menu else R.string.title_edit_menu), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkTextColor)
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = inputName,
                onValueChange = { inputName = it },
                label = { Text(text = stringResource(id = R.string.hint_step_name)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color.Gray
                ),
                trailingIcon = {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkSurfaceColor)
            ) {
                presetNames.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name, color = Color.White) },
                        onClick = {
                            inputName = name
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = stringResource(id = R.string.label_color), fontSize = 14.sp, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            colorOptions.forEachIndexed { index, color ->
                val isSelected = (selectedIndex == index) // インデックスで比較

                Box(
                    modifier = Modifier
                        .size(if (isSelected) 44.dp else 38.dp)
                        .background(color, shape = CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedIndex = index },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = OutlinedTextFieldDefaults.shape
            ) {
                Text(text = "${inputMinutes}分 ${inputSeconds}秒", color = Color.White)
            }
            OutlinedButton(
                onClick = { showTempoPicker = true },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = OutlinedTextFieldDefaults.shape
            ) {
                Text(text = "$inputTempo RPM", color = Color.White)
            }
        }

        if (showTimePicker) {
            ModalBottomSheet(
                onDismissRequest = { showTimePicker = false },
                containerColor = DarkSurfaceColor // RPMと同じ色
            ) {
                TimeDrumPicker(
                    initialMinutes = inputMinutes,
                    initialSeconds = inputSeconds,
                    onTimeSelected = { m, s ->
                        inputMinutes = m
                        inputSeconds = s
                    }
                )
            }
        }

        if (showTempoPicker) {
            ModalBottomSheet(
                onDismissRequest = { showTempoPicker = false },
                containerColor = DarkSurfaceColor
            ) {
                RpmDrumPicker(
                    initialRpm = 85, // または現在の設定値
                    onRpmSelected = { selectedRpm ->
                        inputTempo = selectedRpm.toString() // ここで親の状態を更新
                    }
                )
            }
        }

        Button(
            onClick = {
                if (inputName.isNotBlank()) {
                    // ここで計算！
                    val totalSeconds = (inputMinutes * 60) + inputSeconds

                    tempSteps.add(
                        TimerStep(
                            name = inputName,
                            durationSeconds = totalSeconds, // 計算済みの合計値を渡す
                            tempo = inputTempo.toIntOrNull() ?: 0,
                            color = selectedColor
                        )
                    )
                }
            },
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.btn_add_step))
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            itemsIndexed(tempSteps) { index, step ->
                Card(
                    modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = step.color.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).background(step.color, shape = CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val minutes = step.durationSeconds / 60
                            val seconds = step.durationSeconds % 60
                            Text(step.name, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = String.format("%02d:%02d", minutes, seconds)+" / ${step.tempo}RPM", fontSize = 14.sp, color = Color.LightGray)
                        }

                        if (index > 0) {
                            IconButton(onClick = {
                                val item = tempSteps.removeAt(index)
                                tempSteps.add(index - 1, item)
                            }) {
                                Text("▲", color = Color.LightGray, fontSize = 14.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }

                        if (index < tempSteps.lastIndex) {
                            IconButton(onClick = {
                                val item = tempSteps.removeAt(index)
                                tempSteps.add(index + 1, item)
                            }) {
                                Text("▼", color = Color.LightGray, fontSize = 14.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }

                        IconButton(onClick = { tempSteps.remove(step) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.LightGray)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = menuName,
            onValueChange = { menuName = it },
            label = { Text(text = stringResource(id = R.string.hint_menu_name)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val updatedMenu = TrainingMenu(name = menuName, steps = tempSteps.toList())
                if (editMenuIndex == -1) {
                    allSavedMenus.add(updatedMenu)
                } else {
                    allSavedMenus[editMenuIndex] = updatedMenu
                }
                onSaveFinished()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = if (editMenuIndex == -1) R.string.btn_save_menu else R.string.btn_overwrite_menu))
        }
    }
}

@Composable
fun TimeDrumPicker(
    initialMinutes: Int,
    initialSeconds: Int,
    onTimeSelected: (minutes: Int, seconds: Int) -> Unit
) {
    var minutes by rememberSaveable { mutableIntStateOf(initialMinutes) }
    var seconds by rememberSaveable { mutableIntStateOf(initialSeconds) }

    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically // 【重要】これで縦位置が揃います
    ) {
        // 分エリア（ドラム＋文字）
        Row(verticalAlignment = Alignment.CenterVertically) {
            DigitWheel(0..60, minutes) {
                minutes = it
                onTimeSelected(minutes, seconds)
            }
            Text("分", color = Color.White, modifier = Modifier.padding(start = 4.dp))
        }

        // 秒エリア（ドラム＋文字）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            DigitWheel(0..59, seconds) {
                seconds = it
                onTimeSelected(minutes, seconds)
            }
            Text("秒", color = Color.White, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
@Composable
fun RpmDrumPicker(
    initialRpm: Int,
    onRpmSelected: (Int) -> Unit
) {
    // 桁ごとの状態（最初は初期値から計算）
    var hundreds by rememberSaveable { mutableIntStateOf((initialRpm / 100) % 10) }
    var tens by rememberSaveable { mutableIntStateOf((initialRpm / 10) % 10) }
    var ones by rememberSaveable { mutableIntStateOf(initialRpm % 10) }

    // 値が変更されたら親に通知
    LaunchedEffect(hundreds, tens, ones) {
        val newRpm = (hundreds * 100) + (tens * 10) + ones
        // 30未満にならないように制限
        if (newRpm >= 30) onRpmSelected(newRpm)
    }

    Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.Center) {
        // 百の位 (0-2)
        DigitWheel(range = 0..2, value = hundreds) { hundreds = it }
        // 十の位 (0-9)
        DigitWheel(range = 0..9, value = tens) { tens = it }
        // 一の位 (0-9)
        DigitWheel(range = 0..9, value = ones) { ones = it }
    }
}

@Composable
fun DigitWheel(range: IntRange, value: Int, modifier: Modifier = Modifier, onValueChange: (Int) -> Unit) {
    // 1. スクロール状態を管理する state を作成
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = value)
    // 2. スナップ（ピタッと止まる）動作を定義
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            onValueChange(range.toList()[centerIndex])
        }
    }
    // LazyColumn を使ってドラムのようにスクロール
    LazyColumn(
        state = listState, // 3. state を紐付け
        flingBehavior = flingBehavior, // 4. ここに追加
        modifier = modifier.height(150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 50.dp) // 中央に合わせるパディング
    ) {
        items(range.toList()) { digit ->
            val isSelected = (digit == value)
            Text(
                text = digit.toString(),
                fontSize = if (isSelected) 32.sp else 20.sp,
                color = if (isSelected) Color.White else Color.Gray,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { onValueChange(digit) }
            )
        }
    }
}
@Composable
fun PresetEditScreen(presetNames: SnapshotStateList<String>) {
    var newPreset by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(DarkBackgroundColor).padding(16.dp)) {
        Text(text = stringResource(id = R.string.title_preset_edit), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkTextColor)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPreset,
                onValueChange = { newPreset = it },
                label = { Text(text = stringResource(id = R.string.hint_new_preset_name)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { if(newPreset.isNotBlank()){ presetNames.add(newPreset); newPreset="" } },
                modifier = Modifier.height(56.dp)
            ) {
                Text(text = stringResource(id = R.string.btn_add))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(presetNames) { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(name, modifier = Modifier.weight(1f), fontSize = 18.sp, color = Color.White)
                    IconButton(onClick = { presetNames.remove(name) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun RunningScreen(steps: List<TimerStep>, onFinish: () -> Unit) {
    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var remainingTime by rememberSaveable { mutableIntStateOf(steps.getOrNull(0)?.durationSeconds ?: 0) }
    var isRunning by rememberSaveable { mutableStateOf(true) }
    var startDelay by rememberSaveable { mutableIntStateOf(5) }
    var isStarting by rememberSaveable { mutableStateOf(true) }
    var isFinished by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val totalMenuSeconds = rememberSaveable(steps) { steps.sumOf { it.durationSeconds } }
    val secondsPassed = rememberSaveable(currentStepIndex, remainingTime, isStarting) {
        if (isStarting) 0 else {
            steps.take(currentStepIndex).sumOf { it.durationSeconds } +
                    ((steps.getOrNull(currentStepIndex)?.durationSeconds ?: 0) - remainingTime)
        }
    }
    val totalRemainingSeconds = totalMenuSeconds - secondsPassed

    val navigateToNextStep = {
        if (currentStepIndex < steps.size - 1) {
            currentStepIndex++
            remainingTime = steps[currentStepIndex].durationSeconds // 必ずここで代入する
        } else {
            isFinished = true
            remainingTime = 0 // 安全のため0にする
        }
    }

// 1. 状態管理変数
    var hasFinishedTriggered by rememberSaveable { mutableStateOf(false) }
    var isFirstComposition by rememberSaveable { mutableStateOf(true) } // 回転時の誤爆防止
    var lastProcessedIndex by rememberSaveable { mutableIntStateOf(-1) }

// 2. 終了画面処理
    if (isFinished) {
        LaunchedEffect(Unit) {
            if (!hasFinishedTriggered) {
                triggerStepVibration(context, isEnd = true)
                playTone("switch")
                hasFinishedTriggered = true
            }
        }
        FinishedScreen(onDone = onFinish)
        return
    }

// 3. カウントダウン通知（1-5秒）
    LaunchedEffect(remainingTime) {
        if (!isRunning || isStarting || isFinished) return@LaunchedEffect
        if (remainingTime in 1..3) {
            triggerStepVibration(context, isEnd = false)
            playTone("countdown")
        }
    }

    // 4. ステップ切り替え時の通知
    LaunchedEffect(currentStepIndex) {
        // 初回ロード時はスキップして初期化だけ行う
        if (isFirstComposition) {
            isFirstComposition = false
            lastProcessedIndex = currentStepIndex
            return@LaunchedEffect
        }
        // 停止中・準備中は鳴らさない
        if (!isRunning || isStarting) return@LaunchedEffect

        if (currentStepIndex != lastProcessedIndex) {
            triggerStepVibration(context, isEnd = true)
            playTone("switch")
            lastProcessedIndex = currentStepIndex
        }
    }

// 5. タイマーのメインループ（時間減算のみに集中）
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L.milliseconds)
            if (!isRunning) continue

            if (isStarting) {
                if (startDelay > 0) {
                    startDelay--
                } else {
                    isStarting = false
                    // 【重要】ここで -1 ではなく 0 をセットする！
                    // これにより、最初のステップ開始時の「0 != -1」という不一致が起きず、
                    // 余計な振動が鳴りません。
                    lastProcessedIndex = 0
                    remainingTime = steps[0].durationSeconds
                }
            } else if (!isFinished) {
                if (remainingTime > 1) {
                    remainingTime--
                } else {
                    remainingTime = 0
                    navigateToNextStep()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // 1. 上部：全体の進捗ヘッダー（💡開始前も隠し要素として配置して高さを完全に一致させる）
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!isStarting) {
                    TotalProgressHeader(totalRemainingSeconds, totalMenuSeconds)
                } else {
                    // 開始前（準備中）のときは透明にして高さを確保
                    Box(modifier = Modifier.alpha(0f)) {
                        TotalProgressHeader(totalMenuSeconds, totalMenuSeconds)
                    }
                }
            }

            // 2. 現在のステップ（💡ここも開始前と開始後で枠のサイズを完全に同じに維持する）
            Box(
                modifier = Modifier.fillMaxWidth().height(260.dp), // 260dpに固定
                contentAlignment = Alignment.Center
            ) {
                if (isStarting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(id = R.string.label_preparing), fontSize = 24.sp, color = Color.Gray)
                        Text("$startDelay", fontSize = 120.sp, fontWeight = FontWeight.Black, color = if (isRunning) Color.Red else Color.Gray)
                    }
                } else {
                    MainStepContent(steps.getOrNull(currentStepIndex), remainingTime, isRunning)
                }
            }

            // 3. 中間点：一時停止ボタンと大きくなった早送りボタンの並び
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 💡早送りボタンと同じサイズ（64dp）のダミー幅を左側に置いて一時停止を完全中央に
                Spacer(modifier = Modifier.size(64.dp))

                Spacer(modifier = Modifier.width(16.dp))

                // [一時停止ボタン]（サイズは従来のまま80dp）
                PauseButton(isRunning = isRunning, onClick = { isRunning = !isRunning })

                Spacer(modifier = Modifier.width(16.dp))

                // [次のステップに進むボタン]（💡一回り大きく64dpに変更）
                if (!isStarting) {
                    FilledIconButton(
                        onClick = { navigateToNextStep() },
                        modifier = Modifier.size(64.dp), // 48dpから64dpに拡大
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = DarkSurfaceColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text("▶▶", fontSize = 18.sp, fontWeight = FontWeight.Bold) // 文字も少し大きく
                    }
                } else {
                    // 開始前は、一時停止の配置を狂わせないために透明な状態でスペースだけキープ
                    Spacer(modifier = Modifier.size(64.dp))
                }
            }

            // 4. 次のステップ（💡開始前も高さをキープして、開始した瞬間に位置がズレるのを防ぐ）
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp), // 160dpに固定
                contentAlignment = Alignment.Center
            ) {
                NextStepsPreview(steps = steps, currentIndex = currentStepIndex, isVisible = !isStarting)
            }
        }
    }
}

@Composable
fun FinishedScreen(onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DarkBackgroundColor), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(id = R.string.finished_message), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color(0xFF81C784))
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth(0.6f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)) {
                Text(text = stringResource(id = R.string.btn_back), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun PauseButton(isRunning: Boolean, onClick: () -> Unit) {
    val containerColor = if (isRunning) DarkSurfaceColor else Color(0xFFE57373)
    val icon = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = onClick, modifier = Modifier.size(80.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = containerColor), contentPadding = PaddingValues(0.dp)) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.White)
        }
    }
}

@Composable
fun NextStepsPreview(steps: List<TimerStep>, currentIndex: Int, isVisible: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (isVisible) {
            val next1 = steps.getOrNull(currentIndex + 1)
            if (next1 != null) NextStepRow(step = next1, label = stringResource(id = R.string.label_next)) else Spacer(modifier = Modifier.height(68.dp))
            val next2 = steps.getOrNull(currentIndex + 2)
            if (next2 != null) NextStepRow(step = next2, label = stringResource(id = R.string.label_after_that) ) else Spacer(modifier = Modifier.height(68.dp))
        } else {
            Spacer(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun NextStepRow(step: TimerStep, label: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = step.color.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ${step.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f))
            val minutes = step.durationSeconds / 60
            val seconds = step.durationSeconds % 60
            Text(text = stringResource(id = R.string.label_tempo_and_seconds, step.tempo, minutes, seconds), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun TotalProgressHeader(remaining: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val progress =
            if (total > 0) {
                (total - remaining).toFloat() / total.toFloat()
            } else {
                0f
            }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = Color.Cyan,
            trackColor = DarkSurfaceColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(id = R.string.label_total_remaining), fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60), fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(String.format(Locale.US, " / %02d:%02d", total / 60, total % 60), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun MainStepContent(step: TimerStep?, remainingTime: Int, isRunning: Boolean) {
    if (step == null) return
    var isPulse by remember { mutableStateOf(false) }
    val tempoInterval = remember(step.tempo) { if (step.tempo > 0) (60000 / step.tempo).toLong() else 0L }

    val currentRemainingTime by rememberUpdatedState(remainingTime)
    if (isRunning && tempoInterval > 0L) {
        LaunchedEffect(step.tempo, isRunning) {
            val toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            try {
                while (isActive) {
                    if (!isRunning || currentRemainingTime <= 3) {
                        delay(100L.milliseconds)
                        continue
                    }
                    isPulse = true
                    toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 15)

                    delay(60L.milliseconds)
                    isPulse = false

                    delay(maxOf(tempoInterval - 60L, 1L).milliseconds)
                }
            } finally {
                toneG.release()
            }
        }
    }

    val baseColor = step.color
    val animatedColor by animateColorAsState(
        targetValue = if (isPulse) baseColor.copy(alpha = 1.0f) else baseColor.copy(alpha = 0.4f),
        animationSpec = tween(durationMillis = 50, easing = LinearEasing),
        label = "Pulse"
    )
    val contentColor = if (baseColor.luminance() > 0.5f) Color.Black else Color.White

    Card(
        modifier = Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = animatedColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(step.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = contentColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text("${step.tempo} RPM", fontSize = 42.sp, fontWeight = FontWeight.Black, color = contentColor)
            Spacer(modifier = Modifier.height(8.dp))
            val minutes = step.durationSeconds / 60
            val seconds = step.durationSeconds % 60
            val remainingTimeMinutes = remainingTime / 60
            val remainingTimeSeconds = remainingTime % 60
            var dynamicFontSize by remember { mutableStateOf(42.sp) }
            Text(text = String.format("%02d:%02d / %02d:%02d", remainingTimeMinutes, remainingTimeSeconds, minutes, seconds),
                fontSize = dynamicFontSize,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    // もし横幅が足りていない（didOverflowWidth）なら、サイズを少し下げる
                    if (result.didOverflowWidth && dynamicFontSize > 24.sp) {
                        dynamicFontSize *= 0.9f
                    }
                }
            )
        }
    }
}

@Composable
fun MenuManageScreen(menu: TrainingMenu, onNavigateToRunning: () -> Unit, onNavigateToEdit: () -> Unit, onDeleteMenu: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val gson = remember { Gson() }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackgroundColor).padding(16.dp)) {
        Text(menu.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DarkTextColor)
        Spacer(modifier = Modifier.height(16.dp))

        // ステップ一覧（ここは変更なし）
        if (menu.steps.isEmpty()) {
            Text(text = stringResource(id = R.string.msg_no_steps), color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(menu.steps) { step ->
                    Card(
                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = step.color.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(step.color, shape = CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(step.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.White)
                            val minutes = step.durationSeconds / 60
                            val seconds = step.durationSeconds % 60
                            Text(text = stringResource(id = R.string.label_step_info, minutes, seconds, step.tempo), fontSize = 14.sp, color = Color.LightGray)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 1. 【開始ボタン】 最優先なので一番上に配置。高さを倍（112dp）にして押しやすく
        Button(
            onClick = onNavigateToRunning,
            modifier = Modifier.fillMaxWidth().height(112.dp),
            enabled = menu.steps.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(text = stringResource(id = R.string.btn_start_training_long), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 【編集・削除ボタン】 管理用なので横並びで配置
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onNavigateToEdit,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)
            ) {
                Text(text = stringResource(id = R.string.btn_edit), color = Color.White)
            }
            Button(
                onClick = onDeleteMenu,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) {
                Text(text = stringResource(id = R.string.btn_delete), color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 【シェアボタン】 優先度を下げて下に配置（サイズは標準）
        Button(
            onClick = {
                val jsonText = gson.toJson(menu)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("IntervalTimerSingleMenu", jsonText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.msg_copied, menu.name), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text(text = stringResource(id = R.string.btn_share_menu), color = Color.White)
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.btn_back), color = Color.Gray)
        }
    }
}
@Composable
fun MenuListScreen(menus: SnapshotStateList<TrainingMenu>, onSelectMenu: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val gson = remember { Gson() }

    Column(modifier = Modifier.fillMaxSize().background(DarkBackgroundColor).padding(16.dp)) {
        Text(text = stringResource(id = R.string.title_select_menu), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DarkTextColor)
        Spacer(modifier = Modifier.height(8.dp))

        val invalidDataMessage = stringResource(R.string.err_invalid_data)
        val errorFormatMessage = stringResource(R.string.err_format_error)
        val errClipboardEmptyMessage = stringResource(R.string.err_clipboard_empty)

        Button(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipItem = clipboard.primaryClip?.getItemAt(0)
                val jsonText = clipItem?.text?.toString()
                if (!jsonText.isNullOrBlank()) {
                    try {
                        val importedMenu = gson.fromJson(jsonText, TrainingMenu::class.java)
                        if (importedMenu != null && importedMenu.name.isNotBlank()) {
                            menus.add(importedMenu)
                            Toast.makeText(context, context.getString(R.string.msg_imported, importedMenu.name), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, invalidDataMessage, Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(context, errorFormatMessage, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, errClipboardEmptyMessage, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))
        ) {
            Text(text = stringResource(id = R.string.btn_import_clipboard), color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(menus) { index, menu ->
                Card(
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth().clickable { onSelectMenu(index) },
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(menu.name, fontSize = 20.sp, modifier = Modifier.weight(1f), color = Color.White)
                        Text(text = stringResource(R.string.label_steps_count, menu.steps.size), fontSize = 14.sp, color = Color.Gray)
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)
        ) {
            Text(text = stringResource(id = R.string.btn_back), color = Color.White)
        }
    }
}

fun triggerStepVibration(context: Context, isEnd: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (isEnd) {
            // 終了時：リズムをつけて2回鳴らす（インパクト大）
            // [無音, 振動, 無音, 振動]
            val timings = longArrayOf(0, 300, 100, 300, 100, 300)
            val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            // カウントダウン時：短く1回
            vibrator.vibrate(VibrationEffect.createOneShot(100L, 255))
        }
    } else {
        // API 26未満（古い端末）
        @Suppress("DEPRECATION")
        if (isEnd) {
            vibrator.vibrate(longArrayOf(0, 300, 100, 300, 100, 300), -1)
        } else {
            vibrator.vibrate(100L)
        }
    }
}

// 別途、音だけを鳴らす関数を作っておくと管理が楽です
suspend fun playTone(type: String) {
    withContext(Dispatchers.IO) {
        val toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        try {
            if (type == "countdown") {
                toneG.startTone(ToneGenerator.TONE_DTMF_0, 80)
            } else if (type == "switch") {
                repeat(3) {
                    toneG.startTone(ToneGenerator.TONE_DTMF_0, 80)
                    delay(120.milliseconds)
                }
            }
        } finally {
            toneG.release()
        }
    }
}