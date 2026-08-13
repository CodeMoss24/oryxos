package com.oryxos.web;

import org.springframework.boot.SpringBootConfiguration;

/** 测试专用配置锚点:oryxos-web 自身无启动类,@WebMvcTest 切片需要它定位上下文(不进产物)。 */
@SpringBootConfiguration
public class WebTestApplication {}
