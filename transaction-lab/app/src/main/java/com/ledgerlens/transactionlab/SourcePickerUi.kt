package com.ledgerlens.transactionlab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SourcePickerUi(
    sources:List<SourceProfile>?,
    selected:Set<String>,
    busy:Boolean,
    notice:String?,
    onLoad:()->Unit,
    onSelect:(Set<String>)->Unit,
    onContinue:()->Unit
){
 var search by rememberSaveable { mutableStateOf("") }
 var candidatesOnly by rememberSaveable { mutableStateOf(true) }
 Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)){
  Text("◈  LEDGERLENS / LAB 02",fontSize=12.sp,color=Color(0xFFBEEBC8),fontWeight=FontWeight.Bold)
  Text("Choose SMS sources",fontSize=27.sp,fontWeight=FontWeight.Bold)
  Text("Choose which senders LedgerLens may parse as transactions.",fontSize=13.sp,color=Color(0xFFB7C8BE))
  Spacer(Modifier.height(16.dp))
  if(sources==null){
   Text("Discover sender names and potential financial alerts on-device. No transactions will be added yet.")
   Spacer(Modifier.height(12.dp))
   Button(onClick=onLoad,enabled=!busy,modifier=Modifier.fillMaxWidth()){
    Text(if(busy)"Discovering sources…" else "Discover SMS sources")
   }
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(notice!=null)Text(notice)
   return@Column
  }
  val suggested=sources.filter{it.suggested}.map{it.sender}.toSet()
  Text(sources.size.toString()+" SMS senders · "+selected.size+" selected · "+suggested.size+" suggested",
   color=Color(0xFFBEEBC8),fontSize=12.sp)
  Spacer(Modifier.height(8.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
   OutlinedButton(onClick={onSelect(suggested)},modifier=Modifier.weight(1f)){Text("Suggested",fontSize=12.sp)}
   OutlinedButton(onClick={onSelect(sources.map{it.sender}.toSet())},modifier=Modifier.weight(1f)){Text("Select all",fontSize=12.sp)}
   OutlinedButton(onClick={onSelect(emptySet())},modifier=Modifier.weight(1f)){Text("Clear",fontSize=12.sp)}
  }
  OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),
   singleLine=true,label={Text("Search bank or SMS sender")})
  Row{
   FilterChip(selected=candidatesOnly,onClick={candidatesOnly=!candidatesOnly},label={Text("Likely financial")})
   Spacer(Modifier.width(10.dp))
   Text("Pick any sender",fontSize=12.sp,color=Color(0xFFB7C8BE),modifier=Modifier.padding(top=15.dp))
  }
  val shown=sources.filter { (!candidatesOnly||it.matches>0||it.sender in selected) &&
   (search.isBlank()||it.label.contains(search,true)) }
  Text(shown.size.toString()+" sources shown",fontSize=11.sp,color=Color(0xFFB7C8BE))
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp),
    contentPadding=PaddingValues(bottom=6.dp)){
   items(shown,key={it.sender}){source->
    Surface(color=Color(0xFF213A31),shape=RoundedCornerShape(12.dp),onClick={
     onSelect(if(source.sender in selected)selected-source.sender else selected+source.sender)
    }){
     Row(Modifier.fillMaxWidth().padding(12.dp)){
      Checkbox(checked=source.sender in selected,onCheckedChange={checked->
       onSelect(if(checked)selected+source.sender else selected-source.sender)
      })
      Column(Modifier.padding(start=6.dp).weight(1f)){
       Text(source.label,fontWeight=FontWeight.Medium)
       Text(source.received.toString()+" received · "+source.matches+" possible transactions",
        fontSize=11.sp,color=Color(0xFFB7C8BE))
      }
     }
    }
   }
  }
  Spacer(Modifier.height(6.dp))
  Button(onClick=onContinue,enabled=selected.isNotEmpty()&&!busy,modifier=Modifier.fillMaxWidth()){
   Text("Scan transactions from "+selected.size+" sources")
  }
  Text("Source selection is saved locally. You can change it any time.",
   fontSize=11.sp,color=Color(0xFFB7C8BE))
 }
}
