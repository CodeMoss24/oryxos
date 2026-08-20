package com.oryxos.core.exception;

/**
 * Profile 派生/注册阶段的校验失败(第 30 节 harness 点名类型)。
 *
 * <p>语义:Agent 目录内容校验不通过(如缺必填字段、配置非法)导致注册失败。 实际校验路径(AgentLoader.deriveProfile)抛
 * IllegalArgumentException(与启动扫描同一异常类型),本类型供编排层回滚路径与测试使用—— 任何 RuntimeException 都会触发 create 的回滚,Web
 * 层按 400 映射。
 */
public class ProfileValidationException extends RuntimeException {

  public ProfileValidationException(String message) {
    super(message);
  }
}
