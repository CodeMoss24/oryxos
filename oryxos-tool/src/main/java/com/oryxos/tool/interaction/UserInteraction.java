package com.oryxos.tool.interaction;

/**
 * human-in-the-loop 抽象:Agent 中途向用户提问的统一通道(ask_user 工具的后端)。
 *
 * <p>接口先行——核心阶段只有 Console/Unsupported 两个实现(按交互环境条件装配),未来挂 IM/审批流 通道不改调用方。拿不到回答必须抛异常,绝不静默卡住:异常由
 * AnnotatedToolAdapter 映射为 ToolResult.failure, 模型能看到失败原因。
 */
public interface UserInteraction {

  /** 向用户提问并等待回答。失败抛异常(不返回 null/空串糊弄)。 */
  String ask(String question);
}
