package com.atguigu.gulimall.order;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 使用RabbitMQ
 *  1.引入amqp场景：RabbitAutoconfiguration就会自动生效
 *  2.给容器中自动配置了
 *      RabbitTemplate、AmqpAdmin、CachingConnectionFactory、RabbitMessagingTemplate
 *      所有的属性都是spring.rabbitmq
 *      @ConfigurationProperties(prefix="spring.rabbitmq")
 *      public class RabbitProperties
 *  3.给配置文件中配置 spring.rabbitmq信息
 *  4.@EnableRabbit: @EnableXxxx  开启功能
 *  5.监听消息，使用@RabbitListener；必须有@EnableRabbit
 *      @RabbitListener：类+方法上（监听哪些队列）
 *      @RabbitHandler：标在方法上（重载区分不同的消息）
 */
@EnableRabbit
@SpringBootApplication
public class GulimallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallOrderApplication.class, args);
    }

}
