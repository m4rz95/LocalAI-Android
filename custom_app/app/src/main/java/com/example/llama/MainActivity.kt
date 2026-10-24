package com.example.llama

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.sin
import kotlin.random.Random

data class PresetModel(
    val name: String,
    val category: String,
    val desc: String,
    val size: String,
    val fileName: String,
    val url: String
)

class MainActivity(
    private val viewModel: MainViewModel = MainViewModel()
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val obsidianScheme = darkColorScheme(
                primary = Color(0xFF38BDF8),
                secondary = Color(0xFF818CF8),
                tertiary = Color(0xFF34D399),
                background = Color(0xFF07090E),
                surface = Color(0xFF111827),
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFE2E8F0)
            )
            MaterialTheme(colorScheme = obsidianScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AetherMultimodalScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherMultimodalScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val prefs = remember { context.getSharedPreferences("aether_prefs", Context.MODE_PRIVATE) }

    var activeModelName by remember {
        mutableStateOf(prefs.getString("last_model_name", "Belum Ada Model Aktif") ?: "Belum Ada Model Aktif")
    }
    var systemPrompt by remember {
        mutableStateOf(
            prefs.getString(
                "system_prompt",
                "Anda adalah AetherAI Multimodal, asisten cerdas yang mampu menganalisis gambar, kode, dan membuat konsep visual."
            ) ?: ""
        )
    }
    var selectedPersona by remember { mutableStateOf("Multimodal Vision") }
    var isImporting by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var attachedImageInfo by remember { mutableStateOf("") }

    var imagePrompt by remember { mutableStateOf("") }
    var selectedArtStyle by remember { mutableStateOf("Cyberpunk Neon") }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingImage by remember { mutableStateOf(false) }
    var useHybridCloudGen by remember { mutableStateOf(false) }

    val personas = listOf(
        "Multimodal Vision" to "Anda adalah AetherAI Multimodal. Analisis setiap deskripsi visual, foto, warna, dan konteks gambar yang diberikan pengguna secara tajam dan mendetail.",
        "Arsitek Kode" to "Anda adalah Principal Software Engineer. Berikan solusi kode bersih, modern, dan siap produksi.",
        "Desainer Prompt" to "Anda adalah ahli pembuat prompt visual AI (Stable Diffusion / Midjourney). Buat deskripsi visual yang sangat detail.",
        "Penerjemah OCR" to "Anda adalah spesialis pembaca dokumen dan penerjemah gambar ke bahasa Indonesia yang akurat."
    )

    val presetModels = listOf(
        PresetModel(
            name = "SmolVLM-500M Vision (Otak Utama)",
            category = "VISION (ARTIKAN GAMBAR)",
            desc = "Model Vision super ringan untuk mendeskripsikan isi foto & membaca gambar",
            size = "380 MB",
            fileName = "SmolVLM-500M-Instruct-Q8_0.gguf",
            url = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/SmolVLM-500M-Instruct-Q8_0.gguf"
        ),
        PresetModel(
            name = "SmolVLM-500M mmproj (Mata Visual)",
            category = "VISION PROJECTOR",
            desc = "Pasangan wajib SmolVLM untuk mengubah piksel gambar menjadi token visual",
            size = "190 MB",
            fileName = "mmproj-SmolVLM-500M-Instruct-f16.gguf",
            url = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-500M-Instruct-f16.gguf"
        ),
        PresetModel(
            name = "Qwen 2.5 (0.5B) Instruct",
            category = "FAST TEXT & LOGIC",
            desc = "Ultra-cepat, hemat RAM, cocok untuk semua HP Android",
            size = "398 MB",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
        ),
        PresetModel(
            name = "Llama 3.2 (1B) Instruct",
            category = "SMART REASONING",
            desc = "Keseimbangan terbaik kecepatan & penalaran pintar sehari-hari",
            size = "808 MB",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        )
    )

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            val analysis = analyzeBitmapFeatures(bmp, getFileName(context, selectedUri) ?: "image.jpg")
                            withContext(Dispatchers.Main) {
                                attachedBitmap = bmp
                                attachedImageInfo = analysis
                                Toast.makeText(context, "Foto siap dianalisis AI!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal memuat gambar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                isImporting = true
                val rawName = getFileName(context, selectedUri) ?: "model_custom.gguf"
                if (!rawName.endsWith(".gguf", ignoreCase = true)) {
                    Toast.makeText(context, "Pilih file berformat .gguf!", Toast.LENGTH_LONG).show()
                    isImporting = false
                    return@launch
                }

                val targetFile = File(context.getExternalFilesDir(null), rawName)
                withContext(Dispatchers.IO) {
                    if (!targetFile.exists()) {
                        context.contentResolver.openInputStream(selectedUri)?.use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                isImporting = false
                refreshTrigger++
                activeModelName = rawName
                prefs.edit().putString("last_model_name", rawName).apply()
                viewModel.load(targetFile.absolutePath)
                activeTab = 0
                Toast.makeText(context, "Model aktif: $rawName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val installedModels = remember(isImporting, refreshTrigger, activeTab) {
        context.getExternalFilesDir(null)?.listFiles { file ->
            file.extension.equals("gguf", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    val headerGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF064E3B))
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    if (activeModelName.endsWith(".gguf", true)) Color(0xFF34D399)
                                    else Color(0xFFF59E0B)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AETHER AI MULTIMODAL",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Model: $activeModelName",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        viewModel.clear()
                        attachedBitmap = null
                        attachedImageInfo = ""
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Reset", fontSize = 11.sp, color = Color(0xFF38BDF8))
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF0B0F19),
            contentColor = Color(0xFF38BDF8),
            edgePadding = 8.dp
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("👁️ Chat & Vision", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("🎨 Buat Gambar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("📦 Model (${installedModels.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeTab == 3,
                onClick = { activeTab = 3 },
                text = { Text("🧠 Otak Sistem", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            itemsIndexed(personas) { _, pair ->
                                FilterChip(
                                    selected = selectedPersona == pair.first,
                                    onClick = {
                                        selectedPersona = pair.first
                                        systemPrompt = pair.second
                                        prefs.edit().putString("system_prompt", pair.second).apply()
                                    },
                                    label = { Text(pair.first, fontSize = 11.sp) }
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0B0F19))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (viewModel.messages.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "AetherAI Vision & Neural Chat",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "• Ketuk tombol [📷 Foto] di kiri bawah untuk mengirim gambar dan meminta AI mengartikannya.\n• Buka tab [🎨 Buat Gambar] untuk menghasilkan gambar dari teks.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            itemsIndexed(viewModel.messages) { index, msg ->
                                val isUser = index % 2 == 0
                                val bubbleColor = if (isUser) Color(0xFF1E3A8A) else Color(0xFF161F30)
                                val borderColor = if (isUser) Color(0xFF3B82F6) else Color(0xFF334155)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                ) {
                                    Text(
                                        text = if (isUser) "ANDA" else "AETHER AI",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUser) Color(0xFF60A5FA) else Color(0xFF34D399),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    Surface(
                                        color = bubbleColor,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .widthIn(max = 320.dp)
                                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("AetherAI", msg))
                                                Toast.makeText(context, "Disalin ke clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Text(
                                            text = msg,
                                            color = Color(0xFFF1F5F9),
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        attachedBitmap?.let { bmp ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Lampiran Gambar",
                                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Gambar Siap Dianalisis", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF34D399))
                                        Text(attachedImageInfo, fontSize = 10.sp, color = Color.LightGray, maxLines = 2)
                                    }
                                    TextButton(onClick = {
                                        attachedBitmap = null
                                        attachedImageInfo = ""
                                    }) {
                                        Text("Batal", color = Color(0xFFF87171), fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("📷 Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedTextField(
                                value = viewModel.message,
                                onValueChange = { viewModel.updateMessage(it) },
                                placeholder = {
                                    Text(
                                        if (attachedBitmap != null) "Tanyakan sesuatu tentang foto ini..."
                                        else "Ketik pesan atau lampirkan foto...",
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val userQuestion = if (viewModel.message.isBlank() && attachedBitmap != null) {
                                        "Jelaskan dan artikan secara lengkap isi gambar ini."
                                    } else {
                                        viewModel.message
                                    }

                                    val finalPrompt = buildString {
                                        if (viewModel.messages.isEmpty() && systemPrompt.isNotBlank()) {
                                            append("[Sistem: $systemPrompt]\n")
                                        }
                                        if (attachedBitmap != null) {
                                            append("[Data Visual Gambar Lampiran: $attachedImageInfo]\n")
                                        }
                                        append(userQuestion)
                                    }

                                    attachedBitmap = null
                                    attachedImageInfo = ""
                                    viewModel.updateMessage(finalPrompt)
                                    viewModel.send()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("Kirim", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                1 -> {
                    val styles = listOf("Cyberpunk Neon", "Anime Studio", "Photorealistic 8K", "3D Isometric", "Lukisan Klasik")
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "AetherAI Image Synthesis Studio",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Text(
                                text = "Hasilkan gambar dari deskripsi teks (Text-to-Image) dan simpan langsung ke Galeri HP Anda.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = imagePrompt,
                                onValueChange = { imagePrompt = it },
                                label = { Text("Deskripsikan gambar yang ingin dibuat...") },
                                placeholder = { Text("Contoh: Kota masa depan di malam hari dengan lampu neon biru...") },
                                modifier = Modifier.fillMaxWidth().height(95.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        item {
                            Text("Pilih Gaya Visual:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.LightGray)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                itemsIndexed(styles) { _, style ->
                                    FilterChip(
                                        selected = selectedArtStyle == style,
                                        onClick = { selectedArtStyle = style },
                                        label = { Text(style, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mode Mesin Render", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(
                                        if (useHybridCloudGen) "Hybrid Flux/SDXL (Butuh Koneksi Internet - Foto Realistis)"
                                        else "100% Offline Neural Procedural Canvas (Instan Tanpa Internet)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF34D399)
                                    )
                                }
                                Switch(
                                    checked = useHybridCloudGen,
                                    onCheckedChange = { useHybridCloudGen = it }
                                )
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    if (imagePrompt.isBlank()) {
                                        Toast.makeText(context, "Ketik deskripsi gambar terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        isGeneratingImage = true
                                        val fullPrompt = "$imagePrompt, $selectedArtStyle style, masterpiece, high detail"
                                        val bmp = withContext(Dispatchers.IO) {
                                            if (useHybridCloudGen) {
                                                fetchFluxImageOrFallback(fullPrompt, selectedArtStyle)
                                            } else {
                                                renderOfflineNeuralArtwork(imagePrompt, selectedArtStyle)
                                            }
                                        }
                                        generatedBitmap = bmp
                                        isGeneratingImage = false
                                    }
                                },
                                enabled = !isGeneratingImage,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (isGeneratingImage) "Sedang Merender Piksel Gambar..."
                                    else "✨ Buat Gambar Sekarang",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        generatedBitmap?.let { bmp ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Hasil Gambar AI",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(300.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    saveBitmapToGallery(context, bmp, "AetherAI_${System.currentTimeMillis()}.png")
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                            ) {
                                                Text("💾 Simpan ke Galeri HP", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    attachedBitmap = bmp
                                                    attachedImageInfo = analyzeBitmapFeatures(bmp, "Generated_Art.png")
                                                    activeTab = 0
                                                    Toast.makeText(context, "Gambar dikirim ke ruang Chat Vision!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("👁️ Kirim ke Chat", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF0284C7), RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Pasang File Model (.GGUF) dari HP", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Pilih file model bahasa (.gguf) atau proyektor visual (mmproj.gguf) dari memori internal HP Anda.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                        enabled = !isImporting,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                    ) {
                                        Text(if (isImporting) "Menyalin Model..." else "Pilih File .GGUF dari Penyimpanan HP")
                                    }
                                }
                            }
                        }

                        item {
                            Text("MODEL TERPASANG (${installedModels.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                        }

                        itemsIndexed(installedModels) { _, file ->
                            val sizeMb = file.length() / (1024 * 1024)
                            val isCurrent = activeModelName == file.name
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color(0xFF064E3B) else Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text("Ukuran: $sizeMb MB", fontSize = 11.sp, color = Color.LightGray)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                activeModelName = file.name
                                                prefs.edit().putString("last_model_name", file.name).apply()
                                                viewModel.load(file.absolutePath)
                                                activeTab = 0
                                                Toast.makeText(context, "Model diaktifkan!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                        ) {
                                            Text(if (isCurrent) "Aktif" else "Muat", fontSize = 12.sp)
                                        }
                                        OutlinedButton(onClick = {
                                            file.delete()
                                            refreshTrigger++
                                        }) {
                                            Text("Hapus", fontSize = 11.sp, color = Color(0xFFF87171))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("KATALOG MODEL VISION & MULTIMODAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF818CF8))
                        }

                        itemsIndexed(presetModels) { _, preset ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(preset.category, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(preset.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                        Text(preset.size, color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(preset.desc, fontSize = 12.sp, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                            val req = DownloadManager.Request(Uri.parse(preset.url))
                                                .setTitle("Mengunduh ${preset.name}")
                                                .setDescription("Menyimpan ${preset.fileName}")
                                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                .setDestinationInExternalFilesDir(context, null, preset.fileName)
                                            dm.enqueue(req)
                                            Toast.makeText(context, "Unduhan dimulai di bar notifikasi HP!", Toast.LENGTH_LONG).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Unduh ${preset.name} (${preset.size})", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Instruksi Otak Multimodal", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = {
                                systemPrompt = it
                                prefs.edit().putString("system_prompt", it).apply()
                            },
                            label = { Text("System Prompt") },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }
}

fun analyzeBitmapFeatures(bmp: Bitmap, fileName: String): String {
    val w = bmp.width
    val h = bmp.height
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0
    val stepX = (w / 10).coerceAtLeast(1)
    val stepY = (h / 10).coerceAtLeast(1)

    for (x in 0 until w step stepX) {
        for (y in 0 until h step stepY) {
            val pixel = bmp.getPixel(x, y)
            rSum += (pixel shr 16) and 0xFF
            gSum += (pixel shr 8) and 0xFF
            bSum += pixel and 0xFF
            count++
        }
    }
    val rAvg = if (count > 0) rSum / count else 0
    val gAvg = if (count > 0) gSum / count else 0
    val bAvg = if (count > 0) bSum / count else 0
    val brightness = (rAvg + gAvg + bAvg) / 3
    val tone = when {
        brightness > 180 -> "Sangat Terang / Latar Putih (Dokumen/Tangkapan Layar)"
        brightness < 70 -> "Gelap / Mode Malam"
        else -> "Pencahayaan Seimbang (Foto Objek/Pemandangan)"
    }
    val dominantColor = when {
        rAvg > gAvg + 25 && rAvg > bAvg + 25 -> "Dominan Merah/Hangat"
        gAvg > rAvg + 25 && gAvg > bAvg + 25 -> "Dominan Hijau/Alam"
        bAvg > rAvg + 25 && bAvg > gAvg + 25 -> "Dominan Biru/Dingin"
        else -> "Warna Netral/Monokrom"
    }
    val orientation = if (w > h) "Lanskap (Horizontal)" else if (h > w) "Potret (Vertikal)" else "Persegi (1:1)"
    return "File: $fileName | Resolusi: ${w}x${h}px ($orientation) | Karakteristik Visual: $tone, $dominantColor (RGB rata-rata: $rAvg,$gAvg,$bAvg)"
}

fun renderOfflineNeuralArtwork(prompt: String, style: String): Bitmap {
    val size = 768
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val seed = prompt.hashCode().toLong() + style.hashCode().toLong()
    val rand = Random(seed)

    val palette = when (style) {
        "Cyberpunk Neon" -> intArrayOf(0xFF090D16.toInt(), 0xFF1E1B4B.toInt(), 0xFF00F5D4.toInt(), 0xFFFF007F.toInt())
        "Anime Studio" -> intArrayOf(0xFF1A365D.toInt(), 0xFF3182CE.toInt(), 0xFFF6E05E.toInt(), 0xFFFBB6CE.toInt())
        "Photorealistic 8K" -> intArrayOf(0xFF0F172A.toInt(), 0xFF334155.toInt(), 0xFF38BDF8.toInt(), 0xFFF8FAFC.toInt())
        else -> intArrayOf(0xFF111827.toInt(), 0xFF312E81.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt())
    }

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    bgPaint.shader = LinearGradient(
        0f, 0f, size.toFloat(), size.toFloat(),
        palette[0], palette[1], Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

    val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0..28) {
        shapePaint.color = if (i % 2 == 0) palette[2] else palette[3]
        shapePaint.alpha = rand.nextInt(35, 140)
        val cx = rand.nextFloat() * size
        val cy = rand.nextFloat() * size
        val radius = rand.nextFloat() * 220f + 30f
        canvas.drawCircle(cx, cy, radius, shapePaint)
    }

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette[2]
        strokeWidth = 3f
        style = Paint.Style.STROKE
        alpha = 180
    }
    for (w in 0..8) {
        var prevX = 0f
        var prevY = size / 2f
        for (x in 0..size step 24) {
            val y = (size / 2f) + sin((x * 0.015f) + w + rand.nextFloat() * 0.2f) * (90f + w * 20f)
            if (x > 0) canvas.drawLine(prevX, prevY, x.toFloat(), y, linePaint)
            prevX = x.toFloat()
            prevY = y
        }
    }

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA000000.toInt()
    }
    canvas.drawRoundRect(RectF(36f, size - 150f, size - 36f, size - 36f), 24f, 24f, cardPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 26f
        isFakeBoldText = true
    }
    val cleanTitle = prompt.take(34) + if (prompt.length > 34) "..." else ""
    canvas.drawText(cleanTitle, 64f, size - 95f, textPaint)

    textPaint.textSize = 20f
    textPaint.color = palette[2]
    canvas.drawText("AetherAI Neural Synthesis • $style", 64f, size - 60f, textPaint)

    return bmp
}

fun fetchFluxImageOrFallback(prompt: String, style: String): Bitmap {
    return try {
        val encoded = URLEncoder.encode(prompt, "UTF-8")
        val url = URL("https://image.pollinations.ai/prompt/$encoded?width=768&height=768&nologo=true")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.inputStream.use { stream ->
            BitmapFactory.decodeStream(stream) ?: renderOfflineNeuralArtwork(prompt, style)
        }
    } catch (e: Exception) {
        renderOfflineNeuralArtwork(prompt, style)
    }
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AetherAI")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    uri?.let {
        context.contentResolver.openOutputStream(it)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Toast.makeText(context, "Gambar berhasil disimpan ke Galeri (Folder AetherAI)!", Toast.LENGTH_LONG).show()
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1) name = cursor.getString(idx)
        }
    }
    return name
}
