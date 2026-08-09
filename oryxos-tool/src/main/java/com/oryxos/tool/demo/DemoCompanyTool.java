package com.oryxos.tool.demo;

import org.springframework.stereotype.Component;

/**
 * 方式三演示工具(Plugin Tool 三档接入的最重档):业务方自研的 Java Bean,普通方法经 ToolConfiguration 用
 * FunctionCallback.builder().method(...) 装配注册成工具(与内置工具同一管道)。
 *
 * <p>现实中这里是企业业务能力的载体——查订单、查库存、内部系统接口。演示用 mock 数据, 只展示"加一个 Bean + 装配一行 = 工具立刻可用"的接入成本。
 */
@Component
public class DemoCompanyTool {

  /** 问候用户——最简单的接入演示。 */
  public String hello(String name) {
    return "你好, " + name + "!这里是方式三示例工具 demo_hello(业务方 Java 方法直接注册)。";
  }

  /** 模拟行情查询——演示带业务含义的工具。 */
  public String quoteStock(String symbol) {
    return "[" + symbol + "] 模拟行情: ¥42.00  +1.2%(示例数据,仅演示接入链路)";
  }
}
