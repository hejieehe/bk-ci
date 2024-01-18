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
 */

package com.tencent.devops.process.api.service

import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.pipeline.PipelineModelWithYaml
import com.tencent.devops.common.pipeline.PipelineModelWithYamlRequest
import com.tencent.devops.common.pipeline.pojo.TemplateInstanceCreateRequest
import com.tencent.devops.process.engine.pojo.PipelineVersionWithInfo
import com.tencent.devops.process.pojo.PipelineOperationDetail
import com.tencent.devops.common.pipeline.pojo.transfer.PreviewResponse
import com.tencent.devops.process.pojo.PipelineDetail
import com.tencent.devops.process.pojo.PipelineVersionReleaseRequest
import com.tencent.devops.process.pojo.pipeline.DeployPipelineResult
import com.tencent.devops.process.pojo.setting.PipelineVersionSimple
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.validation.Valid
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Api(tags = ["SERVICE_PIPELINE_VERSION"], description = "服务-流水线版本管理")
@Path("/service/version")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Suppress("LongParameterList")
interface ServicePipelineVersionResource {

    @ApiOperation("获取流水线信息（含草稿）")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/detail")
    fun getPipelineDetailIncludeDraft(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String
    ): Result<PipelineDetail>

    @ApiOperation("将当前模板发布为正式版本")
    @POST
    @Path("/projects/{projectId}/pipelines/{pipelineId}/releaseVersion/{version}")
    fun releaseVersion(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("流水线编排版本", required = true)
        @PathParam("version")
        version: Int,
        @ApiParam(value = "发布流水线版本请求", required = true)
        request: PipelineVersionReleaseRequest
    ): Result<DeployPipelineResult>

    @ApiOperation("通过指定模板创建流水线")
    @POST
    @Path("/projects/{projectId}/createPipelineWithTemplate")
    fun createPipelineFromTemplate(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam(value = "流水线模型实例请求", required = true)
        request: TemplateInstanceCreateRequest
    ): Result<DeployPipelineResult>

    @ApiOperation("获取流水线指定版本的两种编排")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/versions/{version}")
    fun getVersion(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("流水线编排版本", required = true)
        @PathParam("version")
        version: Int
    ): Result<PipelineModelWithYaml>

    @ApiOperation("触发前配置")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/previewCode")
    fun preview(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线id", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("流水线版本号", required = false)
        @QueryParam("version")
        version: Int?
    ): Result<PreviewResponse>

    @ApiOperation("保存流水线编排草稿")
    @POST
    @Path("/projects/{projectId}/saveDraft")
    fun savePipelineDraft(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam(value = "流水线模型与设置", required = true)
        @Valid
        modelAndYaml: PipelineModelWithYamlRequest
    ): Result<DeployPipelineResult>

    @ApiOperation("获取流水线编排创建人列表（分页）")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/creatorList")
    fun creatorList(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("第几页", required = false, defaultValue = "1")
        @QueryParam("page")
        page: Int?,
        @ApiParam("每页多少条", required = false, defaultValue = "20")
        @QueryParam("pageSize")
        pageSize: Int?
    ): Result<Page<String>>

    @ApiOperation("流水线编排版本列表（搜索、分页）")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/versions")
    fun versionList(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("跳转定位的版本号", required = false)
        @QueryParam("fromVersion")
        fromVersion: Int? = null,
        @ApiParam("搜索字段：版本名包含字符", required = false)
        @QueryParam("versionName")
        versionName: String? = null,
        @ApiParam("搜索字段：创建人", required = false)
        @QueryParam("creator")
        creator: String? = null,
        @ApiParam("搜索字段：变更说明", required = false)
        @QueryParam("description")
        description: String? = null,
        @ApiParam("第几页", required = false, defaultValue = "1")
        @QueryParam("page")
        page: Int?,
        @ApiParam("每页多少条", required = false, defaultValue = "5")
        @QueryParam("pageSize")
        pageSize: Int?
    ): Result<Page<PipelineVersionWithInfo>>

    @ApiOperation("获取流水线操作日志列表（分页）")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/operationLog")
    fun getPipelineOperationLogs(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam("第几页", required = false, defaultValue = "1")
        @QueryParam("page")
        page: Int?,
        @ApiParam("每页多少条", required = false, defaultValue = "20")
        @QueryParam("pageSize")
        pageSize: Int?
    ): Result<Page<PipelineOperationDetail>>

    @ApiOperation("获取流水线操作人列表（分页）")
    @GET
    @Path("/projects/{projectId}/pipelines/{pipelineId}/operatorList")
    fun operatorList(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String
    ): Result<List<String>>

    @ApiOperation("回滚到指定的历史版本并覆盖草稿")
    @POST
    @Path("/projects/{projectId}/pipelines/{pipelineId}/rollbackDraft")
    fun rollbackDraftFromVersion(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam("流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam(value = "回回滚目标版本", required = true)
        @QueryParam("version")
        version: Int
    ): Result<PipelineVersionSimple>

    @ApiOperation("导出流水线模板")
    @GET
    @Path("{pipelineId}/projects/{projectId}/export")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun exportPipeline(
        @ApiParam(value = "用户ID", required = true, defaultValue = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam(value = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @ApiParam(value = "流水线Id", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @ApiParam(value = "导出的目标版本", required = false)
        @QueryParam("version")
        version: Int?,
        @ApiParam(value = "导出的数据类型", required = false)
        @QueryParam("storageType")
        storageType: String?
    ): Response
}