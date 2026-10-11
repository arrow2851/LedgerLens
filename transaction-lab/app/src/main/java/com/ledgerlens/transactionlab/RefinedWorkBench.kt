package com.ledgerlens.transactionlab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun readDate(ts:Long):String=SimpleDateFormat("d MMM yyyy · h:mm a",Locale.getDefault()).format(Date(ts))

private fun displayName(item:SmsRow, edit:ManualEdit?):String {
    val p=item.parsed
    val details=item.details
    val party=edit?.counterparty?.takeIf{it.isNotBlank()} ?: details.counterparty
    val method=edit?.paymentMethod?.takeIf{it.isNotBlank()} ?: details.method
    return when {
        (edit?.kind?:p.kind)=="CARD_PAYMENT" -> "Card payment to "+(party?:details.institution?:"issuer")
        method=="ZELLE" -> if((edit?.direction?:p.direction)=="IN")
            "Zelle received"+(if(party!=null)" from "+party else "")
            else "Zelle sent"+(if(party!=null)" to "+party else "")
        (edit?.kind?:p.kind)=="PURCHASE" -> edit?.merchant?.takeIf{it.isNotBlank()}
            ?:p.merchant?:"Card purchase"
        else -> edit?.merchant?.takeIf{it.isNotBlank()} ?:details.title(p)
    }
}

@Composable
internal fun PolishedLab(
    allowed:Boolean,busy:Boolean,data:ScanResult?,edits:Map<Long,ManualEdit>,notice:String?,
    sources:List<SourceProfile>?,selectedSources:Set<String>,sourceMode:Boolean,discovering:Boolean,
    onDiscover:()->Unit,onSelectSources:(Set<String>)->Unit,onStartSelectedScan:()->Unit,onChooseSources:()->Unit,
    onGrant:()->Unit,onScan:()->Unit,onSave:(Long,ManualEdit)->Unit,onExport:()->Unit,onImport:()->Unit
){
    var openedId by remember { mutableStateOf<Long?>(null) }
    if(sourceMode || !allowed){
        RefinedSourceScreen(allowed,sources,selectedSources,discovering,notice,onGrant,
            onDiscover,onSelectSources,onStartSelectedScan)
        return
    }
    val record=data?.rows?.firstOrNull{it.id==openedId}
    if(record!=null){
        RefinedDetailScreen(row=record,edit=edits[record.id],
            onBack={openedId=null},
            onSave={correction->onSave(record.id,correction);openedId=null})
        return
    }
    if(data==null){
        Column(Modifier.fillMaxSize().background(LabColors.canvas).statusBarsPadding()
            .navigationBarsPadding().padding(22.dp)){
            LabChrome(step=2,onBack=onChooseSources)
            Spacer(Modifier.height(40.dp))
            LabHeading("Finding the real\nmoney movement.",
                "Only your chosen sources are scanned. This can include older messages and card authorizations.")
            Spacer(Modifier.height(27.dp))
            Surface(color=LabColors.paper,shape=RoundedCornerShape(22.dp),
                border=BorderStroke(1.dp,LabColors.line)){
                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
                    LabChip(selectedSources.size.toString()+" SOURCES SELECTED","green")
                    Text(if(busy)"Reading messages securely…" else "Ready to scan",
                        color=LabColors.ink,fontWeight=FontWeight.Bold,fontSize=18.sp)
                    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=LabColors.green)
                    else LabPrimary("Scan transactions",onClick=onScan)
                    if(notice!=null)Text(notice,color=LabColors.roseInk,fontSize=12.sp)
                }
            }
        }
        return
    }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var menuOpen by remember { mutableStateOf(false) }
    val pending=data.rows.count{it.parsed.lifecycle=="PENDING" && edits[it.id]?.lifecycle?:"PENDING"=="PENDING" && edits[it.id]?.ignored!=true}
    val todo=data.rows.count{edits[it.id]?.reviewed!=true && edits[it.id]?.ignored!=true}
    val ignored=edits.values.count{it.ignored}
    val shown=remember(data,edits,filter,search){
        data.rows.filter { item ->
            val edit=edits[item.id]
            val isHidden=edit?.ignored==true
            val matchesFilter=when(filter){
                "To review"-> !isHidden && edit?.reviewed!=true
                "Pending"->!isHidden && (edit?.lifecycle ?:item.parsed.lifecycle)=="PENDING"
                "Reviewed"->!isHidden && edit?.reviewed==true
                "Excluded"->isHidden
                else->!isHidden
            }
            matchesFilter && (search.isBlank() || listOf(
                displayName(item,edit),item.sender,item.parsed.rule,
                item.details.institution.orEmpty(),item.details.method.orEmpty(),
                item.details.cardholder.orEmpty(),item.details.counterparty.orEmpty(),
                edit?.counterparty.orEmpty(),edit?.cardholder.orEmpty(),item.body
            ).any{it.contains(search.trim(),true)})
        }
    }
    Column(Modifier.fillMaxSize().background(LabColors.canvas).statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=35.dp,top=22.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxSize()){
            item {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("LEDGERLENS",fontSize=13.sp,fontWeight=FontWeight.Bold,
                            letterSpacing=2.sp,color=LabColors.ink)
                        Text("TRANSACTION LAB   /   03",fontSize=10.sp,
                            letterSpacing=1.sp,color=LabColors.textMuted)
                    }
                    Box{
                        LabSecondary("Manage  ···",onClick={menuOpen=true})
                        DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}){
                            DropdownMenuItem(text={Text("Change SMS sources")},onClick={menuOpen=false;onChooseSources()})
                            DropdownMenuItem(text={Text("Rescan selected sources")},enabled=!busy,
                                onClick={menuOpen=false;onScan()})
                            DropdownMenuItem(text={Text("Export corrections")},onClick={menuOpen=false;onExport()})
                            DropdownMenuItem(text={Text("Import corrections")},onClick={menuOpen=false;onImport()})
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                LabHeading("Your transaction\nworkbench.",
                    "Review the details, resolve uncertain alerts, and keep the decisions that matter.")
            }
            item {
                Surface(color=LabColors.ink,shape=RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)){
                        Text("WAITING FOR YOUR REVIEW",color=Color(0xFFB6D8C7),
                            letterSpacing=1.3.sp,fontSize=10.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment=Alignment.Bottom) {
                            Text(todo.toString(),color=Color.White,
                                fontSize=46.sp,fontWeight=FontWeight.SemiBold,
                                modifier=Modifier.weight(1f))
                            Text("messages",fontSize=13.sp,color=Color(0xFFD2E1DA),modifier=Modifier.padding(bottom=10.dp))
                        }
                        HorizontalDivider(color=Color(0xFF496359))
                        Spacer(Modifier.height(13.dp))
                        Row {
                            OverviewMetric("Pending holds",pending.toString(),Modifier.weight(1f))
                            OverviewMetric("Reviewed",(data.rows.size-todo-ignored).coerceAtLeast(0).toString(),Modifier.weight(1f))
                            OverviewMetric("Sources",selectedSources.size.toString(),Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Text("Transaction candidates · not reconciled bank ledger entries",
                    fontSize=11.sp,color=LabColors.textMuted)
                if(busy){
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(),color=LabColors.green)
                }
                if(notice!=null){
                    Spacer(Modifier.height(8.dp))
                    Surface(color=LabColors.tint,shape=RoundedCornerShape(12.dp)){
                        Text(notice,modifier=Modifier.padding(12.dp),fontSize=12.sp,color=LabColors.green)
                    }
                }
            }
            item {
                OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),
                    leadingIcon={Text("⌕",fontSize=26.sp,color=LabColors.textMuted)},
                    label={Text("Search merchant, person, or source")},
                    singleLine=true,shape=RoundedCornerShape(15.dp),
                    colors=OutlinedTextFieldDefaults.colors(
                        focusedBorderColor=LabColors.green,unfocusedBorderColor=LabColors.line,
                        focusedContainerColor=LabColors.paper,unfocusedContainerColor=LabColors.paper,
                        focusedTextColor=LabColors.ink,unfocusedTextColor=LabColors.ink))
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    listOf("All","To review","Pending","Reviewed","Excluded").forEach{state->
                        FilterChip(selected=filter==state,onClick={filter=state},
                            label={Text(state)},colors=FilterChipDefaults.filterChipColors(
                                selectedContainerColor=LabColors.tint,selectedLabelColor=LabColors.green,
                                containerColor=LabColors.paper,labelColor=LabColors.textMuted))
                    }
                }
                Text(shown.size.toString()+" matching alerts",color=LabColors.textMuted,fontSize=12.sp)
            }
            if(shown.isEmpty()){
                item {
                    Surface(color=LabColors.paper,shape=RoundedCornerShape(18.dp),
                        border=BorderStroke(1.dp,LabColors.line)){
                        Column(Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment=Alignment.CenterHorizontally){
                            Text("Nothing in this view",fontSize=18.sp,
                                fontWeight=FontWeight.SemiBold,color=LabColors.ink)
                            Spacer(Modifier.height(7.dp))
                            Text("Try another filter, search, or select more sources.",
                                fontSize=13.sp,color=LabColors.textMuted)
                        }
                    }
                }
            }else{
                items(shown,key={it.id}) { item ->
                    RefinedTransactionCard(item,edits[item.id],onClick={openedId=item.id})
                }
            }
            item {
                Text("SMS alerts can describe authorization holds, payments, or attempts. Review before using as final spending.",
                    color=LabColors.textMuted,fontSize=11.sp,lineHeight=16.sp)
            }
        }
    }
}

@Composable
private fun OverviewMetric(label:String,value:String,modifier:Modifier=Modifier){
    Column(modifier){
        Text(value,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label,color=Color(0xFFB6D8C7),fontSize=11.sp)
    }
}

@Composable
private fun RefinedTransactionCard(item:SmsRow,edit:ManualEdit?,onClick:()->Unit){
    val p=item.parsed
    val d=item.details
    val method=edit?.paymentMethod?.takeIf{it.isNotBlank()} ?: d.method ?: "SMS ALERT"
    val isPending=(edit?.lifecycle ?:p.lifecycle)=="PENDING"
    val review=edit?.reviewed==true
    val attention=p.outcome=="NEEDS_REVIEW"
    val amount=edit?.amount?.takeIf{it.isNotBlank()} ?: moneyString(p.amountMinor)
    val sign=if((edit?.direction?:p.direction)=="IN")+" else if((edit?.direction?:p.direction)=="OUT")"−" else ""
    val symbol=when(p.currency){"USD"->"$";"INR"->"₹";else->""}
    Surface(color=LabColors.paper,shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,LabColors.line),onClick=onClick){
        Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.Top){
                LabInitials(d.counterparty?:p.merchant?:d.institution?:item.sender,
                    green=(edit?.direction?:p.direction)=="IN")
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text(displayName(item,edit),fontSize=15.sp,
                        fontWeight=FontWeight.SemiBold,color=LabColors.ink,
                        maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=19.sp)
                    Text(method.replace("_"," ")+"   ·   "+(d.institution?:item.sender),
                        fontSize=11.sp,color=LabColors.textMuted,maxLines=1,
                        overflow=TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment=Alignment.End){
                    Text(if(amount.isBlank())"—" else sign+symbol+amount,
                        fontSize=16.sp,color=if(sign=="+")LabColors.green else LabColors.ink,
                        fontWeight=FontWeight.Bold)
                    Text(readDate(item.timestamp).substringBefore(" ·"),fontSize=10.sp,
                        color=LabColors.textMuted)
                }
            }
            LabDivider()
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(6.dp)){
                LabChip(if(review)"REVIEWED" else if(attention)"CHECK EXTRACTION"
                    else if(isPending)"PENDING HOLD" else "UNREVIEWED",
                    if(review)"green" else if(attention)"rose" else if(isPending)"warm" else "neutral")
                val holder=edit?.cardholder?.takeIf{it.isNotBlank()} ?:d.cardholder
                if(holder!=null) Text("Cardholder: "+holder,
                    modifier=Modifier.weight(1f),fontSize=11.sp,color=LabColors.textMuted,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                else Spacer(Modifier.weight(1f))
                Text("View  ›",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=LabColors.green)
            }
        }
    }
}
