package com.ltemapper.app.telephony

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelephonyModule {
    @Binds
    @Singleton
    abstract fun bindTelephonyRepository(impl: TelephonyRepositoryImpl): TelephonyRepository
}
