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
import android.content.res.Configuration
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.alpha
import androidx.core.content.edit
import java.util.Locale
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

object Dimens {
    val Zero = 0.dp
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 24.dp
    val XLarge = 36.dp
    val XXLarge = 48.dp
    val XXXLarge = 60.dp
    val reel150 = 150.dp
    val WidthMax = 480.dp
    val PlayCircle = 80.dp
    val SkipCircle = 64.dp
    val Icon = 40.dp
    val StartTraining = 120.dp
    val ColorSelected = 44.dp
    val ColorNotSelected = 38.dp
    val ColorCheck = 38.dp
    val ColorBorder = 3.dp
}

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
                        listOf(
                            "ウォームアップ",
                            "メイン",
                            "ファスト",
                            "トルク",
                            "レスト",
                            "クールダウン"
                        )
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
                    sharedPreferences.edit { putString("saved_menus", json) }
                }

                LaunchedEffect(presetNames.toList()) {
                    val json = gson.toJson(presetNames.toList())
                    sharedPreferences.edit { putString("preset_names", json) }
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
                                val index =
                                    backStackEntry.arguments?.getString("menuIndex")?.toIntOrNull()
                                        ?: 0
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
                                val index =
                                    backStackEntry.arguments?.getString("menuIndex")?.toIntOrNull()
                                        ?: 0
                                RunningScreen(
                                    steps = allSavedMenus[index].steps,
                                    onFinish = { navController.popBackStack() }
                                )
                            }
                            composable("add_menu/{menuIndex}") { backStackEntry ->
                                val menuIndex =
                                    backStackEntry.arguments?.getString("menuIndex")?.toIntOrNull()
                                        ?: -1
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
fun TopScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToRunning: () -> Unit,
    onNavigateToEdit: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier
                    .widthIn(max = Dimens.WidthMax)
                    .fillMaxWidth()
                    .background(DarkBackgroundColor)
                    .padding(Dimens.Medium)
            ) {
                Text(
                    text = stringResource(id = R.string.title_interval_timer),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextColor
                )
                Spacer(modifier = Modifier.height(Dimens.Medium))
                TopScreenButton(textRes = R.string.btn_create_menu, onNavigateToAdd)
                Spacer(modifier = Modifier.height(Dimens.Medium))
                TopScreenButton(textRes = R.string.btn_start_training, onNavigateToRunning)
                Spacer(modifier = Modifier.height(Dimens.Medium))
                TopScreenButton(textRes = R.string.btn_edit_presets, onNavigateToEdit)
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = Dimens.WidthMax)
                    .fillMaxWidth()
                    .background(DarkBackgroundColor)
                    .padding(Dimens.Medium)
            ) {
                Text(
                    text = stringResource(id = R.string.title_interval_timer),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextColor
                )
                Spacer(modifier = Modifier.height(Dimens.Medium))
                TopScreenButton(textRes = R.string.btn_create_menu, onNavigateToAdd)
                Spacer(modifier = Modifier.height(Dimens.Medium))
                TopScreenButton(textRes = R.string.btn_start_training, onNavigateToRunning)
                Spacer(modifier = Modifier.height(Dimens.Medium))
                TopScreenButton(textRes = R.string.btn_edit_presets, onNavigateToEdit)
            }
        }
    }
}


@Composable
fun TopScreenButton(textRes: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.XXXLarge),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Text(text = stringResource(id = textRes), fontSize = 28.sp)
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

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackgroundColor)
                .padding(horizontal = Dimens.Large),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .background(DarkBackgroundColor)
                    .padding(horizontal = Dimens.Medium),
            ) {
                Text(
                    text = stringResource(id = if (editMenuIndex == -1) R.string.title_create_menu else R.string.title_edit_menu),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextColor
                )
                Spacer(modifier = Modifier.height(Dimens.Medium))
                //ステップ名
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
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White
                                )
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

                Spacer(modifier = Modifier.height(Dimens.Medium))
                //カラーラベル
                Text(
                    text = stringResource(id = R.string.label_color),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.Tiny),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Medium)
                ) {
                    colorOptions.forEachIndexed { index, color ->
                        val isSelected = (selectedIndex == index) // インデックスで比較

                        Box(
                            modifier = Modifier
                                .size(if (isSelected) Dimens.ColorSelected else Dimens.ColorNotSelected)
                                .background(color, shape = CircleShape)
                                .border(
                                    width = if (isSelected) Dimens.ColorBorder else Dimens.Zero,
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
                                    modifier = Modifier.size(Dimens.ColorCheck)
                                )
                            }
                        }
                    }
                }
                //時間とRPMは横並び
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Small)
                ) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.XXXLarge),
                        shape = OutlinedTextFieldDefaults.shape
                    ) {
                        Text(text = "${inputMinutes}分 ${inputSeconds}秒", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { showTempoPicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.XXXLarge),
                        shape = OutlinedTextFieldDefaults.shape
                    ) {
                        Text(text = "$inputTempo RPM", color = Color.White)
                    }
                }
//時間
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
//RPM
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
                Spacer(modifier = Modifier.height(Dimens.Medium))
//ステップ追加ボタン
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.XXXLarge)
                ) {
                    Text(text = stringResource(id = R.string.btn_add_step))
                }
                Spacer(modifier = Modifier.height(Dimens.Medium))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackgroundColor)
                    .padding(horizontal = Dimens.Medium),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .background(DarkBackgroundColor)
                        .padding(horizontal = Dimens.Medium),
                ) {
                    itemsIndexed(tempSteps) { index, step ->
                        Card(
                            modifier = Modifier
                                .padding(vertical = Dimens.Tiny)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = step.color.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(Dimens.Small), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(Dimens.Medium)
                                        .background(step.color, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(Dimens.Medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    val minutes = step.durationSeconds / 60
                                    val seconds = step.durationSeconds % 60
                                    Text(
                                        step.name,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = String.format(
                                            "%02d:%02d",
                                            minutes,
                                            seconds
                                        ) + " / ${step.tempo}RPM",
                                        fontSize = 14.sp,
                                        color = Color.LightGray
                                    )
                                }

                                if (index > 0) {
                                    IconButton(onClick = {
                                        val item = tempSteps.removeAt(index)
                                        tempSteps.add(index - 1, item)
                                    }) {
                                        Text("▲", color = Color.LightGray, fontSize = 14.sp)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(Dimens.XXLarge))
                                }

                                if (index < tempSteps.lastIndex) {
                                    IconButton(onClick = {
                                        val item = tempSteps.removeAt(index)
                                        tempSteps.add(index + 1, item)
                                    }) {
                                        Text("▼", color = Color.LightGray, fontSize = 14.sp)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(Dimens.XXLarge))
                                }

                                IconButton(onClick = { tempSteps.remove(step) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
//トレーニングメニュー名
                OutlinedTextField(
                    value = menuName,
                    onValueChange = { menuName = it },
                    label = { Text(text = stringResource(id = R.string.hint_menu_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(Dimens.Medium))
//保存ボタン
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.XXXLarge)
                ) {
                    Text(text = stringResource(id = if (editMenuIndex == -1) R.string.btn_save_menu else R.string.btn_overwrite_menu))
                }
                Spacer(modifier = Modifier.height(Dimens.Medium))
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackgroundColor)
                .padding(Dimens.Medium)
        ) {
            Text(
                text = stringResource(id = if (editMenuIndex == -1) R.string.title_create_menu else R.string.title_edit_menu),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextColor
            )
            Spacer(modifier = Modifier.height(Dimens.Medium))

            //ステップ名
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
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color.White
                            )
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

            Spacer(modifier = Modifier.height(Dimens.Medium))

            //カラーラベル
            Text(
                text = stringResource(id = R.string.label_color),
                fontSize = 14.sp,
                color = Color.Gray
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.Tiny),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Medium)
            ) {
                colorOptions.forEachIndexed { index, color ->
                    val isSelected = (selectedIndex == index) // インデックスで比較

                    Box(
                        modifier = Modifier
                            .size(if (isSelected) Dimens.ColorSelected else Dimens.ColorNotSelected)
                            .background(color, shape = CircleShape)
                            .border(
                                width = if (isSelected) Dimens.ColorNotSelected else Dimens.Zero,
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
                                modifier = Modifier.size(Dimens.ColorCheck)
                            )
                        }
                    }
                }
            }

//時間とRPMは横並び
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Small)
            ) {
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.XXXLarge),
                    shape = OutlinedTextFieldDefaults.shape
                ) {
                    Text(text = "${inputMinutes}分 ${inputSeconds}秒", color = Color.White)
                }
                OutlinedButton(
                    onClick = { showTempoPicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.XXXLarge),
                    shape = OutlinedTextFieldDefaults.shape
                ) {
                    Text(text = "$inputTempo RPM", color = Color.White)
                }
            }
//時間
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
//RPM
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
//ステップ追加ボタン
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
                modifier = Modifier
                    .padding(top = Dimens.Small)
                    .fillMaxWidth()
                    .height(Dimens.XXXLarge)
            ) {
                Text(text = stringResource(id = R.string.btn_add_step))
            }

            LazyColumn(modifier = Modifier
                .weight(1f)
                .padding(vertical = Dimens.Small)) {
                itemsIndexed(tempSteps) { index, step ->
                    Card(
                        modifier = Modifier
                            .padding(vertical = Dimens.Tiny)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = step.color.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(Dimens.Small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(Dimens.Medium)
                                    .background(step.color, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(Dimens.Medium))
                            Column(modifier = Modifier.weight(1f)) {
                                val minutes = step.durationSeconds / 60
                                val seconds = step.durationSeconds % 60
                                Text(step.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = String.format(
                                        "%02d:%02d",
                                        minutes,
                                        seconds
                                    ) + " / ${step.tempo}RPM",
                                    fontSize = 14.sp,
                                    color = Color.LightGray
                                )
                            }

                            if (index > 0) {
                                IconButton(onClick = {
                                    val item = tempSteps.removeAt(index)
                                    tempSteps.add(index - 1, item)
                                }) {
                                    Text("▲", color = Color.LightGray, fontSize = 14.sp)
                                }
                            } else {
                                Spacer(modifier = Modifier.size(Dimens.XXLarge))
                            }

                            if (index < tempSteps.lastIndex) {
                                IconButton(onClick = {
                                    val item = tempSteps.removeAt(index)
                                    tempSteps.add(index + 1, item)
                                }) {
                                    Text("▼", color = Color.LightGray, fontSize = 14.sp)
                                }
                            } else {
                                Spacer(modifier = Modifier.size(Dimens.XXLarge))
                            }

                            IconButton(onClick = { tempSteps.remove(step) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
//トレーニングメニュー名
            OutlinedTextField(
                value = menuName,
                onValueChange = { menuName = it },
                label = { Text(text = stringResource(id = R.string.hint_menu_name)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(Dimens.Medium))
//保存ボタン
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.XXXLarge)
            ) {
                Text(text = stringResource(id = if (editMenuIndex == -1) R.string.btn_save_menu else R.string.btn_overwrite_menu))
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.reel150),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically // 【重要】これで縦位置が揃います
    ) {
        // 分エリア（ドラム＋文字）
        Row(verticalAlignment = Alignment.CenterVertically) {
            DigitWheel(0..60, minutes) {
                minutes = it
                onTimeSelected(minutes, seconds)
            }
            Text("分", color = Color.White, modifier = Modifier.padding(start = Dimens.Tiny))
        }

        // 秒エリア（ドラム＋文字）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Dimens.Medium)
        ) {
            DigitWheel(0..59, seconds) {
                seconds = it
                onTimeSelected(minutes, seconds)
            }
            Text("秒", color = Color.White, modifier = Modifier.padding(start = Dimens.Tiny))
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.reel150),
        horizontalArrangement = Arrangement.Center
    ) {
        // 百の位 (0-2)
        DigitWheel(range = 0..2, value = hundreds) { hundreds = it }
        // 十の位 (0-9)
        DigitWheel(range = 0..9, value = tens) { tens = it }
        // 一の位 (0-9)
        DigitWheel(range = 0..9, value = ones) { ones = it }
    }
}

@Composable
fun DigitWheel(
    range: IntRange,
    value: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
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
        modifier = modifier.height(Dimens.reel150),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = Dimens.XXLarge) // 中央に合わせるパディング
    ) {
        items(range.toList()) { digit ->
            val isSelected = (digit == value)
            Text(
                text = digit.toString(),
                fontSize = if (isSelected) 32.sp else 20.sp,
                color = if (isSelected) Color.White else Color.Gray,
                modifier = Modifier
                    .padding(Dimens.Small)
                    .clickable { onValueChange(digit) }
            )
        }
    }
}

@Composable
fun PresetEditScreen(presetNames: SnapshotStateList<String>) {
    var newPreset by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier
        .fillMaxSize()
        .background(DarkBackgroundColor)
        .padding(Dimens.Medium)) {
        Text(
            text = stringResource(id = R.string.title_preset_edit),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DarkTextColor
        )
        Spacer(modifier = Modifier.height(Dimens.Medium))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPreset,
                onValueChange = { newPreset = it },
                label = { Text(text = stringResource(id = R.string.hint_new_preset_name)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(Dimens.Medium))
            Button(
                onClick = {
                    if (newPreset.isNotBlank()) {
                        presetNames.add(newPreset); newPreset = ""
                    }
                },
                modifier = Modifier.height(Dimens.XXXLarge)
            ) {
                Text(text = stringResource(id = R.string.btn_add))
            }
        }
        Spacer(modifier = Modifier.height(Dimens.Medium))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(presetNames) { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.Tiny)
                ) {
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        fontSize = 18.sp,
                        color = Color.White
                    )
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
    var remainingTime by rememberSaveable {
        mutableIntStateOf(
            steps.getOrNull(0)?.durationSeconds ?: 0
        )
    }
    var isRunning by rememberSaveable { mutableStateOf(true) }
    var startDelay by rememberSaveable { mutableIntStateOf(5) }
    var isStarting by rememberSaveable { mutableStateOf(true) }
    var isFinished by rememberSaveable { mutableStateOf(false) }
    val toneG = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
    suspend fun playTone(type: String) {
        if (type == "countdown") {
            toneG.startTone(ToneGenerator.TONE_DTMF_0, 100)
            delay(120.milliseconds)
        } else if (type == "switch") {
            repeat(3) {
                toneG.startTone(ToneGenerator.TONE_DTMF_0, 80)
                delay(120.milliseconds)
            }
        }
    }

    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            toneG.release()
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

// 3. カウントダウン通知（1-3秒）
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
        val toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        while (isActive) {
            delay(1000L.milliseconds)
            if (!isRunning) continue

            if (isStarting) {
                if (startDelay > 1) {
                    toneG.startTone(ToneGenerator.TONE_DTMF_0, 100)
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

//ここから表示
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackgroundColor)
                .padding(horizontal = Dimens.Large),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .background(DarkBackgroundColor)
                    .padding(horizontal = Dimens.Medium),
            ) {

            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProgressBar(totalRemainingSeconds, totalMenuSeconds)
                Spacer(modifier = Modifier.height(Dimens.Medium))
                RemainingTime(totalRemainingSeconds, totalMenuSeconds)
                Spacer(modifier = Modifier.height(Dimens.Medium))

                val step: TimerStep = steps.getOrNull(currentStepIndex) ?: return
                var isPulse by remember { mutableStateOf(false) }
                val tempoInterval =
                    remember(step.tempo) { if (step.tempo > 0) (60000 / step.tempo).toLong() else 0L }
                val currentRemainingTime by rememberUpdatedState(remainingTime)
                if (isRunning && tempoInterval > 0L) {
                    LaunchedEffect(step.tempo, true) {
                        val toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                        try {
                            var nextTick = System.currentTimeMillis()
                            while (isActive) {
                                if (!isRunning || currentRemainingTime <= 3) {
                                    delay(100L.milliseconds)
                                    nextTick = System.currentTimeMillis()
                                    continue
                                }
                                nextTick += tempoInterval
                                isPulse = true
                                toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                                delay(60L.milliseconds)
                                isPulse = false
                                val wait = nextTick - System.currentTimeMillis()
                                if (wait > 0) {
                                    delay(wait.milliseconds)
                                }
                            }
                        } finally {
                            toneG.release()
                        }
                    }
                }
                val baseColor = step.color
                val animatedColor by animateColorAsState(
                    targetValue = if (isPulse) baseColor.copy(alpha = 1.0f) else baseColor.copy(
                        alpha = 0.4f
                    ),
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing),
                    label = "Pulse"
                )
                val contentColor = if (baseColor.luminance() > 0.5f) Color.Black else Color.White
                Card(
                    modifier = Modifier,
                    colors = CardDefaults.cardColors(containerColor = animatedColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimens.Small)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimens.Medium, horizontal = Dimens.Small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            step.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.height(Dimens.Medium))
                        Text(
                            "${step.tempo} RPM",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.height(Dimens.Medium))
                        val minutes = step.durationSeconds / 60
                        val seconds = step.durationSeconds % 60
                        val remainingTimeMinutes = remainingTime / 60
                        val remainingTimeSeconds = remainingTime % 60
                        var dynamicFontSize by remember { mutableStateOf(42.sp) }
                        Text(
                            text = String.format(
                                Locale.US,
                                "%02d:%02d / %02d:%02d",
                                remainingTimeMinutes,
                                remainingTimeSeconds,
                                minutes,
                                seconds
                            ),
                            fontSize = dynamicFontSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            onTextLayout = { result ->
                                if (result.didOverflowWidth && dynamicFontSize > 24.sp) {
                                    dynamicFontSize *= 0.9f
                                }
                            }
                        )
                    }
//            Spacer(modifier = Modifier.size(Dimens.XXXLarge))
//            Spacer(modifier = Modifier.width(Dimens.Medium))

                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val playButtonColor = if (isRunning) DarkSurfaceColor else Color(0xFFE57373)
                    val iconPlay = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow
                    val iconNext = Icons.Filled.SkipNext
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // ダミー
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { isRunning = !isRunning },
                            modifier = Modifier.size(Dimens.PlayCircle),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = playButtonColor),
                            contentPadding = PaddingValues(Dimens.Zero)
                        ) {
                            Icon(
                                imageVector = iconPlay,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.Icon),
                                tint = Color.White
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { navigateToNextStep() },
                            modifier = Modifier
                                .size(Dimens.SkipCircle)
                                .offset(x = (-16).dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor),
                            contentPadding = PaddingValues(Dimens.Zero)
                        ) {
                            Icon(
                                imageVector = iconNext,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.Icon),
                                tint = Color.White
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isStarting) {
                        val next1 = steps.getOrNull(currentStepIndex + 1)
                        if (next1 != null) NextStepRow(
                            step = next1,
                            label = stringResource(id = R.string.label_next)
                        ) else Spacer(modifier = Modifier)
                        val next2 = steps.getOrNull(currentStepIndex + 2)
                        if (next2 != null) NextStepRow(
                            step = next2,
                            label = stringResource(id = R.string.label_after_that)
                        ) else Spacer(modifier = Modifier)
                    } else {
                        Spacer(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
fun FinishedScreen(onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment
                .CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.finished_message),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(Dimens.Medium))
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(Dimens.PlayCircle),
                tint = Color(0xFF81C784)
            )
            Spacer(modifier = Modifier.height(Dimens.XXLarge))
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(Dimens.XXXLarge),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)
            ) {
                Text(
                    text = stringResource(id = R.string.btn_back),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}


@Composable
fun ProgressBar(remaining: Int, total: Int) {
    val progress =
        if (total > 0) {
            (total - remaining).toFloat() / total.toFloat()
        } else {
            0f
        }
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.Medium)
            .clip(CircleShape),
        color = Color.Cyan,
        trackColor = DarkSurfaceColor
    )
}

@Composable
fun RemainingTime(remaining: Int, total: Int) {
    Text(
        text = stringResource(id = R.string.label_total_remaining),
        fontSize = 12.sp,
        color = Color.Gray,
        fontWeight = FontWeight.Bold
    )
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            String.format(Locale.US, "%02d:%02d", remaining / 60, remaining % 60),
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            String.format(Locale.US, " / %02d:%02d", total / 60, total % 60),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = Dimens.Tiny, start = Dimens.Tiny)
        )
    }
}

@Composable
fun NextStepRow(step: TimerStep, label: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.Tiny),
        colors = CardDefaults.cardColors(containerColor = step.color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.Medium, vertical = Dimens.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$label: ${step.name}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f)
            )
            val minutes = step.durationSeconds / 60
            val seconds = step.durationSeconds % 60
            Text(
                text = stringResource(
                    id = R.string.label_tempo_and_seconds,
                    step.tempo,
                    minutes,
                    seconds
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}


@Composable
fun MenuManageScreen(
    menu: TrainingMenu,
    onNavigateToRunning: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onDeleteMenu: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gson = remember { Gson() }
    val toastMessage = stringResource(id = R.string.msg_copied, menu.name)

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackgroundColor)
                .padding(horizontal = Dimens.Large),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .background(DarkBackgroundColor)
                    .padding(horizontal = Dimens.Medium)
            ) {
                Text(
                    menu.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextColor
                )
                Spacer(modifier = Modifier.height(Dimens.Medium))
                if (menu.steps.isEmpty()) {
                    Text(text = stringResource(id = R.string.msg_no_steps), color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(menu.steps) { step ->
                            Card(
                                modifier = Modifier
                                    .padding(vertical = Dimens.Tiny)
                                    .fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = step.color.copy(
                                        alpha = 0.4f
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(Dimens.Medium),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(Dimens.Medium)
                                            .background(step.color, shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(Dimens.Medium))
                                    Text(
                                        step.name,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                    val minutes = step.durationSeconds / 60
                                    val seconds = step.durationSeconds % 60
                                    Text(
                                        text = stringResource(
                                            id = R.string.label_step_info,
                                            minutes,
                                            seconds,
                                            step.tempo
                                        ), fontSize = 16.sp, color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackgroundColor)
                    .padding(horizontal = Dimens.Medium)
            ) {
                Button(
                    onClick = onNavigateToRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.StartTraining),
                    enabled = menu.steps.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_start_training_long),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.Medium))
// 2. 【編集・削除ボタン】 管理用なので横並びで配置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Small)
                ) {
                    Button(
                        onClick = onNavigateToEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.XXXLarge),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)
                    ) {
                        Text(text = stringResource(id = R.string.btn_edit), color = Color.White)
                    }
                    Button(
                        onClick = onDeleteMenu,
                        modifier = Modifier
                            .weight(1f)
                            .height(Dimens.XXXLarge),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text(text = stringResource(id = R.string.btn_delete), color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.Medium))
// 3. 【シェアボタン】 優先度を下げて下に配置（サイズは標準）
                Button(
                    onClick = {
                        val jsonText = gson.toJson(menu)
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("IntervalTimerSingleMenu", jsonText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.XXXLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text(text = stringResource(id = R.string.btn_share_menu), color = Color.White)
                }
                Spacer(modifier = Modifier.height(Dimens.Medium))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.XXXLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)
                ) {
                    Text(text = stringResource(id = R.string.btn_back), color = Color.Gray)
                }
            }
        }
    } else {
        Column(modifier = Modifier
            .fillMaxSize()
            .background(DarkBackgroundColor)
            .padding(Dimens.Medium)) {
            Text(menu.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkTextColor)
            Spacer(modifier = Modifier.height(Dimens.Medium))

            // ステップ一覧（ここは変更なし）
            if (menu.steps.isEmpty()) {
                Text(text = stringResource(id = R.string.msg_no_steps), color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(menu.steps) { step ->
                        Card(
                            modifier = Modifier
                                .padding(vertical = Dimens.Tiny)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = step.color.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(Dimens.Medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(Dimens.Medium)
                                        .background(step.color, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(Dimens.Medium))
                                Text(
                                    step.name,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                val minutes = step.durationSeconds / 60
                                val seconds = step.durationSeconds % 60
                                Text(
                                    text = stringResource(
                                        id = R.string.label_step_info,
                                        minutes,
                                        seconds,
                                        step.tempo
                                    ), fontSize = 14.sp, color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Large))

            // 1. 【開始ボタン】 最優先なので一番上に配置。高さを倍（112dp）にして押しやすく
            Button(
                onClick = onNavigateToRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.StartTraining),
                enabled = menu.steps.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = stringResource(id = R.string.btn_start_training_long),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(Dimens.Medium))

            // 2. 【編集・削除ボタン】 管理用なので横並びで配置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Small)
            ) {
                Button(
                    onClick = onNavigateToEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.XXXLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)
                ) {
                    Text(text = stringResource(id = R.string.btn_edit), color = Color.White)
                }
                Button(
                    onClick = onDeleteMenu,
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.XXXLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text(text = stringResource(id = R.string.btn_delete), color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(Dimens.Medium))

            // 3. 【シェアボタン】 優先度を下げて下に配置（サイズは標準）
            Button(
                onClick = {
                    val jsonText = gson.toJson(menu)
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("IntervalTimerSingleMenu", jsonText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.XXXLarge),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(text = stringResource(id = R.string.btn_share_menu), color = Color.White)
            }
            Spacer(modifier = Modifier.height(Dimens.Medium))
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.XXXLarge),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)
            ) {
                Text(text = stringResource(id = R.string.btn_back), color = Color.Gray)
            }
        }
    }
}

@Composable
fun MenuListScreen(
    menus: SnapshotStateList<TrainingMenu>,
    onSelectMenu: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gson = remember { Gson() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.WidthMax)
                .fillMaxWidth()
                .background(DarkBackgroundColor)
                .padding(Dimens.Medium)
        ) {
            Text(
                text = stringResource(id = R.string.title_select_menu),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextColor
            )
            Spacer(modifier = Modifier.height(Dimens.Medium))

            val invalidDataMessage = stringResource(R.string.err_invalid_data)
            val errorFormatMessage = stringResource(R.string.err_format_error)
            val successMessageBase = stringResource(id = R.string.msg_imported, "")
            val errClipboardEmptyMessage = stringResource(R.string.err_clipboard_empty)

            Button(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipItem = clipboard.primaryClip?.getItemAt(0)
                    val jsonText = clipItem?.text?.toString()
                    if (!jsonText.isNullOrBlank()) {
                        try {
                            val importedMenu = gson.fromJson(jsonText, TrainingMenu::class.java)
                            if (importedMenu != null && importedMenu.name.isNotBlank()) {
                                menus.add(importedMenu)
                                val finalMessage = successMessageBase.format(importedMenu.name)
                                Toast.makeText(context, finalMessage, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, invalidDataMessage, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, errorFormatMessage, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, errClipboardEmptyMessage, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.XXXLarge),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))
            ) {
                Text(text = stringResource(id = R.string.btn_import_clipboard), color = Color.White)
            }

            Spacer(modifier = Modifier.height(Dimens.Medium))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(menus) { index, menu ->
                    Card(
                        modifier = Modifier
                            .padding(vertical = Dimens.Tiny)
                            .fillMaxWidth()
                            .clickable { onSelectMenu(index) },
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(Dimens.Medium), verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                menu.name,
                                fontSize = 20.sp,
                                modifier = Modifier.weight(1f),
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.label_steps_count, menu.steps.size),
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(Dimens.Medium))
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.XXXLarge),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceColor)
            ) {
                Text(text = stringResource(id = R.string.btn_back), color = Color.White)
            }
        }
    }
}

fun triggerStepVibration(context: Context, isEnd: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
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

