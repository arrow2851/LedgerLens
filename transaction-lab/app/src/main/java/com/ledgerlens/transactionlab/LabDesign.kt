package com.ledgerlens.transactionlab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LedgerLens Field Lab design language:
 * warm canvas / ink text / deep-green actions / soft evergreen highlights.
 * Large tap targets, readable metadata, and persistent primary actions.
 */
internal object LabColors {
    val canvas = Color(0xFFF7F6F2)
    val paper = Color(0xFFFFFFFF)
    val ink = Color(0xFF19322C)
    val green = Color(0xFF245644)
    val textMuted = Color(0xFF64736C)
    val line = Color(0xFFE5E9E3)
    val tint = Color(0xFFE8F2EC)
    val warm = Color(0xFFFFF1DE)
    val warmInk = Color(0xFF946224)
    val rose = Color(0xFFFBEAE6)
    val roseInk = Color(0xFFA34E42)
}

@Composable
internal fun LabChrome(step:Int, onBack:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        if(onBack!=null){
            Surface(onClick=onBack,shape=CircleShape,color=LabColors.paper,
                border=BorderStroke(1.dp,LabColors.line),modifier=Modifier.size(46.dp)){
                Box(contentAlignment=Alignment.Center){ Text("‹",fontSize=31.sp,color=LabColors.ink) }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("LEDGERLENS",fontWeight=FontWeight.Bold,fontSize=13.sp,letterSpacing=2.sp,color=LabColors.ink)
            Text("TRANSACTION LAB  /  03",fontSize=10.sp,letterSpacing=1.2.sp,color=LabColors.textMuted)
        }
        Surface(shape=RoundedCornerShape(20.dp),color=LabColors.tint){
            Text("LOCAL ONLY",modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp),
                fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=LabColors.green)
        }
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()){
        Box(Modifier.weight(1f).height(4.dp).background(if(step>=1)LabColors.green else LabColors.line, RoundedCornerShape(4.dp)))
        Box(Modifier.weight(1f).height(4.dp).background(if(step>=2)LabColors.green else LabColors.line, RoundedCornerShape(4.dp)))
    }
    Spacer(Modifier.height(10.dp))
    Text(if(step==1)"STEP 01   /   CHOOSE SOURCES" else "STEP 02   /   REVIEW TRANSACTIONS",
        color=LabColors.textMuted,fontSize=10.sp,letterSpacing=1.5.sp,fontWeight=FontWeight.Bold)
}

@Composable
internal fun LabHeading(title:String,description:String) {
    Text(title,color=LabColors.ink,fontSize=34.sp,lineHeight=39.sp,
        fontWeight=FontWeight.SemiBold,fontFamily=FontFamily.Serif)
    Spacer(Modifier.height(7.dp))
    Text(description,color=LabColors.textMuted,fontSize=14.sp,lineHeight=21.sp)
}

@Composable
internal fun LabPrimary(label:String,enabled:Boolean=true,onClick:()->Unit,modifier:Modifier=Modifier) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=54.dp),
        shape=RoundedCornerShape(17.dp),
        colors=ButtonDefaults.buttonColors(containerColor=LabColors.green,contentColor=Color.White,
            disabledContainerColor=LabColors.line,disabledContentColor=LabColors.textMuted)){
        Text(label,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
internal fun LabSecondary(label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=46.dp),enabled=enabled,
        shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,LabColors.line),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=LabColors.green)){
        Text(label,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LabChip(label:String,style:String="neutral") {
    val bg=when(style){"green"->LabColors.tint;"warm"->LabColors.warm;"rose"->LabColors.rose;else->LabColors.canvas}
    val fg=when(style){"green"->LabColors.green;"warm"->LabColors.warmInk;"rose"->LabColors.roseInk;else->LabColors.textMuted}
    Surface(shape=RoundedCornerShape(9.dp),color=bg) {
        Text(label,modifier=Modifier.padding(horizontal=10.dp,vertical=5.dp),
            fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=fg,maxLines=1)
    }
}

@Composable
internal fun LabMeta(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier) {
        Text(label.uppercase(),color=LabColors.textMuted,fontSize=10.sp,
            fontWeight=FontWeight.SemiBold,letterSpacing=1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value,color=LabColors.ink,fontSize=14.sp,fontWeight=FontWeight.Medium,
            maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LabInitials(name:String,modifier:Modifier=Modifier,green:Boolean=false) {
    val letters=name.split(Regex("""[\s\-]+""")).filter{it.isNotBlank()}
        .take(2).joinToString(""){it.first().uppercase()}.take(2).ifBlank{"·"}
    Box(modifier.size(46.dp).background(if(green)LabColors.tint else LabColors.canvas, RoundedCornerShape(15.dp)),
        contentAlignment=Alignment.Center) {
        Text(letters,fontSize=13.sp,fontWeight=FontWeight.Bold,color=LabColors.green)
    }
}

@Composable
internal fun LabDivider() {
    HorizontalDivider(color=LabColors.line,thickness=1.dp)
}
