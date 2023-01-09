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
package com.tencent.devops.repository.service.code

import com.tencent.devops.common.api.constant.RepositoryMessageCode
import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.common.service.utils.MessageCodeUtil
import com.tencent.devops.model.repository.tables.records.TRepositoryRecord
import com.tencent.devops.repository.dao.RepositoryCodeGitLabDao
import com.tencent.devops.repository.dao.RepositoryDao
import com.tencent.devops.repository.pojo.CodeGitlabRepository
import com.tencent.devops.repository.pojo.auth.RepoAuthInfo
import com.tencent.devops.repository.pojo.credential.EmptyCredentialInfo
import com.tencent.devops.repository.pojo.credential.RepoCredentialInfo
import com.tencent.devops.repository.pojo.enums.RepoAuthType
import com.tencent.devops.repository.pojo.enums.TokenTypeEnum
import com.tencent.devops.repository.service.CredentialService
import com.tencent.devops.repository.service.scm.IGitService
import com.tencent.devops.repository.service.scm.IScmService
import com.tencent.devops.scm.pojo.TokenCheckResult
import com.tencent.devops.scm.utils.code.git.GitUtils
import org.apache.commons.lang3.StringUtils
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CodeGitlabRepositoryService @Autowired constructor(
    private val repositoryDao: RepositoryDao,
    private val repositoryCodeGitLabDao: RepositoryCodeGitLabDao,
    private val dslContext: DSLContext,
    private val scmService: IScmService,
    private val credentialService: CredentialService,
    private val gitService: IGitService
) : CodeRepositoryService<CodeGitlabRepository> {
    override fun repositoryType(): String {
        return CodeGitlabRepository::class.java.name
    }

    override fun create(projectId: String, userId: String, repository: CodeGitlabRepository): Long {
        val credentialInfo = checkCredentialInfo(projectId = projectId, repository = repository)
        var repositoryId = 0L
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            repositoryId = repositoryDao.create(
                dslContext = transactionContext,
                projectId = projectId,
                userId = userId,
                aliasName = repository.aliasName,
                url = repository.getFormatURL(),
                type = ScmType.CODE_GITLAB
            )
            // Git项目ID
            val gitProjectId: String = getGitProjectId(
                repo = repository,
                token = credentialInfo.token
            ).toString()
            repositoryCodeGitLabDao.create(
                dslContext = transactionContext,
                repositoryId = repositoryId,
                projectName = GitUtils.getProjectName(repository.url),
                userName = repository.userName,
                privateToken = repository.credentialId,
                authType = repository.authType,
                gitProjectId = gitProjectId
            )
        }
        return repositoryId
    }

    override fun edit(
        userId: String,
        projectId: String,
        repositoryHashId: String,
        repository: CodeGitlabRepository,
        record: TRepositoryRecord
    ) {
        // 提交的参数与数据库中类型不匹配
        if (!StringUtils.equals(record.type, ScmType.CODE_GITLAB.name)) {
            throw OperationException(MessageCodeUtil.getCodeLanMessage(RepositoryMessageCode.GITLAB_INVALID))
        }
        // 凭证信息
        val credentialInfo = checkCredentialInfo(projectId = projectId, repository = repository)
        val repositoryId = HashUtil.decodeOtherIdToLong(repositoryHashId)
        // 原始代码库URL
        val sourceUrl = repositoryDao.get(
            dslContext = dslContext,
            projectId = projectId,
            repositoryId = repositoryId
        ).url
        var gitProjectId: String = StringUtils.EMPTY
        // 需要更新gitProjectId
        if (sourceUrl != repository.url) {
            // Git项目ID
            gitProjectId = getGitProjectId(
                repo = repository,
                token = credentialInfo.token
            ).toString()
        }
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            repositoryDao.edit(
                dslContext = transactionContext,
                repositoryId = repositoryId,
                aliasName = repository.aliasName,
                url = repository.getFormatURL()
            )
            repositoryCodeGitLabDao.edit(
                dslContext = transactionContext,
                repositoryId = repositoryId,
                projectName = GitUtils.getProjectName(repository.url),
                userName = repository.userName,
                credentialId = repository.credentialId,
                gitProjectId = gitProjectId
            )
        }
    }

    override fun compose(repository: TRepositoryRecord): CodeGitlabRepository {
        val record = repositoryCodeGitLabDao.get(dslContext, repository.repositoryId)
        return CodeGitlabRepository(
            aliasName = repository.aliasName,
            url = repository.url,
            credentialId = record.credentialId,
            projectName = record.projectName,
            userName = record.userName,
            projectId = repository.projectId,
            repoHashId = HashUtil.encodeOtherLongId(repository.repositoryId)
        )
    }

    fun checkToken(
        repoCredentialInfo: RepoCredentialInfo,
        repository: CodeGitlabRepository
    ): TokenCheckResult {
        val checkResult: TokenCheckResult = when (repository.authType) {
            RepoAuthType.SSH -> {
                scmService.checkPrivateKeyAndToken(
                    projectName = repository.projectName,
                    url = repository.getFormatURL(),
                    type = ScmType.CODE_GITLAB,
                    privateKey = repoCredentialInfo.privateKey,
                    passPhrase = repoCredentialInfo.passPhrase,
                    token = repoCredentialInfo.token,
                    region = null,
                    userName = repository.userName
                )
            }
            else -> {
                throw ErrorCodeException(
                    errorCode = RepositoryMessageCode.REPO_TYPE_NO_NEED_CERTIFICATION,
                    params = arrayOf(repository.authType!!.name)
                )
            }
        }
        return checkResult
    }

    /**
     * 获取Git项目ID
     */
    fun getGitProjectId(repo: CodeGitlabRepository, token: String): Int {
        logger.info("the repo is:$repo")
        // 根据仓库授权类型匹配Token类型
        val tokenType = if (repo.authType == RepoAuthType.OAUTH) TokenTypeEnum.OAUTH else TokenTypeEnum.PRIVATE_KEY
        val gitProjectInfo = gitService.getGitLabProjectInfo(
            id = repo.projectName,
            token = token,
            tokenType = tokenType,
            repoUrl = repo.url
        )
        logger.info("the gitProjectInfo is:$gitProjectInfo")
        return gitProjectInfo.data?.id ?: -1
    }

    /**
     * 检查凭证信息
     */
    private fun checkCredentialInfo(projectId: String, repository: CodeGitlabRepository): RepoCredentialInfo {
        return if (needCheckToken(repository)) {
            // 凭证信息
            val repoCredentialInfo: RepoCredentialInfo = credentialService.getCredentialInfo(
                projectId = projectId,
                repository = repository
            )
            val checkResult: TokenCheckResult = checkToken(
                repoCredentialInfo = repoCredentialInfo,
                repository = repository
            )
            if (!checkResult.result) {
                logger.warn("Fail to check the repo token & private key because of ${checkResult.message}")
                throw OperationException(checkResult.message)
            }
            repoCredentialInfo
        } else {
            EmptyCredentialInfo()
        }
    }

    fun needCheckToken(repository: CodeGitlabRepository): Boolean {
        return true
    }

    override fun getAuthInfo(repositoryIds: List<Long>): Map<Long, RepoAuthInfo> {
        return repositoryCodeGitLabDao.list(
            dslContext = dslContext,
            repositoryIds = repositoryIds.toSet()
        )?.associateBy({ it -> it.repositoryId }, {
            RepoAuthInfo(
                authType = it.authType ?: RepoAuthType.HTTP.name,
                credentialId = it.credentialId
            )
        }) ?: mapOf()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CodeGitlabRepositoryService::class.java)
    }
}