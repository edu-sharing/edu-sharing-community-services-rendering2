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
import kotlin.text.replaceFirstChar
import kotlin.text.substringAfterLast

abstract class SharedBeans {

    // When modifying please maintain alphabetic ordering
    companion object {
        val set = setOf<String>(
            ApiClient::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ApiExceptionHandler::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            AudioRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            AudioService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            BucketPerMediaTypeStrategy::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ContentTransferService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            DocumentModuleTypeMapper::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            DocumentRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            DocumentService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            EduHtmlRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            EduHtmlService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            EduSharingConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            H5pJobService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            H5pRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            HtmlRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ImageRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ImageService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            JupyterJobService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            JupyterRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            LumiConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            LumiNodeInfoService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MainJobLogic::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MainJobCreationService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            Mapper::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MetadataService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MinioConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MinioStorageService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ModuleRegistry::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MongoConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MoodleRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            MoodleJobService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            NodePermissionSessionContextRepository::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            PermissionEvaluator::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            PdfRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            QueueConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            RedisConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ScormRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            SecurityDisabledConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            ServicesRenderingService2Application::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            SessionConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            SpreadsheetRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            SpringConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            SpringDocConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            TrackingService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            VideoConverterConfig::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            VideoRenderModule::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
            VideoService::class.toString().substringAfterLast('.').replaceFirstChar { it.lowercase() },
        )

        val functionalBeans = setOf(
            "auditingDateTimeProvider",
            "permissionEvaluator",
            "eduMinioAdminClient"
        )
    }
}