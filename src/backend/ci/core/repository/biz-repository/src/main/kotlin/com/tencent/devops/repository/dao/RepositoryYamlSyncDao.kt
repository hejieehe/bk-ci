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

package com.tencent.devops.repository.dao

import com.tencent.devops.model.repository.tables.TRepositoryYamlSync
import com.tencent.devops.repository.pojo.RepoYamlSyncInfo
import com.tencent.devops.repository.pojo.enums.RepoYamlSyncStatusEnum
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class RepositoryYamlSyncDao {

    fun batchAdd(
        dslContext: DSLContext,
        projectId: String,
        repositoryId: Long,
        syncFileInfoList: List<RepoYamlSyncInfo>
    ) {
        if (syncFileInfoList.isEmpty()) {
            return
        }
        val now = LocalDateTime.now()
        dslContext.batch(syncFileInfoList.map {
            with(TRepositoryYamlSync.T_REPOSITORY_YAML_SYNC) {
                dslContext.insertInto(
                    this,
                    PROJECT_ID,
                    REPOSITORY_ID,
                    FILE_PATH,
                    SYNC_STATUS,
                    CREATE_TIME,
                    UPDATE_TIME
                ).values(
                    projectId,
                    repositoryId,
                    it.filePath,
                    it.syncStatus.name,
                    now,
                    now
                ).onDuplicateKeyIgnore()
            }
        }).execute()
    }

    fun updateSyncStatus(
        dslContext: DSLContext,
        projectId: String,
        repositoryId: Long,
        syncFileInfo: RepoYamlSyncInfo
    ) {
        with(TRepositoryYamlSync.T_REPOSITORY_YAML_SYNC) {
            dslContext.update(this)
                .set(SYNC_STATUS, syncFileInfo.syncStatus.name)
                .set(REASON, syncFileInfo.reason)
                .set(REASON_DETAIL, syncFileInfo.reasonDetail)
                .where(PROJECT_ID.eq(projectId))
                .and(REPOSITORY_ID.eq(repositoryId))
                .and(FILE_PATH.eq(syncFileInfo.filePath))
                .execute()
        }
    }

    fun delete(
        dslContext: DSLContext,
        projectId: String,
        repositoryId: Long
    ) {
        with(TRepositoryYamlSync.T_REPOSITORY_YAML_SYNC) {
            dslContext.deleteFrom(this)
                .where(PROJECT_ID.eq(projectId))
                .and(REPOSITORY_ID.eq(repositoryId))
                .execute()
        }
    }

    fun listYamlSync(
        dslContext: DSLContext,
        projectId: String,
        repositoryId: Long,
        syncStatus: String? = null
    ): List<RepoYamlSyncInfo> {
        with(TRepositoryYamlSync.T_REPOSITORY_YAML_SYNC) {
            return dslContext.selectFrom(this)
                .where(PROJECT_ID.eq(projectId))
                .and(REPOSITORY_ID.eq(repositoryId))
                .let { if (syncStatus == null) it else it.and(SYNC_STATUS.eq(syncStatus)) }
                .fetch().map {
                    RepoYamlSyncInfo(
                        filePath = it.filePath,
                        syncStatus = RepoYamlSyncStatusEnum.valueOf(it.syncStatus),
                        reason = it.reason,
                        reasonDetail = it.reasonDetail
                    )
                }
        }
    }

    fun countYamlSync(
        dslContext: DSLContext,
        projectId: String,
        repositoryId: Long,
        syncStatus: String
    ): Int {
        with(TRepositoryYamlSync.T_REPOSITORY_YAML_SYNC) {
            return dslContext.selectCount().from(this)
                .where(PROJECT_ID.eq(projectId))
                .and(REPOSITORY_ID.eq(repositoryId))
                .and(SYNC_STATUS.eq(syncStatus))
                .fetchOne(0, Int::class.java) ?: 0
        }
    }
}