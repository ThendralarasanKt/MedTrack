package com.medtrack.app.hybrid.gateway

import com.medtrack.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GatewayModule {
    @Provides
    @Singleton
    fun provideInferenceGateway(
        mock: MockInferenceGateway,
        http: HttpInferenceGateway
    ): InferenceGateway {
        return if (BuildConfig.USE_MOCK_GATEWAY || BuildConfig.GATEWAY_BASE_URL.isBlank()) {
            mock
        } else {
            http
        }
    }
}
