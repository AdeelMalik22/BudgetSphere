package com.budgetsphere.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.domain.Money

fun categoryIcon(name: String): ImageVector = when(name) {"Person"->Icons.Outlined.Person;"Home"->Icons.Outlined.Home;"Work"->Icons.Outlined.Work;"Travel"->Icons.Outlined.Flight;"Food"->Icons.Outlined.Restaurant;"Shopping"->Icons.Outlined.ShoppingBag;"Transport"->Icons.Outlined.DirectionsBus;"Income"->Icons.Outlined.SouthWest;else->Icons.Outlined.Category}
val iconNames=listOf("Person","Home","Work","Travel","Food","Shopping","Transport","Income","Category")
val colorChoices=listOf(0xFF047857,0xFF1D4ED8,0xFF7E22CE,0xFFB45309,0xFFBE123C,0xFF14213D)
@Composable fun MoneyText(amount: Long,currency: String,hidden: Boolean,large: Boolean=false) {
    Text(if(hidden) "••••••" else Money.format(amount,currency),style=if(large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium)
}
@Composable fun FormField(label: String,value: String,onChange: (String)->Unit,password: Boolean=false,numeric: Boolean=false,multiline: Boolean=false) {
    OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=!multiline,
        visualTransformation=if(password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else if(numeric) KeyboardType.Decimal else KeyboardType.Text,autoCorrectEnabled=!password))
}
@Composable fun Choice(label: String,value: String,options: List<Pair<String,String>>,onChange: (String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Column { Text(label,style=MaterialTheme.typography.labelMedium);Box {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {Text(options.find {it.first==value}?.second?:"Choose $label");Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ExpandMore,null)}
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {options.forEach { (key,text)-> DropdownMenuItem(text={Text(text)},onClick={onChange(key);expanded=false}) }}
    } }
}
@Composable fun SectionCard(title: String,content: @Composable ColumnScope.()->Unit) {
    Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);content()} }
}
@Composable fun EmptyState(title: String,body: String) { Column(Modifier.fillMaxWidth().padding(vertical=32.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(title,style=MaterialTheme.typography.titleLarge);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
@Composable fun ConfirmDialog(title: String,body: String,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=onConfirm){Text("Delete",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
