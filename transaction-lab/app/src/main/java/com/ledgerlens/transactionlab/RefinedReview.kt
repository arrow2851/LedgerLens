package com.ledgerlens.transactionlab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun evidenceText(body:String,p:Parsed) = buildAnnotatedString {
    append(body)
    if(p.amountStart>=0 && p.amountEnd<=body.length && p.amountStart<p.amountEnd){
        addStyle(SpanStyle(background=LabColors.tint,color=LabColors.green,
            fontWeight=FontWeight.Bold),p.amountStart,p.amountEnd)
    }
    if(p.merchantStart>=0 && p.merchantEnd<=body.length && p.merchantStart<p.merchantEnd){
        addStyle(SpanStyle(background=LabColors.warm,color=LabColors.warmInk,
            fontWeight=FontWeight.Bold),p.merchantStart,p.merchantEnd)
    }
}

@Composable
private fun LabField(value:String,label:String,onValueChange:(String)->Unit,
    modifier:Modifier=Modifier,invalid:Boolean=false,support:String?=null) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)){
        OutlinedTextField(value=value,onValueChange=onValueChange,
            label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth(),
            isError=invalid,shape=RoundedCornerShape(14.dp),
            colors=OutlinedTextFieldDefaults.colors(
                focusedBorderColor=LabColors.green,
                unfocusedBorderColor=LabColors.line,
                focusedContainerColor=LabColors.paper,
                unfocusedContainerColor=LabColors.paper,
                focusedTextColor=LabColors.ink,
                unfocusedTextColor=LabColors.ink,
                focusedLabelColor=LabColors.green,
                unfocusedLabelColor=LabColors.textMuted))
        if(support!=null)Text(support,color=if(invalid)LabColors.roseInk else LabColors.textMuted,fontSize=11.sp)
    }
}

@Composable
private fun LabOptionField(label:String,value:String,options:List<String>,onSelect:(String)->Unit,
    modifier:Modifier=Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier){
        Text(label.uppercase(),color=LabColors.textMuted,fontSize=10.sp,letterSpacing=1.sp)
        Spacer(Modifier.height(7.dp))
        Box {
            Surface(onClick={open=true},color=LabColors.paper,
                shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,LabColors.line)){
                Row(Modifier.fillMaxWidth().heightIn(min=51.dp).padding(horizontal=14.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Text(value.replace("_"," ").lowercase().replaceFirstChar{it.uppercase()},
                        color=LabColors.ink,fontSize=13.sp,fontWeight=FontWeight.Medium,
                        modifier=Modifier.weight(1f))
                    Text("⌄",fontSize=20.sp,color=LabColors.textMuted)
                }
            }
            DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                options.forEach { choice ->
                    DropdownMenuItem(text={Text(choice.replace("_"," ").lowercase().replaceFirstChar{it.uppercase()})},
                        onClick={onSelect(choice);open=false})
                }
            }
        }
    }
}

@Composable
internal fun RefinedDetailScreen(
    row:SmsRow,edit:ManualEdit?,onBack:()->Unit,onSave:(ManualEdit)->Unit
){
    val parsed=row.parsed
    val details=row.details
    var amount by remember(row.id,edit){mutableStateOf(edit?.amount ?:moneyString(parsed.amountMinor))}
    var merchant by remember(row.id,edit){mutableStateOf(if(parsed.kind=="PURCHASE")
        (edit?.merchant ?:parsed.merchant.orEmpty()) else "")}
    var counterparty by remember(row.id,edit){mutableStateOf(
        edit?.counterparty?.takeIf{it.isNotBlank()} ?: details.counterparty.orEmpty())}
    var method by remember(row.id,edit){mutableStateOf(
        edit?.paymentMethod?.takeIf{it.isNotBlank()} ?:details.method.orEmpty())}
    var cardholder by remember(row.id,edit){mutableStateOf(
        edit?.cardholder?.takeIf{it.isNotBlank()} ?:details.cardholder.orEmpty())}
    var cardLast4 by remember(row.id,edit){mutableStateOf(
        edit?.cardLast4?.takeIf{it.isNotBlank()} ?:details.cardLast4.orEmpty())}
    var category by remember(row.id,edit){mutableStateOf(edit?.category.orEmpty())}
    var kind by remember(row.id,edit){mutableStateOf(edit?.kind ?:parsed.kind)}
    var direction by remember(row.id,edit){mutableStateOf(edit?.direction ?:parsed.direction)}
    var lifecycle by remember(row.id,edit){mutableStateOf(edit?.lifecycle ?:parsed.lifecycle)}
    var ignored by remember(row.id,edit){mutableStateOf(edit?.ignored ?:false)}
    var evidenceOpen by remember(row.id){mutableStateOf(false)}
    val isAmountValid=amountValid(amount)
    val isCardValid=cardLast4.isBlank() || (cardLast4.length==4 && cardLast4.all{it.isDigit()})
    val canSave=isAmountValid && isCardValid
    val save:(Boolean)->Unit={reviewed->
        if(canSave)onSave(ManualEdit(
            merchant,amount,kind,direction,lifecycle,category,reviewed,ignored,
            counterparty,method,cardholder,cardLast4
        ))
    }
    Column(Modifier.fillMaxSize().background(LabColors.canvas).statusBarsPadding().navigationBarsPadding()){
        Column(Modifier.fillMaxWidth().padding(start=22.dp,end=22.dp,top=22.dp)){
            LabChrome(2,onBack)
            Spacer(Modifier.height(16.dp))
            Text("Transaction details",color=LabColors.ink,
                fontSize=27.sp,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(start=22.dp,end=22.dp,bottom=22.dp),
            verticalArrangement=Arrangement.spacedBy(15.dp)){
            Surface(color=LabColors.ink,shape=RoundedCornerShape(21.dp)){
                Column(Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(if(direction=="IN")"INCOMING" else if(direction=="OUT")"OUTGOING" else "UNCLASSIFIED",
                        color=Color(0xFFB6D8C7),fontSize=10.sp,letterSpacing=1.sp)
                    val c=when(parsed.currency){"USD"->"$";"INR"->"₹";else->""}
                    Text(if(amount.isBlank())"Amount unknown" else c+amount,
                        color=Color.White,fontSize=34.sp,fontWeight=FontWeight.SemiBold)
                    Text(details.title(parsed),color=Color(0xFFF0F6F0),fontSize=15.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        LabChip(lifecycle.replace("_"," "),if(lifecycle=="PENDING")"warm" else "green")
                        LabChip(details.institution?:"Institution unknown","green")
                    }
                }
            }
            if(parsed.outcome=="NEEDS_REVIEW" || lifecycle=="PENDING"){
                Surface(color=LabColors.warm,shape=RoundedCornerShape(13.dp)){
                    Text(if(parsed.outcome=="NEEDS_REVIEW")
                        "This extraction needs verification. Do not treat it as confirmed spending."
                        else "Authorization hold. The SMS does not prove the charge was settled.",
                        color=LabColors.warmInk,fontSize=12.sp,lineHeight=18.sp,
                        modifier=Modifier.padding(13.dp))
                }
            }
            SectionLabel("PAYMENT & PARTICIPANTS","Separate people, methods, and merchants.")
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                LabField(amount, "Amount", {amount=it},
                    modifier=Modifier.weight(1f),invalid=!isAmountValid,
                    support=if(!isAmountValid)"Use up to two decimals" else parsed.currency?:"Currency unknown")
                LabField(method,"Payment method",{method=it},
                    modifier=Modifier.weight(1f))
            }
            if(kind=="PURCHASE"){
                LabField(merchant,"Merchant / place",{merchant=it},
                    support="Where the purchase occurred")
            }
            LabField(counterparty,
                when {
                    kind=="CARD_PAYMENT"->"Card issuer / payee"
                    direction=="IN"->"Sender / source"
                    direction=="OUT"->"Recipient / payee"
                    else->"Counterparty"
                },{counterparty=it},support="Leave blank when the SMS doesn't identify the party")
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                LabField(cardholder,"Named cardholder",{cardholder=it},
                    modifier=Modifier.weight(1f),support="Only if explicit")
                LabField(cardLast4,"Card last four",{cardLast4=it},
                    modifier=Modifier.weight(1f),invalid=!isCardValid,
                    support=if(!isCardValid)"Exactly four digits" else "Optional")
            }
            if(details.accountLast4!=null){
                Surface(color=LabColors.tint,shape=RoundedCornerShape(13.dp)){
                    LabMeta("Account mentioned","•••• "+details.accountLast4,
                        Modifier.fillMaxWidth().padding(13.dp))
                }
            }
            SectionLabel("CLASSIFICATION","Choose what actually happened.")
            LabField(category,"Category",{category=it},
                support="Optional; category automation comes in a later mini app")
            LabOptionField("Transaction type",kind,
                listOf("PURCHASE","TRANSFER","DEPOSIT","CARD_PAYMENT","CASH_WITHDRAWAL","REFUND","REVERSAL","FEE","UNKNOWN")
            ){kind=it}
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                LabOptionField("Direction",direction,
                    listOf("IN","OUT","UNKNOWN"),{direction=it},Modifier.weight(1f))
                LabOptionField("Status",lifecycle,
                    listOf("PENDING","CONFIRMED","FAILED","UNKNOWN","NOT_APPLICABLE"),
                    {lifecycle=it},Modifier.weight(1f))
            }
            Surface(color=LabColors.paper,shape=RoundedCornerShape(17.dp),
                border=BorderStroke(1.dp,LabColors.line)){
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("Exclude this alert",color=LabColors.ink,
                            fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                        Text("Keep it out of the transaction list",color=LabColors.textMuted,fontSize=11.sp)
                    }
                    Switch(checked=ignored,onCheckedChange={ignored=it},
                        colors=SwitchDefaults.colors(checkedTrackColor=LabColors.green))
                }
            }
            SectionLabel("SOURCE EVIDENCE","Original SMS is read from your device.")
            Surface(onClick={evidenceOpen=!evidenceOpen},color=LabColors.paper,
                shape=RoundedCornerShape(17.dp),
                border=BorderStroke(1.dp,LabColors.line)){
                Column(Modifier.fillMaxWidth().padding(16.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(if(evidenceOpen)"Hide original SMS" else "Show original SMS",
                            color=LabColors.green,fontWeight=FontWeight.SemiBold,fontSize=14.sp,
                            modifier=Modifier.weight(1f))
                        Text(if(evidenceOpen)"⌃" else "⌄",color=LabColors.green,fontSize=20.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text("From "+row.sender+" · "+SimpleDateFormat("d MMM yyyy, h:mm a",Locale.getDefault())
                        .format(Date(row.timestamp)),color=LabColors.textMuted,fontSize=11.sp)
                    if(evidenceOpen){
                        Spacer(Modifier.height(14.dp))
                        LabDivider()
                        Spacer(Modifier.height(14.dp))
                        Text(evidenceText(row.body,parsed),fontSize=14.sp,lineHeight=21.sp,color=LabColors.ink)
                    }
                }
            }
            Text("Parser: "+parsed.rule+"  ·  "+parsed.outcome,
                color=LabColors.textMuted,fontSize=11.sp)
            if(details.note!=null)Text(details.note,
                color=LabColors.textMuted,fontSize=12.sp,lineHeight=18.sp)
        }
        Surface(color=LabColors.paper,shadowElevation=8.dp){
            Column(Modifier.fillMaxWidth().padding(start=22.dp,end=22.dp,top=12.dp,bottom=16.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(9.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    LabSecondary("Save draft",onClick={save(false)},enabled=canSave,
                        modifier=Modifier.weight(1f))
                    Box(Modifier.weight(1.7f)){
                        LabPrimary(if(ignored)"Save exclusion" else "Approve & save",
                            enabled=canSave,onClick={save(true)})
                    }
                }
                if(!canSave)Text("Please correct the amount or card last four before saving.",
                    fontSize=11.sp,color=LabColors.roseInk)
            }
        }
    }
}
@Composable
private fun SectionLabel(title:String,description:String){
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text(title,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.15.sp,color=LabColors.ink)
        Text(description,fontSize=12.sp,color=LabColors.textMuted)
    }
}
