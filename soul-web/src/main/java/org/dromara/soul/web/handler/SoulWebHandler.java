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

package org.dromara.soul.web.handler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.dromara.soul.metrics.api.HistogramMetricsTrackerDelegate;
import org.dromara.soul.metrics.enums.MetricsLabelEnum;
import org.dromara.soul.metrics.facade.MetricsTrackerFacade;
import org.dromara.soul.plugin.api.SoulPlugin;
import org.dromara.soul.plugin.api.SoulPluginChain;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * this is web handler request starter.
 *
 * @author xiaoyu(Myth)
 */
public final class SoulWebHandler implements WebHandler {
    
    private List<SoulPlugin> plugins;
    
    private Scheduler scheduler;
    
    /**
     * Instantiates a new Soul web handler.
     *
     * @param plugins the plugins
     */
    public SoulWebHandler(final List<SoulPlugin> plugins) {
        this.plugins = plugins;
        String schedulerType = System.getProperty("soul.scheduler.type", "fixed");
        if (Objects.equals(schedulerType, "fixed")) {
            int threads = Integer.parseInt(System.getProperty(
                    "soul.work.threads", "" + Math.max((Runtime.getRuntime().availableProcessors() << 1) + 1, 16)));
            scheduler = Schedulers.newParallel("soul-work-threads", threads);
        } else {
            scheduler = Schedulers.elastic();
        }
    }
    
    /**
     * Handle the web server exchange.
     *
     * @param exchange the current server exchange
     * @return {@code Mono<Void>} to indicate when request handling is complete
     */
    @Override
    public Mono<Void> handle(@NonNull final ServerWebExchange exchange) {
        // 调用统计
        MetricsTrackerFacade.getInstance().counterInc(MetricsLabelEnum.REQUEST_TOTAL.getName());
        Optional<HistogramMetricsTrackerDelegate> startTimer = MetricsTrackerFacade.getInstance().histogramStartTimer(MetricsLabelEnum.REQUEST_LATENCY.getName());

        return new DefaultSoulPluginChain(plugins).execute(exchange).subscribeOn(scheduler)
                .doOnSuccess(t -> startTimer.ifPresent(time -> MetricsTrackerFacade.getInstance().histogramObserveDuration(time)));
    }

    /**
     *
     * 插件调用链
     *
     */
    private static class DefaultSoulPluginChain implements SoulPluginChain {
        
        private int index;

        //插件集合
        private final List<SoulPlugin> plugins;
        
        /**
         * Instantiates a new Default soul plugin chain.
         *
         * @param plugins the plugins
         */
        DefaultSoulPluginChain(final List<SoulPlugin> plugins) {
            this.plugins = plugins;
        }
        
        /**
         * Delegate to the next {@code WebFilter} in the chain.
         *
         * @param exchange the current server exchange
         * @return {@code Mono<Void>} to indicate when request handling is complete
         */
        @Override
        public Mono<Void> execute(final ServerWebExchange exchange) {
            return Mono.defer(() -> {
                if (this.index < plugins.size()) {
                    // 需要执行的插件
                    SoulPlugin plugin = plugins.get(this.index++);
                    // 是否跳过插件
                    Boolean skip = plugin.skip(exchange);
                    if (skip) {
                        // 递归 调用，相当于跳过本次调用
                        return this.execute(exchange);
                    } else {
                        return plugin.execute(exchange, this);
                    }
                } else {
                    return Mono.empty();
                }
            });
        }
    }
}
