package com.vc.jobfinder.di

import android.content.Context
import androidx.room.Room
import com.vc.jobfinder.data.db.AppDatabase
import com.vc.jobfinder.data.local.CompaniesLoader
import com.vc.jobfinder.domain.CompaniesProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "jobfinder.db")
            .fallbackToDestructiveMigration(true)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds
    abstract fun bindCompaniesProvider(impl: CompaniesLoader): CompaniesProvider
}
