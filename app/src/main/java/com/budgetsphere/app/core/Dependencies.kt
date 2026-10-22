package com.budgetsphere.app.core
import android.content.Context
import androidx.room.Room
import com.budgetsphere.app.data.BudgetDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object Dependencies {
    @Provides @Singleton fun database(@ApplicationContext context: Context): BudgetDatabase = Room.databaseBuilder(context, BudgetDatabase::class.java,"budgetsphere.db").build()
    @Provides fun dao(db: BudgetDatabase)=db.dao()
    @Provides @Singleton fun passwords()=PasswordHasher()
}
