/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package org.dromara.soul.admin.service.sync;

import java.util.Collections;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.dromara.soul.admin.listener.DataChangedEvent;
import org.dromara.soul.admin.service.AppAuthService;
import org.dromara.soul.admin.service.MetaDataService;
import org.dromara.soul.admin.service.PluginService;
import org.dromara.soul.admin.service.RuleService;
import org.dromara.soul.admin.service.SelectorService;
import org.dromara.soul.admin.service.SyncDataService;
import org.dromara.soul.admin.transfer.PluginTransfer;
import org.dromara.soul.admin.vo.PluginVO;
import org.dromara.soul.common.dto.PluginData;
import org.dromara.soul.common.dto.RuleData;
import org.dromara.soul.common.dto.SelectorData;
import org.dromara.soul.common.enums.ConfigGroupEnum;
import org.dromara.soul.common.enums.DataEventTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The type sync data service.
 *
 * 数据同步服务 ， 其实这里还不能够叫事件同步， 只能说是事件通知，
 * 利用 ApplicationEventPublisher，分离了事件的发布者和接收者
 * DataChangedEventDispatcher接收到事件后，才开始真正的数据同步
 *
 *
 * @author xiaoyu(Myth)
 */
@Service("syncDataService")
public class SyncDataServiceImpl implements SyncDataService {

    private final AppAuthService appAuthService;

    private final MetaDataService metaDataService;

    /**
     * The Plugin service.
     */
    private final PluginService pluginService;

    /**
     * The Selector service.
     */
    private final SelectorService selectorService;

    /**
     * The Rule service.
     */
    private final RuleService ruleService;

    /**
     *
     * spring 容器，eventpublisher, 用来发布事件
     *
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Instantiates a new Sync data service.
     *
     * @param appAuthService  the app auth service
     * @param pluginService   the plugin service
     * @param selectorService the selector service
     * @param ruleService     the rule service
     * @param eventPublisher  the event publisher
     * @param metaDataService the meta data service
     */
    @Autowired
    public SyncDataServiceImpl(final AppAuthService appAuthService,
                               final PluginService pluginService,
                               final SelectorService selectorService,
                               final RuleService ruleService,
                               final ApplicationEventPublisher eventPublisher,
                               final MetaDataService metaDataService) {
        this.appAuthService = appAuthService;
        this.pluginService = pluginService;
        this.selectorService = selectorService;
        this.ruleService = ruleService;
        this.eventPublisher = eventPublisher;
        this.metaDataService = metaDataService;
    }

    /**
     *
     * 同步所有插件信息    DataChangedEventDispatcher接收
     *
     * @param type the type
     * @return
     */
    @Override
    public boolean syncAll(final DataEventTypeEnum type) {
        // 授权信息同步
        appAuthService.syncData();
        //插件信息同步
        List<PluginData> pluginDataList = pluginService.listAll();
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.PLUGIN, type, pluginDataList));
        // 选择器同步
        List<SelectorData> selectorDataList = selectorService.listAll();
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.SELECTOR, type, selectorDataList));
        // 规则同步
        List<RuleData> ruleDataList = ruleService.listAll();
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.RULE, type, ruleDataList));

        metaDataService.syncData();
        return true;
    }

    /**
     *
     *  同步指定插件信息
     *
     * @param pluginId the plugin id
     * @return
     */
    @Override
    public boolean syncPluginData(final String pluginId) {
        // 从数据库中查出插件信息
        PluginVO pluginVO = pluginService.findById(pluginId);
        // 利用 ApplicationEventPublisher 发送消息
        eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.PLUGIN, DataEventTypeEnum.UPDATE,
                Collections.singletonList(PluginTransfer.INSTANCE.mapDataTOVO(pluginVO))));
        // 选择器
        List<SelectorData> selectorDataList = selectorService.findByPluginId(pluginId);
        if (CollectionUtils.isNotEmpty(selectorDataList)) {
            eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.SELECTOR, DataEventTypeEnum.REFRESH, selectorDataList));
            for (SelectorData selectData : selectorDataList) {
                // 规则
                List<RuleData> ruleDataList = ruleService.findBySelectorId(selectData.getId());
                eventPublisher.publishEvent(new DataChangedEvent(ConfigGroupEnum.RULE, DataEventTypeEnum.REFRESH, ruleDataList));
            }
        }
        return true;
    }
}
