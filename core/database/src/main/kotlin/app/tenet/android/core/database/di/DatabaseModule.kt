package app.tenet.android.core.database.di

import app.tenet.android.core.database.MIGRATION_10_11
import app.tenet.android.core.database.MIGRATION_11_12
import app.tenet.android.core.database.MIGRATION_12_13
import app.tenet.android.core.database.MIGRATION_13_14
import app.tenet.android.core.database.MIGRATION_14_15
import app.tenet.android.core.database.MIGRATION_15_16
import app.tenet.android.core.database.MIGRATION_16_17
import app.tenet.android.core.database.MIGRATION_9_10
import android.content.Context
import androidx.room.Room
import app.tenet.android.core.database.TenetDatabase
import app.tenet.android.core.database.MIGRATION_1_2
import app.tenet.android.core.database.MIGRATION_2_3
import app.tenet.android.core.database.MIGRATION_3_4
import app.tenet.android.core.database.MIGRATION_4_5
import app.tenet.android.core.database.MIGRATION_5_6
import app.tenet.android.core.database.MIGRATION_6_7
import app.tenet.android.core.database.MIGRATION_7_8
import app.tenet.android.core.database.MIGRATION_8_9
import app.tenet.android.core.database.dao.EntryDao
import app.tenet.android.core.database.dao.FoodDao
import app.tenet.android.core.database.dao.RunDao
import app.tenet.android.core.database.dao.SkillDao
import app.tenet.android.core.database.dao.SportDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TenetDatabase =
        Room.databaseBuilder(context, TenetDatabase::class.java, "tenet.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
            )
            // Change log for the Google/Firebase sync (see SyncTriggers).
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    app.tenet.android.core.database.SyncTriggers.install(db)
                }
            })
            .build()

    @Provides
    fun provideEntryDao(database: TenetDatabase): EntryDao = database.entryDao()

    @Provides
    fun provideFoodDao(database: TenetDatabase): FoodDao = database.foodDao()

    @Provides
    fun provideSportDao(database: TenetDatabase): SportDao = database.sportDao()

    @Provides
    fun provideSkillDao(database: TenetDatabase): SkillDao = database.skillDao()

    @Provides
    fun provideRunDao(database: TenetDatabase): RunDao = database.runDao()
}
