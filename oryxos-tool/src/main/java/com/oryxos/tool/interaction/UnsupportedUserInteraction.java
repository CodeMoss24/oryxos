package com.oryxos.tool.interaction;

/** 无人值守环境实现(Web Service / 定时任务):没有真人可问,ask 一律抛异常——绝不静默卡住, 让模型立刻知道当前环境不支持交互并改走别的路。 */
public class UnsupportedUserInteraction implements UserInteraction {

  @Override
  public String ask(String question) {
    throw new RuntimeException("当前环境不支持用户交互(ask_user),问题: " + question);
  }
}
