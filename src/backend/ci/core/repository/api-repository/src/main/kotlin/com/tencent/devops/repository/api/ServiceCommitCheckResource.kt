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

package com.tencent.devops.repository.api

import com.tencent.devops.common.api.enums.RepositoryType
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.repository.pojo.RepositoryGitCheck
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Tag(name = "SERVICE_COMMIT", description = "提交检查")
@Path("/service/commit/check/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ServiceCommitCheckResource {
    @Operation(summary = "添加提交检查信息")
    @POST
    @Path("/add")
    fun add(repositoryGitCheck: RepositoryGitCheck)

    @Operation(summary = "添加提交检查信息")
    @PUT
    @Path("/git/{checkId}")
    fun update(
        @Parameter(description = "检查ID", required = true)
        @PathParam("checkId")
        checkId: Long,
        @Parameter(description = "检查ID", required = true)
        @QueryParam("buildNum")
        buildNum: Int,
        @Parameter(description = "github check run id", required = false)
        @QueryParam("checkRunId")
        checkRunId: Long? = null
    )

    @Operation(summary = "获取提交检查信息")
    @GET
    @Path("/pipelines/{pipelineId}/commits/{commitId}")
    fun get(
        @Parameter(description = "流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @Parameter(description = "CommitId", required = true)
        @PathParam("commitId")
        commitId: String,
        @Parameter(description = "目标分支", required = false)
        @QueryParam("targetBranch")
        targetBranch: String?,
        @Parameter(description = "检查名称", required = true)
        @QueryParam("context")
        context: String,
        @Parameter(description = "代码id（hashId或别名）", required = true)
        @QueryParam("repositoryId")
        repositoryId: String,
        @Parameter(description = "代码库类型", required = true)
        @QueryParam("repositoryType")
        repositoryType: RepositoryType
    ): Result<RepositoryGitCheck?>
}