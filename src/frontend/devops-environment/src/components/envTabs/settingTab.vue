<template>
    <div class="env-setting-tab-wrapper">
        <bk-button
            v-perm="{
                hasPermission: curEnvDetail.canEdit,
                disablePermissionApi: true,
                permissionData: {
                    projectId: projectId,
                    resourceType: ENV_RESOURCE_TYPE,
                    resourceCode: envHashId,
                    action: ENV_RESOURCE_ACTION.EDIT
                }
            }"
            class="setting-header"
            theme="primary"
            @click="toggleShareProject"
        >
            {{ $t('environment.addProject') }}
        </bk-button>
        <bk-table
            :data="shareEnvProjectList"
            :pagination="pagination"
            @page-change="handlePageChange"
            @page-limit-change="handlePageLimitChange"
        >
            <bk-table-column
                :label="$t('environment.project')"
                prop="name"
            ></bk-table-column>
            <bk-table-column
                :label="$t('environment.operateUser')"
                prop="creator"
            ></bk-table-column>
            <bk-table-column
                :label="$t('environment.operateTime')"
                prop="updateTime"
            ></bk-table-column>
            <bk-table-column
                :label="$t('environment.operation')"
                width="150"
            >
                <template slot-scope="props">
                    <bk-button
                        v-perm="{
                            permissionData: {
                                projectId: projectId,
                                resourceType: ENV_RESOURCE_TYPE,
                                resourceCode: envHashId,
                                action: ENV_RESOURCE_ACTION.EDIT
                            }
                        }"
                        class="mr10"
                        text
                        @click="remove(props.row)"
                    >
                        {{ $t('environment.remove') }}
                    </bk-button>
                </template>
            </bk-table-column>
        </bk-table>
        <select-env-share-dialog
            :show-project-dialog="showProjectDialog"
            :project-id="projectId"
            :env-hash-id="envHashId"
            @confirm="handleShareEnv"
            @cancel="handleCancel"
        />
    </div>
</template>

<script>
    import { ENV_RESOURCE_ACTION, ENV_RESOURCE_TYPE } from '@/utils/permission'
    import { convertTime } from '@/utils/util'
    import { mapActions } from 'vuex'
    import selectEnvShareDialog from './select-env-share-dialog'
    export default {
        name: 'setting-tab',
        components: {
            selectEnvShareDialog
        },
        props: {
            projectId: {
                type: String,
                required: true
            },
            envHashId: {
                type: String,
                required: true
            },
            curEnvDetail: {
                type: Object,
                default: () => ({})
            },
            requestEnvDetail: {
                type: Function,
                required: true
            }
        },
        data () {
            return {
                shareEnvProjectList: [],
                showProjectDialog: false,
                ENV_RESOURCE_ACTION,
                ENV_RESOURCE_TYPE,
                pagination: {
                    current: 1,
                    count: 0,
                    limit: 20
                }
            }
        },

        created () {
            this.fetchEnvProjects()
        },
        
        methods: {
            ...mapActions('environment', [
                'requestShareEnvProjectList',
                'removeProjectShare',
                'requestProjects',
                'shareEnv'
            ]),
            async fetchEnvProjects () {
                const res = await this.requestShareEnvProjectList({
                    projectId: this.projectId,
                    envHashId: this.envHashId,
                    page: this.pagination.current,
                    pageSize: this.pagination.limit
                })
                const { records, count, page } = res
                
                this.shareEnvProjectList = [
                    ...records.map(record => ({
                        ...record,
                        updateTime: convertTime(record.updateTime * 1000)
                    }))
                ]
                if (page === 1) {
                    this.shareEnvProjectList.unshift({
                        projectId: this.projectId,
                        name: this.curEnvDetail.projectName,
                        type: 'PROJECT',
                        creator: this.curEnvDetail.updatedUser,
                        isDefault: true,
                        updateTime: convertTime(this.curEnvDetail.updatedTime * 1000)
                    })
                }
                this.pagination.count = count + 1
            },
            toggleShareProject () {
                this.showProjectDialog = !this.showProjectDialog
            },
            handlePageChange (current) {
                this.pagination.current = current
                this.$nextTick(() => {
                    this.fetchEnvProjects()
                })
            },
            handlePageLimitChange (limit) {
                this.pagination.limit = limit
                this.$nextTick(() => {
                    this.fetchEnvProjects()
                })
            },
            actionWrapper (action, message) {
                return async (...args) => {
                    try {
                        await action(...args)
                        this.fetchEnvProjects()
                        this.$bkMessage({
                            message: message,
                            theme: 'success'
                        })
                    } catch (e) {
                        this.handleError(
                            e,
                            {
                                projectId: this.projectId,
                                resourceType: ENV_RESOURCE_TYPE,
                                resourceCode: this.envHashId,
                                action: ENV_RESOURCE_ACTION.EDIT
                            }
                        )
                    }
                }
            },
            async handleShareEnv (selection) {
                const fn = this.actionWrapper(async () => {
                    const sharedProjects = selection.map(item => ({
                        projectId: item.projectId,
                        name: item.name,
                        type: 'PROJECT',
                        creator: item.creator,
                        createTime: item.createTime,
                        updateTime: item.updateTime
                    }))
                    const res = await this.shareEnv({
                        projectId: this.projectId,
                        envHashId: this.envHashId,
                        body: {
                            sharedProjects
                        }
                    })
                    this.fetchEnvProjects()
                    return res
                }, this.$t('environment.shareEnvSuc'))
                await fn()
                this.toggleShareProject()
            },
            handleCancel () {
                this.toggleShareProject()
            },
            async remove ({ gitProjectId, name }) {
                const fn = this.actionWrapper(async () => {
                    await this.removeProjectShare({
                        projectId: this.projectId,
                        envHashId: this.envHashId,
                        sharedProjectId: gitProjectId
                    })
                }, this.$t('environment.removeShareEnvProjectSuc', [name]))

                this.$bkInfo({
                    type: 'warning',
                    title: this.$t('environment.removeShareProjectConfirm', [name]),
                    maskClose: true,
                    confirmFn: fn
                })
            }
            
        }
        
    }
</script>

<style lang="scss">
  .env-setting-tab-wrapper {
    .setting-header {
      margin-bottom: 12px;
    }
  }
  
</style>
