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

package com.tencent.devops.repository.service.scm

import com.tencent.devops.common.api.constant.CommonMessageCode.GITLAB_INVALID
import com.tencent.devops.common.api.enums.RepositoryConfig
import com.tencent.devops.common.api.enums.RepositoryType
import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.pipeline.pojo.element.trigger.enums.CodeEventType
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.utils.Credential
import com.tencent.devops.process.utils.CredentialUtils
import com.tencent.devops.repository.pojo.CodeGitRepository
import com.tencent.devops.repository.pojo.CodeGitlabRepository
import com.tencent.devops.repository.pojo.CodeP4Repository
import com.tencent.devops.repository.pojo.CodeSvnRepository
import com.tencent.devops.repository.pojo.CodeTGitRepository
import com.tencent.devops.repository.pojo.GithubRepository
import com.tencent.devops.repository.pojo.Repository
import com.tencent.devops.repository.pojo.ScmGitRepository
import com.tencent.devops.repository.pojo.ScmSvnRepository
import com.tencent.devops.repository.pojo.credential.AuthRepository
import com.tencent.devops.repository.pojo.enums.RepoAuthType
import com.tencent.devops.repository.pojo.enums.TokenTypeEnum
import com.tencent.devops.repository.service.RepoCredentialService
import com.tencent.devops.repository.service.RepositoryService
import com.tencent.devops.repository.service.github.IGithubService
import com.tencent.devops.repository.service.hub.ScmRepositoryApiService
import com.tencent.devops.repository.service.tgit.TGitOAuthService
import com.tencent.devops.scm.api.enums.ScmEventType
import com.tencent.devops.scm.api.pojo.repository.git.GitScmServerRepository
import com.tencent.devops.scm.code.git.CodeGitWebhookEvent
import com.tencent.devops.scm.pojo.RevisionInfo
import com.tencent.devops.ticket.pojo.enums.CredentialType
import jakarta.ws.rs.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * SCM 代码库支持服务
 */
@Service
@Suppress("LongMethod", "ComplexMethod", "TooManyFunctions", "ALL")
class ScmRepositorySupportService @Autowired constructor(
    private val repositoryService: RepositoryService,
    private val repoCredentialService: RepoCredentialService,
    private val scmService: IScmService,
    private val scmOauthService: IScmOauthService,
    private val githubService: IGithubService,
    private val gitOauthService: IGitOauthService,
    private val tGitOAuthService: TGitOAuthService,
    private val scmRepositoryApiService: ScmRepositoryApiService,
    private val gitService: IGitService
) {
    fun listBranches(
        projectId: String,
        repositoryType: RepositoryType?,
        repoHashIdOrName: String,
        search: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<String>> {
        val repository = getRepositoryByHashIdOrName(projectId, repositoryType, repoHashIdOrName)
        val branches = RepositoryDispatcher<List<String>>().apply {
            onCodeSvn { repo ->
                val credInfo = getCredential(projectId, repo)
                scmService.listBranches(
                    projectName = repo.projectName,
                    url = repo.url,
                    type = ScmType.CODE_SVN,
                    privateKey = credInfo.privateKey,
                    passPhrase = credInfo.passPhrase,
                    token = null,
                    region = repo.region,
                    userName = credInfo.username,
                    search = search,
                    page = page,
                    pageSize = pageSize
                )
            }

            onCodeGit { repo ->
                if (repo.authType == RepoAuthType.OAUTH) {
                    val token = getGitOauthToken(repo.userName)
                    scmOauthService.listBranches(
                        projectName = repo.projectName,
                        url = repo.url,
                        type = ScmType.CODE_GIT,
                        privateKey = null,
                        passPhrase = null,
                        token = token,
                        region = null,
                        userName = repo.userName,
                        search = search,
                        page = page,
                        pageSize = pageSize
                    )
                } else {
                    val credInfo = getCredential(projectId, repo, getSession = true)
                    scmService.listBranches(
                        projectName = repo.projectName,
                        url = repo.url,
                        type = ScmType.CODE_GIT,
                        privateKey = null,
                        passPhrase = null,
                        token = credInfo.privateKey,
                        region = null,
                        userName = credInfo.username,
                        search = search,
                        page = page,
                        pageSize = pageSize
                    )
                }
            }

            onCodeGitlab { repo ->
                val credInfo = getCredential(projectId, repo)
                scmService.listBranches(
                    projectName = repo.projectName,
                    url = repo.url,
                    type = ScmType.CODE_GITLAB,
                    privateKey = null,
                    passPhrase = null,
                    token = credInfo.privateKey,
                    region = null,
                    userName = credInfo.username,
                    search = search,
                    page = page,
                    pageSize = pageSize
                )
            }

            onGithub { repo ->
                val token = getGithubAccessToken(repo.userName)
                githubService.listBranches(
                    token = token,
                    projectName = repo.projectName
                )
            }

            onCodeTGit { repo ->
                if (repo.authType == RepoAuthType.OAUTH) {
                    scmOauthService.listBranches(
                        projectName = repo.projectName,
                        url = repo.url,
                        type = ScmType.CODE_TGIT,
                        privateKey = null,
                        passPhrase = null,
                        token = getTGitAccessToken(repo.userName),
                        region = null,
                        userName = repo.userName,
                        search = search,
                        page = page,
                        pageSize = pageSize
                    )
                } else {
                    val credInfo = getCredential(projectId, repo, getSession = true)
                    scmService.listBranches(
                        projectName = repo.projectName,
                        url = repo.url,
                        type = ScmType.CODE_TGIT,
                        privateKey = null,
                        passPhrase = null,
                        token = credInfo.privateKey,
                        region = null,
                        userName = credInfo.username,
                        search = search,
                        page = page,
                        pageSize = pageSize
                    )
                }
            }

            onScmGitOrSvn { repo ->
                scmRepositoryApiService.listBranches(
                    projectId = projectId,
                    authRepository = AuthRepository(repo),
                    search = search,
                    page = page,
                    pageSize = pageSize
                ).map { it.name }
            }

            onDefault { repo ->
                logger.warn("listBranches not supported for repo($repo)")
                throw IllegalArgumentException(
                    "Unknown repo type for [$repoHashIdOrName] in project [$projectId]"
                )
            }
        }.dispatch(repository)
        return Result(branches)
    }

    fun listTags(
        projectId: String,
        repositoryType: RepositoryType?,
        repoHashIdOrName: String,
        search: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<String>> {
        val repository = getRepositoryByHashIdOrName(projectId, repositoryType, repoHashIdOrName)
        val tags = RepositoryDispatcher<List<String>>().apply {
            onCodeSvn { emptyList() }
            onScmSvn { emptyList() }

            onCodeGit { repo ->
                if (repo.authType == RepoAuthType.OAUTH) {
                    val token = getGitOauthToken(repo.userName)
                    scmOauthService.listTags(
                        projectName = repo.projectName,
                        url = repo.url,
                        type = ScmType.CODE_GIT,
                        token = token,
                        userName = repo.userName,
                        search = search
                    )
                } else {
                    val credInfo = getCredential(projectId, repo, getSession = true)
                    scmService.listTags(
                        projectName = repo.projectName,
                        url = repo.url,
                        type = ScmType.CODE_GIT,
                        token = credInfo.privateKey,
                        userName = credInfo.username,
                        search = search
                    )
                }
            }

            onCodeGitlab { repo ->
                val credInfo = getCredential(projectId, repo)
                scmService.listTags(
                    projectName = repo.projectName,
                    url = repo.url,
                    type = ScmType.CODE_GITLAB,
                    token = credInfo.privateKey,
                    userName = credInfo.username,
                    search = search
                )
            }

            onGithub { repo ->
                val token = getGithubAccessToken(repo.userName)
                githubService.listTags(
                    token = token,
                    projectName = repo.projectName
                )
            }

            onCodeTGit { repo ->
                if (repo.authType == RepoAuthType.OAUTH) {
                    scmOauthService.listTags(
                        projectName = repo.projectName,
                        url = repo.url,
                        type = ScmType.CODE_TGIT,
                        token = getTGitAccessToken(repo.userName),
                        userName = repo.userName,
                        search = search
                    )
                } else {
                    val credInfo = getCredential(projectId, repo, getSession = true)
                    scmService.listTags(
                        projectName = repo.projectName,
                        url = repo.url,
                        type = ScmType.CODE_TGIT,
                        token = credInfo.privateKey,
                        userName = credInfo.username,
                        search = search
                    )
                }
            }

            onScmGit { repo ->
                scmRepositoryApiService.listTags(
                    projectId = projectId,
                    authRepository = AuthRepository(repo),
                    search = search,
                    page = page,
                    pageSize = pageSize
                ).map { it.name }
            }

            onDefault { repo ->
                logger.warn("listTags not supported for repo($repo)")
                throw IllegalArgumentException(
                    "Unknown repo type for [$repoHashIdOrName] in project [$projectId]"
                )
            }
        }.dispatch(repository)
        return Result(tags)
    }

    fun getLatestRevision(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        branchName: String?,
        additionalPath: String?,
        variables: Map<String, String>?
    ): Result<RevisionInfo> {
        val repo = repositoryService.serviceGet(
            projectId = projectId,
            repositoryConfig = repositoryConfig.resolveWithVariables(variables)
        )
        return RepositoryDispatcher<Result<RevisionInfo>>().apply {
            onCodeSvn { repository ->
                val credInfo = getCredential(projectId, repository)
                Result(
                    scmService.getLatestRevision(
                        projectName = repository.projectName,
                        url = repository.url,
                        type = ScmType.CODE_SVN,
                        branchName = branchName,
                        privateKey = credInfo.privateKey,
                        passPhrase = credInfo.passPhrase,
                        token = null,
                        region = repository.region,
                        userName = credInfo.username
                    )
                )
            }

            onCodeGit { repository ->
                if (repository.authType == RepoAuthType.OAUTH) {
                    val token = getGitOauthToken(repository.userName)
                    Result(
                        scmOauthService.getLatestRevision(
                            projectName = repository.projectName,
                            url = repository.url,
                            type = ScmType.CODE_GIT,
                            branchName = branchName,
                            privateKey = null,
                            passPhrase = null,
                            token = token,
                            region = null,
                            userName = repository.userName
                        )
                    )
                } else {
                    val credInfo = getCredential(projectId, repository, getSession = true)
                    Result(
                        scmService.getLatestRevision(
                            projectName = repository.projectName,
                            url = repository.url,
                            type = ScmType.CODE_GIT,
                            branchName = branchName,
                            privateKey = null,
                            passPhrase = null,
                            token = credInfo.privateKey,
                            region = null,
                            userName = credInfo.username
                        )
                    )
                }
            }

            onCodeTGit { repository ->
                val credInfo = getCredential(projectId, repository, getSession = true)
                Result(
                    scmService.getLatestRevision(
                        projectName = repository.projectName,
                        url = repository.url,
                        type = ScmType.CODE_TGIT,
                        branchName = branchName,
                        privateKey = null,
                        passPhrase = null,
                        token = credInfo.privateKey,
                        region = null,
                        userName = credInfo.username
                    )
                )
            }

            onCodeGitlab { repository ->
                val credInfo = getCredential(projectId, repository)
                Result(
                    scmService.getLatestRevision(
                        projectName = repository.projectName,
                        url = repository.url,
                        type = ScmType.CODE_GITLAB,
                        branchName = branchName,
                        privateKey = null,
                        passPhrase = null,
                        token = credInfo.privateKey,
                        region = null,
                        userName = credInfo.username
                    )
                )
            }

            onGithub { repository ->
                getGithubLatestRevision(repository, branchName)
            }

            onScmGitOrSvn { repository ->
                scmRepositoryApiService.getBranch(
                    projectId = projectId,
                    authRepository = AuthRepository(repository),
                    branch = branchName ?: ""
                )?.let {
                    Result(
                        RevisionInfo(
                            revision = it.sha,
                            updatedMessage = "",
                            branchName = it.name,
                            authorName = ""
                        )
                    )
                } ?: Result(status = -2, message = "can not find branch $branchName")
            }

            onDefault { repository ->
                throw IllegalArgumentException("Unknown repo($repository)")
            }
        }.dispatch(repo)
    }

    /**
     * Github 仓库的 latestRevision 单独处理：先查 branch，找不到再查 tag。
     * 完全对齐 [com.tencent.devops.process.service.scm.ScmProxyService.getLatestRevision] Github 分支。
     */
    private fun getGithubLatestRevision(
        repo: GithubRepository,
        branchName: String?
    ): Result<RevisionInfo> {
        val accessToken = getGithubAccessToken(repo.userName)
        val githubBranch = githubService.getBranch(
            token = accessToken,
            projectName = repo.projectName,
            branch = branchName
        )
        if (githubBranch?.commit != null) {
            return Result(
                RevisionInfo(
                    revision = githubBranch.commit!!.sha,
                    updatedMessage = githubBranch.commit!!.commit?.message ?: "",
                    branchName = githubBranch.name,
                    authorName = githubBranch.commit!!.commit?.author?.name ?: ""
                )
            )
        }
        if (branchName.isNullOrBlank()) {
            return Result(status = -1, message = "can not find tag $branchName")
        }
        val tagData = githubService.getTag(
            token = accessToken,
            projectName = repo.projectName,
            tag = branchName
        ) ?: return Result(status = -1, message = "can not find tag $branchName")
        return if (tagData.tagObject != null) {
            Result(
                RevisionInfo(
                    revision = tagData.tagObject!!.sha,
                    updatedMessage = "",
                    branchName = branchName,
                    authorName = ""
                )
            )
        } else {
            Result(status = -2, message = "can not find tag2 $branchName")
        }
    }

    /**
     * 获取仓库默认分支，行为对齐 [com.tencent.devops.process.service.scm.ScmProxyService.getDefaultBranch]。
     */
    fun getDefaultBranch(
        projectId: String,
        repositoryConfig: RepositoryConfig
    ): String? {
        val repo = repositoryService.serviceGet(
            projectId = projectId,
            repositoryConfig = repositoryConfig
        )
        return RepositoryDispatcher<String?>().apply {
            onCodeGit { repository ->
                val isOauth = repository.authType == RepoAuthType.OAUTH
                val (token, tokenType) = if (isOauth) {
                    getGitOauthToken(repository.userName) to TokenTypeEnum.OAUTH
                } else {
                    getCredential(projectId, repository).privateKey to TokenTypeEnum.PRIVATE_KEY
                }
                gitService.getGitProjectInfo(
                    id = repository.projectName,
                    token = token,
                    tokenType = tokenType
                ).data?.defaultBranch
            }

            onScmGit { repository ->
                val gitScmServerRepository = scmRepositoryApiService.findRepository(
                    projectId = projectId,
                    authRepository = AuthRepository(repository)
                ) as? GitScmServerRepository
                gitScmServerRepository?.defaultBranch
            }

            onCodeSvn { "/" }
            onScmSvn { "/" }

            onDefault { repository ->
                logger.warn(
                    "not support get default branch for " +
                        "${repository.scmCode} repo[${repository.repoHashId}]|$projectId"
                )
                null
            }
        }.dispatch(repo)
    }

    fun addGitWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): CodeGitRepository {
        val repo = repositoryService.serviceGet(projectId, repositoryConfig) as? CodeGitRepository
            ?: throw ErrorCodeException(errorCode = ProcessMessageCode.GIT_INVALID)
        val isOauth = repo.authType == RepoAuthType.OAUTH
        val token = if (isOauth) {
            getGitOauthToken(repo.userName)
        } else {
            getCredential(
                projectId = projectId,
                repository = repo,
                getSession = true
            ).privateKey
        }
        val event = convertEvent(codeEventType)

        logger.info("Add git web hook event($event)")
        if (isOauth) {
            scmOauthService.addWebHook(
                projectName = repo.projectName,
                url = repo.url,
                type = ScmType.CODE_GIT,
                privateKey = null,
                passPhrase = null,
                token = token,
                region = null,
                userName = repo.userName,
                event = event
            )
        } else {
            scmService.addWebHook(
                projectName = repo.projectName,
                url = repo.url,
                type = ScmType.CODE_GIT,
                privateKey = null,
                passPhrase = null,
                token = token,
                region = null,
                userName = repo.userName,
                event = event,
                hookUrl = null
            )
        }

        return repo
    }

    fun addGitlabWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): Repository {
        val repo = repositoryService.serviceGet(projectId, repositoryConfig) as? CodeGitlabRepository
            ?: throw ErrorCodeException(errorCode = GITLAB_INVALID)
        val token = getCredential(projectId, repo).privateKey
        scmService.addWebHook(
            projectName = repo.projectName,
            url = repo.url,
            type = ScmType.CODE_GITLAB,
            privateKey = null,
            passPhrase = null,
            token = token,
            region = null,
            userName = repo.userName,
            event = convertEvent(codeEventType),
            hookUrl = null
        )
        return repo
    }

    fun addSvnWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig
    ): Repository {
        val repo = repositoryService.serviceGet(projectId, repositoryConfig) as? CodeSvnRepository
            ?: throw ErrorCodeException(errorCode = ProcessMessageCode.SVN_INVALID)
        val credential = getCredential(
            projectId = projectId,
            repository = repo,
            getSession = true
        )
        val (isOauth, token) = getSvnToken(credential, repo.svnType, repo.userName)
        if (isOauth) {
            scmOauthService.addWebHook(
                projectName = repo.projectName,
                url = repo.url,
                type = ScmType.CODE_SVN,
                privateKey = credential.username,
                passPhrase = credential.privateKey,
                token = token,
                region = repo.region,
                userName = credential.username,
                event = null
            )
        } else {
            scmService.addWebHook(
                projectName = repo.projectName,
                url = repo.url,
                type = ScmType.CODE_SVN,
                privateKey = credential.username,
                passPhrase = credential.privateKey,
                token = token,
                region = repo.region,
                userName = credential.username,
                event = null,
                hookUrl = null
            )
        }
        return repo
    }

    fun addTGitWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): Repository {
        val repo = repositoryService.serviceGet(projectId, repositoryConfig) as? CodeTGitRepository
            ?: throw ErrorCodeException(
                defaultMessage = "TGit",
                errorCode = ProcessMessageCode.TGIT_INVALID
            )

        if (repo.authType == RepoAuthType.OAUTH) {
            scmOauthService.addWebHook(
                projectName = repo.projectName,
                url = repo.url,
                type = ScmType.CODE_TGIT,
                privateKey = null,
                passPhrase = null,
                token = getTGitAccessToken(repo.userName),
                region = null,
                userName = repo.userName,
                event = convertEvent(codeEventType)
            )
        } else {
            val credInfo = getCredential(
                projectId = projectId,
                repository = repo,
                getSession = true
            )
            scmService.addWebHook(
                projectName = repo.projectName,
                url = repo.url,
                type = ScmType.CODE_TGIT,
                privateKey = null,
                passPhrase = null,
                token = credInfo.privateKey,
                region = null,
                userName = credInfo.username,
                event = convertEvent(codeEventType),
                hookUrl = null
            )
        }
        return repo
    }

    fun addP4Webhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): Repository {
        val repo = repositoryService.serviceGet(projectId, repositoryConfig) as? CodeP4Repository
            ?: throw ErrorCodeException(errorCode = ProcessMessageCode.P4_INVALID)
        val credential = getCredential(projectId, repo)
        scmService.addWebHook(
            projectName = repo.projectName,
            url = repo.url,
            type = ScmType.CODE_P4,
            privateKey = null,
            passPhrase = credential.passPhrase,
            token = null,
            region = null,
            userName = credential.username,
            event = codeEventType?.name,
            hookUrl = null
        )
        return repo
    }

    fun addScmWebhook(
        projectId: String,
        repositoryConfig: RepositoryConfig,
        codeEventType: CodeEventType?
    ): Repository {
        val repository = repositoryService.serviceGet(projectId, repositoryConfig)
        val repo = repository as? ScmGitRepository
            ?: (repository as? ScmSvnRepository)
            ?: throw ErrorCodeException(
                defaultMessage = "ScmRepo",
                errorCode = ProcessMessageCode.SCM_REPO_INVALID
            )
        val eventType = (codeEventType?.convertScmEventType() ?: ScmEventType.PUSH).value
        scmRepositoryApiService.createHook(
            projectId = projectId,
            authRepository = AuthRepository(repo),
            events = listOf(eventType),
            secret = null,
            scmType = repo.getScmType(),
            scmCode = repo.scmCode
        )
        return repo
    }

    private fun convertEvent(codeEventType: CodeEventType?): String? {
        return when (codeEventType) {
            null, CodeEventType.PUSH -> CodeGitWebhookEvent.PUSH_EVENTS.value
            CodeEventType.TAG_PUSH -> CodeGitWebhookEvent.TAG_PUSH_EVENTS.value
            CodeEventType.MERGE_REQUEST, CodeEventType.MERGE_REQUEST_ACCEPT -> {
                CodeGitWebhookEvent.MERGE_REQUESTS_EVENTS.value
            }
            CodeEventType.ISSUES -> CodeGitWebhookEvent.ISSUES_EVENTS.value
            CodeEventType.NOTE -> CodeGitWebhookEvent.NOTE_EVENTS.value
            CodeEventType.REVIEW -> CodeGitWebhookEvent.REVIEW_EVENTS.value
            else -> null
        }
    }

    private fun CodeEventType.convertScmEventType() = when (this) {
        CodeEventType.PUSH -> ScmEventType.PUSH
        CodeEventType.PULL_REQUEST, CodeEventType.MERGE_REQUEST -> ScmEventType.PULL_REQUEST
        CodeEventType.TAG_PUSH -> ScmEventType.TAG
        CodeEventType.ISSUES -> ScmEventType.ISSUE
        CodeEventType.POST_COMMIT -> ScmEventType.POST_COMMIT
        CodeEventType.NOTE -> ScmEventType.NOTE
        CodeEventType.REVIEW -> ScmEventType.PULL_REQUEST_REVIEW
        else -> throw IllegalArgumentException("unknown code event type: $this")
    }

    private fun getSvnToken(
        credential: Credential,
        svnType: String?,
        userName: String
    ): Pair<Boolean, String> = when (svnType) {
        CodeSvnRepository.SVN_TYPE_SSH -> {
            // 凭证中存在token，则直接使用
            if (credential.credentialType == CredentialType.TOKEN_SSH_PRIVATEKEY) {
                Pair(false, credential.svnToken ?: "")
            } else {
                // 兜底，以当前代码关联人的oauthToken去操作
                try {
                    Pair(true, getGitOauthToken(userName))
                } catch (e: Exception) {
                    throw NotFoundException(
                        com.tencent.devops.common.web.utils.I18nUtil.getCodeLanMessage(
                            messageCode = ProcessMessageCode.ERROR_REPOSITORY_NOT_OAUTH,
                            params = arrayOf(userName)
                        )
                    )
                }
            }
        }
        CodeSvnRepository.SVN_TYPE_HTTP -> {
            // 凭证中存在token，则直接使用，反之用session接口返回值，此处svnToken是svn的token
            // 参考：1. com.tencent.devops.process.utils.CredentialUtils.getCredential
            //      2. com.tencent.devops.process.service.scm.ScmProxyService.getCredential
            Pair(false, credential.svnToken ?: "")
        }
        else -> {
            Pair(false, "")
        }
    }


    private fun getRepositoryByHashIdOrName(
        projectId: String,
        repositoryType: RepositoryType?,
        repoHashIdOrName: String
    ): Repository {
        val effectiveType = repositoryType ?: RepositoryType.ID
        val repositoryConfig = if (effectiveType == RepositoryType.ID) {
            RepositoryConfig(repoHashIdOrName, null, RepositoryType.ID)
        } else {
            RepositoryConfig(null, repoHashIdOrName, RepositoryType.NAME)
        }
        return repositoryService.serviceGet(projectId, repositoryConfig)
    }

    private fun RepositoryConfig.resolveWithVariables(
        variables: Map<String, String>?
    ): RepositoryConfig {
        if (variables.isNullOrEmpty()) {
            return this
        }
        val rawId = this.getRepositoryId()
        val resolved = com.tencent.devops.common.api.util.EnvUtils.parseEnv(rawId, variables)
        return when (this.repositoryType) {
            RepositoryType.ID -> RepositoryConfig(resolved, null, RepositoryType.ID)
            RepositoryType.NAME -> RepositoryConfig(null, resolved, RepositoryType.NAME)
        }
    }

    fun getCredential(
        projectId: String,
        repository: Repository,
        getSession: Boolean = false
    ): Credential {
        val (pair, credentialInfo) = repoCredentialService.get(projectId, repository)
        // 凭证字段定义: com.tencent.devops.ticket.pojo.enums.CredentialType
        val v1 = CredentialUtils.decode(
            encode = credentialInfo.v1,
            publicKey = credentialInfo.publicKey,
            privateKey = pair.privateKey
        )
        val v2 = CredentialUtils.decode(
            encode = credentialInfo.v2,
            publicKey = credentialInfo.publicKey,
            privateKey = pair.privateKey
        )
        val v3 = CredentialUtils.decode(
            encode = credentialInfo.v3,
            publicKey = credentialInfo.publicKey,
            privateKey = pair.privateKey
        )
        // 尝试以账密换取 LoginSession 内的 privateToken
        if (getSession && tryGetSession(repository, credentialInfo.credentialType)) {
            // USERNAME_PASSWORD: v1 = username, v2 = password
            val session = try {
                scmService.getLoginSession(
                    type = repository.getScmType(),
                    username = v1,
                    password = v2,
                    url = repository.url
                )
            } catch (ignored: Exception) {
                logger.warn("fail to get login session", ignored)
                null
            }
            return Credential(
                username = v1,
                privateKey = session?.privateToken ?: "",
                passPhrase = v2,
                svnToken = session?.privateToken ?: ""
            )
        }
        // 按顺序封装凭证信息
        val list = when {
            v2.isBlank() -> listOf(v1)
            v3.isBlank() -> listOf(v1, v2)
            else -> listOf(v1, v2, v3)
        }
        return CredentialUtils.getCredential(repository, list, credentialInfo.credentialType).apply {
            this.credentialType = credentialInfo.credentialType
        }
    }

    fun tryGetSession(repository: Repository, credentialType: CredentialType): Boolean {
        val isSessionableRepo = repository is CodeGitRepository ||
                repository is CodeTGitRepository ||
                repository is CodeSvnRepository
        return isSessionableRepo && credentialType == CredentialType.USERNAME_PASSWORD
    }

    private fun getGitOauthToken(userName: String): String {
        val gitToken = gitOauthService.getAccessToken(userName)
            ?: throw NotFoundException("cannot found oauth access token for user($userName)")
        return gitToken.accessToken
    }

    private fun getTGitAccessToken(userName: String): String {
        val gitToken = tGitOAuthService.getAccessToken(userName)
            ?: throw NotFoundException("cannot found oauth access token for user($userName)")
        return gitToken.accessToken
    }

    private fun getGithubAccessToken(userName: String): String {
        val githubToken = githubService.getAccessToken(userName)
            ?: throw NotFoundException("cannot find github oauth accessToken for user($userName)")
        return githubToken.accessToken
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScmRepositorySupportService::class.java)
    }
}
