package com.oryxos.boot;

import com.oryxos.cli.OryxOsCli;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import picocli.CommandLine;
import picocli.spring.PicocliSpringFactory;

@Configuration
public class PicocliConfig {

  /** 用 Spring 工厂创建命令实例:子命令类标 @Component 后由容器实例化,字段注入才生效。 否则 Picocli 反射创建的命令类上 @Autowired 是 null。 */
  @Bean
  public CommandLine commandLine(OryxOsCli command, ApplicationContext context) {
    return new CommandLine(command, new PicocliSpringFactory(context));
  }
}
