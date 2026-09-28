package com.wzbc.studyroom;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用入口。
 *
 * <p>四个注解都不可省：
 * <ul>
 *   <li>{@code @MapperScan} —— 不写则 Mapper 接口不会被注册成 Bean，启动时报找不到 Bean；</li>
 *   <li>{@code @ConfigurationPropertiesScan} —— 让 JwtProperties 这类配置类生效，替代逐个 @EnableConfigurationProperties；</li>
 *   <li>{@code @EnableScheduling} —— 定时任务（如统计聚合、清理）需要。</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.wzbc.studyroom.mapper")
@ConfigurationPropertiesScan
@EnableScheduling
public class StudyRoomApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyRoomApplication.class, args);
    }
}
