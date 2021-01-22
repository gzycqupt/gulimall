package com.atguigu.gulimall.order.config;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gulimall.order.vo.PayVo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "alipay")
@Component
@Data
public class AlipayTemplate {

    //在支付宝创建的应用的id
    private   String app_id = "2021000117602342";

    // 商户私钥，您的PKCS8格式RSA2私钥
    private String merchant_private_key = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCleg7gaCFGsz4fx74v1dHu6IOxeevK2AZe+hY4N5DyWJbqlAVF4C3IF9S1HDOk0tBfLtYt++nJTVGDjXDbJ33blF7XOCgZx6dlUovZ0K2m8gcqYkLV83q/mPoa5HC049oid3GSshXL+iNv75IOgw3P9vAm6gqb/GACRqBTk1ZCQHd07Ykaf0ZBGwZRFGsJc6Z95tFcUHeHLLfNCtbGnsuzcg8Ox8TZrnHj+AIvcxxVsS+nVLzSUReQoYPJdC0o3lWxUOEYLp87VQ2aFzZLTNlR2t2peb090RD3Wo/FbUmAAiKklP88Pde1hEECzPHWFvOLIr1a7DuBdqUJcNixtvK9AgMBAAECggEAK1h4syGzrcBgApUg5Yjd7/XqIUAtmrnRmWlrDpjHmCPKjHHvm6kodOSBLFvRz1gFixlKs0rsp3VgZ9RR/qYumqkX2spz/qTE3Y6s4YZ/Jl75r4mDtmWL/spEoWy7dTbGOgcNsf6CJSdFxxl7Kd/8a0BlEdx2oPuP7qivcJUEoF+zwg4uFwISTIV8JgKBixBSrk1rgpC4WSENbYWqSqmxgxXXzF62QWSUZfTNa4MDw7rAWn7D3w0NSFeneQqmqP7I2NdOrjyeXCvVZVpxtFkeSulpyXrDpTpvnXJonAMOmP+SRLgOCeUvebFq4JhqT37wl3/tvcw1HjqQ001zCoqXBQKBgQDtGo8myWtksJWBaNI8/tsPRcamqUnOJJVeSD1SqhhNMuy0YFF++2Z7fWGVE2wgVq3dCz3pkXW0jCYGZ8AV33PJQZxJDIu2xxrHW81zp3/kw3Eg9w4vr3uYl9A5ZI/c2m7Gb1+wWuMiO5MwEhm5Uovy8VvMzXnUL0Mjq1IWeo+LbwKBgQCyqiaM1DbcYZDbgr2SNsS0hw4cWpero54vproaXPoIQ/Tmc5w+zFvjdD6Kz2a3qCBEK8C3R9J6qZ9bT79Hl9ViBC9rRUDOSTezdVybz6ziXODvlFVvpFffmR3VnnkGhrwB2YW74+LWie3lcNZe2tI1GbeXXa72vcxiwS29jEs+kwKBgDHj6sdAjqZrOao3QjWvcqyuA/TBnlQQApYKIU0pmV91HbV+pudPQustIMFbit7rHj5qxSZGgzvwWXfzkMmJcTWh5p7AUw2sq4fTW1HApvqd3UoQch26kQk0uI27CUJxe+mpObtEBJMJchklVeBW7De8bZKUkdQB9eClhepQ869lAoGAfEIyhYeikCubH9ASIHrO2++CNN4c1lb5UZLXpDY9/zmkPhLx/AsNbWgPh2MvwVPA/Sig49ej34PRPSR9mEFVOtJGWIjVxaLOhpV9TzBkwRpvprC3qunV48EOKuAItC1I3NB4XnDj9un+9rA/p8DwqdW1BiU15o2idJx+P3IvctkCgYAbgqEROLuytlGxT1TfvhZmjZuRWap762qCwntU4pvhMKzi4jo4ZI0m38hl7NM2YO08uXir4eRtJLAr7W7/n/4ZjrCC6oyzemLpd8svc9Ho8rZtwlbpkazPtTHmocZiqNTfN6acTa5ZZhhJD6dlvHKV+Io+t4o1rGXXmffhwC+vwg==";

    // 支付宝公钥,查看地址：https://openhome.alipay.com/platform/keyManage.htm 对应APPID下的支付宝公钥。
    private String alipay_public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAklep/VqmZlk0pu2kVmxRw1W2bgLu2fRfnAvXpn+cSvSpfSEeWGy84mkgUUNihlqQ5+olEKs3mzZmLNFIeQp75sME9YW0c3khMG7mpZjvHeCEXMp8nhjMHm9KIJ2r6nI8r8j0kkuZ9ugYEFcbbTAlbsgE8i2aVV6EFB2vFEE4PvJ0CI49fLeblkEcxS46sWmgj1vaSkb089JEfhOmZKGCT13WRY59HIZ+bHFT4rLUtUQqda7HHKfstKBOEiefKDZkPkQrpQqqY85Mz5yQQ7hZJdcwUbfbcsEr+XWHtyjBAPEUjskW2ILhz7hiT1S3/dV6t8LCFV4yL7cDIxn4oUUXMQIDAQAB";


    // 服务器[异步通知]页面路径  需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    // 支付宝会悄悄的给我们发送一个请求，告诉我们支付成功的信息
    private  String notify_url = "http://tvefnsyfbd.52http.net/payed/notify";

    // 页面跳转同步通知页面路径 需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    //同步通知，支付成功，一般跳转到成功页
    private  String return_url = "http://member.gulimall.com/memberOrder.html";

    // 签名方式
    private  String sign_type = "RSA2";

    // 字符编码格式
    private  String charset = "utf-8";

    // 支付宝网关； https://openapi.alipaydev.com/gateway.do
    private  String gatewayUrl = "https://openapi.alipaydev.com/gateway.do";

    public  String pay(PayVo vo) throws AlipayApiException {

        //AlipayClient alipayClient = new DefaultAlipayClient(AlipayTemplate.gatewayUrl, AlipayTemplate.app_id, AlipayTemplate.merchant_private_key, "json", AlipayTemplate.charset, AlipayTemplate.alipay_public_key, AlipayTemplate.sign_type);
        //1、根据支付宝的配置生成一个支付客户端
        AlipayClient alipayClient = new DefaultAlipayClient(gatewayUrl,
                app_id, merchant_private_key, "json",
                charset, alipay_public_key, sign_type);

        //2、创建一个支付请求 //设置请求参数
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setReturnUrl(return_url);
        alipayRequest.setNotifyUrl(notify_url);

        //商户订单号，商户网站订单系统中唯一订单号，必填
        String out_trade_no = vo.getOut_trade_no();
        //付款金额，必填
        String total_amount = vo.getTotal_amount();
        //订单名称，必填
        String subject = vo.getSubject();
        //商品描述，可空
        String body = vo.getBody();

        alipayRequest.setBizContent("{\"out_trade_no\":\""+ out_trade_no +"\","
                + "\"total_amount\":\""+ total_amount +"\","
                + "\"subject\":\""+ subject +"\","
                + "\"body\":\""+ body +"\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");

        String result = alipayClient.pageExecute(alipayRequest).getBody();

        //会收到支付宝的响应，响应的是一个页面，只要浏览器显示这个页面，就会自动来到支付宝的收银台页面
        System.out.println("支付宝的响应："+result);

        return result;

    }
}
