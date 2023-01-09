package com.linxuelifang.utils;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linxuelifang.config.WechatPayProperties;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.ScheduledUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import lombok.Data;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.jws.Oneway;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * @author NLER
 * @date 2023/1/8 21:59
 */
@Component
@Data
public class MyHttpClient {

    @Autowired
    WechatPayProperties wechatPayProperties;

    /** 创建一个带有微信支付资格的HttpClient */
    public HttpClient createClient() throws FileNotFoundException {
        /**
         * 此处生成商户API私钥
         */
        PrivateKey merchantPrivateKey = PemUtil.loadPrivateKey(
                new FileInputStream("src/main/resources/"+wechatPayProperties.getMerchantPrivateKey()));
        /**
         * 此处使用定时更新平台证书功能，高版本已经废弃
         */
        String apiV3Key = wechatPayProperties.getAppV3Secret();
        ScheduledUpdateCertificatesVerifier verifier = new ScheduledUpdateCertificatesVerifier(
                // 商家id 商家密钥证书序列号 商家密钥（通过上面解析出来的）
                new WechatPay2Credentials(wechatPayProperties.getMchId(), new PrivateKeySigner(wechatPayProperties.getMerchantSerialNumber(), merchantPrivateKey)),
                apiV3Key.getBytes(StandardCharsets.UTF_8));
        /**
         * 生成httpClient
         */
        WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                // 商家id 商家密钥证书序列号 商家密钥
                .withMerchant(wechatPayProperties.getMchId(), wechatPayProperties.getMerchantSerialNumber(), merchantPrivateKey)
                // 微信支付平台证书列表
                //  .withWechatPay(wechatpayCertificates);
                .withValidator(new WechatPay2Validator(verifier));
        // ... 接下来，你仍然可以通过builder设置各种参数，来配置你的HttpClient
        // 通过WechatPayHttpClientBuilder构造的HttpClient，会自动的处理签名和验签
        CloseableHttpClient httpClient = builder.build();
        return httpClient;
    }

    public String getSign(String appId, long timestamp, String nonceStr, String pack) throws Exception{
        String message = buildMessage(appId, timestamp, nonceStr, pack);
        String paySign= sign(message.getBytes("utf-8"));
        return paySign;
    }

    private String buildMessage(String appId, long timestamp, String nonceStr, String pack) {
        return appId + "\n"
                + timestamp + "\n"
                + nonceStr + "\n"
                + pack + "\n";
    }
    private String sign(byte[] message) throws Exception{
        Signature sign = Signature.getInstance("SHA256withRSA");
        //这里需要一个PrivateKey类型的参数，就是商户的私钥。
        sign.initSign(PemUtil.loadPrivateKey(
                new FileInputStream("src/main/resources/"+wechatPayProperties.getMerchantPrivateKey())));
        sign.update(message);
        return Base64.getEncoder().encodeToString(sign.sign());
    }

    /** 开始创建订单（JSAPI下单），并且给小程序提供支付参数*/
    public String readyToPay(String out_trade_no,String payerId,String description,String notify_url,long amount) throws Exception {
        /** 通过Wechat-apache-client下单，获取prepay_id*/
        HttpClient client = createClient();
        HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi");
        httpPost.addHeader("Accept", "application/json");
        httpPost.addHeader("Content-type","application/json; charset=utf-8");
        // 创建下单请求
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        String appid = wechatPayProperties.getAppid();
        String mchid = wechatPayProperties.getMchId();
        // out_trade_no 订单号，交给外部填充
        // 生成随机字符串
        String nonceStr = RandomUtil.randomString(32);
        System.out.println(nonceStr);
        // 创建结点（请求参数）
        ObjectNode rootNode = objectMapper.createObjectNode();
        // 商家号/小程序id/商品描述/回调地址/订单号
        rootNode.put("mchid",mchid)
                .put("appid", appid)
                .put("description", description)
                .put("notify_url", notify_url)
                .put("out_trade_no", out_trade_no);
        // 数量 - 单位为分 1 -》 0.01
        rootNode.putObject("amount")
                // 实际为0.01元
                .put("total", amount);
        // 支付者的openid
        rootNode.putObject("payer")
                .put("openid", payerId);

        // 生成Sign
        /**
         * MD5(appId=wxd678efh567hg6787&nonceStr=5K8264ILTKCH16CQ2502SI8ZNMTM67VS&package=prepay_id=wx2017033010242291fcfe0db70013231072&signType=MD5&timeStamp=1490840662&key=qazwsxedcrfvtgbyhnujmikolp111111)
         */
        objectMapper.writeValue(bos, rootNode);
        httpPost.setEntity(new StringEntity(bos.toString("UTF-8"), "UTF-8"));
        CloseableHttpResponse response = (CloseableHttpResponse) client.execute(httpPost);
        String bodyAsString = EntityUtils.toString(response.getEntity());
        /** 获取返回的prepay_id */
        System.out.println(bodyAsString);
        JSONObject jsonObject = JSONObject.parseObject(bodyAsString);
        // 获取统一下单接口给出的prepay_id
        String prepay_id = jsonObject.getString("prepay_id");
        if (prepay_id == null)
        {
            // prepay_id没生成的话，要检查是什么问题
            throw new Exception(jsonObject.getString("message"));
        }
        else {
            // 可以去让 订单和prepay_id 做对应

            String pack = "prepay_id="+prepay_id;
            /** 下单结束，给小程序wx.preparyPayment提供请求参数 */
            // 时间戳
            long timeStamp = Calendar.getInstance().getTimeInMillis();
            // 算法生成PaySign
            String paySign = getSign(appid, timeStamp, nonceStr, pack);
            Map<String, Object> map = new HashMap<>();
            // noncestr随机生成
            map.put("nonceStr",nonceStr);
            // JSAPI统一下单后获取的prepay_id
            map.put("package","prepay_id="+prepay_id);
            // MD5算法
            map.put("signType","MD5");
            // 算法生成的paySign
            map.put("paySign",paySign);
            // 时间戳
            map.put("timeStamp",timeStamp);
            // 使用fastJson封装
            return JSONObject.toJSONString(map);
        }
    }

    public String getPayState() throws IOException, URISyntaxException {
        HttpClient client = createClient();
        URIBuilder uriBuilder = new URIBuilder("https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/1217752501201407033233368010?mchid=1636219373");
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.addHeader("Accept", "application/json");
        CloseableHttpResponse response = (CloseableHttpResponse) client.execute(httpGet);
        // 获取到的JSON
        String bodyAsString = EntityUtils.toString(response.getEntity());
        JSONObject jsonObject = JSONObject.parseObject(bodyAsString);
        // 返回交易状态
        String trade_state = jsonObject.getString("trade_state");
        return trade_state;
    }
}
