package com.tencent.devops.common.auth.api.pojo

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "资源授权交接条件实体")
@Suppress("LongParameterList")
open class ResourceAuthorizationConditionRequest(
    @get:Schema(title = "项目ID")
    open val projectCode: String,
    @get:Schema(title = "资源类型")
    open val resourceType: String,
    @get:Schema(title = "资源名称")
    open val resourceName: String? = null,
    @get:Schema(title = "授予人")
    open val handoverFrom: String? = null,
    @get:Schema(title = "greaterThanHandoverTime")
    open val greaterThanHandoverTime: Long? = null,
    @get:Schema(title = "lessThanHandoverTime")
    open val lessThanHandoverTime: Long? = null,
    @Parameter(description = "第几页", required = false)
    open val page: Int? = null,
    @Parameter(description = "每页条数", required = false)
    open val pageSize: Int? = null
)