package com.linxuelifang.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @author NLER
 * @date 2023/1/9 16:01
 */
@Configuration
// 注入所有Bean
@ComponentScan({"com.linxuelifang"})
@EnableConfigurationProperties(WechatPayProperties.class)
public class WeChatPayAutoConfiguration {



}
