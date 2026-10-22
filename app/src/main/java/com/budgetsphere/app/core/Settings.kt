package com.budgetsphere.app.core

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException
private val Context.settingsStore by preferencesDataStore("settings")
data class Preferences(val theme: String="System", val autoHide: Boolean=true, val screenshotProtection: Boolean=true, val mondayFirst: Boolean=true, val dateFormat: String="dd MMM yyyy")
@Singleton class Settings @Inject constructor(@ApplicationContext private val context: Context) {
    private val store get()=context.settingsStore
    val preferences=store.data.catch { if(it is IOException) emit(emptyPreferences()) else throw it }.map {
        Preferences(it[stringPreferencesKey("theme")]?:"System",it[booleanPreferencesKey("autoHide")]?:true,it[booleanPreferencesKey("screenshots")]?:true,it[booleanPreferencesKey("monday")]?:true,it[stringPreferencesKey("dateFormat")]?:"dd MMM yyyy")
    }
    suspend fun set(key: String, value: Boolean) { require(key in listOf("autoHide","screenshots","monday")); store.edit { it[booleanPreferencesKey(key)]=value } }
    suspend fun set(key: String, value: String) { require(key in listOf("theme","dateFormat")); store.edit { it[stringPreferencesKey(key)]=value } }
    suspend fun restore(preferences: Preferences) {
        store.edit {
            it[stringPreferencesKey("theme")]=preferences.theme
            it[booleanPreferencesKey("autoHide")]=preferences.autoHide
            it[booleanPreferencesKey("screenshots")]=preferences.screenshotProtection
            it[booleanPreferencesKey("monday")]=preferences.mondayFirst
            it[stringPreferencesKey("dateFormat")]=preferences.dateFormat
        }
    }
    suspend fun active(account: String, profile: String) { store.edit { it[stringPreferencesKey("active_$account")]=profile } }
    fun active(account: String)=store.data.map { it[stringPreferencesKey("active_$account")] }
}
