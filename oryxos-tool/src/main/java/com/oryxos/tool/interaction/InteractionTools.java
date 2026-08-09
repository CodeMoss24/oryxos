package com.oryxos.tool.interaction;

import org.springframework.stereotype.Component;

/**
 * 交互内置 Tool:ask_user。委托 UserInteraction 抽象拿回答——拿到原样回传,拿不到抛异常 (由 AnnotatedToolAdapter 映射为
 * failure,不静默)。
 */
@Component
public class InteractionTools {

  private final UserInteraction userInteraction;

  public InteractionTools(UserInteraction userInteraction) {
    this.userInteraction = userInteraction;
  }

  public String askUser(String question) {
    return userInteraction.ask(question);
  }
}
