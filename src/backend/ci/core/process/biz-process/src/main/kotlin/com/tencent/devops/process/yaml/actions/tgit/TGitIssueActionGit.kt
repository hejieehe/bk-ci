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

package com.tencent.devops.process.yaml.actions.tgit

import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.webhook.pojo.code.git.GitIssueEvent
import com.tencent.devops.process.yaml.actions.BaseAction
import com.tencent.devops.process.yaml.actions.GitActionCommon
import com.tencent.devops.process.yaml.actions.GitBaseAction
import com.tencent.devops.process.yaml.actions.data.ActionMetaData
import com.tencent.devops.process.yaml.actions.data.EventCommonData
import com.tencent.devops.process.yaml.git.pojo.ApiRequestRetryInfo
import com.tencent.devops.process.yaml.git.service.TGitApiService
import com.tencent.devops.process.yaml.pojo.CheckType
import com.tencent.devops.process.yaml.pojo.YamlContent
import com.tencent.devops.process.yaml.pojo.YamlPathListEntry
import com.tencent.devops.process.yaml.v2.enums.StreamObjectKind
import com.tencent.devops.scm.utils.code.git.GitUtils
import org.slf4j.LoggerFactory

class TGitIssueActionGit(
    private val apiService: TGitApiService,
) : TGitActionGit(apiService), GitBaseAction {

    companion object {
        private val logger = LoggerFactory.getLogger(TGitIssueActionGit::class.java)
    }

    override val metaData: ActionMetaData = ActionMetaData(StreamObjectKind.ISSUE)

    override fun event() = data.event as GitIssueEvent

    override val api: TGitApiService
        get() = apiService

    override fun init(): BaseAction? {
        return initCommonData()
    }

    private fun initCommonData(): GitBaseAction {
        val event = event()
        val gitProjectId = event.objectAttributes.projectId

        val defaultBranch = apiService.getGitProjectInfo(
            cred = this.getGitCred(),
            gitProjectId = gitProjectId.toString(),
            retry = ApiRequestRetryInfo(true)
        )!!.defaultBranch!!
        this.data.eventCommon = EventCommonData(
            gitProjectId = event.objectAttributes.projectId.toString(),
            scmType = ScmType.CODE_GIT,
            branch = defaultBranch,
            userId = event.user.username,
            projectName = GitUtils.getProjectName(event.repository.homepage)
        )
        return this
    }

    override fun getYamlPathList(): List<YamlPathListEntry> {
        return GitActionCommon.getYamlPathList(
            action = this,
            gitProjectId = this.getGitProjectIdOrName(),
            ref = this.data.eventCommon.branch
        ).map { (name, blobId) ->
            YamlPathListEntry(name, CheckType.NO_NEED_CHECK, this.data.eventCommon.branch, blobId)
        }
    }

    override fun getYamlContent(fileName: String): YamlContent {
        return YamlContent(
            ref = data.eventCommon.branch,
            content = api.getFileContent(
                cred = this.getGitCred(),
                gitProjectId = getGitProjectIdOrName(),
                fileName = fileName,
                ref = data.eventCommon.branch,
                retry = ApiRequestRetryInfo(true)
            )
        )
    }
}