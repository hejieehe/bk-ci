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

import com.tencent.devops.repository.pojo.CodeGitRepository
import com.tencent.devops.repository.pojo.CodeGitlabRepository
import com.tencent.devops.repository.pojo.CodeP4Repository
import com.tencent.devops.repository.pojo.CodeSvnRepository
import com.tencent.devops.repository.pojo.CodeTGitRepository
import com.tencent.devops.repository.pojo.GithubRepository
import com.tencent.devops.repository.pojo.Repository
import com.tencent.devops.repository.pojo.ScmGitRepository
import com.tencent.devops.repository.pojo.ScmSvnRepository

/**
 * 仓库类型分发器
 * 用于消除 when (repository) 表达式中的冗余类型判断代码
 */
class RepositoryDispatcher<T> {
    private var codeSvnHandler: ((CodeSvnRepository) -> T)? = null
    private var codeGitHandler: ((CodeGitRepository) -> T)? = null
    private var codeGitlabHandler: ((CodeGitlabRepository) -> T)? = null
    private var githubHandler: ((GithubRepository) -> T)? = null
    private var codeTGitHandler: ((CodeTGitRepository) -> T)? = null
    private var scmGitHandler: ((ScmGitRepository) -> T)? = null
    private var scmSvnHandler: ((ScmSvnRepository) -> T)? = null
    private var codeP4Handler: ((CodeP4Repository) -> T)? = null
    private var defaultHandler: ((Repository) -> T)? = null

    /**
     * 注册 CodeSvnRepository 类型的处理器
     */
    fun onCodeSvn(handler: (CodeSvnRepository) -> T) {
        codeSvnHandler = handler
    }

    /**
     * 注册 CodeGitRepository 类型的处理器
     */
    fun onCodeGit(handler: (CodeGitRepository) -> T) {
        codeGitHandler = handler
    }

    /**
     * 注册 CodeGitlabRepository 类型的处理器
     */
    fun onCodeGitlab(handler: (CodeGitlabRepository) -> T) {
        codeGitlabHandler = handler
    }

    /**
     * 注册 GithubRepository 类型的处理器
     */
    fun onGithub(handler: (GithubRepository) -> T) {
        githubHandler = handler
    }

    /**
     * 注册 CodeTGitRepository 类型的处理器
     */
    fun onCodeTGit(handler: (CodeTGitRepository) -> T) {
        codeTGitHandler = handler
    }

    /**
     * 注册 ScmGitRepository 类型的处理器
     */
    fun onScmGit(handler: (ScmGitRepository) -> T) {
        scmGitHandler = handler
    }

    /**
     * 注册 ScmSvnRepository 类型的处理器
     */
    fun onScmSvn(handler: (ScmSvnRepository) -> T) {
        scmSvnHandler = handler
    }

    /**
     * 同时注册 ScmGitRepository 和 ScmSvnRepository 类型的处理器
     */
    fun onScmGitOrSvn(handler: (Repository) -> T) {
        scmGitHandler = handler
        scmSvnHandler = handler
    }

    /**
     * 注册 CodeP4Repository 类型的处理器
     */
    fun onCodeP4(handler: (CodeP4Repository) -> T) {
        codeP4Handler = handler
    }

    /**
     * 注册默认处理器（当没有匹配的类型时使用）
     */
    fun onDefault(handler: (Repository) -> T) {
        defaultHandler = handler
    }

    /**
     * 执行分发逻辑
     */
    fun dispatch(repository: Repository): T {
        return when (repository) {
            is CodeSvnRepository -> {
                codeSvnHandler?.invoke(repository)
                    ?: defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "No handler for CodeSvnRepository"
                    )
            }
            is CodeGitRepository -> {
                codeGitHandler?.invoke(repository)
                    ?: defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "No handler for CodeGitRepository"
                    )
            }
            is CodeGitlabRepository -> {
                codeGitlabHandler?.invoke(repository)
                    ?: defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "No handler for CodeGitlabRepository"
                    )
            }
            is GithubRepository -> {
                githubHandler?.invoke(repository)
                    ?: defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "No handler for GithubRepository"
                    )
            }
            is CodeTGitRepository -> {
                codeTGitHandler?.invoke(repository)
                    ?: defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "No handler for CodeTGitRepository"
                    )
            }
            is ScmGitRepository -> {
                scmGitHandler?.invoke(repository)
                    ?: defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "No handler for ScmGitRepository"
                    )
            }
            is ScmSvnRepository -> {
                scmSvnHandler?.invoke(repository)
                    ?: defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "No handler for ScmSvnRepository"
                    )
            }
            is CodeP4Repository -> {
                codeP4Handler?.invoke(repository)
                    ?: defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "No handler for CodeP4Repository"
                    )
            }
            else -> {
                defaultHandler?.invoke(repository)
                    ?: throw IllegalArgumentException(
                        "Unknown repo type: ${repository::class.simpleName}"
                    )
            }
        }
    }
}

/**
 * 扩展函数：为 Repository 添加 dispatch 方法
 */
fun <T> Repository.dispatch(block: RepositoryDispatcher<T>.() -> Unit): T {
    val dispatcher = RepositoryDispatcher<T>().apply(block)
    return dispatcher.dispatch(this)
}
