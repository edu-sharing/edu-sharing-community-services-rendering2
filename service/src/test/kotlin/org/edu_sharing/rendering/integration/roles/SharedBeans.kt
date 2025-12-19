package org.edu_sharing.rendering.integration.roles

import org.edu_sharing.rendering.ServicesRenderingService2Application
import org.edu_sharing.rendering.cacheCleaner.TrackingService
import org.edu_sharing.rendering.config.*
import org.edu_sharing.rendering.core.dto.mapper.Mapper
import org.edu_sharing.rendering.core.exception.ApiExceptionHandler
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistrationConfig
import org.edu_sharing.rendering.edusharingRepo.services.ContentTransferService
import org.edu_sharing.rendering.edusharingRepo.services.MetadataService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationService
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
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
import org.edu_sharing.rendering.modules.h5p.lumi.LumiContentManagementService
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
import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.edu_sharing.rendering.storage.bucket.BucketPerMediaTypeStrategy
import org.springframework.security.access.PermissionEvaluator
import org.springframework.util.ClassUtils

abstract class SharedBeans {

    // When modifying please maintain alphabetic ordering
    companion object {
        private val classBeans = setOf(
            ApiExceptionHandler::class,
            AppInfo::class,
            AudioRenderModule::class,
            AudioService::class,
            BucketPerMediaTypeStrategy::class,
            ContentTransferService::class,
            DocumentModuleTypeMapper::class,
            DocumentRenderModule::class,
            DocumentService::class,
            EduHtmlRenderModule::class,
            EduHtmlService::class,
            H5pJobService::class,
            H5pRenderModule::class,
            HtmlRenderModule::class,
            ImageRenderModule::class,
            ImageService::class,
            JupyterJobService::class,
            JupyterRenderModule::class,
            JwtUtils::class,
            LumiConfig::class,
            LumiContentManagementService::class,
            MainJobLogic::class,
            MainJobCreationService::class,
            Mapper::class,
            MetadataService::class,
            ModuleRegistry::class,
            MongoConfig::class,
            MoodleRenderModule::class,
            MoodleJobService::class,
            NodePermissionSessionContextRepository::class,
            PermissionEvaluator::class,
            PdfRenderModule::class,
            QueueConfig::class,
            RedisConfig::class,
            RepositoryRegistrationConfig::class,
            RepositoryRegistrationService::class,
            RepositoryRegistrationStorageService::class,
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
