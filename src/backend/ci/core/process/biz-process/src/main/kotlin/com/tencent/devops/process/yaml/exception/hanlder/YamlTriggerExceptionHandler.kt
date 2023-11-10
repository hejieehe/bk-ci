/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.tencent.devops.process.yaml.exception.hanlder

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.I18Variable
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.pojo.trigger.PipelineTriggerDetailBuilder
import com.tencent.devops.process.pojo.trigger.PipelineTriggerReason
import com.tencent.devops.process.pojo.trigger.PipelineTriggerStatus
import com.tencent.devops.process.trigger.PipelineTriggerEventService
import com.tencent.devops.process.yaml.PipelineYamlService
import com.tencent.devops.process.yaml.PipelineYamlSyncService
import com.tencent.devops.process.yaml.actions.BaseAction
import com.tencent.devops.process.yaml.actions.pacActions.PacEnableAction
import com.tencent.devops.process.yaml.exception.YamlTriggerException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * pac触发统一异常处理
 */
@Service
class YamlTriggerExceptionHandler(
    private val pipelineYamlSyncService: PipelineYamlSyncService,
    private val pipelineTriggerEventService: PipelineTriggerEventService,
    private val pipelineYamlService: PipelineYamlService,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(YamlTriggerExceptionHandler::class.java)
    }

    fun <T> handle(
        action: BaseAction,
        f: () -> T?
    ): T? {
        try {
            return f()
        } catch (e: Throwable) {
            return try {
                when (e) {
                    is YamlTriggerException -> handleYamlTriggerException(e)
                    is ErrorCodeException -> handleErrorCodeException(action = action, e = e)
                    else -> {
                        logger.warn("YamlTriggerExceptionHandler|Unknown error|action|${action.format()}", e)
                        handleYamlTriggerException(
                            YamlTriggerException(
                                action = action,
                                reason = PipelineTriggerReason.UNKNOWN_ERROR,
                                errorCode = ProcessMessageCode.UNKNOWN_ERROR,
                                errorMessage = e.message
                            )
                        )
                    }
                }
                null
            } catch (e: Throwable) {
                // 防止Hanlder处理过程中报错，兜底
                logger.error("BKSystemErrorMonitor|PacTriggerExceptionHandler|action|${action.format()}", e)
                null
            }
        }
    }

    /**
     * 其他微服务调用的部分异常会被整合为errorCodeEx，这里做保守处理
     */
    private fun handleErrorCodeException(action: BaseAction, e: ErrorCodeException) {
        handleYamlTriggerException(
            YamlTriggerException(
                action = action,
                reason = PipelineTriggerReason.TRIGGER_FAILED,
                errorCode = e.errorCode,
                params = e.params
            )
        )
    }

    /**
     * 对已知触发异常做逻辑处理
     */
    private fun handleYamlTriggerException(e: YamlTriggerException) {
        val action = e.action
        val projectId = action.data.setting.projectId
        val repoHashId = action.data.setting.repoHashId
        val yamlFile = action.data.context.yamlFile!!
        val pipeline = action.data.context.pipeline
        val reason = e.reason.name
        val reasonDetail = if (e.reason == PipelineTriggerReason.UNKNOWN_ERROR) {
            e.errorMessage ?: PipelineTriggerReason.UNKNOWN_ERROR.detail
        } else {
            I18Variable(code = e.errorCode, params = e.params?.toList()).toJsonStr()
        }
        val pipelineTriggerDetail = PipelineTriggerDetailBuilder()
            .projectId(action.data.setting.projectId)
            .detailId(pipelineTriggerEventService.getDetailId())
            .status(PipelineTriggerStatus.FAILED.name)
            .pipelineId(action.data.context.pipeline?.filePath ?: "")
            .reason(reason)
            .reasonDetail(reasonDetail)
            .build()
        pipelineTriggerEventService.saveTriggerDetail(pipelineTriggerDetail)
        // 如果开启pac异常,需要把同步结果写回代码库
        if (action is PacEnableAction) {
            pipelineYamlSyncService.syncFailed(
                projectId = projectId,
                repoHashId = repoHashId,
                filePath = action.data.context.yamlFile!!.yamlPath,
                reason = reason,
                reasonDetail = reasonDetail
            )
        }
        if (pipeline != null) {
            pipelineYamlService.saveFailed(
                projectId = projectId,
                repoHashId = action.data.setting.repoHashId,
                filePath = yamlFile.yamlPath,
                blobId = yamlFile.blobId!!,
                ref = action.data.eventCommon.branch,
                pipelineId = pipeline.pipelineId,
                reason = reason,
                reasonDetail = reasonDetail,
                userId = action.data.getUserId()
            )
        }
    }
}