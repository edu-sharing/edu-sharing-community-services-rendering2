package org.edu_sharing.rendering.roles

import org.edu_sharing.generated.repository.backend.services.rest.client.ApiClient
import org.edu_sharing.rendering.ServicesRenderingService2Application
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.config.MongoConfig
import org.edu_sharing.rendering.config.RedisConfig
import org.edu_sharing.rendering.config.SessionConfig
import org.edu_sharing.rendering.config.SpringConfig
import org.edu_sharing.rendering.config.SpringDocConfig
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ApiExceptionHandler
import org.edu_sharing.rendering.edusharingRepo.config.EduSharingConfig
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.edusharingRepo.services.MetadataService
import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.av.audio.AudioRenderModule
import org.edu_sharing.rendering.modules.av.audio.AudioService
import org.edu_sharing.rendering.modules.av.video.VideoConverterConfig
import org.edu_sharing.rendering.modules.av.video.VideoRenderModule
import org.edu_sharing.rendering.modules.av.video.VideoService
import org.edu_sharing.rendering.modules.document.DocumentModuleTypeMapper
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.modules.document.DocumentService
import org.edu_sharing.rendering.modules.document.SpreadsheetRenderModule
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlRenderModule
import org.edu_sharing.rendering.modules.eduhtml.EduHtmlService
import org.edu_sharing.rendering.modules.h5p.H5pJobService
import org.edu_sharing.rendering.modules.h5p.H5pRenderModule
import org.edu_sharing.rendering.modules.h5p.lumi.LumiConfig
import org.edu_sharing.rendering.modules.h5p.lumi.LumiNodeInfoService
import org.edu_sharing.rendering.modules.image.ImageRenderModule
import org.edu_sharing.rendering.modules.image.ImageService
import org.edu_sharing.rendering.modules.jupyter.JupyterJobService
import org.edu_sharing.rendering.modules.jupyter.JupyterRenderModule
import org.edu_sharing.rendering.modules.moodle.MoodleJobService
import org.edu_sharing.rendering.modules.moodle.MoodleRenderModule
import org.edu_sharing.rendering.modules.moodle.ScormRenderModule
import org.edu_sharing.rendering.modules.noConversion.HtmlRenderModule
import org.edu_sharing.rendering.modules.noConversion.PdfRenderModule
import org.edu_sharing.rendering.renderingJob.MainJobCreationService
import org.edu_sharing.rendering.renderingJob.MainJobLogic
import org.edu_sharing.rendering.renderingJob.queue.QueueConfig
import org.edu_sharing.rendering.security.NodePermissionSessionContextRepository
import org.edu_sharing.rendering.security.SecurityDisabledConfig
import org.edu_sharing.rendering.storage.minio.MinioConfig
import org.edu_sharing.rendering.storage.minio.MinioStorageService
import org.edu_sharing.rendering.storage.minio.bucket.BucketPerMediaTypeStrategy
import org.springframework.security.access.PermissionEvaluator
import org.springframework.util.ClassUtils

abstract class SharedBeans {

    // When modifying please maintain alphabetic ordering
    companion object {
        private val classBeans = setOf(
            ApiClient::class,
            ApiExceptionHandler::class,
            AudioRenderModule::class,
            AudioService::class,
            BucketPerMediaTypeStrategy::class,
            ContentTransferService::class,
            DocumentModuleTypeMapper::class,
            DocumentRenderModule::class,
            DocumentService::class,
            EduHtmlRenderModule::class,
            EduHtmlService::class,
            EduSharingConfig::class,
            H5pJobService::class,
            H5pRenderModule::class,
            HtmlRenderModule::class,
            ImageRenderModule::class,
            ImageService::class,
            JupyterJobService::class,
            JupyterRenderModule::class,
            LumiConfig::class,
            LumiNodeInfoService::class,
            MainJobLogic::class,
            MainJobCreationService::class,
            Mapper::class,
            MetadataService::class,
            MinioConfig::class,
            MinioStorageService::class,
            ModuleRegistry::class,
            MongoConfig::class,
            MoodleRenderModule::class,
            MoodleJobService::class,
            NodePermissionSessionContextRepository::class,
            PermissionEvaluator::class,
            PdfRenderModule::class,
            QueueConfig::class,
            RedisConfig::class,
            ScormRenderModule::class,
            SecurityDisabledConfig::class,
            ServicesRenderingService2Application::class,
            SessionConfig::class,
            SpreadsheetRenderModule::class,
            SpringConfig::class,
            SpringDocConfig::class,
            TrackingService::class,
            VideoConverterConfig::class,
            VideoRenderModule::class,
            VideoService::class,
        )

        private val functionalBeans = setOf(
            "auditingDateTimeProvider",
            "permissionEvaluator",
            "eduMinioAdminClient"
        )

        val all = classBeans.map { ClassUtils.getShortNameAsProperty(it.java)} union functionalBeans
    }
}