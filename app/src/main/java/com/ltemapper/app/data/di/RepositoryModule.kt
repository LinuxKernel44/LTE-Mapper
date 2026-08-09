package com.ltemapper.app.data.di

import com.ltemapper.app.data.repository.MappingRepositoryImpl
import com.ltemapper.app.domain.repository.MappingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMappingRepository(impl: MappingRepositoryImpl): MappingRepository
}
