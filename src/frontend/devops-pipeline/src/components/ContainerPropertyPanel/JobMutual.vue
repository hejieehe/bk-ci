<template>
    <accordion
        show-checkbox
        :show-content="enableMutual"
        key="otherChoice"
        is-version="true"
    >
        <header
            class="var-header"
            slot="header"
        >
            <span>{{ $t('editPage.mutexGroup') }}</span>
            <!--<i class="devops-icon icon-angle-down" style="display:block"></i>-->
            <input
                class="accordion-checkbox"
                :disabled="disabled"
                :checked="enableMutual"
                type="checkbox"
                @click.stop
                @change="toggleMutual"
            />
        </header>
        <div
            slot="content"
            class="bk-form bk-form-vertical"
            v-if="enableMutual"
        >
            <template v-for="(obj, key) in optionModel">
                <form-field
                    :key="key"
                    v-if="!isHidden(obj, mutexGroup) && key !== 'enable' && enableMutual"
                    :desc="obj.desc"
                    :required="obj.required"
                    :label="obj.label"
                    :is-error="errors.has(key)"
                    :error-msg="errors.first(key)"
                >
                    <component
                        :is="obj.component"
                        :name="key"
                        v-validate.initial="Object.assign({}, obj.rule, { required: !!obj.required })"
                        :handle-change="handleUpdateJobMutual"
                        :value="mutexGroup[key]"
                        :disabled="disabled"
                        v-bind="obj"
                    ></component>
                </form-field>
            </template>
        </div>
    </accordion>
</template>

<script>
    import atomMixin from '@/components/AtomPropertyPanel/atomMixin'
    import validMixins from '@/components/validMixins'
    import jobOptionConfigMixin from '@/store/modules/common/jobOptionConfigMixin'
    import { mapActions } from 'vuex'
    export default {
        name: 'job-mutual',
        mixins: [atomMixin, validMixins, jobOptionConfigMixin],
        props: {
            mutexGroup: {
                type: Object,
                default: {}
            },
            disabled: {
                type: Boolean,
                default: false
            },
            updateContainerParams: {
                type: Function,
                required: true
            }
        },
        computed: {
            optionModel () {
                return this.JOB_MUTUAL || {}
            },
            enableMutual () {
                return this.mutexGroup && this.mutexGroup.enable
            }
        },
        created () {
            if (!this.disabled) {
                this.initOptionConfig()
            }
        },
        methods: {
            ...mapActions('atom', [
                'setPipelineEditing'
            ]),
            handleUpdateJobMutual (name, value) {
                this.setPipelineEditing(true)
                this.updateContainerParams('mutexGroup',
                                           Object.assign(this.mutexGroup || {}, { [name]: value })
                )
            },
            toggleMutual (e) {
                const enable = e.target.checked
                this.handleUpdateJobMutual('enable', enable)
            },
            initOptionConfig () {
                this.updateContainerParams('mutexGroup', this.getJobOptionDefault(this.JOB_MUTUAL, this.mutexGroup))
            }
        }
    }
</script>
