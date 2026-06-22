/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.devops.process.service.scm

import com.tencent.devops.common.api.constant.CommonMessageCode.GITLAB_INVALID
import com.tencent.devops.common.api.enums.RepositoryConfig
import com.tencent.devops.common.api.enums.RepositoryType
import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.EnvUtils
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.pojo.element.trigger.enums.CodeEventType
import com.tencent.devops.common.service.utils.RetryUtils
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_RETRY_3_FAILED
import com.tencent.devops.repository.api.ServiceGithubResource
import com.tencent.devops.repository.api.ServiceRepositoryResource
import com.tencent.devops.repository.api.scm.ServiceScmFileApiResource
import com.tencent.devops.repository.api.scm.ServiceScmRepositoryApiResource
import com.tencent.devops.repository.api.scm.ServiceScmResource
import com.tencent.devops.repository.pojo.CodeGitRepository
import com.tencent.devops.repository.pojo.GithubCheckRuns
import com.tencent.devops.repository.pojo.GithubCheckRunsResponse
import com.tencent.devops.repository.pojo.GithubRepository
import com.tencent.devops.repository.pojo.Repository
import com.tencent.devops.repository.pojo.credential.AuthRepository
import com.tencent.devops.scm.api.pojo.Content
import com.tencent.devops.scm.api.pojo.Tree
import com.tencent.devops.scm.api.pojo.repository.ScmServerRepository
import com.tencent.devops.scm.pojo.RevisionInfo
import jakarta.ws.rs.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Suppress("ALL")
@Service
class ScmProxyService @Autowired constructor(private val client: Client) {
    private val logger = LoggerFactory.getLogger(ScmProxyService::class.java)

    fun recursiveFetchLatestRevision(
        projectId: String,
        pipelineId: String,
        repositoryConfig: RepositoryConfig,
        branchName: String?,
        variables: Map<String, String>,
        retry: Int = 1
    ): Result<RevisionInfo> {

        return RetryUtils.execute(object : RetryUtils.Action<Result<RevisionInfo>> {

            override fun execute(): Result<RevisionInfo> {
                return getLatestRevision(
                    projectId = projectId,
                    repositoryConfig = repositoryConfig,
                    branchName = branchName,
                    additionalPath = null,
                    variables = variables
                )
            }

            override fun fail(e: Throwable): Result<RevisionInfo> {
                return Result(ERROR_RETRY_3_FAILED.toInt())
            }
        }, retry, 2000)
    }

    fun getLatestRevision(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        branchName: String?,
        additionalPath: String?,
        variables: Map<String, String>?
    ): Result<RevisionInfo> {
        checkRepoID(repositoryConfig)
        val resolvedConfig = renderRepositoryConfig(repositoryConfig, variables)
        return client.get(ServiceScmResource::class).getLatestRevisionByRepo(
            projectId = projectId,
            branchName = branchName,
            additionalPath = additionalPath,
            repositoryConfig = resolvedConfig
        )
    }

    fun getDefaultBranch(
        projectId: String,
        repositoryConfig: RepositoryConfig
    ): String? {
        checkRepoID(repositoryConfig)
        return client.get(ServiceScmResource::class).getDefaultBranchByRepo(
            projectId = projectId,
            repositoryConfig = repositoryConfig
        ).data
    }

    private fun renderRepositoryConfig(
        repositoryConfig: RepositoryConfig,
        variables: Map<String, String>?
    ): RepositoryConfig {
        if (variables.isNullOrEmpty()) {
            return repositoryConfig
        }
        val rawId = repositoryConfig.getRepositoryId()
        val resolved = EnvUtils.parseEnv(rawId, variables)
        return when (repositoryConfig.repositoryType) {
            RepositoryType.ID -> RepositoryConfig(resolved, null, RepositoryType.ID)
            RepositoryType.NAME -> RepositoryConfig(null, resolved, RepositoryType.NAME)
        }
    }

    fun listBranches(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        search: String? = null
    ): Result<List<String>> {
        checkRepoID(repositoryConfig)
        return client.get(ServiceScmResource::class).listBranchesByRepo(
            projectId = projectId,
            repositoryType = repositoryConfig.repositoryType,
            repoHashIdOrName = repositoryConfig.getRepositoryId(),
            search = search
        )
    }

    fun listTags(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        search: String? = null
    ): Result<List<String>> {
        checkRepoID(repositoryConfig)
        return client.get(ServiceScmResource::class).listTagsByRepo(
            projectId = projectId,
            repositoryType = repositoryConfig.repositoryType,
            repoHashIdOrName = repositoryConfig.getRepositoryId(),
            search = search
        )
    }

    fun addGitWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): CodeGitRepository {
        checkRepoID(repositoryConfig)
        return delegateAddWebhook(
            projectId = projectId,
            scmType = ScmType.CODE_GIT,
            codeEventType = codeEventType,
            repositoryConfig = repositoryConfig,
            invalidErrorCode = ProcessMessageCode.GIT_INVALID
        ) as CodeGitRepository
    }

    fun addGitlabWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): Repository {
        checkRepoID(repositoryConfig)
        return delegateAddWebhook(
            projectId = projectId,
            scmType = ScmType.CODE_GITLAB,
            codeEventType = codeEventType,
            repositoryConfig = repositoryConfig,
            invalidErrorCode = GITLAB_INVALID
        )
    }

    fun addSvnWebhook(projectId: String, repositoryConfig: RepositoryConfig): Repository {
        checkRepoID(repositoryConfig)
        return delegateAddWebhook(
            projectId = projectId,
            scmType = ScmType.CODE_SVN,
            codeEventType = null,
            repositoryConfig = repositoryConfig,
            invalidErrorCode = ProcessMessageCode.SVN_INVALID
        )
    }

    fun addTGitWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): Repository {
        checkRepoID(repositoryConfig)
        return delegateAddWebhook(
            projectId = projectId,
            scmType = ScmType.CODE_TGIT,
            codeEventType = codeEventType,
            repositoryConfig = repositoryConfig,
            invalidErrorCode = ProcessMessageCode.TGIT_INVALID
        )
    }

    fun addScmWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): Repository {
        checkRepoID(repositoryConfig)
        return delegateAddWebhook(
            projectId = projectId,
            scmType = ScmType.SCM_GIT,
            codeEventType = codeEventType,
            repositoryConfig = repositoryConfig,
            invalidErrorCode = ProcessMessageCode.SCM_REPO_INVALID
        )
    }

    fun addP4Webhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): Repository {
        checkRepoID(repositoryConfig)
        return delegateAddWebhook(
            projectId = projectId,
            scmType = ScmType.CODE_P4,
            codeEventType = codeEventType,
            repositoryConfig = repositoryConfig,
            invalidErrorCode = ProcessMessageCode.P4_INVALID
        )
    }

    private fun delegateAddWebhook(
        projectId: String,
        scmType: ScmType,
        codeEventType: CodeEventType?,
        repositoryConfig: RepositoryConfig,
        invalidErrorCode: String
    ): Repository {
        val result = client.get(ServiceScmResource::class).addWebhookByRepo(
            projectId = projectId,
            scmType = scmType,
            codeEventType = codeEventType?.name,
            repositoryConfig = repositoryConfig
        )
        return result.data ?: throw ErrorCodeException(
            errorCode = invalidErrorCode,
            defaultMessage = result.message
        )
    }

    fun addGithubCheckRuns(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        name: String,
        commitId: String,
        detailUrl: String,
        externalId: String,
        status: String,
        startedAt: String?,
        conclusion: String?,
        completedAt: String?
    ): GithubCheckRunsResponse {
        logger.info("Project($projectId) add github commit($commitId) check runs")

        checkRepoID(repositoryConfig)
        val repo = getRepo(projectId, repositoryConfig) as? GithubRepository
            ?: throw ErrorCodeException(errorCode = ProcessMessageCode.GITHUB_INVALID)
        val accessToken = getGithubAccessToken(repo.userName)
        val checkRuns = GithubCheckRuns(
            name = name,
            headSha = commitId,
            detailsUrl = detailUrl,
            externalId = externalId,
            status = status,
            startedAt = startedAt,
            conclusion = conclusion,
            completedAt = completedAt
        )

        return client.get(ServiceGithubResource::class).addCheckRuns(
            accessToken = accessToken,
            projectName = repo.projectName,
            checkRuns = checkRuns
        ).data!!
    }

    fun updateGithubCheckRuns(
        checkRunId: Long,
        projectId: String,
        repositoryConfig: RepositoryConfig,
        name: String,
        commitId: String,
        detailUrl: String,
        externalId: String,
        status: String,
        startedAt: LocalDateTime?,
        conclusion: String?,
        completedAt: LocalDateTime?
    ) {
        logger.info("Project($projectId) update github commit($commitId) check runs")

        checkRepoID(repositoryConfig)
        val repo = getRepo(projectId, repositoryConfig) as? GithubRepository
            ?: throw ErrorCodeException(errorCode = ProcessMessageCode.GITHUB_INVALID)
        val accessToken = getGithubAccessToken(repo.userName)
        val checkRuns = GithubCheckRuns(
            name = name,
            headSha = commitId,
            detailsUrl = detailUrl,
            externalId = externalId,
            status = status,
            startedAt = startedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ISO_INSTANT),
            conclusion = conclusion,
            completedAt = completedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ISO_INSTANT)
        )

        client.get(ServiceGithubResource::class).updateCheckRuns(
            accessToken = accessToken,
            projectName = repo.projectName,
            checkRunId = checkRunId,
            checkRuns = checkRuns
        )
    }

    private fun checkRepoID(repositoryConfig: RepositoryConfig) {
        when (repositoryConfig.repositoryType) {
            RepositoryType.ID -> if (repositoryConfig.repositoryHashId.isNullOrBlank()) {
                throw ErrorCodeException(errorCode = ProcessMessageCode.ERROR_PIPELINE_REPO_ID_NULL)
            }
            RepositoryType.NAME -> if (repositoryConfig.repositoryName.isNullOrBlank()) {
                throw ErrorCodeException(errorCode = ProcessMessageCode.ERROR_PIPELINE_REPO_NAME_NULL)
            }
        }
    }

    fun getRepo(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        variables: Map<String, String>? = null
    ): Repository {
        val repositoryId = if (variables == null || variables.isEmpty()) {
            repositoryConfig.getURLEncodeRepositoryId()
        } else {
            URLEncoder.encode(EnvUtils.parseEnv(repositoryConfig.getRepositoryId(), variables), "UTF-8")
        }
        val repoResult =
            client.get(ServiceRepositoryResource::class).get(projectId, repositoryId, repositoryConfig.repositoryType)
        if (repoResult.isNotOk() || repoResult.data == null) {
            logger.warn("$projectId|GET_REPO|$repositoryId|${repositoryConfig.repositoryType}|${repoResult.message}")
            throw ErrorCodeException(errorCode = repoResult.status.toString(), defaultMessage = repoResult.message)
        }
        return repoResult.data!!
    }

    private fun getGithubAccessToken(userName: String): String {
        val accessToken = client.get(ServiceGithubResource::class).getAccessToken(userName).data
            ?: throw NotFoundException("cannot find github oauth accessToekn for user($userName)")
        return accessToken.accessToken
    }

    fun getServerRepository(projectId: String, authRepository: AuthRepository): ScmServerRepository {
        return client.get(ServiceScmRepositoryApiResource::class).getServerRepository(
            projectId = projectId,
            authRepository = authRepository
        ).data!!
    }

    fun getFileContent(
        projectId: String,
        path: String,
        ref: String,
        authRepository: AuthRepository
    ): Content? {
        return client.get(ServiceScmFileApiResource::class).getFileContent(
            projectId = projectId,
            path = path,
            ref = ref,
            authRepository = authRepository
        ).data
    }

    fun listFileTree(
        projectId: String,
        path: String,
        ref: String,
        recursive: Boolean = false,
        authRepository: AuthRepository
    ): List<Tree>? {
        return client.get(ServiceScmFileApiResource::class).listFileTree(
            projectId = projectId,
            path = path,
            ref = ref,
            recursive = recursive,
            authRepository = authRepository
        ).data
    }
}
