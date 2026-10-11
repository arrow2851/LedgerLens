package com.ledgerlens.sourcescout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.util.JsonWriter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant

private val pine = Color(0xFF10221E)
private val panel = Color(0xFF20372E)
private val sage = Color(0xFFBBE5C8)
private val white = Color(0xFFF5F5EE)
private val muted = Color(0xFFBCCBC0)
private val gold = Color(0xFFE8BF80)

private data class Pattern(var count: Int = 0, val shape: String)
private class SenderStats(val sender: String) {
    var received = 0
    var financial = 0
    var otp = 0
    var overflow = 0
    val shapes = linkedMapOf<String, Pattern>()
    val samples = arrayListOf<String>()
    fun add(body: String) {
        received++
        when (MessageShape.classify(body)) {
            1 -> otp++
            2 -> {
                financial++
                val shape = MessageShape.signature(body)
                if (shapes.containsKey(shape)) shapes.getValue(shape).count++
                else if (shapes.size < 60) shapes[shape] = Pattern(1, shape)
                else overflow++
                if (samples.size < 5 && samples.none { MessageShape.signature(it) == shape }) samples.add(body)
            }
        }
    }
}
private data class Scan(val all: Int, val inbox: Int, val finance: Int, val otp: Int, val senders: List<SenderStats>)

private object MessageShape {
    private val auth = Regex("""(?i)\b(?:otp|one[ -]?time[ -]?password|verification[ -]?code|security[ -]?code|login[ -]?code|passcode)\b""")
    private val finance = Regex("""(?i)\b(?:debited|credited|debit|credit|upi|imps|neft|rtgs|txn|transaction|spent|paid|payment|purchase|refund|reversal|withdrawal|deposit|balance|account|card|transfer|received|emi|loan)\b|₹""")
    private val tokens = Regex("""[\p{L}]+|\d+(?:[,.]\d+)*|[^\p{L}\p{N}\s]""")
    private val allowed = setOf(
        "RS","INR","USD","EUR","GBP","BAL","BALANCE","AVAILABLE","AVAIL","AC","A","C","ACCOUNT","CARD",
        "ENDING","DEBITED","DEBIT","CREDITED","CREDIT","SPENT","PAID","PAYMENT","PURCHASE","REFUND",
        "REVERSED","REVERSAL","RECEIVED","SENT","TRANSFER","TRANSFERRED","WITHDRAWAL","DEPOSIT",
        "UPI","IMPS","NEFT","RTGS","ATM","POS","TXN","TRANSACTION","REF","REFERENCE","FROM","TO",
        "AT","ON","VIA","BY","FOR","OF","IN","WITH","USING","YOUR","HAS","IS","WAS","THE","AND",
        "BANK","SAVINGS","CURRENT","LIMIT","NEW","TOTAL","CHARGED","CHARGE","FEE","CASH","EMI","LOAN"
    )
    private val punctuation = setOf(".",",",":",";","-","/","(",")","₹","$","£","€","#")
    // 1 = OTP/auth, 2 = finance-like, 0 = other. Conservative, not a parser.
    fun classify(s: String) = when {
        auth.containsMatchIn(s) -> 1
        finance.containsMatchIn(s) -> 2
        else -> 0
    }
    fun signature(s: String): String = tokens.findAll(s.take(1000)).take(110).map { match ->
        val part = match.value
        when {
            part.first().isDigit() -> "<NUM>"
            part.first().isLetter() -> if (part.uppercase() in allowed) part.uppercase() else "<TEXT>"
            part in punctuation -> part
            else -> "<SYMBOL>"
        }
    }.joinToString(" ").replace(Regex("""(?:<TEXT>\s+){4,}"""), "<TEXT> <TEXT> <TEXT> ").take(800)
}

private fun scanInbox(context: Context): Scan {
    val sources = linkedMapOf<String, SenderStats>()
    var all = 0
    var inbox = 0
    var finance = 0
    var otp = 0
    val columns = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.TYPE)
    val cursor = context.contentResolver.query(Telephony.Sms.CONTENT_URI, columns, null, null, null)
        ?: error("SMS provider did not return a cursor")
    cursor.use { c ->
        val addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val typeIndex = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
        while (c.moveToNext()) {
            all++
            if (c.getInt(typeIndex) != Telephony.Sms.MESSAGE_TYPE_INBOX) continue
            inbox++
            val sender = c.getString(addressIndex).orEmpty().ifBlank { "Unknown sender" }
            val body = c.getString(bodyIndex).orEmpty()
            when (MessageShape.classify(body)) {
                1 -> otp++
                2 -> finance++
            }
            sources.getOrPut(sender) { SenderStats(sender) }.add(body)
        }
    }
    return Scan(all, inbox, finance, otp,
        sources.values.sortedWith(compareByDescending<SenderStats> { it.financial }.thenByDescending { it.received }))
}

private fun exportSafe(out: OutputStream, scan: Scan) {
    JsonWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { j ->
        j.setIndent("  ")
        j.beginObject()
        j.name("schema_version").value(1)
        j.name("generated_utc").value(Instant.now().toString())
        j.name("generator").value("LedgerLens Source Scout")
        j.name("warning").value("Generalized patterns are not guaranteed anonymous. Inspect before sharing.")
        j.name("all_sms_rows").value(scan.all)
        j.name("received_sms_rows").value(scan.inbox)
        j.name("financial_candidate_rows").value(scan.finance)
        j.name("otp_or_auth_excluded").value(scan.otp)
        j.name("sources").beginArray()
        scan.senders.forEachIndexed { idx, s ->
            if (s.financial == 0) return@forEachIndexed
            j.beginObject()
            j.name("source_id").value("source_" + (idx + 1).toString().padStart(3, '0'))
            j.name("sender_shape").value(if (s.sender.any { it.isLetter() }) "alphanumeric_or_named" else "numeric_or_other")
            j.name("received_count").value(s.received)
            j.name("financial_candidate_count").value(s.financial)
            j.name("overflow_pattern_messages").value(s.overflow)
            j.name("structures").beginArray()
            s.shapes.values.sortedByDescending { it.count }.forEach { p ->
                j.beginObject()
                j.name("shape").value(p.shape)
                j.name("count").value(p.count)
                j.endObject()
            }
            j.endArray()
            j.endObject()
        }
        j.endArray()
        j.endObject()
    }
}

class MainActivity : ComponentActivity() {
    private var allowed by mutableStateOf(false)
    private var scanning by mutableStateOf(false)
    private var scan by mutableStateOf<Scan?>(null)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()
        setContent {
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                allowed = granted
                if (granted) beginScan() else error = "SMS access was not granted."
            }
            val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                val data = scan
                if (uri != null && data != null) lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val stream = contentResolver.openOutputStream(uri) ?: error("Cannot open export file")
                            stream.use { exportSafe(it, data) }
                        }
                        error = "Safe report saved. Review before sharing."
                    } catch (e: Exception) { error = "Export failed: " + e.message }
                }
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = sage, onPrimary = pine, surface = panel, background = pine, onBackground = white)) {
                ScoutUi(allowed, scanning, scan, error,
                    onPermission = { permission.launch(Manifest.permission.READ_SMS) },
                    onScan = { if (allowed) beginScan() else permission.launch(Manifest.permission.READ_SMS) },
                    onExport = { save.launch("ledgerlens-source-report.json") })
            }
        }
    }
    override fun onResume() {
        super.onResume()
        checkPermission()
    }
    private fun checkPermission() {
        allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) scan = null
    }
    private fun beginScan() {
        if (scanning || !allowed) return
        scanning = true
        error = null
        lifecycleScope.launch {
            try { scan = withContext(Dispatchers.IO) { scanInbox(applicationContext) } }
            catch (e: Exception) { error = "Scan failed: " + e.message }
            finally { scanning = false }
        }
    }
}

@Composable
private fun ScoutUi(
    allowed: Boolean, scanning: Boolean, scan: Scan?, error: String?,
    onPermission: () -> Unit, onScan: () -> Unit, onExport: () -> Unit
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var financialOnly by rememberSaveable { mutableStateOf(true) }
    var showSamples by rememberSaveable { mutableStateOf(false) }
    val selectedSource = scan?.senders?.firstOrNull { it.sender == selected }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        if (selectedSource != null) {
            Text("← All sources", Modifier.clickable { selected = null; showSamples = false }.padding(vertical = 10.dp), color = sage)
            Text(selectedSource.sender, color = white, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            Text(selectedSource.received.toString() + " received · " + selectedSource.financial + " finance-like", color = muted)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show raw local samples", Modifier.weight(1f), color = white)
                Switch(checked = showSamples, onCheckedChange = { showSamples = it })
            }
            Text("Original SMS never appears in the safe export.", color = muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                selectedSource.shapes.values.sortedByDescending { it.count }.take(50).forEachIndexed { i, p ->
                    Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("SHAPE " + (i+1) + " · " + p.count + " messages", color = gold, fontSize = 12.sp)
                            Text(p.shape, color = white, fontSize = 13.sp, lineHeight = 19.sp)
                        }
                    }
                }
                if (showSamples) selectedSource.samples.forEach { sample ->
                    Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                        Text(sample, Modifier.fillMaxWidth().padding(14.dp), color = white, fontSize = 13.sp)
                    }
                }
            }
            return@Column
        }

        Text("◈  LEDGERLENS", color = sage, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Source Scout", color = white, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Discover the message structures behind your finances.", color = muted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        if (!allowed) {
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your messages stay on your device", color = white, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Read SMS permission lets Scout count every stored message, group received messages by sender and identify recurring financial alert shapes. No network permission or automatic uploads.", color = muted)
                    Button(onClick = onPermission, modifier = Modifier.fillMaxWidth()) { Text("Allow SMS scan") }
                }
            }
            return@Column
        }
        if (scan == null) {
            Button(onClick = onScan, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                Text(if (scanning) "Scanning SMS..." else "Scan all stored SMS")
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Stored SMS", scan.all, Modifier.weight(1f))
                Stat("Senders", scan.senders.size, Modifier.weight(1f))
                Stat("Finance-like", scan.finance, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(scan.inbox.toString() + " received · " + (scan.all - scan.inbox) + " sent/other · " + scan.otp + " OTP/auth excluded", color = muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f), enabled = !scanning) { Text("Rescan") }
                Button(onClick = onExport, modifier = Modifier.weight(1.5f), enabled = !scanning) { Text("Export safe JSON") }
            }
            Text("Export contains only source counts and generalized patterns; inspect before sharing.", color = muted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Search sender") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = financialOnly, onClick = { financialOnly = !financialOnly }, label = { Text("Likely financial") })
                Spacer(Modifier.width(9.dp))
                Text("Tap a source to inspect", color = muted, fontSize = 12.sp)
            }
            val filtered = scan.senders.filter {
                (!financialOnly || it.financial > 0) && it.sender.contains(query.trim(), ignoreCase = true)
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.sender }) { source ->
                    Card(onClick = { selected = source.sender }, colors = CardDefaults.cardColors(containerColor = panel)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(source.sender, Modifier.weight(1f), color = white, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("›", color = sage, fontSize = 22.sp)
                            }
                            Text(source.received.toString() + " SMS  ·  " + source.financial + " finance-like  ·  " +
                                source.shapes.size + " shapes", color = muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = gold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = panel)) {
        Column(Modifier.padding(10.dp)) {
            Text(value.toString(), color = sage, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(label, color = muted, fontSize = 11.sp)
        }
    }
}
