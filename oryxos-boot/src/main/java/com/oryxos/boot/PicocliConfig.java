package com.oryxos.boot;

import com.oryxos.cli.OryxOsCli;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import picocli.CommandLine;

@Configuration
public class PicocliConfig {

  @Bean
  public CommandLine commandLine(OryxOsCli command) {
    return new CommandLine(command);
  }
}
