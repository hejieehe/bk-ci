<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import http from '@/http/api';
import GroupDeatil from './group-detail.vue';
import SearchSelect from './search-select';
import GroupTab from './group-tab';
import {
  h,
  ref,
  watch,
  computed,
  onMounted,
  resolveDirective,
  withDirectives,
} from 'vue';
import { useRoute } from 'vue-router';
import BkCheckbox from 'bkui-vue/lib/checkbox';

const route = useRoute();

const props = defineProps({
  groupList: Array,
  projectCode: String,
  isDisabled: Boolean,
  curProject: Object,
});

const { t } = useI18n();
const bkEllipsis = resolveDirective('bk-ellipsis');
const showDetail = ref(false);
const tableRef = ref();
const groupInfo = ref([]);
const selections = ref([]);
const filter = ref([]);
const isLoading = ref(false);
const isDetailLoading = ref(false);
const userGroupList = ref([]);
const groupLevel = ref('PROJECT');
const pagination = ref({
  count: 0,
  limit: 10,
  current: 1,
});
const indeterminate = ref(false);
const isSelectedAll = ref(false);
const applyTips = ref('');

const searchList = computed(() => {
  const datas = [
    {
      name: t('资源实例'),
      id: 'resourceCode',
      multiple: false,
      children: [],
      disabled: props.isDisabled,
    },
    {
      name: t('用户组名'),
      isDefaultOption: true,
      id: 'name',
      multiple: false,
    },
    {
      name: t('操作'),
      id: 'actionId',
      multiple: false,
      children: [],
    },
    {
      name: t('描述'),
      id: 'description',
    },
    {
      name: 'ID',
      id: 'groupId',
      multiple: false,
    },
  ];
  return datas.filter(data => !filter.value.find(val => val.id === data.id));
});

const emits = defineEmits(['handle-change-select-group']);

const handleChangeSelectGroup = (values) => {
  emits('handle-change-select-group', values);
};

// 可选择的用户组 joined -> flase
const optionGroupList = computed(() => userGroupList.value.filter(i => !i.joined));

watch(
  () => props.projectCode, (newVal, oldVal) => {
    if (oldVal) {
      filter.value = [];
      applyTips.value = '';
      userGroupList.value = [];
      pagination.value.current = 1;
    }
  },
  {
    immediate: true,
  },
);

watch(() => selections.value, () => {
  checkSelectedAll();
  checkIndeterminate();
  handleChangeSelectGroup(selections.value);
}, {
  deep: true,
});

watch(() => props.groupList, () => {
  selections.value = props.groupList;
}, {
  immediate: true,
  deep: true,
});

watch(() => userGroupList.value, () => {
  const { groupId } = route.query;
  if (groupId && filter.value.length) {
    const group = userGroupList.value.find(group => String(group.id) === groupId);
    if (group && selections.value.findIndex(selection => String(selection.id) === groupId) === -1) {
      selections.value.push(group);
    }
  }
  checkSelectedAll();
  checkIndeterminate();
});

const handlePageChange = (page) => {
  pagination.value.current = page;
  fetchGroupList(filter.value);
};

const handleLimitChange = (limit) => {
  pagination.value.limit = limit;
  fetchGroupList(filter.value);
};

const handleShowGroupDetail = async (data) => {
  isDetailLoading.value = true;
  showDetail.value = true;
  groupInfo.value = data;
};

const hiddenDetail = (payload) => {
  showDetail.value = payload;
};

const initTable = () => {
  tableRef.value?.clearSelection();
};

const handleChangeSearch = (data) => {
  pagination.value.current = 1;
  setTimeout(() => {
    filter.value = data;
    const { query } = route;
    // const resourceType = query?.resourceType
    if (query && Object.keys(query).length > 1 && query.project_code === props.projectCode) {
      applyTips.value = t('根据筛选条件，匹配到如下用户组:');
    }
    if (!props.curProject) return;
    // if (resourceType && !userGroupList.value.length && !data.length) return
    fetchGroupList(data);
  }, 100);
};

const fetchGroupList = async (payload = []) => {
  const projectId = props.projectCode || route?.query.project_code;
  if (!projectId) return;
  const params = {
    page: pagination.value.current,
    pageSize: pagination.value.limit,
    groupLevel: groupLevel.value,
    projectId,
  };
  payload.forEach((i) => {
    if (i.id === 'actionId') {
      const { values } = i;
      params[i.id] = values.map(i => i.action).join('');
      params.resourceType = values[0].resourceType;
    } else if (i.id === 'resourceCode') {
      const { values } = i;
      params.iamResourceCode = values.map(i => i.iamResourceCode).join('');
      params.resourceType = values[0].resourceType;
    } else {
      params[i.id] = i.values.join();
    }
  });
  isLoading.value = true;
  await http.getUserGroupList(params).then((res) => {
    pagination.value.count = res.count;
    userGroupList.value = res.results;
  })
    .catch(() => {
      isLoading.value = false;
      userGroupList.value = [];
    })
    .finally(() => {
      isLoading.value = false;
    });
};

const handleSelectRow = (value, row) => {
  const index = selections.value.findIndex(i => i.id === row.id);
  if (value && index === -1) {
    selections.value.push(row);
  } else if (!value && index > -1) {
    selections.value.splice(index, 1);
  }
};

const checkSelectedAll = () => {
  if (!selections.value.length) {
    isSelectedAll.value = false;
    return false;
  }

  isSelectedAll.value = optionGroupList.value.every(i => selections.value.some(group => group.id === i.id));
};

const checkIndeterminate = () => {
  if (!selections.value.length) {
    indeterminate.value = false;
    return false;
  }

  if (selections.value.length > optionGroupList.value.length) {
    checkSelectedAll();
  } else {
    indeterminate.value = selections.value.length !== optionGroupList.value.length;
  }
};

const handleSelectAllGroup = (val) => {
  isSelectedAll.value = val;
  if (val) {
    selections.value = userGroupList.value.filter(i => !i.joined);
  } else {
    selections.value = [];
  }
};

const renderSelectionCell = ({ row, column }) => h(
  BkCheckbox,
  {
    modelValue: row.joined ? row.joined : selections.value.some(item => item.id === row.id),
    disabled: row.joined,
    class: 'label-text',
    title: row.joined ? t('你已获得该权限') : '',
    onChange(val) {
      handleSelectRow(val, row);
    },
  },
);

const renderSelectionHeader = (col: any) => h(
  BkCheckbox,
  {
    indeterminate: indeterminate.value,
    modelValue: isSelectedAll.value,
    class: 'group-table-header-checkbox',
    onChange(val) {
      handleSelectAllGroup(val);
    },
  },
);

const handleChangeTab = (id) => {
  groupLevel.value = id;
  filter.value = [];
  fetchGroupList(filter.value);
};

const columns = [
  {
    label: renderSelectionHeader,
    width: 60,
    render: renderSelectionCell,
  },
  {
    label: t('资源实例'),
    render({ cell, row }) {
      return withDirectives(h(
        'div',
        {
          class: 'bk-ellipsis',
        },
        `${row.resourceTypeName}:${row.resourceName}`,
      ), [[bkEllipsis]]);
    },
  },
  {
    label: t('用户组名'),
    render({ cell, row }) {
      return h(
        'span',
        {
          title: row.name,
          style: {
            cursor: 'pointer',
            color: '#3a84ff',
          },
          onClick() {
            handleShowGroupDetail(row);
          },
        },
        [
          cell,
          row.name,
        ],
      );
    },
  },
  {
    label: t('描述'),
    render({ cell, row }) {
      return withDirectives(h(
        'div',
        {
          class: 'bk-ellipsis',
        },
        row.description,
      ), [[bkEllipsis]]);
    },
  },
];

onMounted(() => {
  const { iamRelatedResourceType } = route?.query;
  groupLevel.value = iamRelatedResourceType && iamRelatedResourceType !== 'project' ? 'OTHER' : 'PROJECT';
  if (!iamRelatedResourceType) {
    fetchGroupList(filter.value);
  };
});
</script>

<template>
  <article class="group-search">
    <search-select
      class="group-search-filter"
      v-model="filter"
      :search-list="searchList"
      :is-disabled="isDisabled"
      :project-code="projectCode"
      :cur-project="props.curProject"
      :key="groupLevel"
      @change="handleChangeSearch">
    </search-select>
    <bk-loading
      class="group-table"
      :loading="isLoading">
      <div v-if="applyTips">
        {{ applyTips }}
      </div>
      <group-tab
        :active="groupLevel"
        @change-tab="handleChangeTab"
      />
      <bk-table
        ref="tableRef"
        class="group-table"
        :data="userGroupList"
        :columns="columns"
        :border="['row', 'outer']"
      >
      </bk-table>
      <bk-pagination
        class="table-pagination"
        v-model="pagination.current"
        v-bind="pagination"
        type="default"
        @change="handlePageChange"
        @limit-change="handleLimitChange"
      />
    </bk-loading>
  </article>
  <group-deatil
    :is-show="showDetail"
    :is-detail-loading="isDetailLoading"
    :group-info="groupInfo"
    @hidden-detail="hiddenDetail" />
</template>

<style lang="postcss" scoped>
  .group-search-filter {
    width: 750px;
  }
  .group-table {
    width: 750px !important;
  }
  .group-name {
    color: #3A84FF;
    cursor: pointer;
  }
  :deep(.bk-pagination-total) {
    padding-left: 15px;
  }
  :deep(.bordered-outer) {
    border: 1px solid #dcdee5;
  }

  :deep(.bk-table .bk-table-head table thead th),
  :deep(.bk-table .bk-table-body table thead th) {
    text-align: center !important;
  }
  :deep(.bordered-outer) {
    border-bottom: none;
  }
  .table-pagination {
    border: 1px solid #dcdee5;
    border-top: none;
    height: 40px;
  }
</style>

<style lang="postcss">
  .bk-ellipsis {
    width: 100%;
    text-overflow: ellipsis;
    white-space: nowrap;
    overflow: hidden;
  }
  .group-table {
    .hover-highlight td:nth-child(1) {
      .cell {
        display: flex !important;
        justify-content: space-evenly;
      }
    }
    thead tr th:nth-child(1) {
      .cell {
        display: flex !important;
        justify-content: space-evenly;
      }
    }
  }
</style>
