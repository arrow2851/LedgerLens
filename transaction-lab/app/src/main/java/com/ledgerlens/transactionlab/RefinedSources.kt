package com.ledgerlens.transactionlab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RefinedSourceScreen(
    allowed:Boolean,sources:List<SourceProfile>?,selected:Set<String>,
    discovering:Boolean,notice:String?,onPermission:()->Unit,
    onDiscover:()->Unit,onSelect:(Set<String>)->Unit,onContinue:()->Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("Suggested") }
    LaunchedEffect(allowed) { if(allowed && sources==null) onDiscover() }
    Column(Modifier.fillMaxSize().background(LabColors.canvas).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).padding(horizontal=22.dp).padding(top=22.dp)) {
            LabChrome(1)
            Spacer(Modifier.height(20.dp))
            LabHeading("Make your money\nmessages matter.",
                "First, choose which SMS senders may create transactions. You stay in control of every source.")
            Spacer(Modifier.height(22.dp))
            if(!allowed) {
                Surface(shape=RoundedCornerShape(22.dp),color=LabColors.paper,
                    border=BorderStroke(1.dp,LabColors.line)) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        LabChip("PERMISSION REQUIRED","warm")
                        Text("Read SMS. Never upload it.",fontWeight=FontWeight.Bold,
                            color=LabColors.ink,fontSize=21.sp)
                        Text("Transaction Lab reads SMS on this device. Message text is not uploaded. Your source choices and corrections remain local.",
                            color=LabColors.textMuted,fontSize=14.sp,lineHeight=21.sp)
                        Spacer(Modifier.height(7.dp))
                        LabPrimary("Allow SMS access",onClick=onPermission)
                    }
                }
            } else if(sources==null) {
                Surface(shape=RoundedCornerShape(22.dp),color=LabColors.paper,
                    border=BorderStroke(1.dp,LabColors.line)){
                    Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        LabChip("ON-DEVICE DISCOVERY","green")
                        Text(if(discovering)"Finding your senders" else "Find financial sources",
                            fontWeight=FontWeight.Bold,color=LabColors.ink,fontSize=19.sp)
                        Text("We'll group senders and estimate which ones contain transaction alerts. Nothing is selected automatically.",
                            color=LabColors.textMuted,fontSize=14.sp,lineHeight=21.sp)
                        if(discovering) LinearProgressIndicator(Modifier.fillMaxWidth(),color=LabColors.green)
                        else LabPrimary("Find SMS senders",onClick=onDiscover)
                        if(notice!=null)Text(notice,color=LabColors.roseInk,fontSize=12.sp)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)) {
                        Text(sources.size.toString()+" senders found",fontSize=17.sp,fontWeight=FontWeight.Bold,color=LabColors.ink)
                        Text(selected.size.toString()+" selected · "+sources.count{it.matches>0}+" with possible transactions",
                            color=LabColors.textMuted,fontSize=12.sp)
                    }
                    LabChip("PRIVATE SCAN","green")
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(value=search,onValueChange={search=it},
                    label={Text("Find a bank or sender")},singleLine=true,
                    leadingIcon={Text("⌕",fontSize=26.sp,color=LabColors.textMuted)},
                    modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),
                    colors=OutlinedTextFieldDefaults.colors(
                        focusedBorderColor=LabColors.green,unfocusedBorderColor=LabColors.line,
                        focusedContainerColor=LabColors.paper,unfocusedContainerColor=LabColors.paper,
                        focusedTextColor=LabColors.ink,unfocusedTextColor=LabColors.ink,
                        focusedLabelColor=LabColors.green,unfocusedLabelColor=LabColors.textMuted
                    ))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment=Alignment.CenterVertically){
                    Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        listOf("Suggested","All").forEach { item ->
                            FilterChip(selected=mode==item,onClick={mode=item},label={Text(item)},
                                colors=FilterChipDefaults.filterChipColors(
                                    selectedContainerColor=LabColors.tint,selectedLabelColor=LabColors.green,
                                    containerColor=LabColors.paper,labelColor=LabColors.textMuted)
                            )
                        }
                    }
                    TextButton(onClick={onSelect(emptySet())}) {
                        Text("Clear",color=LabColors.green,fontSize=13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(mode=="Suggested")"RECOMMENDED SOURCES" else "ALL SMS SENDERS",
                        modifier=Modifier.weight(1f),fontSize=10.sp,letterSpacing=1.2.sp,
                        color=LabColors.textMuted,fontWeight=FontWeight.Bold)
                    TextButton(onClick={onSelect(sources.filter{it.suggested}.map{it.sender}.toSet())}) {
                        Text("Select suggested",color=LabColors.green,fontSize=12.sp)
                    }
                }
                val visible = remember(sources,selected,search,mode) {
                    sources.filter {
                        (mode=="All" || it.matches>0 || it.sender in selected) &&
                        (search.isBlank() || it.label.contains(search.trim(),ignoreCase=true))
                    }.sortedWith(compareByDescending<SourceProfile>{it.sender in selected}
                        .thenByDescending{it.matches}.thenByDescending{it.received})
                }
                if(visible.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical=42.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        Text("Nothing matches",fontSize=18.sp,fontWeight=FontWeight.Bold,color=LabColors.ink)
                        Text("Try another sender name or switch to All.",color=LabColors.textMuted,fontSize=13.sp)
                    }
                } else {
                    LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp),
                        contentPadding=PaddingValues(bottom=14.dp)){
                        items(visible,key={it.sender}){source->
                            val checked=source.sender in selected
                            Surface(color=if(checked)LabColors.tint else LabColors.paper,
                                shape=RoundedCornerShape(17.dp),
                                border=BorderStroke(1.dp,if(checked)LabColors.green.copy(alpha=.45f) else LabColors.line),
                                onClick={onSelect(if(checked)selected-source.sender else selected+source.sender)}) {
                                Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=13.dp),
                                    verticalAlignment=Alignment.CenterVertically){
                                    LabInitials(source.institution?:source.sender,green=checked)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                        Text(source.institution?:source.sender,
                                            fontWeight=FontWeight.SemiBold,color=LabColors.ink,fontSize=15.sp,
                                            maxLines=1,overflow=TextOverflow.Ellipsis)
                                        if(source.institution!=null) Text(source.sender,
                                            fontSize=11.sp,color=LabColors.textMuted,maxLines=1,
                                            overflow=TextOverflow.Ellipsis)
                                        Text(source.matches.toString()+" possible alerts · "+source.received+" received",
                                            fontSize=11.sp,color=LabColors.textMuted)
                                    }
                                    Checkbox(checked=checked,onCheckedChange={check->
                                        onSelect(if(check)selected+source.sender else selected-source.sender)
                                    },colors=CheckboxDefaults.colors(checkedColor=LabColors.green))
                                }
                            }
                        }
                    }
                }
            }
        }
        if(allowed && sources!=null) {
            Surface(shadowElevation=8.dp,color=LabColors.paper) {
                Column(Modifier.fillMaxWidth().padding(start=22.dp,end=22.dp,top=14.dp,bottom=16.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(selected.size.toString()+" selected",fontWeight=FontWeight.Bold,fontSize=15.sp,
                            color=LabColors.ink,modifier=Modifier.weight(1f))
                        val count=sources.filter{it.sender in selected}.sumOf{it.matches}
                        Text(count.toString()+" potential alerts",fontSize=12.sp,color=LabColors.textMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    LabPrimary(if(selected.isEmpty())"Choose at least one source" else "Scan selected sources   →",
                        enabled=selected.isNotEmpty()&&!discovering,onClick=onContinue)
                }
            }
        }
    }
}
