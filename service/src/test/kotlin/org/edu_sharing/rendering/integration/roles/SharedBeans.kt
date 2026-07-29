package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.ServicesRenderingService2Application
import org.edu_sharing.rendering.cacheCleaner.CustomTrackingEntryRepositoryImpl
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.config.*
import org.edu_sharing.rendering.core.PingController
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ApiExceptionHandler
import org.edu_sharing.rendering.edusharingRepo.AuthHeaderProvider
import org.edu_sharing.rendering.edusharingRepo.EncryptionService
import org.edu_sharing.rendering.edusharingRepo.RestClientProvider
import org.edu_sharing.rendering.edusharingRepo.TracePropagatingInterceptor
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.edusharingRepo.services.MetadataService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.modules.ConverterWebServiceCaller
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.av.AvModuleTypeMapper
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.av.audio.AudioService
import org.edu_sharing.rendering.modules.av.video.VideoConverterConfig
import org.edu_sharing.rendering.modules.av.video.VideoRenderModule
import org.edu_sharing.rendering.modules.av.video.VideoService
import org.edu_sharing.rendering.modules.binder.*
import org.edu_sharing.rendering.modules.binder.git.GitHubConfig
import org.edu_sharing.rendering.modules.binder.git.GitHubService
import org.edu_sharing.rendering.modules.binder.git.GitServiceRegistry
import org.edu_sharing.rendering.modules.ddb.*
import org.edu_sharing.rendering.modules.document.DocumentModuleTypeMapper
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.modules.document.DocumentService
import org.edu_sharing.rendering.modules.document.SpreadsheetRenderModule
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlRenderModule
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlRenderModuleTypeMapper
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.edu_sharing.rendering.modules.h5p.H5pJobService
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.H5pRenderModuleTypeMapper
import org.edu_sharing.rendering.modules.h5p.lumi.LumiConfig
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
import org.edu_sharing.rendering.modules.h5p.lumi.LumiStorageManager
import org.edu_sharing.rendering.modules.image.ImageRenderModule
import org.edu_sharing.rendering.modules.image.ImageRenderModuleTypeMapper
import org.edu_sharing.rendering.modules.image.ImageService
import org.edu_sharing.rendering.modules.jupyter.JupyterJobService
import org.edu_sharing.rendering.modules.jupyter.JupyterRenderModule
import org.edu_sharing.rendering.modules.jupyter.JupyterRenderModuleTypeMapper
import org.edu_sharing.rendering.modules.moodle.*
import org.edu_sharing.rendering.modules.noConversion.HtmlRenderModule
import org.edu_sharing.rendering.modules.noConversion.NoConversionModuleTypeMapper
import org.edu_sharing.rendering.modules.noConversion.PdfRenderModule
import org.edu_sharing.rendering.modules.omega.OmegaRemoteConfig
import org.edu_sharing.rendering.modules.omega.OmegaRenderModule
import org.edu_sharing.rendering.modules.omega.OmegaRenderModuleTypeMapper
import org.edu_sharing.rendering.modules.onyx.OnyxRenderModule
import org.edu_sharing.rendering.modules.sodix.SodixRemoteConfig
import org.edu_sharing.rendering.modules.sodix.SodixRenderModule
import org.edu_sharing.rendering.modules.sodix.SodixRenderModuleTypeMapper
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.metrics.QueueConsumerMetrics
import org.edu_sharing.rendering.renderingJob.queue.AsyncAckDispatcher
import org.edu_sharing.rendering.renderingJob.queue.RemoteListenerContainerFactorySupport
import org.edu_sharing.rendering.renderingJob.metrics.RenderingMetrics
import org.edu_sharing.rendering.modules.av.AvQueueProperties
import org.edu_sharing.rendering.modules.document.DocumentQueueProperties
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlQueueProperties
import org.edu_sharing.rendering.modules.h5p.H5pLookupQueueProperties
import org.edu_sharing.rendering.modules.h5p.H5pQueueProperties
import org.edu_sharing.rendering.modules.image.ImageQueueProperties
import org.edu_sharing.rendering.modules.jupyter.JupyterQueueProperties
import org.edu_sharing.rendering.modules.omega.OmegaQueueProperties
import org.edu_sharing.rendering.modules.onyx.OnyxQueueProperties
import org.edu_sharing.rendering.modules.sodix.SodixQueueProperties
import org.edu_sharing.rendering.renderingJob.queue.JobQueueProperties
import org.edu_sharing.rendering.renderingJob.queue.QueueConfig
import org.edu_sharing.rendering.renderingJob.queue.QueueProperties
import org.edu_sharing.rendering.renderingJob.repository.CustomRenderingJobRepositoryImpl
import org.edu_sharing.rendering.renderingJob.repository.CustomSubJobRepositoryImpl
import org.edu_sharing.rendering.security.*
import org.edu_sharing.rendering.security.cors.CorsConfig
import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.edu_sharing.rendering.storage.*
import org.edu_sharing.rendering.storage.bucket.BucketPerMediaTypeStrategy
import org.springframework.security.access.PermissionEvaluator
import org.springframework.util.ClassUtils

abstract class SharedBeans {

    // When modifying, please maintain alphabetic ordering
    companion object {
        private val classBeans = setOf(
            ApiExceptionHandler::class,
            AppInfo::class,
            AsyncAckDispatcher::class,
            AudioRenderModule::class,
            AudioService::class,
            AuthHeaderProvider::class,
            AvModuleTypeMapper::class,
            AvQueueProperties::class,
            BinderMainJobLogic::class,
            BinderPreviewQueueProperties::class,
            BinderPreviewService::class,
            BinderQueueProperties::class,
            BinderRenderModule::class,
            BinderRenderModuleTypeMapper::class,
            BinderService::class,
            BinderUploadService::class,
            CustomHttpSessionIdResolver::class,
            CustomRenderingJobRepositoryImpl::class,
            CustomSubJobRepositoryImpl::class,
            CustomTrackingEntryRepositoryImpl::class,
            GitHubConfig::class,
            GitHubService::class,
            GitServiceRegistry::class,
            BucketPerMediaTypeStrategy::class,
            ContentTransferService::class,
            ConverterWebServiceCaller::class,
            CorsConfig::class,
            DdbApiProperties::class,
            DdbApiService::class,
            DdbJobService::class,
            DdbQueueProperties::class,
            DdbRemoteConfig::class,
            DdbRenderModule::class,
            DdbRenderModuleTypeMapper::class,
            DocumentModuleTypeMapper::class,
            DocumentQueueProperties::class,
            DocumentRenderModule::class,
            DocumentService::class,
            EduHtmlQueueProperties::class,
            EduHtmlRenderModule::class,
            EduHtmlRenderModuleTypeMapper::class,
            EduHtmlService::class,
            EncryptionService::class,
            H5pJobService::class,
            H5pLookupQueueProperties::class,
            H5pQueueProperties::class,
            H5pRenderModule::class,
            H5pRenderModuleTypeMapper::class,
            HtmlRenderModule::class,
            ImageQueueProperties::class,
            ImageRenderModule::class,
            ImageRenderModuleTypeMapper::class,
            ImageService::class,
            JobQueueProperties::class,
            JupyterJobService::class,
            JupyterQueueProperties::class,
            JupyterRenderModule::class,
            JupyterRenderModuleTypeMapper::class,
            JwtUtils::class,
            LumiConfig::class,
            LumiContentManagementService::class,
            LumiStorageManager::class,
            MainJobLogic::class,
            MainJobCreationService::class,
            Mapper::class,
            MetadataService::class,
            ModulePermissionService::class,
            ModuleRegistry::class,
            MongoConfig::class,
            MoodleQueueProperties::class,
            MoodleRenderModule::class,
            MoodleRenderModuleTypeMapper::class,
            MoodleJobService::class,
            NoConversionModuleTypeMapper::class,
            NodePermissionSessionContextRepository::class,
            NodeSessionContextRepository::class,
            OmegaQueueProperties::class,
            OmegaRemoteConfig::class,
            OmegaRenderModule::class,
            OmegaRenderModuleTypeMapper::class,
            OnyxQueueProperties::class,
            OnyxRenderModule::class,
            PermissionEvaluator::class,
            PdfRenderModule::class,
            PingController::class,
            QueueConfig::class,
            QueueConsumerMetrics::class,
            QueueProperties::class,
            RedisConfig::class,
            RedisStandaloneConfigurationProperties::class,
            RemoteListenerContainerFactorySupport::class,
            RenderingMetrics::class,
            RepositoryRegistrationConfig::class,
            RepositoryRegistrationService::class,
            RepositoryRegistrationStorageService::class,
            RestClientProvider::class,
            Rs2StorageManager::class,
            S3Config::class,
            S3HealthIndicator::class,
            S3StorageService::class,
            SchedulingConfig::class,
            ScormRenderModule::class,
            ScormRenderModuleTypeMapper::class,
            SecurityDisabledConfig::class,
            ServicesRenderingService2Application::class,
            SessionConfig::class,
            ShedLockConfig::class,
            SodixQueueProperties::class,
            SodixRemoteConfig::class,
            SodixRenderModule::class,
            SodixRenderModuleTypeMapper::class,
            SpreadsheetRenderModule::class,
            SpringConfig::class,
            SpringDocConfig::class,
            StorageManagerRegistry::class,
            TracePropagatingInterceptor::class,
            TrackingService::class,
            VideoConverterConfig::class,
            VideoRenderModule::class,
            VideoService::class,
            WebClientConfig::class,
        )

        private val functionalBeans = setOf(
            "auditingDateTimeProvider",
            "permissionEvaluator",
            "writeConcernResolver",
        )

        val all = classBeans.map { ClassUtils.getShortNameAsProperty(it.java)} union functionalBeans
    }
}
