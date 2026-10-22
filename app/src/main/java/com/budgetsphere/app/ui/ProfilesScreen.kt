package com.budgetsphere.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.budgetsphere.app.data.ProfileEntity
import java.util.Currency

@Composable fun ProfilesScreen(vm: AppViewModel,onSelected: ()->Unit) {
    val profiles by vm.profiles.collectAsState()
    val busy by vm.busy.collectAsState()
    var edit by remember {mutableStateOf<ProfileEntity?>(null)}
    var showForm by remember {mutableStateOf(false)}
    var delete by remember {mutableStateOf<ProfileEntity?>(null)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=20.dp)) {
        item {Text("Your workspaces",style=MaterialTheme.typography.headlineMedium);Text("Separate budgets, one private account.")}
        if(profiles.isEmpty()) item {EmptyState("Create your first profile","Start with Personal, Family, or a workspace of your own.")}
        items(profiles,key={it.id}) { profile->
            Card {Column(Modifier.padding(16.dp)) {
                Row {Icon(categoryIcon(profile.icon),null,tint=Color(profile.color));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(profile.name,style=MaterialTheme.typography.titleLarge);Text("${profile.currency} · ${profile.description}")}}
                Row {TextButton(enabled=!busy,onClick={vm.perform {vm.finance.select(profile.id);onSelected()}}){Text("Open")};TextButton(onClick={edit=profile;showForm=true}){Text("Edit")};TextButton(onClick={delete=profile}){Text("Delete",color=MaterialTheme.colorScheme.error)}}
            }}
        }
        item {Button(enabled=!busy,onClick={edit=null;showForm=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text("Create profile")}}
    }
    if(showForm) ProfileForm(edit,vm,{showForm=false})
    delete?.let { profile->ConfirmDialog("Delete ${profile.name}?","All transactions, categories, budgets, and receipts in this profile will be permanently deleted.",{delete=null},{vm.perform("Profile deleted") {vm.finance.deleteProfile(profile.id)};delete=null}) }
}
@Composable private fun ProfileForm(profile: ProfileEntity?,vm: AppViewModel,dismiss: ()->Unit) {
    var name by remember {mutableStateOf(profile?.name?:"")};var description by remember {mutableStateOf(profile?.description?:"")};var currency by remember {mutableStateOf(profile?.currency?:"PKR")};var color by remember {mutableStateOf(profile?.color?:colorChoices.first())};var icon by remember {mutableStateOf(profile?.icon?:"Person")}
    val busy by vm.busy.collectAsState()
    AlertDialog(onDismissRequest=dismiss,title={Text(if(profile==null)"New profile" else "Edit profile")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        FormField("Profile name",name,{name=it});FormField("Description (optional)",description,{description=it})
        Choice("Currency",currency,Currency.getAvailableCurrencies().sortedBy {it.currencyCode}.map {it.currencyCode to it.currencyCode},{currency=it})
        Choice("Icon",icon,iconNames.map {it to it},{icon=it})
        Choice("Color",color.toString(),colorChoices.mapIndexed {i,c->c.toString() to listOf("Emerald","Blue","Violet","Amber","Rose","Navy")[i]},{color=it.toLong()})
        Text("Currency cannot change once this profile has transactions or budgets. No exchange conversion is performed.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=!busy,onClick={vm.perform {val saved=vm.finance.saveProfile(profile?.id,name,description,currency,color,icon);vm.finance.select(saved.id);dismiss()}}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
