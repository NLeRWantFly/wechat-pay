package com.linxuelifang.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author NLER
 * @date 2023/1/9 16:04
 */
@ConfigurationProperties("wechat.pay.v3")
@Data
public class WechatPayProperties {

    /** 小程序id */
    private String appid;

    /** 微信小程序商家号 v3密钥 */
    private String appV3Secret;

    /** 微信小程序商家号 */
    private String mchId;

    /** 回调地址 */
    private String doMain;

    /** 证书路径，如果放在resource根地址下，可以直接写文件名：例如apiclient_cert.p12 */
    private String certPath;

    /** 证书序列号 */
    private String merchantSerialNumber;

    /** 商家密钥地址 如果放在resource根地址下，可以直接写文件名 例如: apiclient_key.pem */
    private String merchantPrivateKey;
}
