package com.ledgerlens.transactionlab

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.JsonWriter
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

private val ink = Color(0xFF12251F)
private val sheet = Color(0xFF213A31)
private val leaf = Color(0xFFBEEBC8)
private val textPrimary = Color(0xFFF9F9EF)
private val soft = Color(0xFFAEC6B9)
private val amber = Color(0xFFF4CB87)
private val alert = Color(0xFFFFB4A2)

class MainActivity: ComponentActivity() {
    private lateinit var store: EditStore
    private lateinit var sourceStore: SourceStore
    private var allowed by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var data by mutableStateOf<ScanResult?>(null)
    private var edits by mutableStateOf<Map<Long,ManualEdit>>(emptyMap())
    private var notice by mutableStateOf<String?>(null)
    private var sources by mutableStateOf<List<SourceProfile>?>(null)
    private var selectedSources by mutableStateOf<Set<String>>(emptySet())
    private var sourceMode by mutableStateOf(true)
    private var discovering by mutableStateOf(false)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(247,246,242)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(247,246,242)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        store = EditStore(this)
        sourceStore=SourceStore(this)
        selectedSources=sourceStore.load()
        edits = store.load()
        refreshPermission()
        setContent {
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
                allowed = result
                if (result) beginDiscovery() else notice = "READ_SMS permission was denied."
            }
            val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) lifecycleScope.launch {
                    try {
                        val snapshot = edits
                        withContext(Dispatchers.IO) {
                            contentResolver.openOutputStream(uri)?.use { output ->
                                JsonWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { j ->
                                    j.setIndent("  ")
                                    j.beginObject()
                                    j.name("schema_version").value(1)
                                    j.name("export_type").value("TRANSACTION_LAB_USER_CORRECTIONS")
                                    j.name("created_utc").value(Instant.now().toString())
                                    j.name("note").value("Manual edits only; no SMS text. Identifiers and merchant data are sensitive.")
                                    j.name("corrections").beginArray()
                                    snapshot.toSortedMap().forEach { (id, edit) ->
                                        j.beginObject()
                                        j.name("sms_id").value(id)
                                        j.name("merchant").value(edit.merchant)
                                        j.name("amount").value(edit.amount)
                                        j.name("kind").value(edit.kind)
                                        j.name("direction").value(edit.direction)
                                        j.name("lifecycle").value(edit.lifecycle)
                                        j.name("category").value(edit.category)
                                        j.name("reviewed").value(edit.reviewed)
                                        j.name("ignored").value(edit.ignored)
                                        j.name("counterparty").value(edit.counterparty)
                                        j.name("payment_method").value(edit.paymentMethod)
                                        j.name("cardholder").value(edit.cardholder)
                                        j.name("card_last4").value(edit.cardLast4)
                                        j.endObject()
                                    }
                                    j.endArray()
                                    j.endObject()
                                }
                            } ?: error("Cannot open export")
                        }
                        notice = "Corrections JSON saved locally."
                    } catch (e:Exception) { notice = "Export failed: " + e.message }
                }
            }
            val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) lifecycleScope.launch {
                    try {
                        val imports = withContext(Dispatchers.IO) {
                            val input = contentResolver.openInputStream(uri)
                                ?: throw IllegalStateException("Could not open file")
                            val root = input.bufferedReader(Charsets.UTF_8).use {
                                JSONObject(it.readText())
                            }
                            require(root.optString("export_type")=="TRANSACTION_LAB_USER_CORRECTIONS"){
                                "This is not a Transaction Lab corrections JSON."
                            }
                            val arr = root.getJSONArray("corrections")
                            require(arr.length()<=10000){"Too many corrections"}
                            val output = LinkedHashMap<Long,ManualEdit>()
                            for (i in 0 until arr.length()) {
                                val obj=arr.getJSONObject(i)
                                val id=obj.getLong("sms_id")
                                output[id]=ManualEdit.from(obj)
                            }
                            output
                        }
                        for ((id, edit) in imports) store.save(id,edit)
                        edits=edits+imports
                        notice=imports.size.toString()+" corrections restored on this device."
                    } catch(e:Exception){notice="Import failed: "+e.message}
                }
            }
            MaterialTheme(colorScheme = lightColorScheme(
                primary=LabColors.green, onPrimary=androidx.compose.ui.graphics.Color.White,
                background=LabColors.canvas, onBackground=LabColors.ink,
                surface=LabColors.paper, onSurface=LabColors.ink
            )) {
                PolishedLab(
                    allowed=allowed, busy=busy, data=data, edits=edits, notice=notice,
                    sources=sources, selectedSources=selectedSources, sourceMode=sourceMode, discovering=discovering,
                    onDiscover={beginDiscovery()},
                    onSelectSources={ids->selectedSources=ids;sourceStore.save(ids)},
                    onStartSelectedScan={if(selectedSources.isNotEmpty()){sourceMode=false;beginScan()}},
                    onChooseSources={sourceMode=true},
                    onGrant = { permission.launch(Manifest.permission.READ_SMS) },
                    onScan = { if (allowed) beginScan() else permission.launch(Manifest.permission.READ_SMS) },
                    onSave = { id, edit ->
                        store.save(id,edit)
                        edits = edits + (id to edit)
                        notice = "Correction saved on this device."
                    },
                    onExport = { createFile.launch("ledgerlens-transaction-lab-corrections.json") },
                    onImport = { importFile.launch(arrayOf("application/json")) }
                )
            }
        }
    }
    override fun onResume() { super.onResume(); refreshPermission() }
    private fun refreshPermission() {
        allowed = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED
        if (!allowed) data = null
    }
    private fun beginDiscovery(){
        if(!allowed||discovering)return
        discovering=true
        notice=null
        lifecycleScope.launch{
            try{ sources=withContext(Dispatchers.IO){discoverSources(applicationContext)} }
            catch(e:Exception){notice="Could not list sources: "+e.message}
            finally{discovering=false}
        }
    }
    private fun beginScan() {
        if (!allowed || busy) return
        busy=true
        notice=null
        lifecycleScope.launch {
            try {
                val chosen=selectedSources.toSet()
                data=withContext(Dispatchers.IO){scanTransactions(applicationContext,chosen)}
            }
            catch (e:Exception) { notice="Scan failed: "+ e.message }
            finally { busy=false }
        }
    }
}

@Composable
private fun LabScreen(
    allowed:Boolean, busy:Boolean, data:ScanResult?, edits:Map<Long,ManualEdit>, notice:String?,
    sources:List<SourceProfile>?,selectedSources:Set<String>,sourceMode:Boolean,discovering:Boolean,
    onDiscover:()->Unit,onSelectSources:(Set<String>)->Unit,onStartSelectedScan:()->Unit,onChooseSources:()->Unit,
    onGrant:()->Unit, onScan:()->Unit, onSave:(Long,ManualEdit)->Unit, onExport:()->Unit,
    onImport:()->Unit
) {
    if(allowed&&sourceMode){
        SourcePickerUi(sources,selectedSources,discovering,notice,onDiscover,onSelectSources,onStartSelectedScan)
        return
    }
    var detailId by remember { mutableStateOf<Long?>(null) }
    val chosen = data?.rows?.find { it.id == detailId }
    if (chosen != null) {
        DetailPage(chosen,edits[chosen.id],onBack={detailId=null},onSave={ edit -> onSave(chosen.id,edit);detailId=null })
        return
    }
    var filter by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("◈  LEDGERLENS / LAB 02", color=leaf,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
        Text("Transaction Lab", color=textPrimary,fontSize=29.sp,fontWeight=FontWeight.Bold)
        Text("Inspect evidence. Correct the interpretation.",color=soft,fontSize=13.sp)
        Spacer(Modifier.height(18.dp))
        if(!allowed) {
            Surface(shape=RoundedCornerShape(18.dp),color=sheet) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("SMS access stays local",fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Text("Parse SMS alerts without network access, then review or correct them. Only your corrections are stored in this app.",color=soft)
                    Button(onClick=onGrant,modifier=Modifier.fillMaxWidth()) { Text("Allow SMS access") }
                }
            }
            return@Column
        }
        if(data==null) {
            Surface(shape=RoundedCornerShape(18.dp),color=sheet) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Ready to scan",fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text("Parses stored SMS. Recognized transaction alerts appear once scanning finishes.",color=soft)
                    Button(onClick=onScan,enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                        Text(if(busy) "Scanning and parsing…" else "Scan SMS history")
                    }
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if(notice!=null) Text(notice,color=amber,modifier=Modifier.padding(top=12.dp))
            return@Column
        }
        val pending = data.rows.count { (edits[it.id]?.lifecycle ?: it.parsed.lifecycle)=="PENDING" && edits[it.id]?.ignored!=true }
        val review = data.rows.count { edits[it.id]?.reviewed!=true && edits[it.id]?.ignored!=true }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Counter("Detected",data.rows.size,Modifier.weight(1f))
            Counter("Unreviewed",review,Modifier.weight(1f))
            Counter("Pending",pending,Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(data.total.toString()+" SMS scanned · "+data.ignored+" ignored by rules · "+edits.size+" corrections",fontSize=12.sp,color=soft)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick=onChooseSources,modifier=Modifier.weight(1f)){Text("Sources",fontSize=11.sp)}
            OutlinedButton(onClick=onScan,enabled=!busy,modifier=Modifier.weight(1f)){Text("Rescan",fontSize=11.sp)}
            Button(onClick=onExport,modifier=Modifier.weight(1.4f),enabled=!busy){Text("Corrections",fontSize=11.sp)}
        }
        OutlinedButton(onClick=onImport,enabled=!busy,modifier=Modifier.fillMaxWidth()) {
            Text("Import saved corrections")
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=6.dp))
        if(notice!=null) Text(notice,color=amber,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),
            singleLine=true,label={Text("Find merchant, sender, or rule")})
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf("All","Unreviewed","Pending","Confirmed","Reviewed","Ignored").forEach { entry ->
                FilterChip(selected=filter==entry,onClick={filter=entry},label={Text(entry)})
            }
        }
        val shown = remember(data,edits,filter,search) {
            data.rows.filter { e ->
                val m=edits[e.id]
                val lifecycle=m?.lifecycle ?: e.parsed.lifecycle
                val accepted=when(filter) {
                    "Unreviewed" -> m?.reviewed!=true && m?.ignored!=true
                    "Pending" -> lifecycle=="PENDING" && m?.ignored!=true
                    "Confirmed" -> lifecycle=="CONFIRMED" && m?.ignored!=true
                    "Reviewed" -> m?.reviewed==true && m.ignored!=true
                    "Ignored" -> m?.ignored==true
                    else -> m?.ignored!=true
                }
                accepted && (search.isBlank() || listOf(e.sender,e.parsed.rule,
                    m?.merchant ?: e.parsed.merchant ?: "", e.body)
                    .any { it.contains(search.trim(),ignoreCase=true) })
            }
        }
        Text(shown.size.toString()+" matching messages",fontSize=12.sp,color=soft)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp),
            contentPadding=PaddingValues(bottom=20.dp)) {
            items(shown,key={it.id}) { item ->
                val m=edits[item.id]
                val p=item.parsed
                Surface(
                    color=sheet,shape=RoundedCornerShape(16.dp),
                    modifier=Modifier.fillMaxWidth().clickable { detailId=item.id },
                    border=BorderStroke(1.dp, Color(0xFF315245))
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(p.kind=="CARD_PAYMENT"||item.details.method=="ZELLE")
                                item.details.title(p)
                            else m?.merchant?.takeIf { it.isNotBlank() } ?: item.details.title(p),
                                modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=textPrimary,
                                maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(m?.amount?.takeIf{it.isNotBlank()} ?: moneyString(p.amountMinor).ifBlank { "—" },
                                color=leaf,fontWeight=FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            Text(if(p.currency=="INR")"₹" else if(p.currency=="USD")"$" else "",color=soft,fontSize=11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text((m?.kind ?: p.kind)+" · "+(m?.direction ?: p.direction)+" · "+(m?.lifecycle ?: p.lifecycle),
                            fontSize=12.sp,color=if((m?.lifecycle ?: p.lifecycle)=="PENDING")amber else soft)
                        Text("Via "+(m?.paymentMethod?.takeIf{it.isNotBlank()} ?: item.details.method ?: "UNKNOWN")+
                            " · "+(m?.cardholder?.takeIf{it.isNotBlank()} ?: item.details.cardholder ?: item.details.institution ?: "Issuer unknown"),
                            fontSize=11.sp,color=leaf,maxLines=1)
                        Text((if(m?.reviewed==true)"✓ Reviewed" else if(p.outcome=="NEEDS_REVIEW")"Needs parsing review" else "Unreviewed")+" · "+item.sender,
                            fontSize=11.sp,color=soft,maxLines=1)
                        Text(item.body.replace("\n"," ").take(130),fontSize=11.sp,color=soft,maxLines=2,
                            overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Counter(name:String,count:Int,modifier:Modifier) {
    Surface(color=sheet,shape=RoundedCornerShape(14.dp),modifier=modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(count.toString(),color=leaf,fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text(name,color=soft,fontSize=11.sp)
        }
    }
}

private fun annotatedEvidence(text:String,p:Parsed) = buildAnnotatedString {
    append(text)
    if(p.amountStart>=0 && p.amountEnd<=text.length && p.amountStart<p.amountEnd)
        addStyle(SpanStyle(background=Color(0xFF37635B),color=textPrimary,fontWeight=FontWeight.Bold),
            p.amountStart,p.amountEnd)
    if(p.merchantStart>=0 && p.merchantEnd<=text.length && p.merchantStart<p.merchantEnd)
        addStyle(SpanStyle(background=Color(0xFF695130),color=textPrimary,fontWeight=FontWeight.Bold),
            p.merchantStart,p.merchantEnd)
}

@Composable
private fun DetailPage(row:SmsRow,override:ManualEdit?,onBack:()->Unit,onSave:(ManualEdit)->Unit) {
    val p=row.parsed
    var merchant by remember(row.id,override) { mutableStateOf(if(p.kind=="PURCHASE") (override?.merchant ?: p.merchant.orEmpty()) else "") }
    var amount by remember(row.id,override) { mutableStateOf(override?.amount ?: moneyString(p.amountMinor)) }
    var kind by remember(row.id,override) { mutableStateOf(override?.kind ?: p.kind) }
    var direction by remember(row.id,override) { mutableStateOf(override?.direction ?: p.direction) }
    var lifecycle by remember(row.id,override) { mutableStateOf(override?.lifecycle ?: p.lifecycle) }
    var category by remember(row.id,override) { mutableStateOf(override?.category.orEmpty()) }
    var ignored by remember(row.id,override) { mutableStateOf(override?.ignored ?: false) }
    var counterparty by remember(row.id,override){mutableStateOf(override?.counterparty?.takeIf{it.isNotBlank()} ?: row.details.counterparty.orEmpty())}
    var paymentMethod by remember(row.id,override){mutableStateOf(override?.paymentMethod?.takeIf{it.isNotBlank()} ?: row.details.method.orEmpty())}
    var cardholder by remember(row.id,override){mutableStateOf(override?.cardholder?.takeIf{it.isNotBlank()} ?: row.details.cardholder.orEmpty())}
    var cardLast4 by remember(row.id,override){mutableStateOf(override?.cardLast4?.takeIf{it.isNotBlank()} ?: row.details.cardLast4.orEmpty())}
    val valid=amountValid(amount)
    val save:(Boolean)->Unit={ reviewed ->
        if(valid) onSave(ManualEdit(merchant,amount,kind,direction,lifecycle,category,reviewed,ignored,
            counterparty,paymentMethod,cardholder,cardLast4))
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        Text("← Transaction list",modifier=Modifier.clickable(onClick=onBack).padding(vertical=8.dp),color=leaf)
        Text("Review evidence",fontSize=25.sp,fontWeight=FontWeight.Bold,color=textPrimary)
        Text(row.sender+" · "+SimpleDateFormat("MMM d, yyyy · h:mm a",Locale.getDefault()).format(Date(row.timestamp)),
            color=soft,fontSize=12.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(color=sheet,shape=RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("ORIGINAL SMS  ·  highlighted evidence",fontSize=11.sp,color=amber,fontWeight=FontWeight.Bold)
                    Text(annotatedEvidence(row.body,p),color=textPrimary,fontSize=14.sp,lineHeight=21.sp)
                }
            }
            Text("PARSER   "+p.rule,fontSize=12.sp,color=leaf,fontWeight=FontWeight.Bold)
            Text("Institution: "+(row.details.institution ?: "Unknown"),color=soft,fontSize=12.sp)
            Text("Party role: "+(row.details.role ?: "Not established"),color=soft,fontSize=12.sp)
            Text("Account: "+(row.details.accountLast4?.let{"••••"+it} ?: "Not stated"),color=soft,fontSize=12.sp)
            if(row.details.note!=null)Text(row.details.note,color=amber,fontSize=12.sp)
            if(p.note.isNotBlank()) Text(p.note,fontSize=12.sp,color=soft)
            if(p.outcome=="NEEDS_REVIEW") Text("⚠ Incomplete or generic match. Do not assume it is a valid transaction.",color=amber,fontSize=12.sp)
            Text("Correct extraction",fontWeight=FontWeight.Bold,fontSize=17.sp)
            OutlinedTextField(amount,onValueChange={amount=it},modifier=Modifier.fillMaxWidth(),
                singleLine=true,isError=!valid,label={Text("Amount ("+(p.currency ?: "currency unknown")+")")})
            if(!valid) Text("Enter a nonnegative amount with at most two decimal places.",color=alert,fontSize=11.sp)
            OutlinedTextField(merchant,onValueChange={merchant=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Merchant (for purchases)")},singleLine=true)
            OutlinedTextField(counterparty,onValueChange={counterparty=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Counterparty / sender / recipient / issuer")},singleLine=true)
            OutlinedTextField(paymentMethod,onValueChange={paymentMethod=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Method (Zelle, card, wire, etc.)")},singleLine=true)
            OutlinedTextField(cardholder,onValueChange={cardholder=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Named cardholder (only if known)")},singleLine=true)
            OutlinedTextField(cardLast4,onValueChange={cardLast4=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Card last four (if known)")},singleLine=true)
            OutlinedTextField(category,onValueChange={category=it},modifier=Modifier.fillMaxWidth(),
                label={Text("Category (optional)")},singleLine=true)
            ChoiceRow("TYPE",listOf("PURCHASE","TRANSFER","DEPOSIT","CARD_PAYMENT","CASH_WITHDRAWAL","REFUND","REVERSAL","FEE","UNKNOWN"),kind){kind=it}
            ChoiceRow("DIRECTION",listOf("OUT","IN","UNKNOWN"),direction){direction=it}
            ChoiceRow("LIFECYCLE",listOf("PENDING","CONFIRMED","FAILED","UNKNOWN"),lifecycle){lifecycle=it}
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Ignore this message as a transaction",modifier=Modifier.weight(1f),fontSize=13.sp)
                Switch(checked=ignored,onCheckedChange={ignored=it})
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick={save(true)},enabled=valid,modifier=Modifier.fillMaxWidth()) {
            Text(if(ignored)"Save as ignored" else "Save & mark reviewed")
        }
        TextButton(onClick={save(false)},enabled=valid,modifier=Modifier.fillMaxWidth()) {
            Text("Save without marking reviewed")
        }
    }
}

@Composable
private fun ChoiceRow(label:String,options:List<String>,value:String,onChange:(String)->Unit) {
    Column {
        Text(label,color=soft,fontSize=11.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            options.forEach { v ->
                FilterChip(selected=value==v,onClick={onChange(v)},label={Text(v.replace("_"," ").lowercase().replaceFirstChar{it.uppercase()})})
            }
        }
    }
}
